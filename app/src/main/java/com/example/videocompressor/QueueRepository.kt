package com.example.videocompressor

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "compressor_prefs")

sealed class QueueItemStatus {
    data object Queued : QueueItemStatus()
    data class Compressing(
        val progressPercent: Int,
        val elapsedMs: Long,
        val etaMs: Long
    ) : QueueItemStatus()

    data class Completed(
        val compressedSizeBytes: Long,
        val outputUriString: String,
        val elapsedMs: Long
    ) : QueueItemStatus()

    data object Cancelled : QueueItemStatus()
    data class Failed(val reason: String) : QueueItemStatus()
}

data class QueueItem(
    val id: String,
    val inputUriString: String,
    val displayName: String,
    val originalSizeBytes: Long,
    val status: QueueItemStatus = QueueItemStatus.Queued
) {
    val savedPercentage: Int?
        get() = when (val currentStatus = status) {
            is QueueItemStatus.Completed -> {
                if (originalSizeBytes > 0L) {
                    val diff = originalSizeBytes - currentStatus.compressedSizeBytes
                    ((diff * 100L) / originalSizeBytes).toInt()
                } else {
                    0
                }
            }
            else -> null
        }
}

data class PersistedAppState(
    val outputDirUri: String?,
    val preset: CompressionPreset,
    val queue: List<QueueItem>
)

class QueueRepository(private val context: Context) {

    private object Keys {
        val OUTPUT_DIR_URI = stringPreferencesKey("output_dir_uri")
        val SELECTED_PRESET = stringPreferencesKey("selected_preset")
        val QUEUE_JSON = stringPreferencesKey("queue_json")
    }

    val persistedStateFlow: Flow<PersistedAppState> = context.dataStore.data.map { prefs ->
        val outputDir = prefs[Keys.OUTPUT_DIR_URI]
        val preset = CompressionPreset.fromNameOrDefault(prefs[Keys.SELECTED_PRESET])
        val queueJson = prefs[Keys.QUEUE_JSON]
        PersistedAppState(
            outputDirUri = outputDir,
            preset = preset,
            queue = decodeQueue(queueJson)
        )
    }

    suspend fun saveOutputDirUri(uriString: String?) {
        context.dataStore.edit { prefs ->
            if (uriString == null) {
                prefs.remove(Keys.OUTPUT_DIR_URI)
            } else {
                prefs[Keys.OUTPUT_DIR_URI] = uriString
            }
        }
    }

    suspend fun savePreset(preset: CompressionPreset) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_PRESET] = preset.name
        }
    }

    suspend fun saveQueue(items: List<QueueItem>) {
        val encoded = encodeQueue(items)
        context.dataStore.edit { prefs ->
            prefs[Keys.QUEUE_JSON] = encoded
        }
    }

    private fun encodeQueue(items: List<QueueItem>): String {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("inputUriString", item.inputUriString)
                put("displayName", item.displayName)
                put("originalSizeBytes", item.originalSizeBytes)
                when (val status = item.status) {
                    is QueueItemStatus.Queued -> put("statusType", "QUEUED")
                    is QueueItemStatus.Compressing -> {
                        // Persist active items as QUEUED so they can be cleanly restarted after process death
                        put("statusType", "QUEUED")
                    }
                    is QueueItemStatus.Completed -> {
                        put("statusType", "COMPLETED")
                        put("compressedSizeBytes", status.compressedSizeBytes)
                        put("outputUriString", status.outputUriString)
                        put("elapsedMs", status.elapsedMs)
                    }
                    is QueueItemStatus.Cancelled -> put("statusType", "CANCELLED")
                    is QueueItemStatus.Failed -> {
                        put("statusType", "FAILED")
                        put("reason", status.reason)
                    }
                }
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun decodeQueue(jsonString: String?): List<QueueItem> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(jsonString)
            val result = mutableListOf<QueueItem>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val inputUri = obj.optString("inputUriString")
                val name = obj.optString("displayName", "video.mp4")
                val originalSize = obj.optLong("originalSizeBytes", 0L)
                if (id.isBlank() || inputUri.isBlank()) continue

                val status = when (obj.optString("statusType", "QUEUED")) {
                    "COMPLETED" -> QueueItemStatus.Completed(
                        compressedSizeBytes = obj.optLong("compressedSizeBytes", 0L),
                        outputUriString = obj.optString("outputUriString", ""),
                        elapsedMs = obj.optLong("elapsedMs", 0L)
                    )
                    "CANCELLED" -> QueueItemStatus.Cancelled
                    "FAILED" -> QueueItemStatus.Failed(
                        reason = obj.optString("reason", "Interrupted")
                    )
                    else -> QueueItemStatus.Queued
                }

                result.add(
                    QueueItem(
                        id = id,
                        inputUriString = inputUri,
                        displayName = name,
                        originalSizeBytes = originalSize,
                        status = status
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }
}