package com.example.videocompressor

enum class CompressionPreset(
    val displayName: String,
    val description: String,
    val ffmpegArgs: String
) {
    // Menggunakan -preset veryfast agar 4-5x lebih cepat di CPU Android dibanding medium tanpa mengorbankan kualitas
    PERCEPTUALLY_LOSSLESS_HEVC(
        displayName = "Perceptually Lossless HEVC (Default)",
        description = "CRF 24, veryfast preset, copies original audio",
        ffmpegArgs = "-c:v libx265 -preset veryfast -crf 24 -tag:v hvc1 -c:a copy -movflags +faststart"
    ),

    // Menggunakan -preset fast (bukan slow) agar tidak membebani suhu CPU HP secara berlebihan
    MAXIMUM_QUALITY_HEVC(
        displayName = "Maximum Quality HEVC",
        description = "Near-lossless CRF 18, fast preset, copies original audio",
        ffmpegArgs = "-c:v libx265 -preset fast -crf 18 -tag:v hvc1 -c:a copy -movflags +faststart"
    ),

    // Mengganti libsvtav1 (tidak ada di ffmpeg-kit Android) dengan Extreme HEVC CRF 30 + 96k audio
    MAXIMUM_COMPRESSION_HEVC(
        displayName = "Maximum Compression (Smallest Size)",
        description = "Smallest file size, CRF 30, veryfast preset, 96k AAC audio",
        ffmpegArgs = "-c:v libx265 -preset veryfast -crf 30 -tag:v hvc1 -c:a aac -b:a 96k -movflags +faststart"
    ),

    // Menggunakan -preset superfast dan membatasi 30fps (-r 30) agar kompresi selesai dalam hitungan detik
    FAST_720P_HEVC(
        displayName = "Fast 720p HEVC (Sharing)",
        description = "720p downscale, 30fps, CRF 28, superfast preset, 128k AAC",
        ffmpegArgs = "-vf scale=-2:720 -r 30 -c:v libx265 -preset superfast -crf 28 -tag:v hvc1 -c:a aac -b:a 128k -movflags +faststart"
    );

    companion object {
        fun fromNameOrDefault(name: String?): CompressionPreset {
            return entries.firstOrNull { it.name == name } ?: PERCEPTUALLY_LOSSLESS_HEVC
        }
    }
}