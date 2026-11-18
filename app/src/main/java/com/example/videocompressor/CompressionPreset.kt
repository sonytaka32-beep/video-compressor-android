package com.example.videocompressor

enum class CompressionPreset(
    val displayName: String,
    val ffmpegArgs: List<String>
) {
    PERCEPTUALLY_LOSSLESS_HEVC(
        displayName = "Vice Lossless HEVC (Default)",
        ffmpegArgs = listOf(
            "-c:v", "libx265",
            "-crf", "23",
            "-preset", "ultrafast",
            "-threads", "6",
            "-x265-params", "frame-threads=4:wpp=1:no-sao=1:bframes=0:ref=1:rc-lookahead=0:early-skip=1:fast-intra=1",
            "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-movflags", "+faststart"
        )
    ),

    MAXIMUM_QUALITY_HEVC(
        displayName = "Leonida Ultra Quality HEVC",
        ffmpegArgs = listOf(
            "-c:v", "libx265",
            "-crf", "20",
            "-preset", "superfast",
            "-threads", "6",
            "-x265-params", "frame-threads=4:wpp=1:no-sao=1:ref=1:rc-lookahead=5",
            "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-movflags", "+faststart"
        )
    ),

    MAXIMUM_COMPRESSION_SMALLEST(
        displayName = "Balanced Compression HEVC (Crisp)",
        ffmpegArgs = listOf(
            "-c:v", "libx265",
            "-crf", "27",
            "-preset", "ultrafast",
            "-threads", "6",
            "-x265-params", "frame-threads=4:wpp=1:no-sao=1:bframes=0:ref=1:me=dia:subme=1:rc-lookahead=0:early-skip=1:fast-intra=1",
            "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-movflags", "+faststart"
        )
    ),

    MAXIMUM_COMPRESSION_60FPS(
        displayName = "Vice 60fps Turbo HEVC (Crisp)",
        ffmpegArgs = listOf(
            "-c:v", "libx265",
            "-crf", "27",
            "-preset", "ultrafast",
            "-threads", "6",
            "-x265-params", "frame-threads=4:wpp=1:no-sao=1:bframes=0:ref=1:me=dia:subme=1:rc-lookahead=0:early-skip=1:fast-intra=1",
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
            "-threads", "6",
            "-x265-params", "frame-threads=4:wpp=1:no-sao=1:bframes=0:ref=1:me=dia:subme=1:rc-lookahead=0:early-skip=1:fast-intra=1",
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