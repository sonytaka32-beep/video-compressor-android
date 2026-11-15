package com.example.videocompressor

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

sealed class UiState {
    data object Loading : UiState()
    data class Ready(
        val queue: List<QueueItem>,
        val selectedPreset: CompressionPreset,
        val outputDirUriString: String?,
        val outputDirDisplayName: String?,
        val isProcessing: Boolean
    ) : UiState()
}

sealed class UiEvent {
    data class ShowMessage(val message: String) : UiEvent()
    data class QueueFinished(val completedCount: Int, val totalSavedBytes: Long) : UiEvent()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = QueueRepository(application.applicationContext)
    private val videoCompressor = VideoCompressor(application.applicationContext)

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private var queueJob: Job? = null
    @Volatile
    private var activeItemId: String? = null
    @Volatile
    private var cancelAllRequested: Boolean = false

    init {
        viewModelScope.launch {
            val saved = repository.persistedStateFlow.first()
            val folderName = saved.outputDirUri?.let { resolveTreeDisplayName(Uri.parse(it)) }
            _uiState.value = UiState.Ready(
                queue = saved.queue,
                selectedPreset = saved.preset,
                outputDirUriString = saved.outputDirUri,
                outputDirDisplayName = folderName,
                isProcessing = false
            )
        }
    }

    fun addInputFiles(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val currentState = _uiState.value as? UiState.Ready ?: return

        val newItems = uris.map { uri ->
            val (name, size) = queryFileMeta(uri)
            QueueItem(
                id = UUID.randomUUID().toString(),
                inputUriString = uri.toString(),
                displayName = name,
                originalSizeBytes = size,
                status = QueueItemStatus.Queued
            )
        }

        val updatedQueue = currentState.queue + newItems
        updateReadyState(queue = updatedQueue)
        persistQueueAsync(updatedQueue)
    }

    fun selectOutputFolder(treeUri: Uri) {
        val displayName = resolveTreeDisplayName(treeUri)
        updateReadyState(
            outputDirUriString = treeUri.toString(),
            outputDirDisplayName = displayName
        )
        viewModelScope.launch {
            repository.saveOutputDirUri(treeUri.toString())
        }
    }

    fun selectPreset(preset: CompressionPreset) {
        updateReadyState(selectedPreset = preset)
        viewModelScope.launch {
            repository.savePreset(preset)
        }
    }

    fun startQueue() {
        val currentState = _uiState.value as? UiState.Ready ?: return
        if (currentState.isProcessing) return

        val outputUriString = currentState.outputDirUriString
        if (outputUriString.isNullOrBlank()) {
            _events.tryEmit(UiEvent.ShowMessage("Please select an output folder first."))
            return
        }

        val hasRunnableItems = currentState.queue.any {
            it.status is QueueItemStatus.Queued ||
                it.status is QueueItemStatus.Cancelled ||
                it.status is QueueItemStatus.Failed
        }
        if (!hasRunnableItems) {
            _events.tryEmit(UiEvent.ShowMessage("No queued videos to compress."))
            return
        }

        // Reset Cancelled or Failed items to Queued if user hits Start again when none are Queued
        val hasStrictlyQueued = currentState.queue.any { it.status is QueueItemStatus.Queued }
        val normalizedQueue = if (hasStrictlyQueued) {
            currentState.queue
        } else {
            currentState.queue.map { item ->
                if (item.status is QueueItemStatus.Cancelled || item.status is QueueItemStatus.Failed) {
                    item.copy(status = QueueItemStatus.Queued)
                } else {
                    item
                }
            }
        }

        cancelAllRequested = false
        updateReadyState(queue = normalizedQueue, isProcessing = true)
        persistQueueAsync(normalizedQueue)

        queueJob = viewModelScope.launch {
            var completedInRun = 0
            var bytesSavedInRun = 0L

            while (!cancelAllRequested) {
                val latestState = _uiState.value as? UiState.Ready ?: break
                val nextItem = latestState.queue.firstOrNull { it.status is QueueItemStatus.Queued }
                    ?: break

                val presetToUse = latestState.selectedPreset
                val targetTreeUri = Uri.parse(outputUriString)
                activeItemId = nextItem.id

                updateItemStatus(
                    itemId = nextItem.id,
                    newStatus = QueueItemStatus.Compressing(progressPercent = 0, elapsedMs = 0L, etaMs = -1L),
                    saveToDisk = false
                )

                val result = videoCompressor.compress(
                    inputUri = Uri.parse(nextItem.inputUriString),
                    inputDisplayName = nextItem.displayName,
                    outputTreeUri = targetTreeUri,
                    preset = presetToUse,
                    onProgress = { progress ->
                        updateItemStatus(
                            itemId = nextItem.id,
                            newStatus = QueueItemStatus.Compressing(
                                progressPercent = progress.percent,
                                elapsedMs = progress.elapsedMs,
                                etaMs = progress.etaMs
                            ),
                            saveToDisk = false
                        )
                    }
                )

                activeItemId = null

                when (result) {
                    is CompressionResult.Success -> {
                        completedInRun += 1
                        val saved = (nextItem.originalSizeBytes - result.compressedSizeBytes).coerceAtLeast(0L)
                        bytesSavedInRun += saved
                        updateItemStatus(
                            itemId = nextItem.id,
                            newStatus = QueueItemStatus.Completed(
                                compressedSizeBytes = result.compressedSizeBytes,
                                outputUriString = result.outputUri.toString(),
                                elapsedMs = result.elapsedMs
                            ),
                            saveToDisk = true
                        )
                    }

                    is CompressionResult.Cancelled -> {
                        updateItemStatus(
                            itemId = nextItem.id,
                            newStatus = QueueItemStatus.Cancelled,
                            saveToDisk = true
                        )
                    }

                    is CompressionResult.Error -> {
                        updateItemStatus(
                            itemId = nextItem.id,
                            newStatus = QueueItemStatus.Failed(result.message),
                            saveToDisk = true
                        )
                    }
                }
            }

            val finalState = _uiState.value as? UiState.Ready
            if (finalState != null) {
                updateReadyState(isProcessing = false)
                persistQueueAsync(finalState.queue)
            }

            if (completedInRun > 0 && !cancelAllRequested) {
                _events.tryEmit(UiEvent.QueueFinished(completedInRun, bytesSavedInRun))
            } else if (cancelAllRequested) {
                _events.tryEmit(UiEvent.ShowMessage("Queue cancelled."))
            }
        }
    }

