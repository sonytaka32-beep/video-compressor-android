package com.example.videocompressor

enum class CompressionPreset(
    val displayName: String,
    val description: String,
    val ffmpegArgs: String
) {
    // -tag:v hvc1 ensures Apple/QuickTime/Android hardware decoder compatibility for HEVC inside MP4.
    // -movflags +faststart relocates the MP4 moov atom to the front for instant playback/streaming.
    PERCEPTUALLY_LOSSLESS_HEVC(
        displayName = "Perceptually Lossless HEVC (Default)",
        description = "CRF 24, medium preset, copies original audio stream",
        ffmpegArgs = "-c:v libx265 -preset medium -crf 24 -tag:v hvc1 -c:a copy -movflags +faststart"
    ),

    MAXIMUM_QUALITY_HEVC(
        displayName = "Maximum Quality HEVC",
        description = "Near-lossless CRF 18, slow preset, copies original audio stream",
        ffmpegArgs = "-c:v libx265 -preset slow -crf 18 -tag:v hvc1 -c:a copy -movflags +faststart"
    ),

    // -preset 6 balances encoding speed and compression efficiency for SVT-AV1.
    MAXIMUM_COMPRESSION_AV1(
        displayName = "Maximum Compression AV1",
        description = "Smallest file size, CRF 30, SVT-AV1 preset 6 (slower encode)",
        ffmpegArgs = "-c:v libsvtav1 -preset 6 -crf 30 -c:a copy -movflags +faststart"
    ),

    // scale=-2:720 scales height to 720p while keeping aspect ratio and forcing width to an even number (required by YUV420p encoders).
    FAST_720P_HEVC(
        displayName = "Fast 720p HEVC (Sharing)",
        description = "720p downscale, CRF 28, fast preset, 128k AAC audio",
        ffmpegArgs = "-vf scale=-2:720 -c:v libx265 -preset fast -crf 28 -tag:v hvc1 -c:a aac -b:a 128k -movflags +faststart"
    );

    companion object {
        fun fromNameOrDefault(name: String?): CompressionPreset {
            return entries.firstOrNull { it.name == name } ?: PERCEPTUALLY_LOSSLESS_HEVC
        }
    }
}