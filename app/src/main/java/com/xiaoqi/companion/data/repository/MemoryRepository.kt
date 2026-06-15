package com.xiaoqi.companion.data.repository

import com.xiaoqi.companion.data.db.converter.MemoryType
import com.xiaoqi.companion.data.db.dao.MemoryDao
import com.xiaoqi.companion.data.db.dao.MemorySummaryDao
import com.xiaoqi.companion.data.db.entity.MemoryEntity
import com.xiaoqi.companion.data.db.entity.MemorySummaryEntity
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SaveMemoryRequest(
    val content: String,
    val type: MemoryType,
    val importance: Float = 0.5f,
    val confidence: Float = 0.7f,
    val source: String = "tool:save_memory",
    val sourceMessageIds: List<String> = emptyList(),
    val sensitivity: String = "normal",
    val expiresAt: Long? = null,
)

data class SaveMemoryResult(
    val memory: MemoryEntity,
    val merged: Boolean,
)

data class MemorySearchResult(
    val query: String,
    val memories: List<MemoryEntity>,
)

data class PromptMemoryContext(
    val memorySnippets: List<String>,
    val memoryIds: List<String>,
    val summarySnippets: List<String>,
    val summaryIds: List<String>,
)

class MemoryRepository @Inject constructor(
    private val memoryDao: MemoryDao,
    private val summaryDao: MemorySummaryDao,
) {

    suspend fun saveMemory(request: SaveMemoryRequest): SaveMemoryResult =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            val now = System.currentTimeMillis()
            val normalizedContent = request.content.trim()
            AppLogger.info(
                LogTags.Repo,
                "memory_save_started",
                "type" to request.type,
                "source" to request.source,
                "contentLength" to normalizedContent.length,
                "sourceMessageCount" to request.sourceMessageIds.size,
            )
            val sourceIdsJson = json.encodeToString(request.sourceMessageIds.map { it.trim() }.filter { it.isNotBlank() })
            val existing = findMergeTarget(normalizedContent, request.type)
            val entity = existing?.let {
                it.copy(
                    content = mergeContent(it.content, normalizedContent),
                    importance = maxOf(it.importance, request.importance.coerceIn(0f, 1f)),
                    confidence = maxOf(it.confidence, request.confidence.coerceIn(0f, 1f)),
                    source = mergeSource(it.source, request.source),
                    sourceMessageIds = mergeJsonStringLists(it.sourceMessageIds, sourceIdsJson),
                    updatedAt = now,
                    lastAccessed = now,
                    expiresAt = request.expiresAt ?: it.expiresAt,
                    sensitivity = strongestSensitivity(it.sensitivity, request.sensitivity),
                )
            } ?: MemoryEntity(
                id = UUID.randomUUID().toString(),
                type = request.type,
                content = normalizedContent,
                source = request.source,
                importance = request.importance.coerceIn(0f, 1f),
                confidence = request.confidence.coerceIn(0f, 1f),
                sourceMessageIds = sourceIdsJson,
                timestamp = now,
                updatedAt = now,
                expiresAt = request.expiresAt,
                sensitivity = normalizeSensitivity(request.sensitivity),
                lastAccessed = now,
            )
            memoryDao.insert(entity)
            SaveMemoryResult(memory = entity, merged = existing != null).also {
                AppLogger.info(
                    LogTags.Repo,
                    "memory_save_completed",
                    "memoryId" to entity.id,
                    "type" to entity.type,
                    "source" to entity.source,
                    "merged" to (existing != null),
                    "importance" to entity.importance,
                    "confidence" to entity.confidence,
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                )
            }
        }

    suspend fun searchMemories(
        query: String,
        type: MemoryType? = null,
        limit: Int = 10,
    ): MemorySearchResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        val safeLimit = limit.coerceIn(1, MAX_MEMORY_RESULTS)
        val candidateLimit = (safeLimit * CANDIDATE_MULTIPLIER).coerceAtMost(MAX_MEMORY_CANDIDATES)
        val candidates = memoryDao.searchByContent(
            pattern = cleanQuery.toLikePattern(),
            type = type,
            limit = candidateLimit,
        )
        val memories = candidates
            .map { memory -> MemoryHit(memory, scoreMemory(memory.content, cleanQuery, memory.importance, memory.confidence)) }
            .sortedWith(compareByDescending<MemoryHit> { it.score }.thenByDescending { it.memory.lastAccessed })
            .take(safeLimit)
            .map { it.memory }
        markMemoriesAccessed(memories)
        MemorySearchResult(query = cleanQuery, memories = memories)
    }

    suspend fun selectPromptContext(inputText: String): PromptMemoryContext =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            val query = inputText.trim()
            val relevant = searchPromptCandidates(query)
            val important = memoryDao.getPromptMemories(PROMPT_MEMORY_CANDIDATE_LIMIT)
                .filterUsableForPrompt(query, allowPrivateWhenRelevant = false)
            val recent = memoryDao.getRecentMemories(PROMPT_RECENT_LIMIT)
                .filterUsableForPrompt(query, allowPrivateWhenRelevant = false)
            val selectedMemories = selectMemoriesWithinTokenBudget(
                mergeMemoryBuckets(relevant, important, recent),
                PROMPT_MEMORY_TOKEN_BUDGET,
            )
            val selectedSummaries = selectSummariesWithinTokenBudget(
                searchPromptSummaries(query),
                PROMPT_SUMMARY_TOKEN_BUDGET,
            )

            markMemoriesAccessed(selectedMemories.map { it.memory })
            markSummariesAccessed(selectedSummaries.map { it.summary })

            PromptMemoryContext(
                memorySnippets = selectedMemories.map { it.snippet },
                memoryIds = selectedMemories.map { it.memory.id },
                summarySnippets = selectedSummaries.map { it.snippet },
                summaryIds = selectedSummaries.map { it.summary.id },
            ).also {
                AppLogger.debug(
                    LogTags.Repo,
                    "memory_prompt_context_selected",
                    "queryLength" to query.length,
                    "relevantCount" to relevant.size,
                    "importantCount" to important.size,
                    "recentCount" to recent.size,
                    "selectedMemoryCount" to it.memorySnippets.size,
                    "selectedSummaryCount" to it.summarySnippets.size,
                    "memoryTokenBudget" to PROMPT_MEMORY_TOKEN_BUDGET,
                    "summaryTokenBudget" to PROMPT_SUMMARY_TOKEN_BUDGET,
                    "durationMs" to (System.currentTimeMillis() - startedAt),
                )
            }
        }

    fun observeMemoriesPinnedFirst(): Flow<List<MemoryEntity>> = memoryDao.observeAllPinnedFirst()

    /**
     * 给定固定 id 插入一条 memory(供 debug seedDemoInsights 等需要固定 evidence id 的场景)。
     * 不走 merge / 重复检测 — **只用于 seed/migration**,不走用户主流程。
     */
    suspend fun insertMemoryWithId(entity: MemoryEntity) = withContext(Dispatchers.IO) {
        memoryDao.insert(entity)
        AppLogger.info(LogTags.Repo, "memory_insert_with_id", "memoryId" to entity.id)
    }

    suspend fun countAll(): Int = withContext(Dispatchers.IO) { memoryDao.countAll() }

    suspend fun clearAll(): Int = withContext(Dispatchers.IO) {
        val before = memoryDao.countAll()
        memoryDao.clearAll()
        AppLogger.info(LogTags.Repo, "memory_clear_all", "beforeCount" to before)
        before
    }

    suspend fun pinMemory(id: String): Unit = withContext(Dispatchers.IO) {
        memoryDao.setPinned(id, true)
        AppLogger.info(LogTags.Repo, "memory_pinned", "memoryId" to id)
    }

    suspend fun unpinMemory(id: String): Unit = withContext(Dispatchers.IO) {
        memoryDao.setPinned(id, false)
        AppLogger.info(LogTags.Repo, "memory_unpinned", "memoryId" to id)
    }

    suspend fun archiveMemory(id: String): Unit = withContext(Dispatchers.IO) {
        memoryDao.setArchived(id, true)
        AppLogger.info(LogTags.Repo, "memory_archived", "memoryId" to id)
    }

    suspend fun unarchiveMemory(id: String): Unit = withContext(Dispatchers.IO) {
        memoryDao.setArchived(id, false)
        AppLogger.info(LogTags.Repo, "memory_unarchived", "memoryId" to id)
    }

    suspend fun deleteMemory(id: String): Unit = withContext(Dispatchers.IO) {
        memoryDao.deleteById(id)
        AppLogger.info(LogTags.Repo, "memory_deleted_via_repo", "memoryId" to id)
    }

    private suspend fun findMergeTarget(content: String, type: MemoryType): MemoryEntity? {
        val keyTerms = content.keyTerms()
        if (keyTerms.isEmpty()) return null
        val pattern = keyTerms.first().toLikePattern()
        return memoryDao.findSimilar(pattern = pattern, type = type, limit = MERGE_CANDIDATE_LIMIT)
            .map { MemoryHit(it, similarityScore(it.content, content)) }
            .filter { it.score >= MERGE_THRESHOLD }
            .maxByOrNull { it.score }
            ?.memory
    }

    private suspend fun searchPromptCandidates(query: String): List<MemoryEntity> {
        if (query.isBlank()) return emptyList()
        val fullPhraseMatches = searchMemories(query = query, limit = PROMPT_RELEVANT_LIMIT).memories
        val termMatches = query.keyTerms()
            .flatMap { term -> searchMemories(query = term, limit = PROMPT_RELEVANT_LIMIT).memories }
        return (fullPhraseMatches + termMatches)
            .distinctBy { it.id }
            .filterUsableForPrompt(query, allowPrivateWhenRelevant = true)
            .take(PROMPT_RELEVANT_LIMIT)
    }

    private suspend fun searchPromptSummaries(query: String): List<MemorySummaryEntity> {
        val patterns = if (query.isBlank()) listOf("%") else listOf(query.toLikePattern()) + query.keyTerms().map { it.toLikePattern() }
        val candidates = patterns
            .flatMap { pattern ->
                summaryDao.searchByText(
                    pattern = pattern,
                    type = null,
                    limit = PROMPT_SUMMARY_CANDIDATES,
                )
            }
            .distinctBy { it.id }
        return candidates
            .map { summary -> SummaryHit(summary, scoreSummary(summary, query)) }
            .sortedWith(compareByDescending<SummaryHit> { it.score }.thenByDescending { it.summary.lastAccessed })
            .map { it.summary }
    }

    private suspend fun markMemoriesAccessed(memories: List<MemoryEntity>) {
        if (memories.isEmpty()) return
        val now = System.currentTimeMillis()
        memories.forEach { memoryDao.updateLastAccessed(it.id, now) }
    }

    private suspend fun markSummariesAccessed(summaries: List<MemorySummaryEntity>) {
        if (summaries.isEmpty()) return
        val now = System.currentTimeMillis()
        summaries.forEach { summaryDao.updateLastAccessed(it.id, now) }
    }

    private fun mergeMemoryBuckets(vararg buckets: List<MemoryEntity>): List<MemoryEntity> =
        buckets
            .flatMap { it }
            .distinctBy { it.id }

    private fun selectMemoriesWithinTokenBudget(
        memories: List<MemoryEntity>,
        tokenBudget: Int,
    ): List<PromptMemorySelection> {
        if (memories.isEmpty() || tokenBudget <= 0) return emptyList()

        val selected = mutableListOf<PromptMemorySelection>()
        var usedTokens = 0

        for (memory in memories) {
            val tokens = estimateTokens(memory.content)
            if (usedTokens + tokens <= tokenBudget) {
                selected += PromptMemorySelection(memory = memory, snippet = memory.content)
                usedTokens += tokens
                continue
            }

            val remainingTokens = tokenBudget - usedTokens
            if (remainingTokens > 0) {
                val truncated = memory.content.truncateToTokenBudget(remainingTokens)
                selected += PromptMemorySelection(memory = memory, snippet = truncated)
            }
            break
        }

        return selected
    }

    private fun selectSummariesWithinTokenBudget(
        summaries: List<MemorySummaryEntity>,
        tokenBudget: Int,
    ): List<PromptSummarySelection> {
        if (summaries.isEmpty() || tokenBudget <= 0) return emptyList()

        val selected = mutableListOf<PromptSummarySelection>()
        var usedTokens = 0

        for (summary in summaries) {
            val snippet = summary.toPromptSnippet()
            val tokens = estimateTokens(snippet)
            if (usedTokens + tokens <= tokenBudget) {
                selected += PromptSummarySelection(summary = summary, snippet = snippet)
                usedTokens += tokens
                continue
            }

            val remainingTokens = tokenBudget - usedTokens
            if (remainingTokens > 0) {
                selected += PromptSummarySelection(
                    summary = summary,
                    snippet = snippet.truncateToTokenBudget(remainingTokens),
                )
            }
            break
        }

        return selected
    }

    private data class MemoryHit(val memory: MemoryEntity, val score: Float)
    private data class SummaryHit(val summary: MemorySummaryEntity, val score: Float)
    private data class PromptMemorySelection(val memory: MemoryEntity, val snippet: String)
    private data class PromptSummarySelection(val summary: MemorySummaryEntity, val snippet: String)

    private companion object {
        const val MAX_MEMORY_RESULTS = 50
        const val MAX_MEMORY_CANDIDATES = 200
        const val CANDIDATE_MULTIPLIER = 4
        const val PROMPT_MEMORY_TOKEN_BUDGET = 10_000
        const val PROMPT_MEMORY_CANDIDATE_LIMIT = 200
        const val PROMPT_RELEVANT_LIMIT = 5
        const val PROMPT_RECENT_LIMIT = 3
        const val PROMPT_SUMMARY_CANDIDATES = 20
        const val PROMPT_SUMMARY_TOKEN_BUDGET = 5_000
        const val MERGE_CANDIDATE_LIMIT = 8
        const val MERGE_THRESHOLD = 0.72f
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    }
}

