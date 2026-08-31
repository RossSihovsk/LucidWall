package com.ross.lucidwall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_APPLY_WALLPAPER) {
            val entryId = intent.getStringExtra(EXTRA_ENTRY_ID) ?: return
            
            // Inform the user we started working on it
            Toast.makeText(context, R.string.applying_wallpaper_bg, Toast.LENGTH_SHORT).show()
            
            val pendingResult = goAsync()
            
            CoroutineScope(Dispatchers.Default).launch {
                try {
                    applyWallpaperInBackground(context, entryId)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.success_applied, Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, R.string.failed_to_apply, Toast.LENGTH_SHORT).show()
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private suspend fun applyWallpaperInBackground(context: Context, entryId: String) {
        val repository = WallpaperRepository.getInstance(context)
        val entries = repository.loadEntries()
        val entry = entries.find { it.id == entryId } ?: throw IllegalArgumentException("Entry not found")

        val uri = Uri.parse(entry.imageUriString)
        
        val originalBitmap = withContext(Dispatchers.IO) {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Cannot open URI")
            BitmapFactory.decodeStream(inputStream)
                ?: throw IllegalArgumentException("Cannot decode bitmap")
        }

        // Apply blur
        val blurredBitmap = WallpaperHelper.blurBitmap(originalBitmap, entry.blurRadius)

        // Crop and transform both clear and blurred
        val finalClear = WallpaperHelper.cropAndTransform(
            originalBitmap, entry.cw, entry.ch, entry.scale, entry.offsetX, entry.offsetY
        )
        val finalBlurred = WallpaperHelper.cropAndTransform(
            blurredBitmap, entry.cw, entry.ch, entry.scale, entry.offsetX, entry.offsetY
        )

        // Apply to system
        WallpaperHelper.applyToSystem(context, finalClear, finalBlurred, entry.configuration)
        
        // Update timestamp and move to front
        val newEntry = entry.copy(appliedAt = System.currentTimeMillis())
        repository.saveEntry(newEntry)
    }

    companion object {
        const val ACTION_APPLY_WALLPAPER = "com.ross.lucidwall.ACTION_APPLY_WALLPAPER"
        const val EXTRA_ENTRY_ID = "extra_entry_id"
    }
}
