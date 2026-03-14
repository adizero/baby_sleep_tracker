package com.akocis.babysleeptracker.ui.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

enum class PatternType(val label: String) {
    CHECKERBOARD("Checkerboard"),
    STRIPES_H("Horizontal Stripes"),
    STRIPES_V("Vertical Stripes"),
    BULLSEYE("Bullseye"),
    CONCENTRIC_SQUARES("Concentric Squares"),
    ZIGZAG("Zigzag"),
    DOTS("Dots"),
    FACE("Face"),
    STAR("Star"),
    SPIRAL("Spiral"),
    DIAMONDS("Diamonds"),
    TRIANGLES("Triangles");
}

enum class ColorScheme(val label: String, val fg: Int, val bg: Int) {
    BLACK_WHITE("Black & White", 0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
    WHITE_BLACK("White & Black", 0xFFFFFFFF.toInt(), 0xFF000000.toInt()),
    RED_WHITE("Red & White", 0xFFCC0000.toInt(), 0xFFFFFFFF.toInt()),
    RED_BLACK("Red & Black", 0xFFCC0000.toInt(), 0xFF000000.toInt()),
    BLUE_WHITE("Blue & White", 0xFF0000CC.toInt(), 0xFFFFFFFF.toInt()),
    YELLOW_BLACK("Yellow & Black", 0xFFFFDD00.toInt(), 0xFF000000.toInt());
}

object HighContrastImageGenerator {

    fun generate(
        width: Int,
        height: Int,
        pattern: PatternType,
        colorScheme: ColorScheme,
        seed: Long = System.currentTimeMillis()
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val fg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorScheme.fg; style = Paint.Style.FILL }
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorScheme.bg; style = Paint.Style.FILL }
        canvas.drawPaint(bg)

        val w = width.toFloat()
        val h = height.toFloat()
        val s = min(w, h)

