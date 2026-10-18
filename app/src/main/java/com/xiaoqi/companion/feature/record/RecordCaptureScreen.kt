package com.xiaoqi.companion.feature.record

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.xiaoqi.companion.core.record.MealItemDraft
import com.xiaoqi.companion.core.record.PurchaseItemDraft
import com.xiaoqi.companion.core.record.RecordDomain
import com.xiaoqi.companion.data.db.converter.MealType
import com.xiaoqi.companion.feature.chat.AuraLoadingIndicator
import com.xiaoqi.companion.ui.theme.ChatCardSurface
import kotlinx.coroutines.delay

private val MEAL_TYPE_LABELS = mapOf(
    MealType.BREAKFAST to "早餐",
    MealType.LUNCH to "午餐",
    MealType.DINNER to "晚餐",
    MealType.SNACK to "加餐",
)
private val PORTION_OPTIONS = listOf("小" to 0.75, "中" to 1.0, "大" to 1.25)
private val DOMAIN_LABELS = mapOf(
    RecordDomain.MEAL to "餐食",
    RecordDomain.PURCHASE to "采购",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordCaptureScreen(
    viewModel: RecordCaptureViewModel = hiltViewModel(),
    onDone: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let { viewModel.onImagePicked(it.toString()) } },
    )
    val launchPicker = {
        pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(state.phase) {
        if (state.phase == RecordCapturePhase.SAVED) {
            delay(700)
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.domain == RecordDomain.MEAL) "记一餐" else "记采购") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state.phase) {
                RecordCapturePhase.PICK_IMAGE -> PickImageContent(
                    note = state.note,
                    onNoteChange = viewModel::setNote,
                    onPickImage = launchPicker,
                )
                RecordCapturePhase.EXTRACTING -> ExtractingContent(state = state)
                RecordCapturePhase.FAILED -> FailedContent(
                    state = state,
                    onRetry = viewModel::retryExtraction,
                    onManual = viewModel::startManualEntry,
                    onPickImage = launchPicker,
                )
                RecordCapturePhase.CONFIRMING, RecordCapturePhase.SAVING -> {
                    if (state.domain == RecordDomain.MEAL) {
                        MealConfirmContent(
                            state = state,
                            onMealType = viewModel::setMealType,
                            onSwitchDomain = viewModel::switchDomain,
                            onUpdateItem = viewModel::updateItem,
                            onDeleteItem = viewModel::deleteItem,
                            onAddItem = viewModel::addItem,
                            onAdjustPortion = viewModel::adjustPortion,
                            onSave = viewModel::save,
                        )
                    } else {
                        PurchaseConfirmContent(
                            state = state,
                            onSwitchDomain = viewModel::switchDomain,
                            onStoreName = viewModel::setStoreName,
                            onAdjustInventory = viewModel::setAdjustInventory,
                            onUpdateItem = viewModel::updatePurchaseItem,
                            onDeleteItem = viewModel::deletePurchaseItem,
                            onAddItem = viewModel::addPurchaseItem,
                            onSave = viewModel::save,
                        )
                    }
                }
                RecordCapturePhase.SAVED -> SavedContent()
            }
        }
    }
}

@Composable
private fun PickImageContent(
    note: String,
    onNoteChange: (String) -> Unit,
    onPickImage: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Default.PhotoCamera,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text("拍下一餐或一次采购", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "食物照片会估算热量与营养,小票或货品照片会提取品牌与数量",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = note,
            onValueChange = onNoteChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("补充说明(可选),如:在永辉买的") },
            singleLine = false,
            minLines = 1,
            maxLines = 3,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
            Text("选择照片")
        }
    }
}

@Composable
private fun ExtractingContent(state: RecordCaptureUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        state.imageUriString?.let { uri ->
            AsyncImage(
                model = uri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
            Spacer(Modifier.height(24.dp))
        }
        Text("Aura 正在看图…", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        AuraLoadingIndicator()
    }
}

@Composable
private fun FailedContent(
    state: RecordCaptureUiState,
    onRetry: () -> Unit,
    onManual: () -> Unit,
    onPickImage: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            state.failureReason ?: "提取失败",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        if (state.imageBase64 != null) {
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("重试识别") }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) { Text("换一张照片") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) { Text("手动填写") }
    }
}

