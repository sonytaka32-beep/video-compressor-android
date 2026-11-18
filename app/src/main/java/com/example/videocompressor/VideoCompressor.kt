package com.example.videocompressor

import android.content.Context
import android.media.MediaCodecList
import android.media.MediaFormat
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

    private val hardwareEncoderAvailable: Boolean by lazy {
        checkHevcMediaCodecSupport()
    }

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

            // Build FFmpeg command with hardware acceleration if available
            val ffmpegArgs = buildFfmpegArgs(preset, hardwareEncoderAvailable)
            // WAJIB SEBELUM "-i": Buffer antrean + multi-thread decoder agar AV1 dan H.264 berjalan serentak
            // Input decoder acceleration: -threads 8 untuk percepatan pembacaan file AV1/H.264
            val command = "-y -thread_queue_size 1024 -threads 8 -flags2 +fast -skip_loop_filter all -i \"${tempInput.absolutePath}\" $ffmpegArgs \"${tempOutput.absolutePath}\""

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
                    // Fallback to software encoder if hardware fails
                    if (hardwareEncoderUsed(preset) && codeValue != 0) {
                        FFmpegKitConfig.enableStatisticsCallback(null)
                        return@withContext trySoftwareFallback(tempInput, tempOutput, outputTreeUri, inputDisplayName, preset, durationMs, startTimeMs, onProgress)
                    }
                    CompressionResult.Error("FFmpeg exited ($codeValue): $lastLog")
                }
            }

            result
        } catch (t: Throwable) {
            try {
                FFmpegKitConfig.enableStatisticsCallback(null)
            } catch (_: Throwable) {
            }
            CompressionResult.Error(t.message ?: "Unexpected native error: ${t.javaClass.simpleName}")
        } finally {
            try {
                FFmpegKitConfig.enableStatisticsCallback(null)
            } catch (_: Throwable) {
            }
            tempInput.delete()
            tempOutput.delete()
        }
    }

    private fun buildFfmpegArgs(preset: CompressionPreset, useHardware: Boolean): String {
        val args = preset.ffmpegArgs
        if (useHardware && hardwareEncoderAvailable && shouldUseHardwareEncoder(preset)) {
            // Replace libx265 with hevc_mediacodec for hardware acceleration
            return buildHardwareArgs(preset)
        }
        return args.joinToString(" ")
    }

    private fun shouldUseHardwareEncoder(preset: CompressionPreset): Boolean {
        // Hardware encoder (hevc_mediacodec) is for HEVC presets, not libx264 presets
        return preset == CompressionPreset.PERCEPTUALLY_LOSSLESS_HEVC ||
               preset == CompressionPreset.MAXIMUM_QUALITY_HEVC
    }

    private fun hardwareEncoderUsed(preset: CompressionPreset): Boolean {
        return hardwareEncoderAvailable && shouldUseHardwareEncoder(preset)
    }

    private fun buildHardwareArgs(preset: CompressionPreset): String {
        // Hardware encoder uses bitrate-based encoding, not CRF
        // Base config for hevc_mediacodec
        return when (preset) {
            CompressionPreset.PERCEPTUALLY_LOSSLESS_HEVC -> {
                listOf(
                    "-hwaccel", "auto",
                    "-c:v", "hevc_mediacodec",
                    "-b:v", "1800k",
                    "-maxrate", "2200k",
                    "-bufsize", "3600k",
                    "-r", "30",
                    "-pix_fmt", "yuv420p",
                    "-c:a", "copy",
                    "-movflags", "+faststart"
                ).joinToString(" ")
            }
            CompressionPreset.MAXIMUM_QUALITY_HEVC -> {
                listOf(
                    "-hwaccel", "auto",
                    "-c:v", "hevc_mediacodec",
                    "-b:v", "2500k",
                    "-maxrate", "3000k",
                    "-bufsize", "5000k",
                    "-r", "30",
                    "-pix_fmt", "yuv420p",
                    "-c:a", "copy",
                    "-movflags", "+faststart"
                ).joinToString(" ")
            }
            else -> preset.ffmpegArgs.joinToString(" ")
        }
    }

    private suspend fun trySoftwareFallback(
        tempInput: File,
        tempOutput: File,
        outputTreeUri: Uri,
        inputDisplayName: String,
        preset: CompressionPreset,
        durationMs: Long,
        startTimeMs: Long,
        onProgress: (CompressionProgress) -> Unit
    ): CompressionResult {
        // Re-enable stats callback for fallback run
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

        val command = "-y -threads 0 -i \"${tempInput.absolutePath}\" ${preset.ffmpegArgs.joinToString(" ")} \"${tempOutput.absolutePath}\""
        val session: FFmpegSession = FFmpegKit.execute(command)
        FFmpegKitConfig.enableStatisticsCallback(null)

        val returnCode: ReturnCode? = session.returnCode

        return when {
            ReturnCode.isSuccess(returnCode) -> {
                if (!tempOutput.exists() || tempOutput.length() == 0L) {
                    CompressionResult.Error("Software fallback: Encoder produced an empty file.")
                } else {
                    val compressedSize = tempOutput.length()
                    val savedDocUri: Uri? = writeToOutputTree(
                        tempOutput = tempOutput,
                        outputTreeUri = outputTreeUri,
                        originalName = inputDisplayName,
                        preset = preset
                    )
                    if (savedDocUri == null) {
                        CompressionResult.Error("Software fallback: Failed to write output file to selected SAF directory.")
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
                CompressionResult.Error("Software fallback failed ($codeValue): $lastLog")
            }
        }
    }

    fun cancelActive() {
        FFmpegKit.cancel()
    }

    private fun checkHevcMediaCodecSupport(): Boolean {
        try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val codecInfos = codecList.codecInfos
            for (info in codecInfos) {
                if (info.isEncoder) {
                    val caps = info.getCapabilitiesForType("video/hevc")
                    if (caps != null) {
                        // Check if hardware encoder is available
                        return true
                    }
                }
            }
        } catch (_: Exception) {
            // Ignore and return false
        }
        return false
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
            CompressionPreset.PERCEPTUALLY_LOSSLESS_HEVC -> "libx264_crf23_superfast"
            CompressionPreset.MAXIMUM_QUALITY_HEVC -> "libx264_crf20_superfast"
            CompressionPreset.MAXIMUM_COMPRESSION_SMALLEST -> "libx264_crf30_superfast_1500k_aac64k"
            CompressionPreset.MAXIMUM_COMPRESSION_60FPS -> "libx264_crf24_superfast_2800k_60fps_aac64k"
            CompressionPreset.FAST_720P_HEVC -> "libx264_crf26_superfast_1800k_720p"
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