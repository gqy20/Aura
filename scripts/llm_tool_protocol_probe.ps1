param(
    [ValidateSet('GLM', 'MODELSCOPE')]
    [string]$Provider = 'GLM',
    [ValidateSet('All', 'BaselineStream', 'SingleToolStream', 'DualToolStream', 'FollowupCanonical', 'FollowupSplit', 'FollowupAppLike', 'FollowupMissingResult', 'ForcedToolChoice')]
    [string]$Scenario = 'All',
    [string]$Model = '',
    [string]$EnvFile = (Join-Path $PSScriptRoot '..\.env')
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http

function Read-DotEnv([string]$Path) {
    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ($line -notmatch '^\s*([A-Za-z_][A-Za-z0-9_]*)=(.*)$') { continue }
        $name = $Matches[1]
        $value = $Matches[2].Trim()
        if (($value.StartsWith('"') -and $value.EndsWith('"')) -or
            ($value.StartsWith("'") -and $value.EndsWith("'"))) {
            $value = $value.Substring(1, $value.Length - 2)
        }
        $values[$name] = $value
    }
    return $values
}

function New-Tool([string]$Name, [string]$Property) {
    return [ordered]@{
        name = $Name
        description = "Return a deterministic test value for $Property."
        input_schema = [ordered]@{
            type = 'object'
            properties = [ordered]@{
                $Property = [ordered]@{ type = 'string'; description = "Test $Property" }
            }
            required = @($Property)
        }
    }
}

function New-BaseBody([bool]$Stream, [string]$PromptText) {
    return [ordered]@{
        model = $script:ResolvedModel
        max_tokens = 256
        stream = $Stream
        system = 'Protocol probe. Follow the user instruction exactly. Do not call tools unless explicitly requested.'
        messages = @([ordered]@{ role = 'user'; content = $PromptText })
    }
}

