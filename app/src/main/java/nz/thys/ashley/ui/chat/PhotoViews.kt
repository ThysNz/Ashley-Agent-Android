package nz.thys.ashley.ui.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nz.thys.ashley.device.Photos
import nz.thys.ashley.ui.theme.Ashley
import java.io.File

@Composable
private fun rememberBitmap(file: File, maxPx: Int): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(null, file.path, file.lastModified(), maxPx) {
        value = withContext(Dispatchers.IO) { Photos.decode(file, maxPx)?.asImageBitmap() }
    }
    return bmp
}

/** A square, cropped thumbnail. */
@Composable
fun PhotoThumb(file: File, sizeDp: Int, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val bmp = rememberBitmap(file, sizeDp * 3)
    Box(
        modifier.size(sizeDp.dp).clip(RoundedCornerShape(10.dp)).background(Ashley.colors.surface2)
            .let { if (onClick != null) it.clickable(onClickLabel = "Open photo", onClick = onClick) else it },
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) Image(bmp, contentDescription = "Photo", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Text("photo", fontSize = 11.sp, color = Ashley.colors.faint)
    }
}

/** A photo in the chat: wide, keeps its shape, tap to open full screen. */
@Composable
fun PhotoInChat(file: File, onClick: () -> Unit) {
    val bmp = rememberBitmap(file, 900)
    Box(
        Modifier.fillMaxWidth(0.7f).heightIn(max = 280.dp).clip(RoundedCornerShape(14.dp)).background(Ashley.colors.surface2)
            .clickable(onClickLabel = "Open photo", onClick = onClick),
    ) {
        if (bmp != null) Image(bmp, contentDescription = "Photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth())
        else Box(Modifier.size(120.dp))
    }
}

/** Full-screen photo with an optional caption; tap anywhere to close. */
@Composable
fun PhotoViewer(file: File, caption: String?, onDismiss: () -> Unit) {
    val bmp = rememberBitmap(file, 2048)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).clickable(onClickLabel = "Close", onClick = onDismiss).padding(12.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (bmp != null) Image(bmp, contentDescription = "Photo", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().weight(1f, fill = false))
            Text(file.name, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            if (!caption.isNullOrBlank()) Text(caption, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
