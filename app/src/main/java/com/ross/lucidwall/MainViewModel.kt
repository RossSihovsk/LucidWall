package com.ross.lucidwall

import android.app.Application
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.renderscript.Toolkit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class ImageSelected(val uri: Uri, val bitmap: Bitmap, val blurredBitmap: Bitmap?) : UiState()
    data class Error(val messageResId: Int) : UiState()
    data class Success(val messageResId: Int) : UiState()
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = WallpaperRepository.getInstance(application)

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _blurRadius = MutableStateFlow(0f)
    val blurRadius: StateFlow<Float> = _blurRadius.asStateFlow()

    private val _configuration = MutableStateFlow(0) // 0: Home Only, 1: Lock Only, 2: Both
    val configuration: StateFlow<Int> = _configuration.asStateFlow()

    private val _history = MutableStateFlow<List<WallpaperHistoryEntry>>(emptyList())
    val history: StateFlow<List<WallpaperHistoryEntry>> = _history.asStateFlow()

    private var lastSelectedImage: UiState.ImageSelected? = null

    init {
        _history.value = repository.loadEntries()
    }

    fun onConfigurationChanged(conf: Int) {
        _configuration.value = conf
    }

    fun onImagePicked(context: Context, uri: Uri?) {
        if (uri == null) return
        // Persist access permission so the URI remains valid after reboot
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) { /* permission not grantable for this URI type */ }

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(inputStream)
                }
                if (bitmap != null) {
                    val newState = UiState.ImageSelected(uri, bitmap, null)
                    lastSelectedImage = newState
                    _uiState.value = newState
                    updateBlur(bitmap, _blurRadius.value)
                } else {
                    _uiState.value = UiState.Error(R.string.error_loading_image)
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(R.string.error_loading_image)
            }
        }
    }

    fun onBlurRadiusChanged(radius: Float) {
        _blurRadius.value = radius
        lastSelectedImage?.bitmap?.let { bitmap ->
            updateBlur(bitmap, radius)
        }
    }

    private fun updateBlur(originalBitmap: Bitmap, radius: Float) {
        viewModelScope.launch {
            try {
                val blurredBitmap = if (radius > 0f) {
                    withContext(Dispatchers.Default) {
                        // Downscale before blurring to intensify the effect and speed up processing
                        // Reduced scaling factor as it was too intense
                        val scaleDown = 1f + (radius / 8f)
                        val w = (originalBitmap.width / scaleDown).toInt().coerceAtLeast(10)
                        val h = (originalBitmap.height / scaleDown).toInt().coerceAtLeast(10)
                        
                        val scaled = if (w < originalBitmap.width) {
                            Bitmap.createScaledBitmap(originalBitmap, w, h, true)
                        } else {
                            originalBitmap
                        }
                        
                        Toolkit.blur(scaled, radius.toInt().coerceIn(1, 25))
                    }
                } else {
                    originalBitmap
                }

                if (lastSelectedImage != null) {
                    lastSelectedImage = lastSelectedImage!!.copy(blurredBitmap = blurredBitmap)
                    if (_uiState.value is UiState.ImageSelected) {
                        _uiState.value = lastSelectedImage!!
                    }
                }
            } catch (e: Exception) {
                // Ignore minor blur calculation errors during slider drag
            }
        }
    }

    fun applyWallpaper(context: Context, scale: Float, offsetX: Float, offsetY: Float, cw: Int, ch: Int) {
        val selected = lastSelectedImage ?: return
        val originalBitmap = selected.bitmap
        val blurredBitmap = selected.blurredBitmap ?: originalBitmap
        val config = _configuration.value

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val thumbnailBase64 = withContext(Dispatchers.Default) {
                    buildThumbnailBase64(selected.blurredBitmap ?: selected.bitmap)
                }

                withContext(Dispatchers.IO) {
                    val finalClear = cropAndTransform(originalBitmap, cw, ch, scale, offsetX, offsetY)
                    val finalBlurred = cropAndTransform(blurredBitmap, cw, ch, scale, offsetX, offsetY)

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

                // Persist history entry
                val entry = WallpaperHistoryEntry(
                    id = UUID.randomUUID().toString(),
                    imageUriString = selected.uri.toString(),
                    blurRadius = _blurRadius.value,
                    configuration = config,
                    appliedAt = System.currentTimeMillis(),
                    thumbnailBase64 = thumbnailBase64
                )
                repository.saveEntry(entry)
                _history.value = repository.loadEntries()

                _uiState.value = UiState.Success(R.string.success_applied)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(R.string.failed_to_apply)
            }
        }
    }

    /**
     * Restores a history entry into the editor so the user can tweak and re-apply.
     */
    fun loadHistoryEntry(entry: WallpaperHistoryEntry, context: Context) {
        val uri = Uri.parse(entry.imageUriString)
        // Restore configuration and blur state immediately (fast UI feedback)
        _configuration.value = entry.configuration
        _blurRadius.value = entry.blurRadius

        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    BitmapFactory.decodeStream(inputStream)
                }
                if (bitmap != null) {
                    val newState = UiState.ImageSelected(uri, bitmap, null)
                    lastSelectedImage = newState
                    _uiState.value = newState
                    updateBlur(bitmap, entry.blurRadius)
                } else {
                    _uiState.value = UiState.Error(R.string.error_loading_image)
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(R.string.error_loading_image)
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Creates a compact Base64-encoded JPEG thumbnail (~120×213 px) for history display.
     */
    private fun buildThumbnailBase64(source: Bitmap): String {
        val thumbW = 120
        val thumbH = 213
        val scaled = Bitmap.createScaledBitmap(source, thumbW, thumbH, true)
        val stream = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 60, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }

    private fun cropAndTransform(
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

    fun acknowledgeState() {
        if (lastSelectedImage != null) {
            _uiState.value = lastSelectedImage!!
        } else {
            _uiState.value = UiState.Idle
        }
    }
}