private fun String.toLikePattern(): String =
    if (isBlank()) "%" else "%${replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")}%"

private fun scoreMemory(content: String, query: String, importance: Float, confidence: Float): Float {
    if (query.isBlank()) return importance * 3f + confidence
    val normalizedContent = content.lowercase()
    val normalizedQuery = query.lowercase()
    val terms = normalizedQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
    var score = importance * 3f + confidence * 2f
    if (normalizedContent.contains(normalizedQuery)) score += 20f
    terms.forEach { term ->
        if (normalizedContent.contains(term)) score += 5f
    }
    if (normalizedContent.startsWith(normalizedQuery)) score += 4f
    return score
}

private fun scoreSummary(summary: MemorySummaryEntity, query: String): Float {
    if (query.isBlank()) return summary.importance
    val haystack = "${summary.title}\n${summary.summary}\n${summary.keywords}".lowercase()
    val normalizedQuery = query.lowercase()
    val terms = normalizedQuery.split(Regex("\\s+")).filter { it.isNotBlank() }
    var score = summary.importance * 3f
    if (haystack.contains(normalizedQuery)) score += 20f
    terms.forEach { term ->
        if (haystack.contains(term)) score += 5f
    }
    if (summary.title.lowercase().contains(normalizedQuery)) score += 6f
    return score
}

