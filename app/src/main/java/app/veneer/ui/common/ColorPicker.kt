package app.veneer.ui.common

import android.graphics.Color.HSVToColor
import android.graphics.Color.colorToHSV
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import java.util.Locale

/** A checkerboard, drawn behind colors so transparency is visible. */
val CheckerBrush: Brush by lazy {
    val bmp = createBitmap(16, 16)
    for (y in 0 until 16) for (x in 0 until 16) {
        bmp[x, y] = if ((x / 8 + y / 8) % 2 == 0) 0xFFDDDDDD.toInt() else 0xFFFFFFFF.toInt()
    }
    ShaderBrush(ImageShader(bmp.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
}

private val Palette = listOf(
    0xFFFFFFFF, 0xFFE0E0E0, 0xFF9E9E9E, 0xFF424242, 0xFF000000,
    0xFFFF5252, 0xFFFF4081, 0xFFE040FB, 0xFF7C4DFF, 0xFF536DFE,
    0xFF448AFF, 0xFF40C4FF, 0xFF18FFFF, 0xFF64FFDA, 0xFF69F0AE,
    0xFFB2FF59, 0xFFEEFF41, 0xFFFFFF00, 0xFFFFD740, 0xFFFFAB40,
    0xFFFF6E40, 0xFF8D6E63, 0xFF8AB4F8, 0xFFF3E9DA, 0xFF1F2340,
).map { it.toInt() }

fun Int.toHex(withAlpha: Boolean = true): String =
    if (withAlpha) String.format(Locale.US, "#%08X", this) else String.format(Locale.US, "#%06X", this and 0xFFFFFF)

fun parseHex(text: String): Int? {
    val clean = text.trim().removePrefix("#")
    return when (clean.length) {
        6 -> clean.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() }
        8 -> clean.toLongOrNull(16)?.toInt()
        else -> null
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Int,
    allowAlpha: Boolean = true,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val hsv = remember { FloatArray(3).also { colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(hsv[0]) }
    var sat by remember { mutableFloatStateOf(hsv[1]) }
    var value by remember { mutableFloatStateOf(hsv[2]) }
    var alpha by remember { mutableFloatStateOf(if (allowAlpha) (initial ushr 24) / 255f else 1f) }
    fun current(): Int {
        val rgb = HSVToColor(floatArrayOf(hue, sat, value))
        return ((alpha * 255).toInt() shl 24) or (rgb and 0xFFFFFF)
    }
    var hexText by remember { mutableStateOf(initial.toHex(allowAlpha)) }
    fun setFrom(color: Int) {
        val out = FloatArray(3)
        colorToHSV(color, out)
        hue = out[0]; sat = out[1]; value = out[2]
        if (allowAlpha) alpha = (color ushr 24) / 255f
        hexText = current().toHex(allowAlpha)
    }
    fun changed() {
        hexText = current().toHex(allowAlpha)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = { TextButton(onClick = { onPick(current()) }) { Text("Use color") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(CheckerBrush)
                            .background(Color(current()))
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)),
                    )
                    Spacer(Modifier.width(16.dp))
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { text ->
                            hexText = text
                            parseHex(text)?.let { c ->
                                val out = FloatArray(3)
                                colorToHSV(c, out)
                                hue = out[0]; sat = out[1]; value = out[2]
                                if (allowAlpha) alpha = (c ushr 24) / 255f
                            }
                        },
                        singleLine = true,
                        label = { Text("Hex") },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("Hue", style = MaterialTheme.typography.labelMedium)
                GradientSlider(
                    fraction = hue / 360f,
                    brush = Brush.horizontalGradient((0..6).map { Color.hsv(it * 60f % 360f, 1f, 1f) }),
                ) { hue = it * 360f; changed() }
                Text("Saturation", style = MaterialTheme.typography.labelMedium)
                GradientSlider(
                    fraction = sat,
                    brush = Brush.horizontalGradient(listOf(Color.hsv(hue, 0f, value), Color.hsv(hue, 1f, value))),
                ) { sat = it; changed() }
                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                GradientSlider(
                    fraction = value,
                    brush = Brush.horizontalGradient(listOf(Color.Black, Color.hsv(hue, sat, 1f))),
                ) { value = it; changed() }
                if (allowAlpha) {
                    Text("Opacity", style = MaterialTheme.typography.labelMedium)
                    GradientSlider(
                        fraction = alpha,
                        brush = Brush.horizontalGradient(listOf(Color.Transparent, Color.hsv(hue, sat, value))),
                        checker = true,
                    ) { alpha = it; changed() }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Palette.forEach { c -> ColorSwatch(c, size = 30) { setFrom(if (allowAlpha) c else c or 0xFF000000.toInt()) } }
                }
            }
        },
    )
}

@Composable
private fun GradientSlider(fraction: Float, brush: Brush, checker: Boolean = false, onChange: (Float) -> Unit) {
    val density = LocalDensity.current
    val latest by rememberUpdatedState(onChange)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(28.dp),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val width by rememberUpdatedState(widthPx)
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .then(if (checker) Modifier.background(CheckerBrush) else Modifier)
                .background(brush)
                .pointerInput(Unit) { detectTapGestures { latest((it.x / width).coerceIn(0f, 1f)) } }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(onDragStart = { latest((it.x / width).coerceIn(0f, 1f)) }) { change, _ ->
                        change.consume()
                        latest((change.position.x / width).coerceIn(0f, 1f))
                    }
                },
        )
        val thumbX = with(density) { (fraction.coerceIn(0f, 1f) * widthPx).toDp() } - 12.dp
        Box(
            Modifier
                .offset(x = thumbX.coerceIn(0.dp, maxWidth - 24.dp), y = 2.dp)
                .size(24.dp)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, Color(0x33000000), CircleShape),
        )
    }
}
