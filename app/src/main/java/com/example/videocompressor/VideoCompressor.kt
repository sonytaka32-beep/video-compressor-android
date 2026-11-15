package com.example.videocompressor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed class CompressionResult {
    data class Success(
        val outputUri: Uri,
        val compressedSizeBytes: Long,
        val elapsedMs: Long
    ) : CompressionResult()

    data object Cancelled : CompressionResult()
    data class Error(val message: String) : CompressionResult()
}

data class CompressionProgress(
    val percent: Int,
    val elapsedMs: Long,
    val etaMs: Long
)

class VideoCompressor(private val context: Context) {

    suspend fun compress(
        inputUri: Uri,
        inputDisplayName: String,
        outputTreeUri: Uri,
        preset: CompressionPreset,
        onProgress: (CompressionProgress) -> Unit
    ): CompressionResult = withContext(Dispatchers.IO) {
        val tempInput = File(context.cacheDir, "in_${System.currentTimeMillis()}.tmp")
        val tempOutput = File(context.cacheDir, "out_${System.currentTimeMillis()}.mp4")
        val startTimeMs = System.currentTimeMillis()

        try {
            copyUriToFile(inputUri, tempInput)
            val durationMs = extractDurationMs(tempInput).coerceAtLeast(1L)

            // Reset and enable statistics callback for progress tracking
            FFmpegKitConfig.enableStatisticsCallback { stats ->
                val currentMediaTimeMs = stats.time.toLong().coerceAtLeast(0L)
                val percent = ((currentMediaTimeMs * 100L) / durationMs)
                    .toInt()
                    .coerceIn(0, 99)
                val elapsedMs = (System.currentTimeMillis() - startTimeMs).coerceAtLeast(1L)
                val etaMs = if (percent > 0) {
                    (elapsedMs * (100 - percent)) / percent
                } else {
                    -1L
                }
                onProgress(
                    CompressionProgress(
                        percent = percent,
                        elapsedMs = elapsedMs,
                        etaMs = etaMs
                    )
                )
            }

            // Quote file paths in case cache path contains spaces
            val command = "-y -i \"${tempInput.absolutePath}\" ${preset.ffmpegArgs.trim()} \"${tempOutput.absolutePath}\""

            val session: FFmpegSession = FFmpegKit.execute(command)
            FFmpegKitConfig.enableStatisticsCallback(null)

            val returnCode: ReturnCode? = session.returnCode

            val result: CompressionResult = when {
                ReturnCode.isSuccess(returnCode) -> {
                    if (!tempOutput.exists() || tempOutput.length() == 0L) {
                        CompressionResult.Error("Encoder produced an empty file.")
                    } else {
                        val compressedSize = tempOutput.length()
                        val savedDocUri: Uri? = writeToOutputTree(
                            tempOutput = tempOutput,
                            outputTreeUri = outputTreeUri,
                            originalName = inputDisplayName,
                            preset = preset
                        )
                        if (savedDocUri == null) {
                            CompressionResult.Error("Failed to write output file to selected SAF directory.")
                        } else {
                            val totalElapsedMs = System.currentTimeMillis() - startTimeMs
                            CompressionResult.Success(
                                outputUri = savedDocUri,
                                compressedSizeBytes = compressedSize,
                                elapsedMs = totalElapsedMs
                            )
                        }
                    }
                }

                ReturnCode.isCancel(returnCode) -> CompressionResult.Cancelled

                else -> {
                    val codeValue = returnCode?.value ?: -1
                    val lastLog = session.output?.takeLast(300) ?: "Unknown FFmpeg error"
                    CompressionResult.Error("FFmpeg exited ($codeValue): $lastLog")
                }
            }

            result
        } catch (e: Exception) {
            FFmpegKitConfig.enableStatisticsCallback(null)
            CompressionResult.Error(e.message ?: "Unexpected error during compression")
        } finally {
            FFmpegKitConfig.enableStatisticsCallback(null)
            tempInput.delete()
            tempOutput.delete()
        }
    }

    fun cancelActive() {
        FFmpegKit.cancel()
    }

    private fun copyUriToFile(sourceUri: Uri, targetFile: File) {
        val inputStream = context.contentResolver.openInputStream(sourceUri)
            ?: throw IOException("Cannot open input stream for URI: $sourceUri")
        inputStream.use { input ->
            targetFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun extractDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationStr?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun writeToOutputTree(
        tempOutput: File,
        outputTreeUri: Uri,
        originalName: String,
        preset: CompressionPreset
    ): Uri? {
        val treeDir = DocumentFile.fromTreeUri(context, outputTreeUri) ?: return null
        val baseName = originalName.substringBeforeLast(".")
        val suffix = when (preset) {
            CompressionPreset.PERCEPTUALLY_LOSSLESS_HEVC -> "hevc_crf24"
            CompressionPreset.MAXIMUM_QUALITY_HEVC -> "hevc_crf18"
            CompressionPreset.MAXIMUM_COMPRESSION_AV1 -> "av1_crf30"
            CompressionPreset.FAST_720P_HEVC -> "hevc_720p"
        }
        val targetFileName = "${baseName}_${suffix}.mp4"

        val targetDoc = treeDir.createFile("video/mp4", targetFileName) ?: return null
        val targetUri = targetDoc.uri

        val outputStream = context.contentResolver.openOutputStream(targetUri, "w") ?: return null
        outputStream.use { out ->
            tempOutput.inputStream().use { input ->
                input.copyTo(out)
            }
        }
        return targetUri
    }
}