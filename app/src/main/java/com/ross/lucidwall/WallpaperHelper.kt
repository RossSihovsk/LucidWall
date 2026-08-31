package com.ross.lucidwall

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import com.google.android.renderscript.Toolkit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WallpaperHelper {

    /**
     * Applies an intense downscaled blur to the original bitmap.
     */
    fun blurBitmap(originalBitmap: Bitmap, radius: Float): Bitmap {
        if (radius <= 0f) return originalBitmap
        
        val scaleDown = 1f + (radius / 8f)
        val w = (originalBitmap.width / scaleDown).toInt().coerceAtLeast(10)
        val h = (originalBitmap.height / scaleDown).toInt().coerceAtLeast(10)
        
        val scaled = if (w < originalBitmap.width) {
            Bitmap.createScaledBitmap(originalBitmap, w, h, true)
        } else {
            originalBitmap
        }
        
        return Toolkit.blur(scaled, radius.toInt().coerceIn(1, 25))
    }

    /**
     * Recreates the exact pan, zoom, and crop applied by the user in the preview.
     */
    fun cropAndTransform(
        bitmap: Bitmap,
        cw: Int,
        ch: Int,
        scale: Float,
        offsetX: Float,
        offsetY: Float
    ): Bitmap {
        if (cw <= 0 || ch <= 0) return bitmap

        val result = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        val bw = bitmap.width
        val bh = bitmap.height
        val cropScale = maxOf(cw.toFloat() / bw, ch.toFloat() / bh)
        val dx = (cw - bw * cropScale) / 2f
        val dy = (ch - bh * cropScale) / 2f

        val matrix = android.graphics.Matrix()
        matrix.postScale(cropScale, cropScale)
        matrix.postTranslate(dx, dy)
        matrix.postScale(scale, scale, cw / 2f, ch / 2f)
        matrix.postTranslate(offsetX, offsetY)

        val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(bitmap, matrix, paint)
        return result
    }

    /**
     * Applies the blurred and clear bitmaps to the appropriate screens.
     */
    suspend fun applyToSystem(
        context: Context,
        finalClear: Bitmap,
        finalBlurred: Bitmap,
        config: Int
    ) = withContext(Dispatchers.IO) {
        val wallpaperManager = WallpaperManager.getInstance(context)
        when (config) {
            0 -> { // Blurred Home, Clear Lock
                wallpaperManager.setBitmap(finalClear, null, false, WallpaperManager.FLAG_LOCK)
                wallpaperManager.setBitmap(finalBlurred, null, false, WallpaperManager.FLAG_SYSTEM)
            }
            1 -> { // Blurred Lock, Clear Home
                wallpaperManager.setBitmap(finalClear, null, false, WallpaperManager.FLAG_SYSTEM)
                wallpaperManager.setBitmap(finalBlurred, null, false, WallpaperManager.FLAG_LOCK)
            }
            2 -> { // Both screens get blurred
                wallpaperManager.setBitmap(finalBlurred, null, false, WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK)
            }
        }
    }
}
