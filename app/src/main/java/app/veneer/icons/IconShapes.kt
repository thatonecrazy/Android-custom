package app.veneer.icons

import android.graphics.Path
import android.graphics.RectF
import app.veneer.data.IconShape
import kotlin.math.cos
import kotlin.math.sin

object IconShapes {

    /**
     * The outline of [shape] filling a [size] x [size] square, or null for the system shape.
     * [corner] is the Rounded shape's roundness, 0 to 100.
     */
    fun path(shape: IconShape, size: Float, corner: Int = 44): Path? {
        val rect = RectF(0f, 0f, size, size)
        return when (shape) {
            IconShape.Original -> null
            IconShape.Circle -> Path().apply { addOval(rect, Path.Direction.CW) }
            IconShape.Square -> Path().apply { addRect(rect, Path.Direction.CW) }
            IconShape.RoundedSquare -> Path().apply {
                val r = size / 2f * corner.coerceIn(0, 100) / 100f
                addRoundRect(rect, r, r, Path.Direction.CW)
            }
            IconShape.Squircle -> squircle(size)
            IconShape.Teardrop -> corners(rect, size / 2f, size / 2f, size * 0.1f, size / 2f)
            IconShape.Leaf -> corners(rect, size / 2f, size * 0.12f, size / 2f, size * 0.12f)
            IconShape.Hexagon -> hexagon(size)
            IconShape.Pebble -> pebble(size)
            IconShape.Octagon -> octagon(size)
            IconShape.Flower -> flower(size)
            IconShape.Arch -> corners(rect, size / 2f, size / 2f, size * 0.12f, size * 0.12f)
            IconShape.Diamond -> diamond(size)
        }
    }

    /** A smooth, slightly uneven stone. */
    private fun pebble(s: Float) = Path().apply {
        moveTo(s * 0.52f, s * 0.01f)
        cubicTo(s * 0.86f, s * 0.0f, s * 1.0f, s * 0.22f, s * 0.99f, s * 0.52f)
        cubicTo(s * 0.98f, s * 0.84f, s * 0.74f, s * 1.0f, s * 0.47f, s * 0.99f)
        cubicTo(s * 0.17f, s * 0.98f, s * 0.0f, s * 0.78f, s * 0.01f, s * 0.47f)
        cubicTo(s * 0.02f, s * 0.17f, s * 0.21f, s * 0.02f, s * 0.52f, s * 0.01f)
        close()
    }

    /** A square with its corners cut off, softened a little. */
    private fun octagon(s: Float) = Path().apply {
        val c = s * 0.29f
        val pts = listOf(c to 0f, s - c to 0f, s to c, s to s - c, s - c to s, c to s, 0f to s - c, 0f to c)
        pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
        close()
    }

    /** A circle with eight soft scallops around the edge. */
    private fun flower(s: Float) = Path().apply {
        val c = s / 2f
        val steps = 160
        for (i in 0..steps) {
            val a = 2.0 * Math.PI * i / steps
            val r = c * (0.88f + 0.12f * cos(8 * a).toFloat())
            val x = c + r * cos(a - Math.PI / 2).toFloat()
            val y = c + r * sin(a - Math.PI / 2).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }

    /** A rounded square turned 45 degrees. */
    private fun diamond(s: Float): Path {
        val side = s / 1.414f
        val r = side * 0.2f
        val c = s / 2f
        return Path().apply {
            addRoundRect(RectF(c - side / 2f, c - side / 2f, c + side / 2f, c + side / 2f), r, r, Path.Direction.CW)
            transform(android.graphics.Matrix().apply { setRotate(45f, c, c) })
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
