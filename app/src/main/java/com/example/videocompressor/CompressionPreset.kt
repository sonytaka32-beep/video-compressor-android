package com.example.videocompressor

enum class CompressionPreset(
    val displayName: String,
    val ffmpegArgs: List<String>
) {
    PERCEPTUALLY_LOSSLESS_HEVC(
        displayName = "Perceptually Lossless HEVC (Default)",
        ffmpegArgs = listOf(
            "-c:v", "libx265",
            "-crf", "24",
            "-preset", "ultrafast",
            "-threads", "0",
            "-x265-params", "frame-threads=4:wpp=1:no-sao=1",
            "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-movflags", "+faststart"
        )
    ),

    MAXIMUM_QUALITY_HEVC(
        displayName = "Maximum Quality HEVC",
        ffmpegArgs = listOf(
            "-c:v", "libx265",
            "-crf", "21",
            "-preset", "superfast",
            "-threads", "0",
            "-x265-params", "frame-threads=4:wpp=1:no-sao=1",
            "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-movflags", "+faststart"
        )
    ),

    MAXIMUM_COMPRESSION_SMALLEST(
        displayName = "Maximum Compression (Smallest Size)",
        ffmpegArgs = listOf(
            "-c:v", "libx264",
            "-crf", "35",
            "-preset", "ultrafast",
            "-x264-params", "sliced-threads=1:slices=8:cabac=1:ref=1:subme=0:me=dia:bframes=0:trellis=0:rc-lookahead=0:no-deblock=1:weightp=0:8x8dct=0:aq-mode=0",
            "-maxrate", "700k",
            "-bufsize", "1400k",
            "-r", "24",
            "-threads", "8",
            "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-movflags", "+faststart"
        )
    ),

    MAXIMUM_COMPRESSION_60FPS(
        displayName = "Maximum Compression 60fps (Smallest Size)",
        ffmpegArgs = listOf(
            "-c:v", "libx264",
            "-crf", "33",
            "-preset", "ultrafast",
            "-x264-params", "sliced-threads=1:slices=8:cabac=1:ref=1:subme=0:me=dia:bframes=0:trellis=0:rc-lookahead=0:no-deblock=1:weightp=0:8x8dct=0:aq-mode=0",
            "-maxrate", "1400k",
            "-bufsize", "2800k",
            "-r", "60",
            "-threads", "8",
            "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-movflags", "+faststart"
        )
    ),

    FAST_720P_HEVC(
        displayName = "Fast 720p HEVC (Sharing)",
        ffmpegArgs = listOf(
            "-c:v", "libx264",
            "-crf", "30",
            "-preset", "ultrafast",
            "-x264-params", "sliced-threads=1:slices=8:cabac=1:ref=1:subme=0:me=dia:bframes=0:trellis=0:rc-lookahead=0:weightp=0:8x8dct=0:aq-mode=0",
            "-maxrate", "1200k",
            "-bufsize", "2400k",
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
            return entries.firstOrNull { it.name == name } ?: PERCEPTUALLY_LOSSLESS_HEVC
        }
    }
}