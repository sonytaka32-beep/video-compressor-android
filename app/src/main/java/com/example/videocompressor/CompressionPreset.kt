package com.example.videocompressor

enum class CompressionPreset(
    val displayName: String,
    val ffmpegArgs: List<String>
) {
    PERCEPTUALLY_LOSSLESS_HEVC(
        displayName = "Perceptually Lossless H.264 (Default)",
        ffmpegArgs = listOf(
            "-c:v", "libx264",
            "-crf", "23",
            "-preset", "superfast",
            "-threads", "8",
            "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-movflags", "+faststart"
        )
    ),

    MAXIMUM_QUALITY_HEVC(
        displayName = "Maximum Quality H.264",
        ffmpegArgs = listOf(
            "-c:v", "libx264",
            "-crf", "20",
            "-preset", "superfast",
            "-threads", "8",
            "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-movflags", "+faststart"
        )
    ),

    MAXIMUM_COMPRESSION_SMALLEST(
        displayName = "Maximum Compression (Smallest Size)",
        ffmpegArgs = listOf(
            "-c:v", "libx264",
            "-crf", "30",
            "-preset", "superfast",
            "-maxrate", "1500k",
            "-bufsize", "3000k",
            "-threads", "8",
            "-pix_fmt", "yuv420p",
            "-c:a", "aac",
            "-b:a", "64k",
            "-movflags", "+faststart"
        )
    ),

    MAXIMUM_COMPRESSION_60FPS(
        displayName = "Maximum Compression 60fps (Smallest Size)",
        ffmpegArgs = listOf(
            "-c:v", "libx264",
            "-crf", "29",
            "-preset", "superfast",
            "-maxrate", "2800k",
            "-bufsize", "5600k",
            "-threads", "8",
            "-r", "60",
            "-pix_fmt", "yuv420p",
            "-c:a", "aac",
            "-b:a", "64k",
            "-movflags", "+faststart"
        )
    ),

    FAST_720P_HEVC(
        displayName = "Fast 720p H.264 (Sharing)",
        ffmpegArgs = listOf(
            "-c:v", "libx264",
            "-crf", "28",
            "-preset", "superfast",
            "-maxrate", "1800k",
            "-bufsize", "3600k",
            "-threads", "8",
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