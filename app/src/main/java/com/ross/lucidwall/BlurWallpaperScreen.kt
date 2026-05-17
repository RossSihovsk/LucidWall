package com.ross.lucidwall

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt

@Composable
fun BlurWallpaperScreen(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val blurRadius by viewModel.blurRadius.collectAsState()
    val configuration by viewModel.configuration.collectAsState()

    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> viewModel.onImagePicked(context, uri) }
    )

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is UiState.Error -> {
                Toast.makeText(context, context.getString(state.messageResId), Toast.LENGTH_SHORT).show()
                viewModel.acknowledgeState()
            }
            is UiState.Success -> {
                Toast.makeText(context, context.getString(state.messageResId), Toast.LENGTH_SHORT).show()
                viewModel.acknowledgeState()
            }
            else -> {}
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212))) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offsetX by remember { mutableFloatStateOf(0f) }
        var offsetY by remember { mutableFloatStateOf(0f) }

// Next edit chunk starts here, so we replace from 'var containerSize by' down to end of controls button select image 
        var containerSize by remember { mutableStateOf(IntSize.Zero) }

        var cardOffsetY by remember { mutableFloatStateOf(0f) }
        var cardHeight by remember { mutableFloatStateOf(0f) }

        // 1. Image Background (Full Screen)
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (uiState is UiState.ImageSelected) {
                val state = uiState as UiState.ImageSelected
                val bmpToDisplay = state.blurredBitmap ?: state.bitmap

                Image(
                    bitmap = bmpToDisplay.asImageBitmap(),
                    contentDescription = stringResource(R.string.image_preview),
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { containerSize = it }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 5f)
                                val maxX = (containerSize.width * (scale - 1)) / 2f
                                val maxY = (containerSize.height * (scale - 1)) / 2f
                                offsetX = (offsetX + pan.x * scale).coerceIn(-maxX, maxX)
                                offsetY = (offsetY + pan.y * scale).coerceIn(-maxY, maxY)
                            }
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        ),
                    contentScale = ContentScale.Crop
                )
            } else if (uiState is UiState.Loading) {
                CircularProgressIndicator(color = Color.White)
            } else {
                Text(stringResource(R.string.your_blurred_wallpaper), style = MaterialTheme.typography.titleLarge, color = Color.White)
            }
        }

        // 2. Controls Overlay (Bottom)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .offset { IntOffset(0, cardOffsetY.roundToInt()) }
                .onSizeChanged { cardHeight = it.height.toFloat() }
                .padding(16.dp)
                // Offset bottom to avoid nav bar issues if not handling edge-to-edge perfectly.
                .padding(bottom = 32.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF1E1E1E).copy(alpha = 0.85f),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val maxDown = maxOf(0f, cardHeight - 80f)
                                cardOffsetY = (cardOffsetY + dragAmount.y).coerceIn(0f, maxDown)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (uiState is UiState.ImageSelected) stringResource(R.string.change_image) else stringResource(R.string.select_image))
                    }

                if (uiState is UiState.ImageSelected) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(stringResource(R.string.blur_intensity), style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Slider(
                        value = blurRadius,
                        onValueChange = { viewModel.onBlurRadiusChanged(it) },
                        valueRange = 0f..25f,
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.LightGray
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.target_screen_title), style = MaterialTheme.typography.titleSmall, color = Color.White)
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = configuration == 0,
                            onClick = { viewModel.onConfigurationChanged(0) },
                            colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = Color.LightGray)
                        )
                        Text(stringResource(R.string.target_home_screen), style = MaterialTheme.typography.bodySmall, color = Color.White)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = configuration == 1,
                            onClick = { viewModel.onConfigurationChanged(1) },
                            colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = Color.LightGray)
                        )
                        Text(stringResource(R.string.target_lock_screen), style = MaterialTheme.typography.bodySmall, color = Color.White)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = configuration == 2,
                            onClick = { viewModel.onConfigurationChanged(2) },
                            colors = RadioButtonDefaults.colors(selectedColor = Color.White, unselectedColor = Color.LightGray)
                        )
                        Text(stringResource(R.string.target_both_screens), style = MaterialTheme.typography.bodySmall, color = Color.White)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { 
                            viewModel.applyWallpaper(
                                context = context, 
                                scale = scale, 
                                offsetX = offsetX, 
                                offsetY = offsetY, 
                                cw = containerSize.width, 
                                ch = containerSize.height
                            ) 
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        Text(stringResource(R.string.apply_wallpaper))
                    }
                }
            }
        }
    }
}}