function Get-ResponseSummary([string]$Body, [bool]$Stream) {
    if (-not $Stream) {
        try {
            $json = $Body | ConvertFrom-Json
            $types = @($json.content | ForEach-Object { $_.type }) -join ','
            $toolInputs = @($json.content | Where-Object { $_.type -eq 'tool_use' } | ForEach-Object {
                $inputJson = ($_.input | ConvertTo-Json -Depth 20 -Compress)
                "$($_.name):$($inputJson.Substring(0, [Math]::Min(220, $inputJson.Length)))"
            }) -join ' | '
            $summary = "stop_reason=$($json.stop_reason) content_types=$types"
            if ($toolInputs) { $summary += " tool_inputs=[$toolInputs]" }
            return $summary
        } catch {
            return "non_json_body_length=$($Body.Length)"
        }
    }

    $eventCounts = @{}
    $toolStarts = New-Object System.Collections.Generic.List[string]
    $toolStops = New-Object System.Collections.Generic.List[string]
    $toolSequence = New-Object System.Collections.Generic.List[string]
    $jsonDeltaLengths = @{}
    foreach ($line in ($Body -split "`r?`n")) {
        if (-not $line.StartsWith('data: ')) { continue }
        $data = $line.Substring(6).Trim()
        if ($data -eq '[DONE]') { continue }
        try { $event = $data | ConvertFrom-Json } catch { continue }
        $type = [string]$event.type
        if (-not $eventCounts.ContainsKey($type)) { $eventCounts[$type] = 0 }
        $eventCounts[$type] += 1
        $index = [string]$event.index
        if ($type -eq 'content_block_start' -and $event.content_block.type -eq 'tool_use') {
            $toolStarts.Add("$index`:$($event.content_block.name)")
            $toolSequence.Add("start($index`:$($event.content_block.name))")
        }
        if ($type -eq 'content_block_delta' -and $event.delta.type -eq 'input_json_delta') {
            if (-not $jsonDeltaLengths.ContainsKey($index)) { $jsonDeltaLengths[$index] = 0 }
            $deltaLength = ([string]$event.delta.partial_json).Length
            $jsonDeltaLengths[$index] += $deltaLength
            $toolSequence.Add("delta($index`:$deltaLength)")
        }
        if ($type -eq 'content_block_stop') {
            $toolStops.Add($index)
            $toolSequence.Add("stop($index)")
        }
    }
    $counts = @($eventCounts.GetEnumerator() | Sort-Object Name | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join ' '
    $deltas = @($jsonDeltaLengths.GetEnumerator() | Sort-Object Name | ForEach-Object { "$($_.Name)=$($_.Value)" }) -join ','
    return "events=[$counts] tool_starts=[$($toolStarts -join ',')] tool_stops=[$($toolStops -join ',')] json_delta_lengths=[$deltas] sequence=[$($toolSequence -join ' ')]"
}

function Invoke-Probe([string]$Name, [hashtable]$Body) {
    $isStream = [bool]$Body.stream
    $jsonBody = $Body | ConvertTo-Json -Depth 20 -Compress
    $request = [System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::Post,
        "$($script:BaseUrl.TrimEnd('/'))/v1/messages"
    )
    $request.Headers.TryAddWithoutValidation('x-api-key', $script:ApiKey) | Out-Null
    $request.Headers.TryAddWithoutValidation('anthropic-version', '2023-06-01') | Out-Null
    $request.Content = [System.Net.Http.StringContent]::new($jsonBody, [Text.Encoding]::UTF8, 'application/json')
    $watch = [Diagnostics.Stopwatch]::StartNew()
    try {
        $response = $script:HttpClient.SendAsync($request).GetAwaiter().GetResult()
        $responseBody = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $watch.Stop()
        $status = [int]$response.StatusCode
        $summary = Get-ResponseSummary -Body $responseBody -Stream $isStream
        Write-Output "$Name status=$status duration_ms=$($watch.ElapsedMilliseconds) $summary"
        if ($status -ge 400) {
            $safeError = ($responseBody -replace '\s+', ' ').Trim()
            Write-Output "$Name error=$($safeError.Substring(0, [Math]::Min(400, $safeError.Length)))"
        }
        $response.Dispose()
    } catch {
        $watch.Stop()
        Write-Output "$Name exception=$($_.Exception.GetType().Name) duration_ms=$($watch.ElapsedMilliseconds) message=$($_.Exception.Message)"
    } finally {
        $request.Dispose()
    }
}

function Invoke-Scenario([string]$Name) {
    switch ($Name) {
        'BaselineStream' {
            Invoke-Probe $Name (New-BaseBody $true 'Reply with exactly: OK')
        }
        'SingleToolStream' {
            $body = New-BaseBody $true 'Call lookup_place exactly once with query=West Lake. Do not call any other tool.'
            $body.tools = @(New-Tool 'lookup_place' 'query')
            Invoke-Probe $Name $body
        }
        'DualToolStream' {
            $body = New-BaseBody $true 'Call lookup_place with query=West Lake and lookup_note with topic=sleep. Call both tools in this turn.'
            $body.tools = @(
                (New-Tool 'lookup_place' 'query'),
                (New-Tool 'lookup_note' 'topic')
            )
            Invoke-Probe $Name $body
        }
        'FollowupCanonical' {
            $body = New-BaseBody $false 'Use both tools.'
            $body.messages = @(
                [ordered]@{ role = 'user'; content = 'Use both tools.' },
                [ordered]@{
                    role = 'assistant'
                    content = @(
                        [ordered]@{ type = 'tool_use'; id = 'call-place'; name = 'lookup_place'; input = [ordered]@{ query = 'West Lake' } },
                        [ordered]@{ type = 'tool_use'; id = 'call-note'; name = 'lookup_note'; input = [ordered]@{ topic = 'sleep' } }
                    )
                },
                [ordered]@{
                    role = 'user'
                    content = @(
                        [ordered]@{ type = 'tool_result'; tool_use_id = 'call-place'; content = '{"name":"West Lake"}'; is_error = $false },
                        [ordered]@{ type = 'tool_result'; tool_use_id = 'call-note'; content = '{"count":0}'; is_error = $false }
                    )
                }
            )
            $body.tools = @((New-Tool 'lookup_place' 'query'), (New-Tool 'lookup_note' 'topic'))
            Invoke-Probe $Name $body
        }
        'FollowupSplit' {
            $body = New-BaseBody $false 'Use both tools.'
            $body.messages = @(
                [ordered]@{ role = 'user'; content = 'Use both tools.' },
                [ordered]@{ role = 'assistant'; content = @([ordered]@{ type = 'tool_use'; id = 'call-place'; name = 'lookup_place'; input = [ordered]@{ query = 'West Lake' } }) },
                [ordered]@{ role = 'assistant'; content = @([ordered]@{ type = 'tool_use'; id = 'call-note'; name = 'lookup_note'; input = [ordered]@{ _raw = '{"topic":睡眠,"limit":10}' } }) },
                [ordered]@{ role = 'user'; content = @([ordered]@{ type = 'tool_result'; tool_use_id = 'call-place'; content = '{"name":"West Lake"}'; is_error = $false }) },
                [ordered]@{ role = 'user'; content = @([ordered]@{ type = 'tool_result'; tool_use_id = 'call-note'; content = '{"count":0}'; is_error = $false }) },
                [ordered]@{ role = 'user'; content = 'Answer now without more tools.' }
            )
            $body.tools = @((New-Tool 'lookup_place' 'query'), (New-Tool 'lookup_note' 'topic'))
            Invoke-Probe $Name $body
        }
        'FollowupAppLike' {
            $validationError = @"
Tool with name 'lookup_note' failed to parse arguments due to the error: String literal for key 'topic' should be quoted at element: $.topic.
Use 'isLenient = true' in 'Json {}' builder to accept non-compliant JSON.
JSON input: {"topic":睡眠,"limit":10}
"@.Trim()
            $body = New-BaseBody $false 'Find coffee and inspect my sleep note.'
            $body.messages = @(
                [ordered]@{ role = 'user'; content = 'Find coffee and inspect my sleep note.' },
                [ordered]@{ role = 'assistant'; content = @([ordered]@{ type = 'text'; text = 'I will check both.' }) },
                [ordered]@{ role = 'assistant'; content = @([ordered]@{ type = 'tool_use'; id = 'call-note'; name = 'lookup_note'; input = [ordered]@{ topic = 'sleep' } }) },
                [ordered]@{ role = 'user'; content = @([ordered]@{ type = 'tool_result'; tool_use_id = 'call-note'; content = $validationError; is_error = $true }) },
                [ordered]@{ role = 'user'; content = 'Answer now without calling any more tools.' }
            )
            $body.tools = @((New-Tool 'lookup_place' 'query'), (New-Tool 'lookup_note' 'topic'))
            Invoke-Probe $Name $body
        }
        'FollowupMissingResult' {
            $body = New-BaseBody $false 'Use both tools.'
            $body.messages = @(
                [ordered]@{ role = 'user'; content = 'Use both tools.' },
                [ordered]@{
                    role = 'assistant'
                    content = @(
                        [ordered]@{ type = 'tool_use'; id = 'call-place'; name = 'lookup_place'; input = [ordered]@{ query = 'West Lake' } },
                        [ordered]@{ type = 'tool_use'; id = 'call-note'; name = 'lookup_note'; input = [ordered]@{ topic = 'sleep' } }
                    )
                },
                [ordered]@{
                    role = 'user'
                    content = @(
                        [ordered]@{ type = 'tool_result'; tool_use_id = 'call-note'; content = '{"count":0}'; is_error = $false }
                    )
                }
            )
            $body.tools = @((New-Tool 'lookup_place' 'query'), (New-Tool 'lookup_note' 'topic'))
            Invoke-Probe $Name $body
        }
        'ForcedToolChoice' {
            # MR 结构化记录 P0 前置验证:tool_choice 强制 + 嵌套 items 数组 schema,
            # 模拟 VisionRecordExtractor 的 submit_meal_record 协议(文本版,图片协议已由既有 vision 链路覆盖)。
            # glm-5.3-flash 是推理模型,thinking 块会消耗预算,max_tokens 需给足
            $body = New-BaseBody $false 'I had a bowl of beef noodle soup and one braised egg for lunch.'
            $body.max_tokens = 4096
            $body.tools = @(
                [ordered]@{
                    name = 'submit_meal_record'
                    description = 'Submit one structured meal record extracted from the user message. Call this tool exactly once with the full record.'
                    input_schema = [ordered]@{
                        type = 'object'
                        properties = [ordered]@{
                            meal_type = [ordered]@{ type = 'string'; enum = @('BREAKFAST','LUNCH','DINNER','SNACK') }
                            confidence = [ordered]@{ type = 'number'; description = '0.0 to 1.0' }
                            items = [ordered]@{
                                type = 'array'
                                items = [ordered]@{
                                    type = 'object'
                                    properties = [ordered]@{
                                        name = [ordered]@{ type = 'string' }
                                        portion_text = [ordered]@{ type = 'string' }
                                        calories = [ordered]@{ type = 'number' }
                                        protein_g = [ordered]@{ type = 'number' }
                                    }
                                    required = @('name','portion_text','calories','protein_g')
                                }
                            }
                        }
                        required = @('meal_type','items')
                    }
                }
            )
            $body.tool_choice = [ordered]@{ type = 'tool'; name = 'submit_meal_record' }
            # glm-5.3-flash 默认思考 ~55s;关 thinking 验证提取场景的可行时延
            $body.thinking = [ordered]@{ type = 'disabled' }
            Invoke-Probe $Name $body
        }
    }
}

if (-not (Test-Path -LiteralPath $EnvFile)) { throw "Env file not found: $EnvFile" }
$envValues = Read-DotEnv $EnvFile
if ($Provider -eq 'GLM') {
    $script:BaseUrl = 'https://open.bigmodel.cn/api/anthropic'
    $script:ApiKey = [string]$envValues.GLM_API_KEY
    $script:ResolvedModel = if ($Model) { $Model } elseif ($envValues.LLM_MODEL) { [string]$envValues.LLM_MODEL } else { 'glm-5.3-flash' }
} else {
    $script:BaseUrl = 'https://api-inference.modelscope.cn'
    $script:ApiKey = [string]$envValues.MODELSCOPE_API_KEY
    $script:ResolvedModel = if ($Model) { $Model } elseif ($envValues.LLM_MODEL) { [string]$envValues.LLM_MODEL } else { 'Qwen/Qwen3.5-397B-A17B' }
}
if ([string]::IsNullOrWhiteSpace($script:ApiKey)) { throw "$Provider API key is missing" }

$handler = [System.Net.Http.HttpClientHandler]::new()
$script:HttpClient = [System.Net.Http.HttpClient]::new($handler)
$script:HttpClient.Timeout = [TimeSpan]::FromSeconds(60)
try {
    Write-Output "provider=$Provider model=$script:ResolvedModel base_url=$script:BaseUrl"
    $scenarios = if ($Scenario -eq 'All') {
        @('BaselineStream', 'SingleToolStream', 'DualToolStream', 'FollowupCanonical', 'FollowupSplit', 'FollowupAppLike', 'FollowupMissingResult', 'ForcedToolChoice')
    } else {
        @($Scenario)
    }
    foreach ($name in $scenarios) { Invoke-Scenario $name }
} finally {
    $script:HttpClient.Dispose()
    $handler.Dispose()
}
