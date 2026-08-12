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
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.example.ocr_finace.data.ProcessingStatus
import com.example.ocr_finace.data.OcrPromptType
import com.example.ocr_finace.data.ReceiptEntity
import com.example.ocr_finace.integration.cashew.CashewLinkBuilder
import com.example.ocr_finace.settings.LmStudioConfig
import com.example.ocr_finace.settings.SwipeAction
import com.example.ocr_finace.settings.SwipeConfig
import com.example.ocr_finace.settings.HomeNetworkConfig
import com.example.ocr_finace.settings.ThemeMode
import com.example.ocr_finace.settings.ReceiptLayoutMode
import com.example.ocr_finace.settings.CashewExportConfig
import com.example.ocr_finace.ui.receipt.ReceiptViewModel
import com.example.ocr_finace.ui.receipt.receiptDisplayTitle
import com.example.ocr_finace.ui.receipt.ReceiptDestination
import com.example.ocr_finace.ui.theme.OCR_FinaceTheme
import java.io.File
import java.text.DateFormat
import java.util.Date
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
    val snackbarHost = remember { SnackbarHostState() }
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

    LaunchedEffect(destination) {
        viewModel.select((destination as? ReceiptDestination.Detail)?.receiptId)
    }

    LaunchedEffect(destination, missingSelectionId) {
        if (destination is ReceiptDestination.Detail &&
            missingSelectionId == destination.receiptId
        ) {
            destinationValue = ReceiptDestination.List.encode()
            viewModel.select(null)
        }
    }

    BackHandler(enabled = destination !is ReceiptDestination.List) {
        destinationValue = if (destination is ReceiptDestination.CashewSettings) {
            ReceiptDestination.Settings.encode()
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
                        },
                    )
                },
                navigationIcon = {
                    if (destination !is ReceiptDestination.List) {
                        TextButton(onClick = {
                            destinationValue = if (destination is ReceiptDestination.CashewSettings) {
                                ReceiptDestination.Settings.encode()
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
                    viewModel.prepareCapture()?.let(camera::launch)
                },
                onImport = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
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
            )
            is ReceiptDestination.Detail -> ReceiptDetail(
                receipt = selected,
                modifier = Modifier.padding(padding),
                onSave = { id, merchant, date, subtotal, tax, total, currency, rawText ->
                    viewModel.save(id, merchant, date, subtotal, tax, total, currency, rawText) {
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
        }
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
        }
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
                    label = { Text("From YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = dateTo,
                    onValueChange = { dateTo = it },
                    label = { Text("To YYYY-MM-DD") },
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(scopedReceipts.filterNot { it.id in hiddenReceiptIds }, key = { it.id }) { receipt ->
                    SwipeableReceiptCard(
                        receipt = receipt,
                        swipeConfig = swipeConfig,
                        onClick = { onOpen(receipt.id) },
                        onLongClick = { actionReceipt = receipt },
                        onMenuClick = { actionReceipt = receipt },
                        layoutMode = layoutMode,
                        onSwipe = { action ->
                            requestedSwipe = PendingSwipeAction(
                                receipt,
                                action.toReceiptListAction(receipt.isArchived),
                            )
                        },
                    )
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
            title = { Text("$verb receipt?") },
            text = { Text(receiptActionConfirmation(requested.action)) },
            confirmButton = {
                TextButton(onClick = {
                    requestedSwipe = null
                    activeUndo?.let { previous ->
                        undoJob?.cancel()
                        commitSwipe(previous)
                    }
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

private enum class ReceiptListAction { ARCHIVE, RESTORE, DELETE }

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
    ReceiptSort.DATE_NEWEST -> "Newest"
    ReceiptSort.DATE_OLDEST -> "Oldest"
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
}

internal const val SWIPE_UNDO_WINDOW_MILLIS = 5_000L

private fun swipeActionVerb(action: ReceiptListAction): String = when (action) {
    ReceiptListAction.ARCHIVE -> "Archive"
    ReceiptListAction.RESTORE -> "Restore"
    ReceiptListAction.DELETE -> "Delete"
}

private fun receiptActionConfirmation(action: ReceiptListAction): String = when (action) {
    ReceiptListAction.ARCHIVE ->
        "This receipt will be removed from the active list. You can undo for five seconds."
    ReceiptListAction.RESTORE ->
        "This receipt will return to the active list. You can undo for five seconds."
    ReceiptListAction.DELETE ->
        "This receipt and its image will be permanently deleted after five seconds."
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
            Modifier.padding(if (layoutMode == ReceiptLayoutMode.THUMBNAIL) 12.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (layoutMode == ReceiptLayoutMode.THUMBNAIL) {
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
                if (layoutMode == ReceiptLayoutMode.THUMBNAIL) {
                    Text(receipt.transactionDate.ifBlank { DateFormat.getDateInstance().format(Date(receipt.createdAt)) })
                    Text(statusLabel(receipt))
                } else {
                    Text(
                        listOf(
                            receipt.transactionDate.ifBlank {
                                DateFormat.getDateInstance().format(Date(receipt.createdAt))
                            },
                            statusLabel(receipt),
                        ).joinToString(" · "),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
private fun ReceiptDetail(
    receipt: ReceiptEntity?,
    modifier: Modifier = Modifier,
    onSave: (String, String, String, String, String, String, String, String) -> Unit,
    onRetry: (String) -> Unit,
    onDelete: (String) -> Unit,
    onCashew: (ReceiptEntity) -> Unit,
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
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmRetry by remember { mutableStateOf(false) }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AsyncImage(
            model = File(receipt.imagePath),
            contentDescription = "Full receipt",
            modifier = Modifier.fillMaxWidth().height(280.dp),
            contentScale = ContentScale.Fit,
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
            onClick = { onSave(receipt.id, merchant, date, subtotal, tax, total, currency, rawText) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save changes") }
        OutlinedButton(
            onClick = { onCashew(receipt.copy(merchantName = merchant, transactionDate = date, total = total)) },
            enabled = total.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (receipt.cashewExportedAt == null) "Open in Cashew" else "Open in Cashew again") }
        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Delete receipt", color = MaterialTheme.colorScheme.error)
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
        Button(
            onClick = {
                onSave(SettingsSelection(
                    lmStudio = LmStudioConfig(url, model, token),
                    swipes = SwipeConfig(rightSwipe, leftSwipe),
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
            value = selected.name.lowercase().replaceFirstChar(Char::uppercase),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor().fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            SwipeAction.entries.forEach { action ->
                DropdownMenuItem(
                    text = { Text(action.name.lowercase().replaceFirstChar(Char::uppercase)) },
                    onClick = {
                        onSelected(action)
                        expanded = false
                    },
                )
            }
        }
    }
}