private fun MemorySummaryEntity.toPromptSnippet(): String =
    "$title: $summary"

private fun estimateTokens(text: String): Int =
    ((text.length + 2) / 3).coerceAtLeast(1)

private fun String.truncateToTokenBudget(tokenBudget: Int): String {
    val maxChars = (tokenBudget * 3).coerceAtLeast(1)
    if (length <= maxChars) return this
    if (maxChars <= 3) return take(maxChars)
    return take(maxChars - 3).trimEnd().let { "$it..." }
}

private fun similarityScore(a: String, b: String): Float {
    val left = a.keyTerms().toSet()
    val right = b.keyTerms().toSet()
    if (left.isEmpty() || right.isEmpty()) return 0f
    val intersection = left.intersect(right).size.toFloat()
    val union = left.union(right).size.toFloat()
    val jaccard = intersection / union
    val directContainment = if (a.contains(b, ignoreCase = true) || b.contains(a, ignoreCase = true)) 0.35f else 0f
    return (jaccard + directContainment).coerceAtMost(1f)
}

private fun String.keyTerms(): List<String> =
    lowercase()
        .replace(Regex("[^\\p{L}\\p{N}\\s]"), " ")
        .split(Regex("\\s+"))
        .map { it.trim() }
        .filter { it.length >= 2 }

