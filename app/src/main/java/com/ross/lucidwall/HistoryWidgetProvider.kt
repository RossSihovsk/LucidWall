package com.ross.lucidwall

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Base64
import android.view.View
import android.widget.RemoteViews

class HistoryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = android.content.ComponentName(context, HistoryWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (id in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, id)
            }
        }

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            
            val repository = WallpaperRepository.getInstance(context)
            val entries = repository.loadEntries()

            val itemViews = listOf(
                R.id.widget_item_1,
                R.id.widget_item_2,
                R.id.widget_item_3,
                R.id.widget_item_4,
                R.id.widget_item_5
            )

            val imageViews = listOf(
                R.id.widget_image_1,
                R.id.widget_image_2,
                R.id.widget_image_3,
                R.id.widget_image_4,
                R.id.widget_image_5
            )
            
            val iconViews = listOf(
                R.id.widget_icon_1,
                R.id.widget_icon_2,
                R.id.widget_icon_3,
                R.id.widget_icon_4,
                R.id.widget_icon_5
            )

            for (i in itemViews.indices) {
                val itemId = itemViews[i]
                val imageId = imageViews[i]
                val iconId = iconViews[i]
                
                if (i < entries.size) {
                    val entry = entries[i]
                    views.setViewVisibility(itemId, View.VISIBLE)
                    
                    // Decode thumbnail
                    try {
                        val bytes = Base64.decode(entry.thumbnailBase64, Base64.NO_WRAP)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        views.setImageViewBitmap(imageId, bitmap)
                    } catch (e: Exception) {
                        // ignore bad thumbnails
                    }
                    
                    // Set icon based on configuration
                    val srcRes = when (entry.configuration) {
                        0 -> R.drawable.ic_history_home
                        1 -> R.drawable.ic_history_lock
                        else -> R.drawable.ic_history_both
                    }
                    val bgRes = when (entry.configuration) {
                        0 -> R.drawable.badge_bg_home
                        1 -> R.drawable.badge_bg_lock
                        else -> R.drawable.badge_bg_both
                    }
                    views.setImageViewResource(iconId, srcRes)
                    views.setInt(iconId, "setBackgroundResource", bgRes)

                    // Set pending intent for click on the whole frame
                    val intent = Intent(context, WidgetActionReceiver::class.java).apply {
                        action = WidgetActionReceiver.ACTION_APPLY_WALLPAPER
                        putExtra(WidgetActionReceiver.EXTRA_ENTRY_ID, entry.id)
                    }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        entry.id.hashCode(), // unique request code
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(itemId, pendingIntent)
                } else {
                    views.setViewVisibility(itemId, View.GONE)
                }
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