    fun cancelItem(itemId: String) {
        val currentState = _uiState.value as? UiState.Ready ?: return
        val target = currentState.queue.firstOrNull { it.id == itemId } ?: return

        when (target.status) {
            is QueueItemStatus.Compressing -> {
                if (activeItemId == itemId) {
                    videoCompressor.cancelActive()
                }
            }
            is QueueItemStatus.Queued -> {
                updateItemStatus(itemId, QueueItemStatus.Cancelled, saveToDisk = true)
            }
            else -> {
                val filtered = currentState.queue.filterNot { it.id == itemId }
                updateReadyState(queue = filtered)
                persistQueueAsync(filtered)
            }
        }
    }

    fun cancelEntireQueue() {
        val currentState = _uiState.value as? UiState.Ready ?: return
        cancelAllRequested = true
        videoCompressor.cancelActive()

        val updated = currentState.queue.map { item ->
            when (item.status) {
                is QueueItemStatus.Queued, is QueueItemStatus.Compressing -> {
                    item.copy(status = QueueItemStatus.Cancelled)
                }
                else -> item
            }
        }
        updateReadyState(queue = updated, isProcessing = false)
        persistQueueAsync(updated)
    }

    fun clearFinishedItems() {
        val currentState = _uiState.value as? UiState.Ready ?: return
        val remaining = currentState.queue.filter {
            it.status is QueueItemStatus.Queued || it.status is QueueItemStatus.Compressing
        }
        updateReadyState(queue = remaining)
        persistQueueAsync(remaining)
    }

    fun clearAllItems() {
        cancelEntireQueue()
        updateReadyState(queue = emptyList(), isProcessing = false)
        persistQueueAsync(emptyList())
    }

    private fun updateItemStatus(itemId: String, newStatus: QueueItemStatus, saveToDisk: Boolean) {
        _uiState.update { state ->
            if (state is UiState.Ready) {
                val updatedList = state.queue.map { item ->
                    if (item.id == itemId) item.copy(status = newStatus) else item
                }
                if (saveToDisk) {
                    persistQueueAsync(updatedList)
                }
                state.copy(queue = updatedList)
            } else {
                state
            }
        }
    }

    private fun updateReadyState(
        queue: List<QueueItem>? = null,
        selectedPreset: CompressionPreset? = null,
        outputDirUriString: String? = null,
        outputDirDisplayName: String? = null,
        isProcessing: Boolean? = null
    ) {
        _uiState.update { state ->
            if (state is UiState.Ready) {
                state.copy(
                    queue = queue ?: state.queue,
                    selectedPreset = selectedPreset ?: state.selectedPreset,
                    outputDirUriString = outputDirUriString ?: state.outputDirUriString,
                    outputDirDisplayName = outputDirDisplayName ?: state.outputDirDisplayName,
                    isProcessing = isProcessing ?: state.isProcessing
                )
            } else {
                state
            }
        }
    }

    private fun persistQueueAsync(queue: List<QueueItem>) {
        viewModelScope.launch {
            repository.saveQueue(queue)
        }
    }

    private fun queryFileMeta(uri: Uri): Pair<String, Long> {
        val resolver = getApplication<Application>().contentResolver
        var displayName = "video_${System.currentTimeMillis()}.mp4"
        var sizeBytes = 0L

        resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex) ?: displayName
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    sizeBytes = cursor.getLong(sizeIndex)
                }
            }
        }
        return displayName to sizeBytes
    }

    private fun resolveTreeDisplayName(treeUri: Uri): String {
        val doc = DocumentFile.fromTreeUri(getApplication(), treeUri)
        return doc?.name ?: treeUri.lastPathSegment ?: "Selected Folder"
    }
}