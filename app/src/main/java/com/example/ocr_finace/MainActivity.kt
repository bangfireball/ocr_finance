package com.example.ocr_finace

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.ocr_finace.data.ProcessingStatus
import com.example.ocr_finace.data.OcrPromptType
import com.example.ocr_finace.data.ReceiptEntity
import com.example.ocr_finace.data.formatAddedDate
import com.example.ocr_finace.integration.cashew.CashewLinkBuilder
import com.example.ocr_finace.settings.LmStudioConfig
import com.example.ocr_finace.settings.SwipeAction
import com.example.ocr_finace.settings.SwipeConfig
import com.example.ocr_finace.settings.HomeNetworkConfig
import com.example.ocr_finace.settings.ThemeMode
import com.example.ocr_finace.settings.ReceiptLayoutMode
import com.example.ocr_finace.settings.CashewExportConfig
import com.example.ocr_finace.image.CropSelection
import com.example.ocr_finace.image.NormalizedPoint
import com.example.ocr_finace.image.defaultCropCorners
import com.example.ocr_finace.image.rotateCropClockwise
import com.example.ocr_finace.image.isValidCrop
import com.example.ocr_finace.image.orientedImageDimensions
import com.example.ocr_finace.ui.receipt.ReceiptViewModel
import com.example.ocr_finace.ui.receipt.receiptDisplayTitle
import com.example.ocr_finace.ui.receipt.ReceiptDestination
import com.example.ocr_finace.ui.theme.OCR_FinaceTheme
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ReceiptRoot(openUri = { startActivity(Intent(Intent.ACTION_VIEW, it)) })
        }
    }
}

@Composable
private fun ReceiptRoot(
    viewModel: ReceiptViewModel = viewModel(),
    openUri: (android.net.Uri) -> Unit,
) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    OCR_FinaceTheme(darkTheme = isDarkTheme(themeMode, isSystemInDarkTheme())) {
        ReceiptApp(viewModel = viewModel, openUri = openUri)
    }
}

