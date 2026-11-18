package com.example.videocompressor

enum class CompressionPreset(
    val displayName: String,
    val ffmpegArgs: List<String>
) {
    PERCEPTUALLY_LOSSLESS_HEVC(
        displayName = "Perceptually Lossless HEVC (Default)",
        ffmpegArgs = listOf(
            "-c:v", "libx265",
            "-crf", "23",
            "-preset", "ultrafast",
            "-threads", "0",
            "-x265-params", "pools=+:frame-threads=8:wpp=1:pmode=1:no-sao=1:bframes=0:ref=1:rc-lookahead=0",
            "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-movflags", "+faststart"
        )
    ),

    MAXIMUM_QUALITY_HEVC(
        displayName = "Maximum Quality HEVC",
        ffmpegArgs = listOf(
            "-c:v", "libx265",
            "-crf", "20",
            "-preset", "superfast",
            "-threads", "0",
            "-x265-params", "pools=+:frame-threads=8:wpp=1:pmode=1:no-sao=1:ref=1:rc-lookahead=5",
            "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-movflags", "+faststart"
        )
    ),

    MAXIMUM_COMPRESSION_SMALLEST(
        displayName = "Maximum Compression (Smallest Size)",
        ffmpegArgs = listOf(
            "-c:v", "libx265",
            "-crf", "30",
            "-preset", "ultrafast",
            "-threads", "0",
            "-x265-params", "pools=+:frame-threads=8:wpp=1:pmode=1:no-sao=1:no-deblock=1:bframes=0:ref=1:me=dia:subme=0:rc-lookahead=0:early-skip=1:fast-intra=1",
            "-r", "30",
            "-pix_fmt", "yuv420p",
            "-c:a", "aac",
            "-b:a", "96k",
            "-movflags", "+faststart"
        )
    ),

    MAXIMUM_COMPRESSION_60FPS(
        displayName = "Maximum Compression 60fps (Smallest Size)",
        ffmpegArgs = listOf(
            "-c:v", "libx265",
            "-crf", "30",
            "-preset", "ultrafast",
            "-threads", "0",
            "-x265-params", "pools=+:frame-threads=8:wpp=1:pmode=1:no-sao=1:no-deblock=1:bframes=0:ref=1:me=dia:subme=0:rc-lookahead=0:early-skip=1:fast-intra=1",
            "-r", "60",
            "-pix_fmt", "yuv420p",
            "-c:a", "aac",
            "-b:a", "96k",
            "-movflags", "+faststart"
        )
    ),

    FAST_720P_HEVC(
        displayName = "Fast 720p HEVC (Sharing)",
        ffmpegArgs = listOf(
            "-c:v", "libx265",
            "-crf", "26",
            "-preset", "ultrafast",
            "-threads", "0",
            "-x265-params", "pools=+:frame-threads=8:wpp=1:pmode=1:no-sao=1:no-deblock=1:bframes=0:ref=1:me=dia:subme=0:rc-lookahead=0",
            "-vf", "scale=-2:720",
            "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-movflags", "+faststart"
        )
    );

    override fun toString(): String = displayName

    companion object {
        fun fromNameOrDefault(name: String?): CompressionPreset {
            return entries.firstOrNull { it.name == name } ?: PERCEPTUALLY_LOSSLESS_HEVC
        }
    }
}