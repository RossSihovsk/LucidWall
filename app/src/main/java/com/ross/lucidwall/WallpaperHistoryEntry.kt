package com.ross.lucidwall

/**
 * Represents a single applied-wallpaper history record.
 *
 * @param id           Unique UUID used as a stable list key.
 * @param imageUriString Content URI string of the original image (persistable permission taken on pick).
 * @param blurRadius   Blur radius at the time of apply (0–25).
 * @param configuration Target screen config: 0 = Home blurred, 1 = Lock blurred, 2 = Both blurred.
 * @param appliedAt    Epoch millis when the wallpaper was applied.
 * @param thumbnailBase64 Small (~120×213 px) JPEG thumbnail encoded as Base64 for fast display.
 */
data class WallpaperHistoryEntry(
    val id: String,
    val imageUriString: String,
    val blurRadius: Float,
    val configuration: Int,
    val appliedAt: Long,
    val thumbnailBase64: String,
    // Layout metrics for reproducing the exact crop/pan/zoom in the background
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val cw: Int = 1080,
    val ch: Int = 1920
)
