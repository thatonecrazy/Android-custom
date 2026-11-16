package app.veneer.icons

import android.graphics.Path
import android.graphics.RectF
import app.veneer.data.IconShape
import kotlin.math.cos
import kotlin.math.sin

object IconShapes {

    /** The outline of [shape] filling a [size] x [size] square, or null for the system shape. */
    fun path(shape: IconShape, size: Float): Path? {
        val rect = RectF(0f, 0f, size, size)
        return when (shape) {
            IconShape.Original -> null
            IconShape.Circle -> Path().apply { addOval(rect, Path.Direction.CW) }
            IconShape.Square -> Path().apply { addRect(rect, Path.Direction.CW) }
            IconShape.RoundedSquare -> Path().apply {
                val r = size * 0.22f
                addRoundRect(rect, r, r, Path.Direction.CW)
            }
            IconShape.Squircle -> squircle(size)
            IconShape.Teardrop -> corners(rect, size / 2f, size / 2f, size * 0.1f, size / 2f)
            IconShape.Leaf -> corners(rect, size / 2f, size * 0.12f, size / 2f, size * 0.12f)
            IconShape.Hexagon -> hexagon(size)
        }
    }

    private fun corners(rect: RectF, tl: Float, tr: Float, br: Float, bl: Float) = Path().apply {
        addRoundRect(rect, floatArrayOf(tl, tl, tr, tr, br, br, bl, bl), Path.Direction.CW)
    }

    /** A superellipse-like shape made of four cubic curves. */
    private fun squircle(size: Float): Path {
        val h = size / 2f
        val k = 0.92f
        val c = h * k
        return Path().apply {
            moveTo(h, 0f)
            cubicTo(h + c, 0f, size, h - c, size, h)
            cubicTo(size, h + c, h + c, size, h, size)
            cubicTo(h - c, size, 0f, h + c, 0f, h)
            cubicTo(0f, h - c, h - c, 0f, h, 0f)
            close()
        }
    }

    private fun hexagon(size: Float): Path {
        val c = size / 2f
        val r = size / 2f
        return Path().apply {
            for (i in 0 until 6) {
                val angle = Math.toRadians((60.0 * i) - 90.0)
                val x = c + r * cos(angle).toFloat()
                val y = c + r * sin(angle).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
    }
}
