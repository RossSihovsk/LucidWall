package com.ross.lucidwall

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Accent colors for blur-mode badges
private val HomeColor  = Color(0xFF4FC3F7) // light blue
private val LockColor  = Color(0xFFCE93D8) // lavender
private val BothColor  = Color(0xFF80CBC4) // teal

/**
 * A horizontal strip showing the last applied wallpapers.
 *
 * @param entries      List of history items (most-recent first).
 * @param selectedId   ID of the currently loaded history entry (highlighted).
 * @param onSelect     Called when user taps a history card.
 */
@Composable
fun WallpaperHistorySection(
    entries: List<WallpaperHistoryEntry>,
    selectedId: String?,
    onSelect: (WallpaperHistoryEntry) -> Unit
) {
    if (entries.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                HistoryCard(
                    entry = entry,
                    isSelected = entry.id == selectedId,
                    onClick = { onSelect(entry) }
                )
            }
        }
    }
}

@Composable
private fun HistoryCard(
    entry: WallpaperHistoryEntry,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.Transparent,
        animationSpec = tween(durationMillis = 250),
        label = "borderColor"
    )

    val badgeColor = when (entry.configuration) {
        0 -> HomeColor
        1 -> LockColor
        else -> BothColor
    }

    val badgeText = when (entry.configuration) {
        0 -> stringResource(R.string.history_home)
        1 -> stringResource(R.string.history_lock)
        else -> stringResource(R.string.history_both)
    }

    val badgeIcon = when (entry.configuration) {
        0 -> R.drawable.ic_history_home
        1 -> R.drawable.ic_history_lock
        else -> R.drawable.ic_history_both
    }

    Box(
        modifier = Modifier
            .width(100.dp)
            .height(162.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
    ) {
        // Thumbnail
        val bitmap = remember(entry.thumbnailBase64) {
            runCatching {
                val bytes = Base64.decode(entry.thumbnailBase64, Base64.NO_WRAP)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }

        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(Color(0xFF2A2A2A)))
        }

        // Gradient scrim at bottom for badge readability
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
        )

        // Blur-mode badge
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp),
            color = badgeColor.copy(alpha = 0.92f),
            shape = RoundedCornerShape(6.dp),
            tonalElevation = 0.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Icon(
                    painter = painterResource(id = badgeIcon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
