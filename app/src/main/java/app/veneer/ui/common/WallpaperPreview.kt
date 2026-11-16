package app.veneer.ui.common

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import app.veneer.data.WallpaperSpec
import app.veneer.system.Wallpapers

/** A small copy of a generated wallpaper, drawn by the same code that makes the real one. */
@Composable
fun WallpaperPreview(spec: WallpaperSpec, modifier: Modifier = Modifier) {
    val image = remember(spec) { Wallpapers.render(spec, 270, 540).asImageBitmap() }
    Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}
