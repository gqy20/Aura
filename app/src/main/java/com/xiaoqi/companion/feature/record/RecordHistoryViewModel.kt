package com.xiaoqi.companion.feature.record

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaoqi.companion.core.logging.AppLogger
import com.xiaoqi.companion.core.logging.LogTags
import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.data.db.dao.MealRecordWithItems
import com.xiaoqi.companion.data.db.dao.PurchaseRecordWithItems
import com.xiaoqi.companion.data.repository.MealRecordRepository
import com.xiaoqi.companion.data.repository.PurchaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface HistoryEntry {
    val id: String
    val timeMillis: Long
    val dayKey: LocalDate

    data class Meal(
        val record: MealRecordWithItems,
    ) : HistoryEntry {
        override val id: String get() = record.record.id
        override val timeMillis: Long get() = record.record.photoTakenAt ?: record.record.createdAt
        override val dayKey: LocalDate get() = dayOf(timeMillis)
        val mealType: MealType get() = record.record.mealType
    }

    data class Purchase(
        val record: PurchaseRecordWithItems,
    ) : HistoryEntry {
        override val id: String get() = record.record.id
        override val timeMillis: Long get() = record.record.photoTakenAt ?: record.record.purchasedAt
        override val dayKey: LocalDate get() = dayOf(timeMillis)
    }
}

private fun dayOf(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

data class HistoryDay(
    val date: LocalDate,
    val entries: List<HistoryEntry>,
)

data class RecordHistoryUiState(
    val days: List<HistoryDay> = emptyList(),
    val expandedEntryId: String? = null,
)

@HiltViewModel
class RecordHistoryViewModel @Inject constructor(
    private val mealRecordRepository: MealRecordRepository,
    private val purchaseRepository: PurchaseRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(RecordHistoryUiState())
    val state: StateFlow<RecordHistoryUiState> = _state.asStateFlow()

    private val expandedId = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            combine(
                mealRecordRepository.observeRecentRecords(RECORD_LIMIT),
                purchaseRepository.observeRecentPurchases(RECORD_LIMIT),
                expandedId,
            ) { meals, purchases, expanded ->
                val entries = (meals.map { HistoryEntry.Meal(it) } + purchases.map { HistoryEntry.Purchase(it) })
                    .sortedByDescending { it.timeMillis }
                val days = entries.groupBy { it.dayKey }
                    .toSortedMap(compareByDescending { it })
                    .map { (date, list) -> HistoryDay(date, list) }
                RecordHistoryUiState(days = days, expandedEntryId = expanded)
            }.collect { _state.value = it }
        }
    }

    fun toggleExpand(entryId: String) {
        expandedId.update { current -> if (current == entryId) null else entryId }
    }

    fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            val entry = _state.value.days.asSequence()
                .flatMap { it.entries }
                .firstOrNull { it.id == entryId } ?: return@launch
            runCatching {
                when (entry) {
                    is HistoryEntry.Meal -> mealRecordRepository.deleteMeal(entryId)
                    is HistoryEntry.Purchase -> purchaseRepository.deletePurchase(entryId)
                }
            }.onSuccess {
                expandedId.update { null }
                AppLogger.info(LogTags.Record, "history_entry_deleted", "entryId" to entryId)
            }.onFailure { error ->
                AppLogger.warn(LogTags.Record, "history_entry_delete_failed", "entryId" to entryId, "error" to (error.message ?: ""))
            }
        }
    }

    private companion object {
        const val RECORD_LIMIT = 100
    }
}