// 提取器自动分类,这两个 chip 是手动纠正兜底(design.md §6.2)
@Composable
private fun DomainSwitcher(
    domain: RecordDomain,
    enabled: Boolean,
    onSwitchDomain: (RecordDomain) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DOMAIN_LABELS.forEach { (candidate, label) ->
            FilterChip(
                selected = domain == candidate,
                enabled = enabled,
                onClick = { onSwitchDomain(candidate) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
private fun MealConfirmContent(
    state: RecordCaptureUiState,
    onMealType: (MealType) -> Unit,
    onSwitchDomain: (RecordDomain) -> Unit,
    onUpdateItem: (Int, MealItemDraft) -> Unit,
    onDeleteItem: (Int) -> Unit,
    onAddItem: () -> Unit,
    onAdjustPortion: (Int, Double) -> Unit,
    onSave: () -> Unit,
) {
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            state.imageUriString?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
            }
        }
        item { DomainSwitcher(state.domain, enabled = state.phase == RecordCapturePhase.CONFIRMING, onSwitchDomain) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MEAL_TYPE_LABELS.forEach { (type, label) ->
                    FilterChip(
                        selected = state.mealType == type,
                        onClick = { onMealType(type) },
                        label = { Text(label) },
                    )
                }
            }
        }
        itemsIndexed(state.items, key = { index, _ -> index }) { index, item ->
            MealItemCard(
                item = item,
                portionScale = state.portionScales.getOrNull(index) ?: 1.0,
                onEdit = { editingIndex = index },
                onDelete = { onDeleteItem(index) },
                onAdjustPortion = { scale -> onAdjustPortion(index, scale) },
            )
        }
        item {
            TextButton(onClick = onAddItem) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加一项")
            }
        }
        item { MealTotalsRow(items = state.items) }
        item { SaveSection(state = state, failureReason = state.failureReason, onSave = onSave) }
    }

    val editingItem = editingIndex?.let { index -> state.items.getOrNull(index) }
    editingItem?.let { item ->
        EditMealItemDialog(
            item = item,
            onDismiss = { editingIndex = null },
            onSave = { updated ->
                editingIndex?.let { index -> onUpdateItem(index, updated) }
                editingIndex = null
            },
        )
    }
}

@Composable
private fun PurchaseConfirmContent(
    state: RecordCaptureUiState,
    onSwitchDomain: (RecordDomain) -> Unit,
    onStoreName: (String) -> Unit,
    onAdjustInventory: (Boolean) -> Unit,
    onUpdateItem: (Int, PurchaseItemDraft) -> Unit,
    onDeleteItem: (Int) -> Unit,
    onAddItem: () -> Unit,
    onSave: () -> Unit,
) {
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            state.imageUriString?.let { uri ->
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(MaterialTheme.shapes.medium),
                )
            }
        }
        item { DomainSwitcher(state.domain, enabled = state.phase == RecordCapturePhase.CONFIRMING, onSwitchDomain) }
        item {
            OutlinedTextField(
                value = state.storeName,
                onValueChange = onStoreName,
                label = { Text("商店(可选)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        itemsIndexed(state.purchaseItems, key = { index, _ -> index }) { index, item ->
            PurchaseItemCard(
                item = item,
                onEdit = { editingIndex = index },
                onDelete = { onDeleteItem(index) },
            )
        }
        item {
            TextButton(onClick = onAddItem) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加一项")
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("同步进库存", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "保存后自动累加到家庭库存",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.adjustInventory, onCheckedChange = onAdjustInventory)
            }
        }
        item { PurchaseTotalsRow(items = state.purchaseItems) }
        item { SaveSection(state = state, failureReason = state.failureReason, onSave = onSave) }
    }

    val editingItem = editingIndex?.let { index -> state.purchaseItems.getOrNull(index) }
    editingItem?.let { item ->
        EditPurchaseItemDialog(
            item = item,
            onDismiss = { editingIndex = null },
            onSave = { updated ->
                editingIndex?.let { index -> onUpdateItem(index, updated) }
                editingIndex = null
            },
        )
    }
}