        when (pattern) {
            PatternType.CHECKERBOARD -> drawCheckerboard(canvas, w, h, s, fg)
            PatternType.STRIPES_H -> drawStripesH(canvas, w, h, s, fg)
            PatternType.STRIPES_V -> drawStripesV(canvas, w, h, s, fg)
            PatternType.BULLSEYE -> drawBullseye(canvas, w, h, s, fg, bg)
            PatternType.CONCENTRIC_SQUARES -> drawConcentricSquares(canvas, w, h, s, fg, bg)
            PatternType.ZIGZAG -> drawZigzag(canvas, w, h, s, fg)
            PatternType.DOTS -> drawDots(canvas, w, h, s, fg)
            PatternType.FACE -> drawFace(canvas, w, h, s, fg, bg)
            PatternType.STAR -> drawStar(canvas, w, h, s, fg)
            PatternType.SPIRAL -> drawSpiral(canvas, w, h, s, fg)
            PatternType.DIAMONDS -> drawDiamonds(canvas, w, h, s, fg)
            PatternType.TRIANGLES -> drawTriangles(canvas, w, h, s, fg)
        }
        return bitmap
    }

    fun generateRandom(
        width: Int,
        height: Int,
        enabledPatterns: Set<PatternType>,
        enabledColors: Set<ColorScheme>,
        seed: Long = System.currentTimeMillis()
    ): Bitmap {
        val rng = Random(seed)
        val patterns = enabledPatterns.ifEmpty { PatternType.entries.toSet() }
        val colors = enabledColors.ifEmpty { ColorScheme.entries.toSet() }
        val pattern = patterns.random(rng)
        val color = colors.random(rng)
        return generate(width, height, pattern, color, seed)
    }

    private fun drawCheckerboard(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint) {
        val cellSize = s / 8f
        var y = 0f
        var row = 0
        while (y < h) {
            var x = 0f
            var col = 0
            while (x < w) {
                if ((row + col) % 2 == 0) {
                    canvas.drawRect(x, y, x + cellSize, y + cellSize, fg)
                }
                x += cellSize
                col++
            }
            y += cellSize
            row++
        }
    }

    private fun drawStripesH(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint) {
        val stripeH = s / 10f
        var y = 0f
        var i = 0
        while (y < h) {
            if (i % 2 == 0) canvas.drawRect(0f, y, w, y + stripeH, fg)
            y += stripeH
            i++
        }
    }

    private fun drawStripesV(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint) {
        val stripeW = s / 10f
        var x = 0f
        var i = 0
        while (x < w) {
            if (i % 2 == 0) canvas.drawRect(x, 0f, x + stripeW, h, fg)
            x += stripeW
            i++
        }
    }

    private fun drawBullseye(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint, bg: Paint) {
        val cx = w / 2f
        val cy = h / 2f
        val rings = 8
        val ringW = s / (rings * 2f)
        for (i in rings downTo 1) {
            val paint = if (i % 2 == 1) fg else bg
            canvas.drawCircle(cx, cy, i * ringW, paint)
        }
    }

    private fun drawConcentricSquares(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint, bg: Paint) {
        val cx = w / 2f
        val cy = h / 2f
        val rings = 8
        val step = s / (rings * 2f)
        for (i in rings downTo 1) {
            val paint = if (i % 2 == 1) fg else bg
            val half = i * step
            canvas.drawRect(cx - half, cy - half, cx + half, cy + half, paint)
        }
    }

    private fun drawZigzag(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint) {
        fg.style = Paint.Style.STROKE
        fg.strokeWidth = s / 12f
        val amplitude = s / 6f
        val period = s / 4f
        val rows = (h / (amplitude * 2)).toInt() + 2
        for (r in 0..rows) {
            val baseY = r * amplitude * 2f
            val path = Path()
            path.moveTo(0f, baseY)
            var x = 0f
            var up = true
            while (x < w + period) {
                x += period / 2f
                val y = if (up) baseY - amplitude else baseY + amplitude
                path.lineTo(x, y)
                up = !up
            }
            canvas.drawPath(path, fg)
        }
        fg.style = Paint.Style.FILL
    }

    private fun drawDots(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint) {
        val dotR = s / 16f
        val spacing = s / 6f
        var y = spacing / 2f
        var row = 0
        while (y < h) {
            var x = if (row % 2 == 0) spacing / 2f else spacing
            while (x < w) {
                canvas.drawCircle(x, y, dotR, fg)
                x += spacing
            }
            y += spacing * 0.866f
            row++
        }
    }

    private fun drawFace(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint, bg: Paint) {
        val cx = w / 2f
        val cy = h / 2f
        val r = s * 0.4f
        // Head outline
        fg.style = Paint.Style.STROKE
        fg.strokeWidth = s / 20f
        canvas.drawCircle(cx, cy, r, fg)
        fg.style = Paint.Style.FILL
        // Eyes
        val eyeR = r * 0.15f
        val eyeY = cy - r * 0.2f
        val eyeOffX = r * 0.3f
        canvas.drawCircle(cx - eyeOffX, eyeY, eyeR, fg)
        canvas.drawCircle(cx + eyeOffX, eyeY, eyeR, fg)
        // Eye highlights
        val hlR = eyeR * 0.35f
        canvas.drawCircle(cx - eyeOffX + hlR * 0.5f, eyeY - hlR * 0.5f, hlR, bg)
        canvas.drawCircle(cx + eyeOffX + hlR * 0.5f, eyeY - hlR * 0.5f, hlR, bg)
        // Mouth - simple arc
        fg.style = Paint.Style.STROKE
        fg.strokeWidth = s / 30f
        val mouthRect = RectF(cx - r * 0.35f, cy + r * 0.05f, cx + r * 0.35f, cy + r * 0.45f)
        canvas.drawArc(mouthRect, 0f, 180f, false, fg)
        fg.style = Paint.Style.FILL
    }

    private fun drawStar(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint) {
        val cx = w / 2f
        val cy = h / 2f
        val outerR = s * 0.4f
        val innerR = s * 0.18f
        val points = 5
        val path = Path()
        for (i in 0 until points * 2) {
            val r = if (i % 2 == 0) outerR else innerR
            val angle = Math.toRadians((i * 360.0 / (points * 2)) - 90.0)
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, fg)
    }

    private fun drawSpiral(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint) {
        fg.style = Paint.Style.STROKE
        fg.strokeWidth = s / 16f
        val cx = w / 2f
        val cy = h / 2f
        val maxR = s * 0.42f
        val path = Path()
        val turns = 4.0
        val steps = 360
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val angle = t * turns * 2 * Math.PI
            val r = t * maxR
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, fg)
        fg.style = Paint.Style.FILL
    }

    private fun drawDiamonds(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint) {
        val cellSize = s / 6f
        var row = 0
        var y = -cellSize / 2f
        while (y < h + cellSize) {
            var x = if (row % 2 == 0) 0f else cellSize
            while (x < w + cellSize) {
                val path = Path()
                path.moveTo(x, y - cellSize / 2f)
                path.lineTo(x + cellSize / 2f, y)
                path.lineTo(x, y + cellSize / 2f)
                path.lineTo(x - cellSize / 2f, y)
                path.close()
                if (row % 2 == 0) canvas.drawPath(path, fg)
                x += cellSize * 2f
            }
            y += cellSize
            row++
        }
    }

    private fun drawTriangles(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint) {
        val triSize = s / 5f
        val triH = triSize * 0.866f
        var row = 0
        var y = 0f
        while (y < h + triH) {
            var x = if (row % 2 == 0) 0f else triSize / 2f
            var col = 0
            while (x < w + triSize) {
                if ((row + col) % 2 == 0) {
                    val path = Path()
                    path.moveTo(x, y + triH)
                    path.lineTo(x + triSize / 2f, y)
                    path.lineTo(x + triSize, y + triH)
                    path.close()
                    canvas.drawPath(path, fg)
                }
                x += triSize
                col++
            }
            y += triH
            row++
        }
    }
}