private fun mergeContent(existing: String, incoming: String): String =
    when {
        incoming.isBlank() -> existing
        existing.contains(incoming, ignoreCase = true) -> existing
        incoming.contains(existing, ignoreCase = true) -> incoming
        else -> "$existing\n$incoming"
    }

private fun mergeSource(existing: String, incoming: String): String =
    listOf(existing, incoming)
        .flatMap { it.split(",") }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .joinToString(",")

private fun mergeJsonStringLists(left: String, right: String): String {
    val json = Json { ignoreUnknownKeys = true }
    val leftItems = runCatching { json.decodeFromString<List<String>>(left) }.getOrDefault(emptyList())
    val rightItems = runCatching { json.decodeFromString<List<String>>(right) }.getOrDefault(emptyList())
    return json.encodeToString((leftItems + rightItems).map { it.trim() }.filter { it.isNotBlank() }.distinct())
}

private fun strongestSensitivity(left: String, right: String): String {
    val order = listOf("normal", "private", "sensitive")
    val normalizedLeft = normalizeSensitivity(left)
    val normalizedRight = normalizeSensitivity(right)
    return if (order.indexOf(normalizedLeft) >= order.indexOf(normalizedRight)) normalizedLeft else normalizedRight
}

private fun normalizeSensitivity(value: String): String =
    when (value.lowercase()) {
        "private" -> "private"
        "sensitive" -> "sensitive"
        else -> "normal"
    }

private fun List<MemoryEntity>.filterUsableForPrompt(
    query: String,
    allowPrivateWhenRelevant: Boolean,
): List<MemoryEntity> {
    val now = System.currentTimeMillis()
    val terms = query.keyTerms()
    return filter { memory ->
        val isExpired = memory.expiresAt?.let { it <= now } == true
        if (isExpired) return@filter false

        when (normalizeSensitivity(memory.sensitivity)) {
            "normal" -> true
            "private" -> allowPrivateWhenRelevant && memory.matchesAnyTerm(terms)
            "sensitive" -> allowPrivateWhenRelevant && memory.matchesAnyTerm(terms) && terms.size >= 2
            else -> true
        }
    }
}

private fun MemoryEntity.matchesAnyTerm(terms: List<String>): Boolean {
    if (terms.isEmpty()) return false
    val haystack = content.lowercase()
    return terms.any { haystack.contains(it) }
}
