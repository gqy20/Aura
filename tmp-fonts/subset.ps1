$ErrorActionPreference = 'Continue'
$root = "D:\C\Desktop\ai\android\tmp-fonts"
$fontOut = "D:\C\Desktop\ai\android\app\src\main\res\font"
New-Item -ItemType Directory -Force -Path $fontOut | Out-Null

# 1. 生成 GB2312 字符表
$charsFile = Join-Path $root "chars.txt"
uv run --with fonttools python (Join-Path $root "build_gb2312.py") $charsFile 2>&1 | Out-Null
if (-not (Test-Path $charsFile)) { Write-Host "FAIL: chars.txt not built"; exit 1 }
$charCount = (Get-Content $charsFile -Raw).Length
Write-Host "built chars.txt ($charCount chars)"

# 2. 子集化三个字重
$pairs = @(
    @{ src = "MiSans-Regular.ttf";  dst = "misans_regular.ttf"  },
    @{ src = "MiSans-Medium.ttf";   dst = "misans_medium.ttf"   },
    @{ src = "MiSans-Semibold.ttf"; dst = "misans_semibold.ttf" }
)

foreach ($p in $pairs) {
    $srcPath = Join-Path $root $p.src
    $dstPath = Join-Path $fontOut $p.dst
    Write-Host "subsetting $($p.src) -> $($p.dst)..."
    # 用 cmd /c 包住,避免 PowerShell 把 uv 的 stderr 进度当作 error 中断脚本
    $cmd = "uv run --with `"fonttools[unicode,wtc]`" pyftsubset `"$srcPath`" --text-file=`"$charsFile`" --output-file=`"$dstPath`" --layout-features=* --name-IDs=* --glyph-names --symbol-cmap --drop-tables=DSIG"
    cmd /c $cmd 2>&1 | Out-Null
    if (Test-Path $dstPath) {
        $size = [math]::Round((Get-Item $dstPath).Length/1MB, 2)
        Write-Host "  -> $($p.dst) size=${size}MB"
    } else {
        Write-Host "  FAILED: $dstPath not created"
        exit 1
    }
}
Write-Host "All subsetted."
