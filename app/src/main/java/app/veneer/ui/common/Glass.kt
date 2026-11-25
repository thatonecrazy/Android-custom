package app.veneer.ui.common

import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * The same glass as Glass icons, for things drawn in Compose (folders): a shadow, a tinted body,
 * shading from top to bottom, a reflection across the top and a bright rim.
 */
fun Modifier.glassSurface(shape: Shape, tint: Color, clarity: Int, shine: Int, depth: Int): Modifier {
    val s = shine.coerceIn(0, 100) / 100f
    val d = depth.coerceIn(0, 100) / 100f
    val solid = 1f - clarity.coerceIn(0, 90) / 100f
    return this
        .then(if (d > 0f) Modifier.shadow((8 * d).dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black) else Modifier)
        .clip(shape)
        .background(tint.copy(alpha = tint.alpha * solid))
        .drawBehind {
            drawRect(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.27f * s), 0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.47f * d)))
        }
        .drawWithContent {
            drawContent()
            if (s > 0f) {
                drawOval(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.37f * s), Color.Transparent), startY = 0f, endY = size.height * 0.44f),
                    topLeft = Offset(-size.width * 0.12f, -size.height * 0.45f),
                    size = Size(size.width * 1.24f, size.height * 0.89f),
                )
            }
            val outline = shape.createOutline(size, layoutDirection, this)
            if (d > 0f) {
                drawOutline(outline, Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.43f * d))), style = Stroke(size.minDimension * 0.09f))
            }
            if (s > 0f) {
                drawOutline(
                    outline,
                    Brush.linearGradient(
                        0f to Color.White.copy(alpha = 0.9f * s), 0.6f to Color.White.copy(alpha = 0.1f * s), 1f to Color.White.copy(alpha = 0.35f * s),
                        start = Offset.Zero, end = Offset(size.width * 0.7f, size.height),
                    ),
                    style = Stroke(size.minDimension * 0.04f),
                )
            }
        }
}
