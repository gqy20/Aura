package com.xiaoqi.companion.feature.record

import androidx.activity.compose.rememberLauncherForActivityResult
import android.Manifest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.ui.text.style.TextOverflow
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordCaptureScreen(
    viewModel: RecordCaptureViewModel = hiltViewModel(),
    onSaved: (String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showHelp by rememberSaveable { mutableStateOf(false) }
    // 多选:一餐多图(面条+配菜+饮料分拍) 一次选齐,逐张提取追加进同一张表
    val pickImages = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 5),
        onResult = { uris -> viewModel.onImagesPicked(uris.map { it.toString() }) },
    )
    // 扫条码复用同一个 Photo Picker,但走独立入口(不触发整图提取)
    val pickBarcode = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let { viewModel.onBarcodeImagePicked(it.toString()) } },
    )
    val context = androidx.compose.ui.platform.LocalContext.current
    // ACCESS_MEDIA_LOCATION: Photo Picker 默认剥离 GPS EXIF,授权后选图才带拍摄位置。
    // 拒绝不阻塞 —— 位置只是店名/趋势的增强源,时间 EXIF 不受此权限影响。
    val pickAfterPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ -> pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    val launchPicker = {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            pickAfterPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
    }

    LaunchedEffect(state.phase) {
        if (state.phase == RecordCapturePhase.SAVED) {
            delay(600)
            // 带着新记录 id 去列表页高亮定位,强确认"存上了"
            state.savedRecordId?.let(onSaved) ?: onSaved("")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.domain == RecordDomain.MEAL) "记一餐" else "记采购") },
                navigationIcon = {
                    IconButton(onClick = { onSaved("") }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 域切换低频(模型自动分类),收进顶栏,给内容层让出整行
                    val target = if (state.domain == RecordDomain.MEAL) RecordDomain.PURCHASE else RecordDomain.MEAL
                    TextButton(onClick = { viewModel.switchDomain(target) }) {
                        Text(if (target == RecordDomain.PURCHASE) "采购" else "餐食")
                    }
                    IconButton(onClick = { showHelp = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = "热量估算说明",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (showHelp) {
            // 按域给文案:餐食讲热量换算,采购讲条码/库存
            val helpTitle: String
            val helpLines: List<Pair<String, String>>
            if (state.domain == RecordDomain.MEAL) {
                helpTitle = "热量是怎么算的"
                helpLines = listOf(
                    "1" to "识别照片里的食物,按碗中、盘中的比例估算克重",
                    "2" to "对照《中国食物成分表》查出营养,按克重换算热量",
                    "3" to "查不到的菜由 AI 估算,数值标着「估算」,误差偏大",
                    "4" to "手动改过的数值会被记住,下次同样的菜优先使用",
                )
            } else {
                helpTitle = "采购是怎么记录的"
                helpLines = listOf(
                    "1" to "识别小票或包装上的商品名、品牌、数量与单价",
                    "2" to "扫条码可从开放商品库直接带入,无需手填",
                    "3" to "保存后自动累加进家庭库存,可用开关关闭",
                    "4" to "库存条目可在聊天里问 Aura:家里还有什么",
                )
            }
            AlertDialog(
                onDismissRequest = { showHelp = false },
                title = { Text(helpTitle) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        helpLines.forEach { (num, line) ->
                            Row {
                                Text(
                                    num,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.width(16.dp),
                                )
                                Text(line, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHelp = false }) { Text("知道了") }
                },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val showConfirming = state.phase == RecordCapturePhase.CONFIRMING ||
                state.phase == RecordCapturePhase.SAVING ||
                (state.phase == RecordCapturePhase.EXTRACTING && state.hasExtractedContent)
            val launchBarcode = { pickBarcode.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            when {
                state.phase == RecordCapturePhase.PICK_IMAGE -> PickImageContent(
                    note = state.note,
                    onNoteChange = viewModel::setNote,
                    onPickImages = launchPicker,
                )
                showConfirming && state.domain == RecordDomain.MEAL ->
                    MealConfirmContent(state, viewModel, launchPicker)
                showConfirming && state.domain == RecordDomain.PURCHASE ->
                    PurchaseConfirmContent(state, viewModel, launchBarcode, launchPicker)
                state.phase == RecordCapturePhase.EXTRACTING -> ExtractingContent(state = state)
                state.phase == RecordCapturePhase.FAILED -> FailedContent(
                    state = state,
                    onRetry = viewModel::retryExtraction,
                    onManual = viewModel::startManualEntry,
                    onPickImages = launchPicker,
                )
                state.phase == RecordCapturePhase.SAVED -> SavedContent()
                else -> PickImageContent(note = state.note, onNoteChange = viewModel::setNote, onPickImages = launchPicker)
            }
        }
    }
}

@Composable
private fun PickImageContent(
    note: String,
    onNoteChange: (String) -> Unit,
    onPickImages: () -> Unit,
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
            "可以一次选多张,食物照片估算热量与营养,小票或货品照片提取品牌与数量",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
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
        Button(onClick = onPickImages, modifier = Modifier.fillMaxWidth()) {
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
        state.photos.firstOrNull()?.let { photo ->
            AsyncImage(
                model = photo.uriString,
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
    onPickImages: () -> Unit,
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
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("重试识别") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onPickImages, modifier = Modifier.fillMaxWidth()) { Text("换一张照片") }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) { Text("手动填写") }
    }
}

/**
 * 第 1 层:图片行。缩略图横排(点击删除该图及其条目) + "再加一张" + 提取进度。
 */
@Composable
private fun PhotoStrip(
    state: RecordCaptureUiState,
    onAddPhoto: () -> Unit,
    onRemovePhoto: (Int) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(state.photos) { photo ->
                    Box {
                        AsyncImage(
                            model = photo.uriString,
                            contentDescription = null,
                            modifier = Modifier
                                .height(64.dp)
                                .width(88.dp)
                                .clip(MaterialTheme.shapes.small),
                        )
                        IconButton(
                            onClick = { onRemovePhoto(state.photos.indexOf(photo)) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(20.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "删除这张图",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    OutlinedButton(
                        onClick = onAddPhoto,
                        enabled = state.extractingCount == 0,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("再加一张", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            if (state.extractingCount > 0) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(4.dp))
                Text(
                    "识别中 ${state.extractingCount} 张",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        state.appendFailure?.let { reason ->
            Spacer(Modifier.height(4.dp))
            Text(
                "有一张没识别出来:$reason",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MealConfirmContent(
    state: RecordCaptureUiState,
    viewModel: RecordCaptureViewModel,
    onPickImages: () -> Unit,
) {
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 第 1 层:图片
        item { PhotoStrip(state, onPickImages, viewModel::removePhoto) }
        // 第 2 层:餐次(EXIF 自动预选,错了才需要点)
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MEAL_TYPE_LABELS.forEach { (type, label) ->
                    FilterChip(
                        selected = state.mealType == type,
                        onClick = { viewModel.setMealType(type) },
                        label = { Text(label) },
                    )
                }
            }
        }
        // 第 3 层:条目(两行卡片)
        itemsIndexed(state.items, key = { index, _ -> index }) { index, item ->
            MealItemCard(
                item = item,
                portionScale = state.portionScales.getOrNull(index) ?: 1.0,
                onEdit = { editingIndex = index },
                onDelete = { viewModel.deleteItem(index) },
                onAdjustPortion = { scale -> viewModel.adjustPortion(index, scale) },
            )
        }
        item {
            TextButton(onClick = { viewModel.addItem() }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("添加一项")
            }
        }
        // 第 4 层:汇总 + 保存
        item { MealTotalsRow(items = state.items) }
        item { SaveSection(state = state, failureReason = state.failureReason, onSave = viewModel::save) }
    }

    val editingItem = editingIndex?.let { index -> state.items.getOrNull(index) }
    editingItem?.let { item ->
        EditMealItemDialog(
            item = item,
            onDismiss = { editingIndex = null },
            onSave = { updated ->
                editingIndex?.let { index -> viewModel.updateItem(index, updated) }
                editingIndex = null
            },
        )
    }
}

@Composable
private fun PurchaseConfirmContent(
    state: RecordCaptureUiState,
    viewModel: RecordCaptureViewModel,
    onScanBarcode: () -> Unit,
    onPickImages: () -> Unit,
) {
    var editingIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PhotoStrip(state, onPickImages, viewModel::removePhoto) }
        item {
            OutlinedTextField(
                value = state.storeName,
                onValueChange = viewModel::setStoreName,
                label = { Text("商店(可选)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        itemsIndexed(state.purchaseItems, key = { index, _ -> index }) { index, item ->
            PurchaseItemCard(
                item = item,
                onEdit = { editingIndex = index },
                onDelete = { viewModel.deletePurchaseItem(index) },
            )
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = { viewModel.addPurchaseItem() }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("添加一项")
                }
                // 条码预填:包装条码照片 → OFF 查询,零估算录入(design.md 估算诚实原则)
                TextButton(onClick = onScanBarcode, enabled = !state.barcodeScanning) {
                    if (state.barcodeScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(4.dp))
                    } else {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                    }
                    Text("扫条码")
                }
            }
        }
        if (state.barcodeNotFound) {
            item {
                Text(
                    "没识别到条码——换一张清晰的条码特写,或直接手动添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (state.barcodeLookupFailed) {
            item {
                Text(
                    "条码识别成功,但商品库暂未收录——名称请手动补一下",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                Switch(checked = state.adjustInventory, onCheckedChange = viewModel::setAdjustInventory)
            }
        }
        item { PurchaseTotalsRow(items = state.purchaseItems) }
        item { SaveSection(state = state, failureReason = state.failureReason, onSave = viewModel::save) }
    }

    val editingItem = editingIndex?.let { index -> state.purchaseItems.getOrNull(index) }
    editingItem?.let { item ->
        EditPurchaseItemDialog(
            item = item,
            onDismiss = { editingIndex = null },
            onSave = { updated ->
                editingIndex?.let { index -> viewModel.updatePurchaseItem(index, updated) }
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

/**
 * 两行卡片(用户分层原则:非必要不加行,文字组织进单行):
 * 行 1 = 菜名 + 估算徽标 + 份量小/中/大 chips + 删除(都是"这条是什么"的信息)
 * 行 2 = kcal + 三大营养素 + 份量描述,合并成一行数值文字(都是"这条是多少")
 */
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
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    item.name.ifBlank { "未命名" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (item.isEstimate) {
                    EstimateBadge()
                }
                PORTION_OPTIONS.forEach { (label, scale) ->
                    FilterChip(
                        selected = portionScale == scale,
                        onClick = { onAdjustPortion(scale) },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    )
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
            Text(
                buildString {
                    append("${item.calories.toInt()} kcal")
                    append(" · 蛋白 ${item.proteinG.toInt()}g")
                    append(" · 碳水 ${item.carbsG.toInt()}g")
                    append(" · 脂肪 ${item.fatG.toInt()}g")
                    if (item.portionText.isNotBlank()) append(" · ${item.portionText}")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    item.name.ifBlank { "未命名" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                item.brand?.let { brand ->
                    Text(
                        brand,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
            Text(
                buildString {
                    append("${formatQuantity(item.quantity)} ${item.unit}")
                    item.unitPrice?.let { price -> append(" · 单价 %.2f 元".format(price)) }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        Text(
            buildString {
                append("合计 ${calories.toInt()} kcal")
                append(" · 蛋白 ${protein.toInt()}g")
                append(" · ${items.size} 项")
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun PurchaseTotalsRow(items: List<PurchaseItemDraft>) {
    val totalCost = items.sumOf { (it.unitPrice ?: 0.0) * it.quantity }
    ChatCardSurface {
        Text(
            buildString {
                append("合计 ${items.size} 项商品")
                if (totalCost > 0) append(" · %.2f 元".format(totalCost))
            },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
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