@Composable
private fun SaveSection(
    state: RecordCaptureUiState,
    failureReason: String?,
    onSave: () -> Unit,
) {
    Column {
        failureReason?.let { reason ->
            Text(
                reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Button(
            onClick = onSave,
            enabled = state.phase == RecordCapturePhase.CONFIRMING,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.phase == RecordCapturePhase.SAVING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text("保存记录")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun EstimateBadge() {
    Text(
        "估算",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .clip(MaterialTheme.shapes.small),
    )
}

@Composable
private fun MealItemCard(
    item: MealItemDraft,
    portionScale: Double,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAdjustPortion: (Double) -> Unit,
) {
    ChatCardSurface(
        modifier = Modifier.clickable(onClick = onEdit),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.name.ifBlank { "未命名" },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (item.isEstimate) {
                    EstimateBadge()
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "删除该项",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PORTION_OPTIONS.forEach { (label, scale) ->
                    FilterChip(
                        selected = portionScale == scale,
                        onClick = { onAdjustPortion(scale) },
                        label = { Text(label) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${item.calories.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    " kcal",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    "蛋白 ${item.proteinG.toInt()}g · 碳水 ${item.carbsG.toInt()}g · 脂肪 ${item.fatG.toInt()}g",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.portionText.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    item.portionText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PurchaseItemCard(
    item: PurchaseItemDraft,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    ChatCardSurface(
        modifier = Modifier.clickable(onClick = onEdit),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name.ifBlank { "未命名" }, style = MaterialTheme.typography.titleSmall)
                    item.brand?.let { brand ->
                        Text(
                            brand,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "删除该项",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatQuantity(item.quantity),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    " ${item.unit}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                item.unitPrice?.let { price ->
                    Text(
                        "单价 %.2f 元".format(price),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun formatQuantity(quantity: Double): String =
    if (quantity == quantity.toLong().toDouble()) "${quantity.toLong()}" else "%.1f".format(quantity)

@Composable
private fun MealTotalsRow(items: List<MealItemDraft>) {
    val calories = items.sumOf { it.calories }
    val protein = items.sumOf { it.proteinG }
    ChatCardSurface {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("合计", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text(
                "${calories.toInt()} kcal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "蛋白 ${protein.toInt()}g",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PurchaseTotalsRow(items: List<PurchaseItemDraft>) {
    val totalCost = items.sumOf { (it.unitPrice ?: 0.0) * it.quantity }
    ChatCardSurface {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("合计", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            Text(
                "${items.size} 项商品",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (totalCost > 0) {
                Spacer(Modifier.width(12.dp))
                Text(
                    "%.2f 元".format(totalCost),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun EditMealItemDialog(
    item: MealItemDraft,
    onDismiss: () -> Unit,
    onSave: (MealItemDraft) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(item.name) }
    var portionText by rememberSaveable { mutableStateOf(item.portionText) }
    var calories by rememberSaveable { mutableStateOf(item.calories.toString()) }
    var protein by rememberSaveable { mutableStateOf(item.proteinG.toString()) }
    var carbs by rememberSaveable { mutableStateOf(item.carbsG.toString()) }
    var fat by rememberSaveable { mutableStateOf(item.fatG.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑食物") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, singleLine = true)
                OutlinedTextField(value = portionText, onValueChange = { portionText = it }, label = { Text("份量描述") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = calories,
                        onValueChange = { calories = it },
                        label = { Text("热量 kcal") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = protein,
                        onValueChange = { protein = it },
                        label = { Text("蛋白 g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = carbs,
                        onValueChange = { carbs = it },
                        label = { Text("碳水 g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = fat,
                        onValueChange = { fat = it },
                        label = { Text("脂肪 g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        item.copy(
                            name = name.trim(),
                            portionText = portionText.trim(),
                            calories = calories.toDoubleOrNull() ?: item.calories,
                            proteinG = protein.toDoubleOrNull() ?: item.proteinG,
                            carbsG = carbs.toDoubleOrNull() ?: item.carbsG,
                            fatG = fat.toDoubleOrNull() ?: item.fatG,
                            // 用户改过的数值以输入为准,不再是模型估算
                            isEstimate = false,
                        )
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun EditPurchaseItemDialog(
    item: PurchaseItemDraft,
    onDismiss: () -> Unit,
    onSave: (PurchaseItemDraft) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(item.name) }
    var brand by rememberSaveable { mutableStateOf(item.brand ?: "") }
    var quantity by rememberSaveable { mutableStateOf(formatQuantity(item.quantity)) }
    var unit by rememberSaveable { mutableStateOf(item.unit) }
    var unitPrice by rememberSaveable { mutableStateOf(item.unitPrice?.let { "%.2f".format(it) } ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑商品") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("品名") }, singleLine = true)
                OutlinedTextField(value = brand, onValueChange = { brand = it }, label = { Text("品牌(可选)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("数量") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("单位") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                OutlinedTextField(
                    value = unitPrice,
                    onValueChange = { unitPrice = it },
                    label = { Text("单价(元,可选)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        item.copy(
                            name = name.trim(),
                            brand = brand.trim().takeIf { it.isNotBlank() },
                            quantity = quantity.toDoubleOrNull()?.takeIf { it > 0 } ?: item.quantity,
                            unit = unit.trim().ifBlank { "个" },
                            unitPrice = unitPrice.toDoubleOrNull()?.takeIf { it >= 0 },
                        )
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

@Composable
private fun SavedContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("已保存", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text("回到主页查看今日汇总", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
