package com.example.videocompressor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.arthenica.ffmpegkit.FFmpegKit
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
            // Stage input and output in local cache because -movflags +faststart requires
            // a seekable filesystem to relocate the MP4 moov header after encoding finishes.
            copyUriToFile(inputUri, tempInput)
            val durationMs = extractDurationMs(tempInput).coerceAtLeast(1L)

            val parsedPresetArgs = preset.ffmpegArgs
                .trim()
                .split(Regex("\\s+"))
                .joinToString(" ")

            // -y overwrites the staged output file if created beforehand.
            val command = "-y -i \"${tempInput.absolutePath}\" $parsedPresetArgs \"${tempOutput.absolutePath}\""

            val session: FFmpegSession = FFmpegKit.execute(command)

            val returnCode = session.getReturnCode()

            when (returnCode) {
                ReturnCode.SUCCESS -> {
                    if (!tempOutput.exists() || tempOutput.length() == 0L) {
                        return@withContext CompressionResult.Error("Encoder produced an empty file.")
                    }
                    val compressedSize = tempOutput.length()
                    val savedDocUri = writeToOutputTree(
                        tempOutput = tempOutput,
                        outputTreeUri = outputTreeUri,
                        originalName = inputDisplayName,
                        preset = preset
                    ) ?: return@withContext CompressionResult.Error(
                        "Failed to write output file to selected SAF directory."
                    )

                    val totalElapsedMs = System.currentTimeMillis() - startTimeMs
                    CompressionResult.Success(
                        outputUri = savedDocUri,
                        compressedSizeBytes = compressedSize,
                        elapsedMs = totalElapsedMs
                    )
                }

                ReturnCode.CANCEL -> CompressionResult.Cancelled

                else -> {
                    val lastLog = session.getOutput()?.takeLast(300) ?: "Unknown FFmpeg error"
                    CompressionResult.Error("FFmpeg exited ($returnCode): $lastLog")
                }
            }
        } catch (e: Exception) {
            CompressionResult.Error(e.message ?: "Unexpected error during compression")
        } finally {
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

        // If a file with the same name exists in the destination tree, create a new unique document
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