internal fun isDarkTheme(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.FOLLOW_DEVICE -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptApp(
    viewModel: ReceiptViewModel = viewModel(),
    openUri: (android.net.Uri) -> Unit,
) {
    var destinationValue by rememberSaveable { mutableStateOf(ReceiptDestination.List.encode()) }
    val destination = ReceiptDestination.decode(destinationValue)
    val receipts by viewModel.receipts.collectAsStateWithLifecycle()
    val selected by viewModel.selectedReceipt.collectAsStateWithLifecycle()
    val missingSelectionId by viewModel.missingSelectionId.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val availableModels by viewModel.availableModels.collectAsStateWithLifecycle()
    val testingConnection by viewModel.testingConnection.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val receiptLayoutMode by viewModel.receiptLayoutMode.collectAsStateWithLifecycle()
    val cashewExportConfig by viewModel.cashewExportConfig.collectAsStateWithLifecycle()
    val pendingAdjustmentId by viewModel.pendingAdjustmentId.collectAsStateWithLifecycle()
    val snackbarHost = remember { SnackbarHostState() }
    var showSetupRequired by rememberSaveable { mutableStateOf(false) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {
        viewModel.onCaptureResult(it)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.importImage(uri)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHost.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(pendingAdjustmentId) {
        pendingAdjustmentId?.let { receiptId ->
            viewModel.select(receiptId)
            destinationValue = ReceiptDestination.Adjust(receiptId).encode()
        }
    }

    LaunchedEffect(destination) {
        viewModel.select(
            when (destination) {
                is ReceiptDestination.Detail -> destination.receiptId
                is ReceiptDestination.Adjust -> destination.receiptId
                else -> null
            },
        )
    }

    LaunchedEffect(destination, missingSelectionId) {
        val selectedDestinationId = when (destination) {
            is ReceiptDestination.Detail -> destination.receiptId
            is ReceiptDestination.Adjust -> destination.receiptId
            else -> null
        }
        if (selectedDestinationId != null && missingSelectionId == selectedDestinationId
        ) {
            destinationValue = ReceiptDestination.List.encode()
            viewModel.select(null)
        }
    }

    BackHandler(enabled = destination !is ReceiptDestination.List) {
        destinationValue = if (destination is ReceiptDestination.CashewSettings) {
            ReceiptDestination.Settings.encode()
        } else if (destination is ReceiptDestination.Adjust) {
            val processAfter = selected?.processingStatus == ProcessingStatus.PENDING.name
            viewModel.finishAdjustment(destination.receiptId, null, processAfter)
            if (processAfter) ReceiptDestination.List.encode()
            else ReceiptDestination.Detail(destination.receiptId).encode()
        } else {
            ReceiptDestination.List.encode()
        }
        viewModel.select(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (destination) {
                            ReceiptDestination.List -> "OCR Finance"
                            is ReceiptDestination.Detail -> "Receipt"
                            ReceiptDestination.Settings -> "LM Studio"
                            ReceiptDestination.CashewSettings -> "Cashew Export"
                            is ReceiptDestination.Adjust -> "Adjust document"
                        },
                    )
                },
                navigationIcon = {
                    if (destination !is ReceiptDestination.List) {
                        TextButton(onClick = {
                            destinationValue = if (destination is ReceiptDestination.CashewSettings) {
                                ReceiptDestination.Settings.encode()
                            } else if (destination is ReceiptDestination.Adjust) {
                                val processAfter = selected?.processingStatus == ProcessingStatus.PENDING.name
                                viewModel.finishAdjustment(destination.receiptId, null, processAfter)
                                if (processAfter) ReceiptDestination.List.encode()
                                else ReceiptDestination.Detail(destination.receiptId).encode()
                            } else {
                                ReceiptDestination.List.encode()
                            }
                            viewModel.select(null)
                        }) { Text("Back") }
                    }
                },
                actions = {
                    if (destination is ReceiptDestination.List) {
                        TextButton(onClick = { destinationValue = ReceiptDestination.Settings.encode() }) {
                            Text("Settings")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        when (destination) {
            ReceiptDestination.List -> ReceiptList(
                receipts = receipts,
                modifier = Modifier.padding(padding),
                onTakePhoto = {
                    if (viewModel.isLmStudioConfigured()) {
                        viewModel.prepareCapture()?.let(camera::launch)
                    } else {
                        showSetupRequired = true
                    }
                },
                onImport = {
                    if (viewModel.isLmStudioConfigured()) {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    } else {
                        showSetupRequired = true
                    }
                },
                onOpen = {
                    viewModel.select(it)
                    destinationValue = ReceiptDestination.Detail(it).encode()
                },
                snackbarHost = snackbarHost,
                swipeConfig = viewModel.loadSwipeConfig(),
                layoutMode = receiptLayoutMode,
                onLayoutModeChanged = viewModel::saveReceiptLayoutMode,
                onArchive = viewModel::archive,
                onRestore = viewModel::restore,
                onDelete = { id, onDeleted -> viewModel.delete(id, onDeleted) },
                onCashew = { receipt ->
                    runCatching { openUri(CashewLinkBuilder.build(receipt, cashewExportConfig)) }
                        .onSuccess { viewModel.markCashewOpened(receipt.id) }
                },
                onAdjust = { receiptId ->
                    destinationValue = ReceiptDestination.Adjust(receiptId).encode()
                },
            )
            is ReceiptDestination.Detail -> ReceiptDetail(
                receipt = selected,
                modifier = Modifier.padding(padding),
                onSave = { id, merchant, date, subtotal, tax, total, currency, rawText, addedDate ->
                    viewModel.save(id, merchant, date, subtotal, tax, total, currency, rawText, addedDate) {
                        viewModel.select(null)
                        destinationValue = ReceiptDestination.List.encode()
                    }
                },
                onRetry = viewModel::retry,
                onDelete = { id ->
                    viewModel.delete(id) { destinationValue = ReceiptDestination.List.encode() }
                },
                onCashew = { receipt ->
                    runCatching { openUri(CashewLinkBuilder.build(receipt, cashewExportConfig)) }
                        .onSuccess { viewModel.markCashewOpened(receipt.id) }
                },
                onAdjust = { receiptId ->
                    destinationValue = ReceiptDestination.Adjust(receiptId).encode()
                },
            )
            ReceiptDestination.Settings -> SettingsScreen(
                initial = viewModel.loadSettings(),
                initialSwipe = viewModel.loadSwipeConfig(),
                initialOcrConcurrency = viewModel.loadOcrConcurrency(),
                initialHomeNetwork = viewModel.loadHomeNetwork(),
                initialThemeMode = themeMode,
                availableModels = availableModels,
                testingConnection = testingConnection,
                modifier = Modifier.padding(padding),
                onTestConnection = viewModel::testConnection,
                currentSsid = viewModel::useCurrentNetwork,
                onHomeNetworkChanged = viewModel::saveHomeNetwork,
                onForgetHomeNetwork = viewModel::forgetHomeNetwork,
                onThemeModeChanged = viewModel::saveThemeMode,
                onOpenCashewExport = {
                    destinationValue = ReceiptDestination.CashewSettings.encode()
                },
                onSave = {
                    viewModel.saveSettings(it.lmStudio)
                    viewModel.saveSwipeConfig(it.swipes)
                    viewModel.saveOcrConcurrency(it.ocrConcurrency)
                    viewModel.saveHomeNetwork(it.homeNetwork)
                    destinationValue = ReceiptDestination.List.encode()
                },
            )
            ReceiptDestination.CashewSettings -> CashewExportSettingsScreen(
                config = cashewExportConfig,
                modifier = Modifier.padding(padding),
                onConfigChanged = viewModel::saveCashewExportConfig,
            )
            is ReceiptDestination.Adjust -> DocumentAdjustmentScreen(
                receipt = selected,
                initial = viewModel.loadCropSelection(destination.receiptId),
                modifier = Modifier.padding(padding),
                onCancel = {
                    val processAfter = selected?.processingStatus == ProcessingStatus.PENDING.name
                    viewModel.finishAdjustment(destination.receiptId, null, processAfter)
                    destinationValue = if (processAfter) ReceiptDestination.List.encode()
                    else ReceiptDestination.Detail(destination.receiptId).encode()
                },
                onApply = { selection ->
                    val processAfter = selected?.processingStatus == ProcessingStatus.PENDING.name
                    viewModel.finishAdjustment(destination.receiptId, selection, processAfter)
                    destinationValue = if (processAfter) ReceiptDestination.List.encode()
                    else ReceiptDestination.Detail(destination.receiptId).encode()
                },
            )
        }
    }
    if (showSetupRequired) {
        AlertDialog(
            onDismissRequest = { showSetupRequired = false },
            title = { Text("Set up LM Studio first") },
            text = {
                Text(
                    "Choose a reachable LM Studio server and vision-capable model before adding a receipt. " +
                        "This prevents the first OCR attempt from being queued with incomplete settings.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showSetupRequired = false
                    destinationValue = ReceiptDestination.Settings.encode()
                }) { Text("Open settings") }
            },
            dismissButton = {
                TextButton(onClick = { showSetupRequired = false }) { Text("Not now") }
            },
        )
    }
}

@Composable
private fun ReceiptList(
    receipts: List<ReceiptEntity>,
    modifier: Modifier = Modifier,
    onTakePhoto: () -> Unit,
    onImport: () -> Unit,
    onOpen: (String) -> Unit,
    snackbarHost: SnackbarHostState,
    swipeConfig: SwipeConfig,
    layoutMode: ReceiptLayoutMode,
    onLayoutModeChanged: (ReceiptLayoutMode) -> Unit,
    onArchive: (String, () -> Unit) -> Unit,
    onRestore: (String, () -> Unit) -> Unit,
    onDelete: (String, () -> Unit) -> Unit,
    onCashew: (ReceiptEntity) -> Unit,
    onAdjust: (String) -> Unit,
) {
    var actionReceipt by remember { mutableStateOf<ReceiptEntity?>(null) }
    var listScope by rememberSaveable { mutableStateOf(ReceiptListScope.ACTIVE) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var receiptSort by rememberSaveable { mutableStateOf(ReceiptSort.DATE_NEWEST) }
    var statusFilter by rememberSaveable { mutableStateOf(ReceiptStatusFilter.ANY) }
    var sourceFilter by rememberSaveable { mutableStateOf(ReceiptSourceFilter.ANY) }
    var cashewFilter by rememberSaveable { mutableStateOf(CashewStatusFilter.ANY) }
    var currencyFilter by rememberSaveable { mutableStateOf("") }
    var dateFrom by rememberSaveable { mutableStateOf("") }
    var dateTo by rememberSaveable { mutableStateOf("") }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    var requestedSwipe by remember { mutableStateOf<PendingSwipeAction?>(null) }
    var hiddenReceiptIds by remember { mutableStateOf(emptySet<String>()) }
    var activeUndo by remember { mutableStateOf<PendingSwipeAction?>(null) }
    var undoJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    BackHandler(enabled = filtersExpanded) {
        filtersExpanded = false
    }
    fun commitSwipe(requested: PendingSwipeAction) {
        val onComplete = {
            hiddenReceiptIds = hiddenReceiptIds - requested.receipt.id
        }
        when (requested.action) {
            ReceiptListAction.ARCHIVE -> onArchive(requested.receipt.id, onComplete)
            ReceiptListAction.RESTORE -> onRestore(requested.receipt.id, onComplete)
            ReceiptListAction.DELETE -> onDelete(requested.receipt.id, onComplete)
            ReceiptListAction.CASHEW -> {
                onCashew(requested.receipt)
                onComplete()
            }
        }
    }
    fun performSwipe(requested: PendingSwipeAction) {
        activeUndo?.let { previous ->
            undoJob?.cancel()
            commitSwipe(previous)
        }
        if (requested.action == ReceiptListAction.CASHEW) {
            commitSwipe(requested)
            return
        }
        val verb = swipeActionVerb(requested.action)
        hiddenReceiptIds = hiddenReceiptIds + requested.receipt.id
        activeUndo = requested
        undoJob = coroutineScope.launch {
            snackbarHost.currentSnackbarData?.dismiss()
            val result = withTimeoutOrNull(SWIPE_UNDO_WINDOW_MILLIS) {
                snackbarHost.showSnackbar(
                    message = "Receipt ${verb.lowercase()}d",
                    actionLabel = "Undo",
                    duration = SnackbarDuration.Indefinite,
                )
            }
            if (result == SnackbarResult.ActionPerformed) {
                hiddenReceiptIds = hiddenReceiptIds - requested.receipt.id
            } else {
                commitSwipe(requested)
            }
            activeUndo = null
            undoJob = null
        }
    }
    fun requestSwipe(receipt: ReceiptEntity, action: SwipeAction) {
        val requested = PendingSwipeAction(receipt, action.toReceiptListAction(receipt.isArchived))
        if (swipeConfig.confirmActions) requestedSwipe = requested else performSwipe(requested)
    }
    val scopedReceipts = applyReceiptListOptions(
        receipts = receipts,
        scope = listScope,
        query = searchQuery,
        sort = receiptSort,
        statusFilter = statusFilter,
        sourceFilter = sourceFilter,
        cashewFilter = cashewFilter,
        currency = currencyFilter,
        dateFrom = dateFrom,
        dateTo = dateTo,
    )
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedButton(
            onClick = { filtersExpanded = !filtersExpanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                filterSummary(
                    expanded = filtersExpanded,
                    scope = listScope,
                    query = searchQuery,
                    sort = receiptSort,
                    status = statusFilter,
                    layout = layoutMode,
                    source = sourceFilter,
                    cashew = cashewFilter,
                    currency = currencyFilter,
                    dateFrom = dateFrom,
                    dateTo = dateTo,
                    resultCount = scopedReceipts.size,
                ),
            )
        }
        if (filtersExpanded) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReceiptListScope.entries.forEach { scope ->
                    if (scope == listScope) {
                        Button(onClick = { listScope = scope }, modifier = Modifier.weight(1f)) {
                            Text(receiptListScopeLabel(scope))
                        }
                    } else {
                        OutlinedButton(onClick = { listScope = scope }, modifier = Modifier.weight(1f)) {
                            Text(receiptListScopeLabel(scope))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReceiptLayoutMode.entries.forEach { mode ->
                    if (mode == layoutMode) {
                        Button(onClick = { onLayoutModeChanged(mode) }, modifier = Modifier.weight(1f)) {
                            Text(receiptLayoutLabel(mode))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onLayoutModeChanged(mode) },
                            modifier = Modifier.weight(1f),
                        ) { Text(receiptLayoutLabel(mode)) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search receipts") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReceiptSortDropdown(
                    value = receiptSort,
                    onValueChanged = { receiptSort = it },
                    modifier = Modifier.weight(1f),
                )
                ReceiptStatusDropdown(
                    value = statusFilter,
                    onValueChanged = { statusFilter = it },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReceiptSourceDropdown(
                    value = sourceFilter,
                    onValueChanged = { sourceFilter = it },
                    modifier = Modifier.weight(1f),
                )
                CashewStatusDropdown(
                    value = cashewFilter,
                    onValueChanged = { cashewFilter = it },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = currencyFilter,
                onValueChange = { currencyFilter = it },
                label = { Text("Currency (for example USD)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = dateFrom,
                    onValueChange = { dateFrom = it },
                    label = { Text("Transaction from YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = dateTo,
                    onValueChange = { dateTo = it },
                    label = { Text("Transaction to YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            TextButton(onClick = {
                listScope = ReceiptListScope.ACTIVE
                searchQuery = ""
                receiptSort = ReceiptSort.DATE_NEWEST
                statusFilter = ReceiptStatusFilter.ANY
                sourceFilter = ReceiptSourceFilter.ANY
                cashewFilter = CashewStatusFilter.ANY
                currencyFilter = ""
                dateFrom = ""
                dateTo = ""
            }, modifier = Modifier.fillMaxWidth()) { Text("Clear filters") }
        }
        Spacer(Modifier.height(16.dp))
        if (scopedReceipts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyScopeMessage(listScope))
            }
        } else {
            val visibleReceipts = scopedReceipts.filterNot { it.id in hiddenReceiptIds }
            if (layoutMode == ReceiptLayoutMode.THUMBNAIL) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(visibleReceipts, key = { it.id }) { receipt ->
                        ReceiptThumbnailCard(
                            receipt = receipt,
                            onClick = { onOpen(receipt.id) },
                            onLongClick = { actionReceipt = receipt },
                            onMenuClick = { actionReceipt = receipt },
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(visibleReceipts, key = { it.id }) { receipt ->
                        SwipeableReceiptCard(
                            receipt = receipt,
                            swipeConfig = swipeConfig,
                            onClick = { onOpen(receipt.id) },
                            onLongClick = { actionReceipt = receipt },
                            onMenuClick = { actionReceipt = receipt },
                            layoutMode = layoutMode,
                            onSwipe = { action -> requestSwipe(receipt, action) },
                        )
                    }
                }
            }
        }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(20.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(28.dp),
                )
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onTakePhoto) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_camera),
                    contentDescription = "Take photo",
                )
            }
            IconButton(onClick = onImport) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_gallery),
                    contentDescription = "Import receipt",
                )
            }
        }
    }
    requestedSwipe?.let { requested ->
        val verb = swipeActionVerb(requested.action)
        AlertDialog(
            onDismissRequest = { requestedSwipe = null },
            title = {
                Text(if (requested.action == ReceiptListAction.CASHEW) "$verb?" else "$verb receipt?")
            },
            text = { Text(receiptActionConfirmation(requested.action)) },
            confirmButton = {
                TextButton(onClick = {
                    requestedSwipe = null
                    performSwipe(requested)
                }) { Text(verb) }
            },
            dismissButton = {
                TextButton(onClick = { requestedSwipe = null }) { Text("Cancel") }
            },
        )
    }
    actionReceipt?.let { receipt ->
        AlertDialog(
            onDismissRequest = { actionReceipt = null },
            title = { Text(receiptDisplayTitle(receipt)) },
            text = {
                Column {
                    TextButton(onClick = {
                        actionReceipt = null
                        onOpen(receipt.id)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Open / Edit") }
                    TextButton(onClick = {
                        actionReceipt = null
                        onAdjust(receipt.id)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Adjust document") }
                    TextButton(onClick = {
                        actionReceipt = null
                        onCashew(receipt)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Open in Cashew") }
                    TextButton(onClick = {
                        actionReceipt = null
                        requestedSwipe = PendingSwipeAction(
                            receipt,
                            if (receipt.isArchived) ReceiptListAction.RESTORE else ReceiptListAction.ARCHIVE,
                        )
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (receipt.isArchived) "Restore" else "Archive")
                    }
                    TextButton(onClick = {
                        actionReceipt = null
                        requestedSwipe = PendingSwipeAction(receipt, ReceiptListAction.DELETE)
                    }, modifier = Modifier.fillMaxWidth()) { Text("Delete") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { actionReceipt = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableReceiptCard(
    receipt: ReceiptEntity,
    swipeConfig: SwipeConfig,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
    layoutMode: ReceiptLayoutMode,
    onSwipe: (SwipeAction) -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onSwipe(swipeConfig.right)
                SwipeToDismissBoxValue.EndToStart -> onSwipe(swipeConfig.left)
                SwipeToDismissBoxValue.Settled -> return@rememberSwipeToDismissBoxState false
            }
            false
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val action = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> swipeConfig.right
                SwipeToDismissBoxValue.EndToStart -> swipeConfig.left
                SwipeToDismissBoxValue.Settled -> null
            }
            Box(
                Modifier.fillMaxSize().background(
                    if (action == SwipeAction.DELETE) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                ).padding(horizontal = 24.dp),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                    Alignment.CenterEnd
                } else {
                    Alignment.CenterStart
                },
            ) {
                Text(
                    when {
                        action == SwipeAction.DELETE -> "Delete"
                        action == SwipeAction.CASHEW -> "Open in Cashew"
                        receipt.isArchived -> "Restore"
                        else -> "Archive"
                    },
                )
            }
        },
    ) {
        ReceiptCard(receipt, layoutMode, onClick, onLongClick, onMenuClick)
    }
}

private data class PendingSwipeAction(
    val receipt: ReceiptEntity,
    val action: ReceiptListAction,
)

private enum class ReceiptListAction { ARCHIVE, RESTORE, DELETE, CASHEW }

private fun receiptListScopeLabel(scope: ReceiptListScope): String = when (scope) {
    ReceiptListScope.ACTIVE -> "Active"
    ReceiptListScope.ARCHIVED -> "Archived"
    ReceiptListScope.ALL -> "All"
}

private fun emptyScopeMessage(scope: ReceiptListScope): String = when (scope) {
    ReceiptListScope.ACTIVE -> "Photograph or import a receipt to create your first record."
    ReceiptListScope.ARCHIVED -> "No archived receipts."
    ReceiptListScope.ALL -> "No receipts yet."
}

private fun receiptLayoutLabel(mode: ReceiptLayoutMode): String = when (mode) {
    ReceiptLayoutMode.THUMBNAIL -> "Thumbnail"
    ReceiptLayoutMode.MIXED -> "Mixed"
    ReceiptLayoutMode.LIST -> "List"
}

private fun filterSummary(
    expanded: Boolean,
    scope: ReceiptListScope,
    query: String,
    sort: ReceiptSort,
    status: ReceiptStatusFilter,
    layout: ReceiptLayoutMode,
    source: ReceiptSourceFilter,
    cashew: CashewStatusFilter,
    currency: String,
    dateFrom: String,
    dateTo: String,
    resultCount: Int,
): String {
    val values = buildList {
        add("$resultCount results")
        add(receiptListScopeLabel(scope))
        add(receiptStatusFilterLabel(status))
        add(receiptSortLabel(sort))
        add(receiptLayoutLabel(layout))
        if (source != ReceiptSourceFilter.ANY) add(receiptSourceFilterLabel(source))
        if (cashew != CashewStatusFilter.ANY) add(cashewStatusFilterLabel(cashew))
        if (currency.isNotBlank()) add(currency.trim().uppercase())
        if (dateFrom.isNotBlank() || dateTo.isNotBlank()) {
            add("${dateFrom.ifBlank { "…" }}–${dateTo.ifBlank { "…" }}")
        }
        if (query.isNotBlank()) add("Search: ${query.trim()}")
    }
    return "Filters ${if (expanded) "▲" else "▼"}  ${values.joinToString(" · ")}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptSourceDropdown(
    value: ReceiptSourceFilter,
    onValueChanged: (ReceiptSourceFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }, modifier) {
        OutlinedTextField(
            value = receiptSourceFilterLabel(value),
            onValueChange = {},
            readOnly = true,
            label = { Text("Source") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            ReceiptSourceFilter.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(receiptSourceFilterLabel(option)) },
                    onClick = { onValueChanged(option); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CashewStatusDropdown(
    value: CashewStatusFilter,
    onValueChanged: (CashewStatusFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }, modifier) {
        OutlinedTextField(
            value = cashewStatusFilterLabel(value),
            onValueChange = {},
            readOnly = true,
            label = { Text("Cashew") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            CashewStatusFilter.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(cashewStatusFilterLabel(option)) },
                    onClick = { onValueChanged(option); expanded = false },
                )
            }
        }
    }
}

private fun receiptSourceFilterLabel(filter: ReceiptSourceFilter): String = when (filter) {
    ReceiptSourceFilter.ANY -> "Any source"
    ReceiptSourceFilter.CAMERA -> "Camera"
    ReceiptSourceFilter.IMPORTED -> "Imported"
}

private fun cashewStatusFilterLabel(filter: CashewStatusFilter): String = when (filter) {
    CashewStatusFilter.ANY -> "Any Cashew"
    CashewStatusFilter.SENT -> "Sent"
    CashewStatusFilter.NOT_SENT -> "Not sent"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptSortDropdown(
    value: ReceiptSort,
    onValueChanged: (ReceiptSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = receiptSortLabel(value),
            onValueChange = {},
            readOnly = true,
            label = { Text("Sort") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReceiptSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(receiptSortLabel(option)) },
                    onClick = {
                        onValueChanged(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReceiptStatusDropdown(
    value: ReceiptStatusFilter,
    onValueChanged: (ReceiptStatusFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = receiptStatusFilterLabel(value),
            onValueChange = {},
            readOnly = true,
            label = { Text("Status") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ReceiptStatusFilter.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(receiptStatusFilterLabel(option)) },
                    onClick = {
                        onValueChanged(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun receiptSortLabel(sort: ReceiptSort): String = when (sort) {
    ReceiptSort.DATE_NEWEST -> "Recently added"
    ReceiptSort.DATE_OLDEST -> "Oldest added"
    ReceiptSort.MERCHANT_ASC -> "Merchant"
    ReceiptSort.TOTAL_HIGH -> "Total high"
    ReceiptSort.TOTAL_LOW -> "Total low"
}

private fun receiptStatusFilterLabel(filter: ReceiptStatusFilter): String = when (filter) {
    ReceiptStatusFilter.ANY -> "Any"
    ReceiptStatusFilter.READY -> "Ready"
    ReceiptStatusFilter.IN_PROGRESS -> "In progress"
    ReceiptStatusFilter.FAILED -> "Failed"
}

private fun SwipeAction.toReceiptListAction(isArchived: Boolean): ReceiptListAction = when (this) {
    SwipeAction.ARCHIVE -> if (isArchived) ReceiptListAction.RESTORE else ReceiptListAction.ARCHIVE
    SwipeAction.DELETE -> ReceiptListAction.DELETE
    SwipeAction.CASHEW -> ReceiptListAction.CASHEW
}

internal const val SWIPE_UNDO_WINDOW_MILLIS = 5_000L

private fun swipeActionVerb(action: ReceiptListAction): String = when (action) {
    ReceiptListAction.ARCHIVE -> "Archive"
    ReceiptListAction.RESTORE -> "Restore"
    ReceiptListAction.DELETE -> "Delete"
    ReceiptListAction.CASHEW -> "Open in Cashew"
}

private fun receiptActionConfirmation(action: ReceiptListAction): String = when (action) {
    ReceiptListAction.ARCHIVE ->
        "This receipt will be removed from the active list. You can undo for five seconds."
    ReceiptListAction.RESTORE ->
        "This receipt will return to the active list. You can undo for five seconds."
    ReceiptListAction.DELETE ->
        "This receipt and its image will be permanently deleted after five seconds."
    ReceiptListAction.CASHEW ->
        "This will open a prepared transaction in Cashew."
}

@Composable
private fun ReceiptThumbnailCard(
    receipt: ReceiptEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
    ) {
        Column {
            Box {
                AsyncImage(
                    model = File(receipt.imagePath),
                    contentDescription = "Receipt image for ${receiptDisplayTitle(receipt)}",
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    contentScale = ContentScale.Crop,
                )
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.align(Alignment.TopEnd).background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
                        RoundedCornerShape(bottomStart = 16.dp),
                    ),
                ) {
                    Text("⋮", style = MaterialTheme.typography.headlineSmall)
                }
            }
            Text(
                receiptDisplayTitle(receipt),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun ReceiptCard(
    receipt: ReceiptEntity,
    layoutMode: ReceiptLayoutMode,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
    ) {
        Row(
            Modifier.padding(if (layoutMode == ReceiptLayoutMode.MIXED) 12.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (layoutMode == ReceiptLayoutMode.MIXED) {
                AsyncImage(
                    model = File(receipt.imagePath),
                    contentDescription = "Receipt image",
                    modifier = Modifier.size(72.dp),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    receiptDisplayTitle(receipt),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (layoutMode == ReceiptLayoutMode.MIXED) {
                    if (receipt.transactionDate.isNotBlank()) {
                        Text("Transaction ${receipt.transactionDate}")
                    }
                    Text("Added ${formatAddedDate(receipt.createdAt)}")
                    Text(statusLabel(receipt))
                } else {
                    Text(
                        "Added ${formatAddedDate(receipt.createdAt)} · ${statusLabel(receipt)}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (receipt.transactionDate.isNotBlank()) {
                        Text(
                            "Transaction ${receipt.transactionDate}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(listOf(receipt.currency, receipt.total).filter(String::isNotBlank).joinToString(" "))
                IconButton(onClick = onMenuClick) {
                    Text("⋮", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
}

private fun statusLabel(receipt: ReceiptEntity): String = when (receipt.processingStatus) {
    ProcessingStatus.PENDING.name, ProcessingStatus.QUEUED.name -> "Queued for OCR"
    ProcessingStatus.PROCESSING.name -> "Processing with LM Studio…"
    ProcessingStatus.COMPLETE.name -> "Ready"
    ProcessingStatus.FAILED.name -> "Processing failed"
    else -> receipt.processingStatus
}

@Composable
private fun DocumentAdjustmentScreen(
    receipt: ReceiptEntity?,
    initial: CropSelection,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
    onApply: (CropSelection) -> Unit,
) {
    if (receipt == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    var selection by remember(receipt.id) { mutableStateOf(initial) }
    val latestSelection by rememberUpdatedState(selection)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val valid = isValidCrop(selection.corners)
    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Drag each corner to the matching receipt corner. The saved points will be used for perspective correction.",
            style = MaterialTheme.typography.bodySmall,
        )
        BoxWithConstraints(
            Modifier.fillMaxWidth().weight(1f).background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            val dimensions = orientedImageDimensions(File(receipt.imagePath), selection.rotation)
            val imageAspect = dimensions.first.toFloat() / dimensions.second
            val availableAspect = maxWidth.value / maxHeight.value
            val imageModifier = if (imageAspect > availableAspect) {
                Modifier.fillMaxWidth().aspectRatio(imageAspect)
            } else {
                Modifier.fillMaxHeight().aspectRatio(imageAspect)
            }
            Box(imageModifier.align(Alignment.Center)) {
                AsyncImage(
                    model = File(receipt.imagePath),
                    contentDescription = "Receipt being adjusted",
                    modifier = Modifier.fillMaxSize().graphicsLayer { rotationZ = selection.rotation.toFloat() },
                    contentScale = ContentScale.Fit,
                )
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(canvasSize) {
                        var activeCorner = -1
                        detectDragGestures(
                            onDragStart = { position ->
                                val nearest = latestSelection.corners.indices.minByOrNull { index ->
                                    val point = latestSelection.corners[index]
                                    val dx = position.x - point.x * canvasSize.width
                                    val dy = position.y - point.y * canvasSize.height
                                    dx * dx + dy * dy
                                } ?: -1
                                activeCorner = nearest.takeIf { index ->
                                    val point = latestSelection.corners[index]
                                    val dx = position.x - point.x * canvasSize.width
                                    val dy = position.y - point.y * canvasSize.height
                                    dx * dx + dy * dy <= 56.dp.toPx() * 56.dp.toPx()
                                } ?: -1
                            },
                            onDragEnd = { activeCorner = -1 },
                            onDragCancel = { activeCorner = -1 },
                        ) { change, dragAmount ->
                            if (activeCorner >= 0 && canvasSize.width > 0 && canvasSize.height > 0) {
                                change.consume()
                                val updated = latestSelection.corners.toMutableList()
                                val current = updated[activeCorner]
                                updated[activeCorner] = NormalizedPoint(
                                    current.x + dragAmount.x / canvasSize.width,
                                    current.y + dragAmount.y / canvasSize.height,
                                ).constrained()
                                selection = latestSelection.copy(corners = updated)
                            }
                        }
                        },
                ) {
                    val offsets = selection.corners.map { Offset(it.x * size.width, it.y * size.height) }
                    val path = Path().apply {
                        moveTo(offsets[0].x, offsets[0].y)
                        offsets.drop(1).forEach { lineTo(it.x, it.y) }
                        close()
                    }
                    clipPath(path, ClipOp.Difference) {
                        drawRect(Color.Black.copy(alpha = 0.52f))
                    }
                    drawPath(path, Color(0xFF00E5FF), style = Stroke(width = 3.dp.toPx()))
                    offsets.forEach { point ->
                        drawCircle(Color.White, radius = 18.dp.toPx(), center = point)
                        drawCircle(Color(0xFF00A7C4), radius = 12.dp.toPx(), center = point)
                    }
                }
            }
        }
        if (!valid) {
            Text("Move the corners into a non-crossing document shape.", color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { selection = selection.copy(corners = defaultCropCorners()) },
                modifier = Modifier.weight(1f),
            ) { Text("Reset") }
            OutlinedButton(
                onClick = { selection = rotateCropClockwise(selection) },
                modifier = Modifier.weight(1f),
            ) { Text("Rotate") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(
                onClick = { onApply(selection) },
                enabled = valid,
                modifier = Modifier.weight(1f),
            ) { Text("Apply") }
        }
    }
}

@Composable
private fun ReceiptDetail(
    receipt: ReceiptEntity?,
    modifier: Modifier = Modifier,
    onSave: (String, String, String, String, String, String, String, String, String) -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCashew: (ReceiptEntity) -> Unit,
    onAdjust: (String) -> Unit,
) {
    if (receipt == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    var merchant by rememberSaveable(receipt.id, receipt.updatedAt) { mutableStateOf(receipt.merchantName) }
    var date by rememberSaveable(receipt.id, receipt.updatedAt) { mutableStateOf(receipt.transactionDate) }
    var subtotal by rememberSaveable(receipt.id, receipt.updatedAt) { mutableStateOf(receipt.subtotal) }
    var tax by rememberSaveable(receipt.id, receipt.updatedAt) { mutableStateOf(receipt.tax) }
    var total by rememberSaveable(receipt.id, receipt.updatedAt) { mutableStateOf(receipt.total) }
    var currency by rememberSaveable(receipt.id, receipt.updatedAt) { mutableStateOf(receipt.currency) }
    var rawText by rememberSaveable(receipt.id, receipt.updatedAt) { mutableStateOf(receipt.rawOcrText) }
    var addedDate by rememberSaveable(receipt.id, receipt.updatedAt) {
        mutableStateOf(formatAddedDate(receipt.createdAt))
    }
    var showLmInput by rememberSaveable(receipt.id) { mutableStateOf(false) }
    var showFullScreenImage by rememberSaveable(receipt.id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRetry by remember { mutableStateOf(false) }
    val originalImage = File(receipt.imagePath)
    val lmInputImage = File(originalImage.parentFile, "lm-input.jpg")
    val displayedImage = if (showLmInput && lmInputImage.exists()) lmInputImage else originalImage

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (lmInputImage.exists()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!showLmInput) {
                    Button(onClick = { showLmInput = false }, modifier = Modifier.weight(1f)) {
                        Text("Original")
                    }
                    OutlinedButton(onClick = { showLmInput = true }, modifier = Modifier.weight(1f)) {
                        Text("Sent to LM")
                    }
                } else {
                    OutlinedButton(onClick = { showLmInput = false }, modifier = Modifier.weight(1f)) {
                        Text("Original")
                    }
                    Button(onClick = { showLmInput = true }, modifier = Modifier.weight(1f)) {
                        Text("Sent to LM")
                    }
                }
            }
        }
        AsyncImage(
            model = displayedImage,
            contentDescription = if (showLmInput) "Image sent to LM Studio" else "Original receipt",
            modifier = Modifier.fillMaxWidth().height(280.dp).clickable { showFullScreenImage = true },
            contentScale = ContentScale.Fit,
        )
        Text(
            if (showLmInput && lmInputImage.exists()) "Exact prepared image sent to LM Studio"
            else "Original stored receipt",
            style = MaterialTheme.typography.bodySmall,
        )
        Text(statusLabel(receipt), style = MaterialTheme.typography.labelLarge)
        if (receipt.processingAttempt > 0) {
            val attemptKind = if (receipt.lastPromptType == OcrPromptType.SECOND_ATTEMPT.name) {
                "second-attempt prompt"
            } else {
                "standard prompt"
            }
            Text(
                "OCR attempt ${receipt.processingAttempt} · $attemptKind",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (receipt.processingStatus == ProcessingStatus.PROCESSING.name) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        }
        receipt.processingError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (receipt.processingStatus != ProcessingStatus.PROCESSING.name &&
            receipt.processingStatus != ProcessingStatus.QUEUED.name
        ) {
            OutlinedButton(
                onClick = { confirmRetry = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Try OCR again") }
        }
        HorizontalDivider()
        OutlinedTextField(
            addedDate,
            { addedDate = it },
            label = { Text("Added date (YYYY-MM-DD)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(date, { date = it }, label = { Text("Transaction date") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(subtotal, { subtotal = it }, label = { Text("Subtotal") }, modifier = Modifier.weight(1f))
            OutlinedTextField(tax, { tax = it }, label = { Text("Tax") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(total, { total = it }, label = { Text("Total") }, modifier = Modifier.weight(1f))
            OutlinedTextField(currency, { currency = it }, label = { Text("Currency") }, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(
            rawText,
            { rawText = it },
            label = { Text("Extracted text") },
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
        Button(
            onClick = {
                onSave(receipt.id, merchant, date, subtotal, tax, total, currency, rawText, addedDate)
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save changes") }
        OutlinedButton(
            onClick = { onCashew(receipt.copy(merchantName = merchant, transactionDate = date, total = total)) },
            enabled = total.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (receipt.cashewExportedAt == null) "Open in Cashew" else "Open in Cashew again") }
        OutlinedButton(
            onClick = { onAdjust(receipt.id) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Adjust document corners") }
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Delete receipt", color = MaterialTheme.colorScheme.error)
        }
    }
    if (showFullScreenImage) {
        Dialog(
            onDismissRequest = { showFullScreenImage = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                AsyncImage(
                    model = displayedImage,
                    contentDescription = if (showLmInput) {
                        "Full-screen image sent to LM Studio"
                    } else {
                        "Full-screen original receipt"
                    },
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
                TextButton(
                    onClick = { showFullScreenImage = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                ) {
                    Text("Close", color = Color.White)
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete receipt?") },
            text = { Text("The financial record and stored image will be permanently deleted.") },
            confirmButton = { TextButton(onClick = { onDelete(receipt.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
    if (confirmRetry) {
        AlertDialog(
            onDismissRequest = { confirmRetry = false },
            title = { Text("Try OCR again?") },
            text = {
                Text(
                    "Your current values stay visible while the receipt is queued. " +
                        "A valid new result will replace the extracted fields.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRetry = false
                    onRetry(receipt.id)
                }) { Text("Try again") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRetry = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SettingsScreen(
    initial: LmStudioConfig,
    initialSwipe: SwipeConfig,
    initialOcrConcurrency: Int,
    initialHomeNetwork: HomeNetworkConfig,
    initialThemeMode: ThemeMode,
    availableModels: List<String>,
    testingConnection: Boolean,
    modifier: Modifier = Modifier,
    onTestConnection: (LmStudioConfig) -> Unit,
    currentSsid: () -> String?,
    onHomeNetworkChanged: (HomeNetworkConfig) -> Unit,
    onForgetHomeNetwork: () -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onOpenCashewExport: () -> Unit,
    onSave: (SettingsSelection) -> Unit,
) {
    var url by rememberSaveable { mutableStateOf(initial.baseUrl) }
    var model by rememberSaveable { mutableStateOf(initial.model) }
    var token by rememberSaveable { mutableStateOf(initial.apiToken) }
    var rightSwipe by rememberSaveable { mutableStateOf(initialSwipe.right) }
    var leftSwipe by rememberSaveable { mutableStateOf(initialSwipe.left) }
    var confirmSwipeActions by rememberSaveable { mutableStateOf(initialSwipe.confirmActions) }
    var ocrConcurrency by rememberSaveable { mutableStateOf(initialOcrConcurrency) }
    var homeNetworkEnabled by rememberSaveable { mutableStateOf(initialHomeNetwork.enabled) }
    var homeNetworkSsid by rememberSaveable { mutableStateOf(initialHomeNetwork.ssid) }
    var networkMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var themeMode by rememberSaveable { mutableStateOf(initialThemeMode) }
    val context = LocalContext.current
    val networkPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    fun selectCurrentNetwork() {
        val ssid = currentSsid()
        if (ssid == null) {
            networkMessage = "Unable to read the current Wi-Fi name. Connect to Wi-Fi and ensure network permission is allowed."
        } else {
            homeNetworkSsid = ssid
            homeNetworkEnabled = true
            onHomeNetworkChanged(HomeNetworkConfig(enabled = true, ssid = ssid))
            networkMessage = "Home network set to $ssid"
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (networkPermissions.all { results[it] == true }) selectCurrentNetwork()
        else networkMessage = "Network permission is required to identify the current Wi-Fi network."
    }
    LaunchedEffect(availableModels) {
        if (availableModels.isNotEmpty() && model !in availableModels) {
            model = availableModels.firstOrNull { it.equals(model, ignoreCase = true) }
                ?: availableModels.first()
        }
    }
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Appearance", style = MaterialTheme.typography.titleMedium)
        ThemeModeDropdown(themeMode) {
            themeMode = it
            onThemeModeChanged(it)
        }
        Text(
            "Theme changes apply and save immediately.",
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider()
        Text("Connect to a vision-capable model running in LM Studio on this computer or your local network.")
        OutlinedTextField(url, { url = it }, label = { Text("Server URL") }, modifier = Modifier.fillMaxWidth())
        Text("Emulator default: http://10.0.2.2:1234", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(token, { token = it }, label = { Text("API token (optional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedButton(
            onClick = { onTestConnection(LmStudioConfig(url, model, token)) },
            enabled = url.isNotBlank() && !testingConnection,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (testingConnection) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("Testing connection…")
            } else {
                Text("Test connection")
            }
        }
        ModelDropdown(
            selected = model,
            models = availableModels,
            onSelected = { model = it },
        )
        OcrConcurrencyDropdown(ocrConcurrency) { ocrConcurrency = it }
        Text(
            "Receipts are queued in capture order. Higher values allow LM Studio to process multiple receipts at once.",
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider()
        Text("Home network", style = MaterialTheme.typography.titleMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Process OCR only at home")
                Text(
                    if (homeNetworkSsid.isBlank()) "No home network selected" else homeNetworkSsid,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = homeNetworkEnabled,
                onCheckedChange = { enabled ->
                    homeNetworkEnabled = enabled
                    onHomeNetworkChanged(HomeNetworkConfig(enabled, homeNetworkSsid))
                    networkMessage = if (enabled) {
                        "Home-only OCR enabled"
                    } else {
                        "Home-only OCR disabled"
                    }
                },
                enabled = homeNetworkSsid.isNotBlank(),
            )
        }
        OutlinedButton(
            onClick = {
                if (networkPermissions.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    }
                ) {
                    selectCurrentNetwork()
                } else {
                    permissionLauncher.launch(networkPermissions)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Set current Wi-Fi as home") }
        if (homeNetworkSsid.isNotBlank()) {
            TextButton(
                onClick = {
                    homeNetworkEnabled = false
                    homeNetworkSsid = ""
                    onForgetHomeNetwork()
                    networkMessage = "Home network forgotten"
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Forget home network") }
        }
        networkMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text(
            "Home-network changes save immediately. When enabled, receipts wait for unmetered Wi-Fi and remain queued until LM Studio is reachable.",
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider()
        Text("Integrations", style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = onOpenCashewExport, modifier = Modifier.fillMaxWidth()) {
            Text("Cashew Export")
        }
        Text(
            "Choose which receipt fields are included when opening Cashew.",
            style = MaterialTheme.typography.bodySmall,
        )
        HorizontalDivider()
        Text("Receipt list swipe actions", style = MaterialTheme.typography.titleMedium)
        SwipeActionDropdown("Swipe right", rightSwipe) { rightSwipe = it }
        SwipeActionDropdown("Swipe left", leftSwipe) { leftSwipe = it }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Confirm swipe actions")
                Text(
                    if (confirmSwipeActions) {
                        "Ask before running the selected swipe action."
                    } else {
                        "Run swipe actions immediately. Archive and delete still have Undo."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = confirmSwipeActions,
                onCheckedChange = { confirmSwipeActions = it },
            )
        }
        Button(
            onClick = {
                onSave(SettingsSelection(
                    lmStudio = LmStudioConfig(url, model, token),
                    swipes = SwipeConfig(rightSwipe, leftSwipe, confirmSwipeActions),
                    ocrConcurrency = ocrConcurrency,
                    homeNetwork = HomeNetworkConfig(homeNetworkEnabled, homeNetworkSsid),
                ))
            },
            enabled = url.isNotBlank() && model.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save settings") }
        Text(
            "For a physical phone, use the computer's LAN address and enable Serve on Local Network in LM Studio.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun CashewExportSettingsScreen(
    config: CashewExportConfig,
    modifier: Modifier = Modifier,
    onConfigChanged: (CashewExportConfig) -> Unit,
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Amount", style = MaterialTheme.typography.titleMedium)
        Text("Receipt total → Cashew amount (required)")
        HorizontalDivider()
        CashewExportToggle(
            title = "Merchant name",
            destination = "Cashew title",
            checked = config.includeTitle,
        ) { onConfigChanged(config.copy(includeTitle = it)) }
        CashewExportToggle(
            title = "Transaction date",
            destination = "Cashew date",
            checked = config.includeDate,
        ) { onConfigChanged(config.copy(includeDate = it)) }
        CashewExportToggle(
            title = "Receipt reference",
            destination = "Cashew notes",
            checked = config.includeReceiptReference,
        ) { onConfigChanged(config.copy(includeReceiptReference = it)) }
        CashewExportToggle(
            title = "Raw OCR text",
            destination = "Cashew notes",
            checked = config.includeOcrText,
        ) { onConfigChanged(config.copy(includeOcrText = it)) }
        HorizontalDivider()
        Text("Preview", style = MaterialTheme.typography.titleMedium)
        Text(
            buildList {
                add("Amount: receipt total")
                if (config.includeTitle) add("Title: merchant name")
                if (config.includeDate) add("Date: transaction date")
                if (config.includeReceiptReference) add("Notes: receipt reference")
                if (config.includeOcrText) add("Notes: raw OCR text")
            }.joinToString("\n"),
        )
        Text("Changes save immediately and apply to every Cashew action.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CashewExportToggle(
    title: String,
    destination: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text("→ $destination", style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeDropdown(selected: ThemeMode, onSelected: (ThemeMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(
            value = themeModeLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text("Theme") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            ThemeMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(themeModeLabel(mode)) },
                    onClick = {
                        onSelected(mode)
                        expanded = false
                    },
                )
            }
        }
    }
}

internal fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.FOLLOW_DEVICE -> "Follow device"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private data class SettingsSelection(
    val lmStudio: LmStudioConfig,
    val swipes: SwipeConfig,
    val ocrConcurrency: Int,
    val homeNetwork: HomeNetworkConfig,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrConcurrencyDropdown(selected: Int, onSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(
            value = selected.toString(),
            onValueChange = {},
            readOnly = true,
            label = { Text("Concurrent OCR jobs") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            (1..4).forEach { count ->
                DropdownMenuItem(
                    text = { Text(count.toString()) },
                    onClick = {
                        onSelected(count)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelDropdown(
    selected: String,
    models: List<String>,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (models.isNotEmpty()) expanded = !expanded },
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            enabled = models.isNotEmpty(),
            label = { Text("Model") },
            placeholder = { Text("Test connection to load models") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = { Text(model) },
                    onClick = {
                        onSelected(model)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeActionDropdown(
    label: String,
    selected: SwipeAction,
    onSelected: (SwipeAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = !expanded }) {
        OutlinedTextField(
            value = swipeActionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            SwipeAction.entries.forEach { action ->
                DropdownMenuItem(
                    text = { Text(swipeActionLabel(action)) },
                    onClick = {
                        onSelected(action)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun swipeActionLabel(action: SwipeAction): String = when (action) {
    SwipeAction.ARCHIVE -> "Archive / Restore"
    SwipeAction.DELETE -> "Delete"
    SwipeAction.CASHEW -> "Open in Cashew"
}
