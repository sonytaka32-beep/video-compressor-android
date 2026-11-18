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
            "-threads", "6",
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
            "-threads", "6",
            "-x265-params", "frame-threads=4:wpp=1:no-sao=1",
            "-pix_fmt", "yuv420p",
            "-c:a", "copy",
            "-movflags", "+faststart"
        )
    ),

    // Gunakan preset ini untuk video panjang/AV1 seperti MADU.mp4 (2.5 jam)
    MAXIMUM_COMPRESSION_SMALLEST(
        displayName = "Maximum Compression (Smallest Size)",
        ffmpegArgs = listOf(
            "-c:v", "libx264",
            "-crf", "34",
            "-preset", "superfast",
            "-maxrate", "900k",
            "-bufsize", "1800k",
            "-threads", "6",
            "-pix_fmt", "yuv420p",
            "-c:a", "aac",
            "-b:a", "64k",
            "-movflags", "+faststart"
        )
    ),

    // Khusus video 60fps: Cepat (~1.5 - 2 menit), ukuran hemat 80%+, anti-minus di video horizontal
    MAXIMUM_COMPRESSION_60FPS(
        displayName = "Maximum Compression 60fps (Smallest Size)",
        ffmpegArgs = listOf(
            "-c:v", "libx264",
            "-crf", "33",
            "-preset", "superfast",
            "-maxrate", "1400k",
            "-bufsize", "2800k",
            "-threads", "6",
            "-r", "60",
            "-pix_fmt", "yuv420p",
            "-c:a", "aac",
            "-b:a", "64k",
            "-movflags", "+faststart"
        )
    ),

    FAST_720P_HEVC(
        displayName = "Fast 720p HEVC (Sharing)",
        ffmpegArgs = listOf(
            "-c:v", "libx264",
            "-crf", "30",
            "-preset", "superfast",
            "-maxrate", "1200k",
            "-bufsize", "2400k",
            "-threads", "6",
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