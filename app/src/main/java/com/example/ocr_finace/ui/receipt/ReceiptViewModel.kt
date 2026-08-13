package com.example.ocr_finace.ui.receipt

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ocr_finace.FinanceApplication
import com.example.ocr_finace.data.ReceiptEntity
import com.example.ocr_finace.data.OcrPromptType
import com.example.ocr_finace.data.parseAddedDate
import com.example.ocr_finace.settings.LmStudioConfig
import com.example.ocr_finace.settings.SwipeConfig
import com.example.ocr_finace.settings.HomeNetworkConfig
import com.example.ocr_finace.settings.ThemeMode
import com.example.ocr_finace.settings.ReceiptLayoutMode
import com.example.ocr_finace.settings.CashewExportConfig
import com.example.ocr_finace.image.CropSelection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ReceiptViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as FinanceApplication).container
    private val repository = container.receipts
    private val ocrQueue = container.ocrQueue
    private val selectedId = MutableStateFlow<String?>(null)
    private val _missingSelectionId = MutableStateFlow<String?>(null)
    val missingSelectionId: StateFlow<String?> = _missingSelectionId
    private val _pendingAdjustmentId = MutableStateFlow<String?>(null)
    val pendingAdjustmentId: StateFlow<String?> = _pendingAdjustmentId
    private var pendingCaptureId: String? = null

    val receipts: StateFlow<List<ReceiptEntity>> = repository.observeAll().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList(),
    )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val selectedReceipt: StateFlow<ReceiptEntity?> = selectedId.flatMapLatest { id ->
        if (id == null) flowOf(null) else repository.observe(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val message = MutableStateFlow<String?>(null)
    val availableModels = MutableStateFlow<List<String>>(emptyList())
    val testingConnection = MutableStateFlow(false)
    val themeMode = MutableStateFlow(container.settings.loadThemeMode())
    val receiptLayoutMode = MutableStateFlow(container.settings.loadReceiptLayoutMode())
    val cashewExportConfig = MutableStateFlow(container.settings.loadCashewExportConfig())

    fun select(id: String?) {
        selectedId.value = id
        _missingSelectionId.value = null
        if (id != null) viewModelScope.launch(Dispatchers.IO) {
            if (repository.get(id) == null) _missingSelectionId.value = id
        }
    }

    fun prepareCapture(): Uri? = runCatching {
        val (id, uri) = repository.prepareCapture()
        pendingCaptureId = id
        uri
    }.onFailure { message.value = it.message }.getOrNull()

    fun onCaptureResult(success: Boolean) {
        val id = pendingCaptureId ?: return
        pendingCaptureId = null
        if (!success) {
            repository.cancelCapture(id)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.finishCapture(id) }
                .onSuccess { _pendingAdjustmentId.value = it.id }
                .onFailure { message.value = it.message ?: "Unable to save the photo" }
        }
    }

    fun importImage(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { repository.importImage(uri) }
                .onSuccess { _pendingAdjustmentId.value = it.id }
                .onFailure { message.value = it.message ?: "Unable to import the image" }
        }
    }

    fun retry(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ocrQueue.enqueue(id, OcrPromptType.SECOND_ATTEMPT)
        }
    }

    fun save(
        id: String,
        merchant: String,
        date: String,
        subtotal: String,
        tax: String,
        total: String,
        currency: String,
        rawText: String,
        addedDate: String,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                repository.updateFields(
                    id,
                    merchant,
                    date,
                    subtotal,
                    tax,
                    total,
                    currency,
                    rawText,
                    parseAddedDate(addedDate),
                )
            }.onSuccess {
                message.value = "Receipt saved"
                launch(Dispatchers.Main) { onSaved() }
            }.onFailure {
                message.value = it.message ?: "Unable to save receipt"
            }
        }
    }

    fun delete(id: String, onDeleted: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            ocrQueue.cancel(id)
            repository.delete(id)
            selectedId.value = null
            launch(Dispatchers.Main) { onDeleted() }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ocrQueue.cancel(id)
            repository.delete(id)
        }
    }

    fun archive(id: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.archive(id) }
    }

    fun archive(id: String, onArchived: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.archive(id)
            launch(Dispatchers.Main) { onArchived() }
        }
    }

    fun restore(id: String, onRestored: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.restore(id)
            launch(Dispatchers.Main) { onRestored() }
        }
    }

    fun markCashewOpened(id: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.markCashewOpened(id) }
    }

    fun loadSettings(): LmStudioConfig = container.settings.load()

    fun isLmStudioConfigured(): Boolean = container.settings.load().let { config ->
        config.baseUrl.isNotBlank() && config.model.isNotBlank()
    }

    fun saveSettings(config: LmStudioConfig) {
        container.settings.save(config)
        message.value = "LM Studio settings saved"
    }

    fun testConnection(config: LmStudioConfig) {
        if (testingConnection.value) return
        testingConnection.value = true
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { container.api.listModels(config.baseUrl, config.apiToken) }
                .onSuccess { models ->
                    availableModels.value = models
                    message.value = if (models.isEmpty()) {
                        "Connected to LM Studio, but no models are available"
                    } else {
                        "Connected to LM Studio — ${models.size} models available"
                    }
                }
                .onFailure {
                    availableModels.value = emptyList()
                    message.value = it.message ?: "Unable to connect to LM Studio"
                }
            testingConnection.value = false
        }
    }

    fun loadSwipeConfig(): SwipeConfig = container.settings.loadSwipeConfig()

    fun saveSwipeConfig(config: SwipeConfig) {
        container.settings.saveSwipeConfig(config)
    }

    fun loadOcrConcurrency(): Int = container.settings.loadOcrConcurrency()

    fun saveOcrConcurrency(value: Int) {
        container.settings.saveOcrConcurrency(value)
    }

    fun loadHomeNetwork(): HomeNetworkConfig = container.settings.loadHomeNetwork()

    fun useCurrentNetwork(): String? = container.homeNetwork.currentSsid()

    fun saveHomeNetwork(config: HomeNetworkConfig) {
        container.settings.saveHomeNetwork(config)
    }

    fun forgetHomeNetwork() {
        container.settings.forgetHomeNetwork()
    }

    fun saveThemeMode(mode: ThemeMode) {
        container.settings.saveThemeMode(mode)
        themeMode.value = mode
    }

    fun saveReceiptLayoutMode(mode: ReceiptLayoutMode) {
        container.settings.saveReceiptLayoutMode(mode)
        receiptLayoutMode.value = mode
    }

    fun saveCashewExportConfig(config: CashewExportConfig) {
        container.settings.saveCashewExportConfig(config)
        cashewExportConfig.value = config
    }

    fun loadCropSelection(receiptId: String): CropSelection =
        container.settings.loadCropSelection(receiptId)

    fun saveCropSelection(receiptId: String, selection: CropSelection) {
        container.settings.saveCropSelection(receiptId, selection)
        message.value = "Document corners saved"
    }

    fun finishAdjustment(receiptId: String, selection: CropSelection?, processAfter: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            selection?.let { container.settings.saveCropSelection(receiptId, it) }
            if (processAfter) ocrQueue.enqueue(receiptId)
            if (_pendingAdjustmentId.value == receiptId) _pendingAdjustmentId.value = null
            if (selection != null) message.value = "Document corners saved"
        }
    }

    fun clearMessage() {
        message.value = null
    }
}
