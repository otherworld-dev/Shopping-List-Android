package dev.otherworld.shoppinglist.ui.items

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import dev.otherworld.shoppinglist.R
import dev.otherworld.shoppinglist.data.photo.ItemPhotoUrls

/**
 * An item's photo, cropped square with rounded corners. One that fails to load (a stale key,
 * a locked link) leaves no gap: it hides until the URL changes, like the web app's.
 */
@Composable
internal fun PhotoThumb(url: String, size: Dp, onClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    var failed by remember(url) { mutableStateOf(false) }
    if (failed) return
    AsyncImage(
        model = url,
        contentDescription = stringResource(R.string.cd_item_photo),
        contentScale = ContentScale.Crop,
        onState = { if (it is AsyncImagePainter.State.Error) failed = true },
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    )
}

/** The full photo on a dark screen; the thumbnail stands in while it loads. A tap or Back closes it. */
@Composable
internal fun PhotoViewer(urls: ItemPhotoUrls, onDismiss: () -> Unit) {
    var failed by remember(urls) { mutableStateOf(false) }
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            if (failed) {
                Text(stringResource(R.string.photo_load_failed), color = Color.White)
            } else {
                AsyncImage(
                    model = remember(urls) {
                        ImageRequest.Builder(context)
                            .data(urls.full)
                            .placeholderMemoryCacheKey(urls.thumbnail)
                            .build()
                    },
                    contentDescription = stringResource(R.string.cd_item_photo),
                    contentScale = ContentScale.Fit,
                    onState = { if (it is AsyncImagePainter.State.Error) failed = true },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * The edit dialog's photo part. Changes go to the server right away, like "Move to list",
 * rather than waiting for Save.
 */
@Composable
internal fun PhotoSection(
    urls: ItemPhotoUrls?,
    hasPhoto: Boolean,
    busy: Boolean,
    onView: () -> Unit,
    onTake: () -> Unit,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
            if (urls != null) {
                PhotoThumb(urls.thumbnail, 56.dp, onClick = onView)
            } else {
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (busy) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
        }
        Spacer(Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.Center) {
            if (hasPhoto) {
                var replaceMenu by remember { mutableStateOf(false) }
                Box {
                    TextButton(onClick = { replaceMenu = true }, enabled = !busy) { Text(stringResource(R.string.photo_replace)) }
                    DropdownMenu(expanded = replaceMenu, onDismissRequest = { replaceMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.photo_take)) }, onClick = { replaceMenu = false; onTake() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.photo_choose)) }, onClick = { replaceMenu = false; onChoose() })
                    }
                }
                TextButton(onClick = onRemove, enabled = !busy) {
                    Text(stringResource(R.string.photo_remove), color = MaterialTheme.colorScheme.error)
                }
            } else {
                TextButton(onClick = onTake, enabled = !busy) { Text(stringResource(R.string.photo_take)) }
                TextButton(onClick = onChoose, enabled = !busy) { Text(stringResource(R.string.photo_choose)) }
            }
        }
    }
}
