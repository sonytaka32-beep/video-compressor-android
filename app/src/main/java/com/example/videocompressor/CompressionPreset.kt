package com.example.videocompressor

enum class CompressionPreset(
    val displayName: String,
    val ffmpegArgs: List<String>
) {
    PERCEPTUALLY_LOSSLESS_HEVC(
        displayName = "Sonybae Lossless HEVC (Default)",
        ffmpegArgs = listOf(
            "-c:v", "libx265",
            "-crf", "24",
            "-preset", "ultrafast",
            "-threads", "8",
            "-x265-params", "frame-threads=4:wpp=1:no-sao=1",
            "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-movflags", "+faststart"
        )
    ),

    MAXIMUM_QUALITY_HEVC(
        displayName = "Maximum Quality HEVC (CRF 21)",
        ffmpegArgs = listOf(
            "-c:v", "libx265",
            "-crf", "21",
            "-preset", "ultrafast",
            "-threads", "8",
            "-x265-params", "frame-threads=4:wpp=1:no-sao=1",
            "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-movflags", "+faststart"
        )
    ),

    MAXIMUM_COMPRESSION_SMALLEST(
        displayName = "Maximum Compression HEVC (Smallest Size)",
        ffmpegArgs = listOf(
            "-c:v", "libx265",
            "-crf", "28",
            "-preset", "ultrafast",
            "-threads", "8",
            "-x265-params", "frame-threads=4:wpp=1:no-sao=1",
            "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-movflags", "+faststart"
        )
    ),

    MAXIMUM_COMPRESSION_60FPS(
        displayName = "Maximum Compression 60fps HEVC",
        ffmpegArgs = listOf(
            "-c:v", "libx265",
            "-crf", "26",
            "-preset", "ultrafast",
            "-threads", "8",
            "-x265-params", "frame-threads=4:wpp=1:no-sao=1",
            "-r", "60",
            "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-movflags", "+faststart"
        )
    ),

    FAST_720P_HEVC(
        displayName = "Fast 720p HEVC (Sharing)",
        ffmpegArgs = listOf(
            "-c:v", "libx265",
            "-crf", "25",
            "-preset", "ultrafast",
            "-threads", "8",
            "-x265-params", "frame-threads=4:wpp=1:no-sao=1",
            "-vf", "scale=-2:720",
            "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-movflags", "+faststart"
        )
    );

    override fun toString(): String = displayName

    companion object {
        fun fromNameOrDefault(name: String?): CompressionPreset {
            if (name.isNullOrBlank()) return PERCEPTUALLY_LOSSLESS_HEVC
            return entries.firstOrNull {
                it.name.equals(name, ignoreCase = true) ||
                    it.displayName.equals(name, ignoreCase = true)
            } ?: PERCEPTUALLY_LOSSLESS_HEVC
        }
    }
}