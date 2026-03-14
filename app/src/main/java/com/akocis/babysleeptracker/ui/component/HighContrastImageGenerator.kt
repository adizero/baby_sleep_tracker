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
    TRIANGLES("Triangles"),
    CROSS("Cross"),
    HEART("Heart"),
    WAVES("Waves"),
    SUN("Sun"),
    MOON("Moon"),
    HOUSE("House"),
    FLOWER("Flower"),
    ARROWS("Arrows");
}

enum class ColorScheme(val label: String, val fg: Int, val bg: Int, val accent: Int? = null) {
    BLACK_WHITE("Black & White", 0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
    WHITE_BLACK("White & Black", 0xFFFFFFFF.toInt(), 0xFF000000.toInt()),
    RED_WHITE("Red & White", 0xFFCC0000.toInt(), 0xFFFFFFFF.toInt()),
    RED_BLACK("Red & Black", 0xFFCC0000.toInt(), 0xFF000000.toInt()),
    BLUE_WHITE("Blue & White", 0xFF0000CC.toInt(), 0xFFFFFFFF.toInt()),
    YELLOW_BLACK("Yellow & Black", 0xFFFFDD00.toInt(), 0xFF000000.toInt()),
    GREEN_WHITE("Green & White", 0xFF00AA00.toInt(), 0xFFFFFFFF.toInt()),
    ORANGE_BLACK("Orange & Black", 0xFFFF6600.toInt(), 0xFF000000.toInt()),
    PURPLE_WHITE("Purple & White", 0xFF7700BB.toInt(), 0xFFFFFFFF.toInt()),
    BLACK_WHITE_RED("Black White Red", 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFFCC0000.toInt()),
    RED_BLACK_WHITE("Red Black White", 0xFFCC0000.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
    BLUE_WHITE_YELLOW("Blue White Yellow", 0xFF0000CC.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFDD00.toInt());
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
        val ac: Paint? = colorScheme.accent?.let { Paint(Paint.ANTI_ALIAS_FLAG).apply { color = it; style = Paint.Style.FILL } }
        canvas.drawPaint(bg)

        val w = width.toFloat()
        val h = height.toFloat()
        val s = min(w, h)

        when (pattern) {
            PatternType.CHECKERBOARD -> drawCheckerboard(canvas, w, h, s, fg, ac)
            PatternType.STRIPES_H -> drawStripesH(canvas, w, h, s, fg, ac)
            PatternType.STRIPES_V -> drawStripesV(canvas, w, h, s, fg, ac)
            PatternType.BULLSEYE -> drawBullseye(canvas, w, h, s, fg, bg, ac)
            PatternType.CONCENTRIC_SQUARES -> drawConcentricSquares(canvas, w, h, s, fg, bg, ac)
            PatternType.ZIGZAG -> drawZigzag(canvas, w, h, s, fg)
            PatternType.DOTS -> drawDots(canvas, w, h, s, fg, ac)
            PatternType.FACE -> drawFace(canvas, w, h, s, fg, bg, ac)
            PatternType.STAR -> drawStar(canvas, w, h, s, fg, ac)
            PatternType.SPIRAL -> drawSpiral(canvas, w, h, s, fg)
            PatternType.DIAMONDS -> drawDiamonds(canvas, w, h, s, fg, ac)
            PatternType.TRIANGLES -> drawTriangles(canvas, w, h, s, fg, ac)
            PatternType.CROSS -> drawCross(canvas, w, h, s, fg, ac)
            PatternType.HEART -> drawHeart(canvas, w, h, s, fg, ac)
            PatternType.WAVES -> drawWaves(canvas, w, h, s, fg)
            PatternType.SUN -> drawSun(canvas, w, h, s, fg, bg, ac)
            PatternType.MOON -> drawMoon(canvas, w, h, s, fg, bg, ac)
            PatternType.HOUSE -> drawHouse(canvas, w, h, s, fg, bg, ac)
            PatternType.FLOWER -> drawFlower(canvas, w, h, s, fg, bg, ac)
            PatternType.ARROWS -> drawArrows(canvas, w, h, s, fg)
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

    private fun drawCheckerboard(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint, ac: Paint?) {
        val cellSize = s / 8f
        var y = 0f
        var row = 0
        while (y < h) {
            var x = 0f
            var col = 0
            while (x < w) {
                val mod = if (ac != null) (row + col) % 3 else (row + col) % 2
                if (mod == 0) {
                    canvas.drawRect(x, y, x + cellSize, y + cellSize, fg)
                } else if (ac != null && mod == 1) {
                    canvas.drawRect(x, y, x + cellSize, y + cellSize, ac)
                }
                x += cellSize
                col++
            }
            y += cellSize
            row++
        }
    }

    private fun drawStripesH(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint, ac: Paint?) {
        val stripeH = s / 10f
        var y = 0f
        var i = 0
        while (y < h) {
            val mod = if (ac != null) i % 3 else i % 2
            if (mod == 0) canvas.drawRect(0f, y, w, y + stripeH, fg)
            else if (ac != null && mod == 1) canvas.drawRect(0f, y, w, y + stripeH, ac)
            y += stripeH
            i++
        }
    }

    private fun drawStripesV(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint, ac: Paint?) {
        val stripeW = s / 10f
        var x = 0f
        var i = 0
        while (x < w) {
            val mod = if (ac != null) i % 3 else i % 2
            if (mod == 0) canvas.drawRect(x, 0f, x + stripeW, h, fg)
            else if (ac != null && mod == 1) canvas.drawRect(x, 0f, x + stripeW, h, ac)
            x += stripeW
            i++
        }
    }

    private fun drawBullseye(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint, bg: Paint, ac: Paint?) {
        val cx = w / 2f
        val cy = h / 2f
        val rings = if (ac != null) 9 else 8
        val ringW = s / (rings * 2f)
        for (i in rings downTo 1) {
            val paint = if (ac != null) {
                when (i % 3) { 0 -> fg; 1 -> ac; else -> bg }
            } else {
                if (i % 2 == 1) fg else bg
            }
            canvas.drawCircle(cx, cy, i * ringW, paint)
        }
    }

    private fun drawConcentricSquares(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint, bg: Paint, ac: Paint?) {
        val cx = w / 2f
        val cy = h / 2f
        val rings = if (ac != null) 9 else 8
        val step = s / (rings * 2f)
        for (i in rings downTo 1) {
            val paint = if (ac != null) {
                when (i % 3) { 0 -> fg; 1 -> ac; else -> bg }
            } else {
                if (i % 2 == 1) fg else bg
            }
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

    private fun drawDots(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint, ac: Paint?) {
        val dotR = s / 16f
        val spacing = s / 6f
        var y = spacing / 2f
        var row = 0
        var dotIdx = 0
        while (y < h) {
            var x = if (row % 2 == 0) spacing / 2f else spacing
            while (x < w) {
                val paint = if (ac != null && dotIdx % 2 == 1) ac else fg
                canvas.drawCircle(x, y, dotR, paint)
                x += spacing
                dotIdx++
            }
            y += spacing * 0.866f
            row++
        }
    }

    private fun drawFace(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint, bg: Paint, ac: Paint?) {
        val cx = w / 2f
        val cy = h / 2f
        val r = s * 0.4f
        // Head outline
        fg.style = Paint.Style.STROKE
        fg.strokeWidth = s / 20f
        canvas.drawCircle(cx, cy, r, fg)
        fg.style = Paint.Style.FILL
        // Eyes - use accent color if available
        val eyePaint = ac ?: fg
        val eyeR = r * 0.15f
        val eyeY = cy - r * 0.2f
        val eyeOffX = r * 0.3f
        canvas.drawCircle(cx - eyeOffX, eyeY, eyeR, eyePaint)
        canvas.drawCircle(cx + eyeOffX, eyeY, eyeR, eyePaint)
        // Eye highlights
        val hlR = eyeR * 0.35f
        canvas.drawCircle(cx - eyeOffX + hlR * 0.5f, eyeY - hlR * 0.5f, hlR, bg)
        canvas.drawCircle(cx + eyeOffX + hlR * 0.5f, eyeY - hlR * 0.5f, hlR, bg)
        // Mouth - use accent color if available
        val mouthPaint = ac ?: fg
        mouthPaint.style = Paint.Style.STROKE
        mouthPaint.strokeWidth = s / 30f
        val mouthRect = RectF(cx - r * 0.35f, cy + r * 0.05f, cx + r * 0.35f, cy + r * 0.45f)
        canvas.drawArc(mouthRect, 0f, 180f, false, mouthPaint)
        mouthPaint.style = Paint.Style.FILL
    }

    private fun drawStar(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint, ac: Paint?) {
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
        // Accent inner star
        if (ac != null) {
            val innerPath = Path()
            val innerOuterR = outerR * 0.5f
            val innerInnerR = innerR * 0.5f
            for (i in 0 until points * 2) {
                val r = if (i % 2 == 0) innerOuterR else innerInnerR
                val angle = Math.toRadians((i * 360.0 / (points * 2)) - 90.0)
                val x = cx + (r * cos(angle)).toFloat()
                val y = cy + (r * sin(angle)).toFloat()
                if (i == 0) innerPath.moveTo(x, y) else innerPath.lineTo(x, y)
            }
            innerPath.close()
            canvas.drawPath(innerPath, ac)
        }
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

    private fun drawDiamonds(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint, ac: Paint?) {
        val cellSize = s / 6f
        var row = 0
        var y = -cellSize / 2f
        var dIdx = 0
        while (y < h + cellSize) {
            var x = if (row % 2 == 0) 0f else cellSize
            while (x < w + cellSize) {
                val path = Path()
                path.moveTo(x, y - cellSize / 2f)
                path.lineTo(x + cellSize / 2f, y)
                path.lineTo(x, y + cellSize / 2f)
                path.lineTo(x - cellSize / 2f, y)
                path.close()
                if (row % 2 == 0) {
                    val paint = if (ac != null && dIdx % 2 == 1) ac else fg
                    canvas.drawPath(path, paint)
                    dIdx++
                }
                x += cellSize * 2f
            }
            y += cellSize
            row++
        }
    }

    private fun drawTriangles(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint, ac: Paint?) {
        val triSize = s / 5f
        val triH = triSize * 0.866f
        var row = 0
        var y = 0f
        var tIdx = 0
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
                    val paint = if (ac != null && tIdx % 2 == 1) ac else fg
                    canvas.drawPath(path, paint)
                    tIdx++
                }
                x += triSize
                col++
            }
            y += triH
            row++
        }
    }

    private fun drawCross(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint, ac: Paint?) {
        val cx = w / 2f
        val cy = h / 2f
        val arm = s * 0.35f
        val thickness = s * 0.15f
        canvas.drawRect(cx - thickness / 2f, cy - arm, cx + thickness / 2f, cy + arm, fg)
        canvas.drawRect(cx - arm, cy - thickness / 2f, cx + arm, cy + thickness / 2f, fg)
        // Accent center square
        if (ac != null) {
            val half = thickness * 0.7f
            canvas.drawRect(cx - half, cy - half, cx + half, cy + half, ac)
        }
    }

    private fun drawHeart(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint, ac: Paint?) {
        val cx = w / 2f
        val cy = h / 2f
        val r = s * 0.2f
        val path = Path()
        // Bottom point
        path.moveTo(cx, cy + r * 1.8f)
        // Left curve
        path.cubicTo(cx - r * 2.5f, cy + r * 0.2f, cx - r * 2.5f, cy - r * 1.5f, cx, cy - r * 0.5f)
        // Right curve
        path.cubicTo(cx + r * 2.5f, cy - r * 1.5f, cx + r * 2.5f, cy + r * 0.2f, cx, cy + r * 1.8f)
        path.close()
        canvas.drawPath(path, fg)
        // Accent smaller heart inside
        if (ac != null) {
            val r2 = r * 0.55f
            val innerPath = Path()
            innerPath.moveTo(cx, cy + r2 * 1.8f)
            innerPath.cubicTo(cx - r2 * 2.5f, cy + r2 * 0.2f, cx - r2 * 2.5f, cy - r2 * 1.5f, cx, cy - r2 * 0.5f)
            innerPath.cubicTo(cx + r2 * 2.5f, cy - r2 * 1.5f, cx + r2 * 2.5f, cy + r2 * 0.2f, cx, cy + r2 * 1.8f)
            innerPath.close()
            canvas.drawPath(innerPath, ac)
        }
    }

    private fun drawWaves(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint) {
        fg.style = Paint.Style.STROKE
        fg.strokeWidth = s / 12f
        val amplitude = s / 8f
        val wavelength = s / 2f
        val rows = (h / (amplitude * 3)).toInt() + 2
        for (r in 0..rows) {
            val baseY = r * amplitude * 3f
            val path = Path()
            path.moveTo(0f, baseY)
            var x = 0f
            while (x < w + wavelength) {
                path.cubicTo(
                    x + wavelength / 4f, baseY - amplitude,
                    x + wavelength * 3f / 4f, baseY + amplitude,
                    x + wavelength, baseY
                )
                x += wavelength
            }
            canvas.drawPath(path, fg)
        }
        fg.style = Paint.Style.FILL
    }

    private fun drawSun(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint, bg: Paint, ac: Paint?) {
        val cx = w / 2f
        val cy = h / 2f
        val r = s * 0.2f
        val rayLen = s * 0.18f
        val rayW = s / 30f
        // Rays - alternate colors when accent available
        val rayPaint = Paint(fg)
        rayPaint.strokeWidth = rayW
        rayPaint.style = Paint.Style.STROKE
        val rays = 12
        for (i in 0 until rays) {
            if (ac != null && i % 2 == 1) { rayPaint.color = ac.color } else { rayPaint.color = fg.color }
            val angle = Math.toRadians(i * 360.0 / rays)
            val x1 = cx + ((r + s * 0.03f) * cos(angle)).toFloat()
            val y1 = cy + ((r + s * 0.03f) * sin(angle)).toFloat()
            val x2 = cx + ((r + rayLen) * cos(angle)).toFloat()
            val y2 = cy + ((r + rayLen) * sin(angle)).toFloat()
            canvas.drawLine(x1, y1, x2, y2, rayPaint)
        }
        fg.style = Paint.Style.FILL
        // Center circle
        canvas.drawCircle(cx, cy, r, ac ?: fg)
        // Eyes and smile
        canvas.drawCircle(cx - r * 0.3f, cy - r * 0.15f, r * 0.1f, bg)
        canvas.drawCircle(cx + r * 0.3f, cy - r * 0.15f, r * 0.1f, bg)
        bg.style = Paint.Style.STROKE
        bg.strokeWidth = rayW * 0.8f
        val smileRect = RectF(cx - r * 0.3f, cy + r * 0.0f, cx + r * 0.3f, cy + r * 0.35f)
        canvas.drawArc(smileRect, 0f, 180f, false, bg)
        bg.style = Paint.Style.FILL
    }

    private fun drawMoon(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint, bg: Paint, ac: Paint?) {
        val cx = w / 2f
        val cy = h / 2f
        val r = s * 0.35f
        // Full circle
        canvas.drawCircle(cx, cy, r, fg)
        // Cut out a circle offset to the right to create crescent
        canvas.drawCircle(cx + r * 0.45f, cy - r * 0.1f, r * 0.8f, bg)
        // Accent stars around the moon
        if (ac != null) {
            val starPositions = listOf(
                Pair(cx + r * 1.1f, cy - r * 0.6f),
                Pair(cx + r * 0.8f, cy - r * 1.0f),
                Pair(cx - r * 0.8f, cy - r * 0.9f)
            )
            for ((sx, sy) in starPositions) {
                drawSmallStar(canvas, sx, sy, s * 0.04f, ac)
            }
        }
    }

    private fun drawSmallStar(canvas: Canvas, cx: Float, cy: Float, r: Float, paint: Paint) {
        val path = Path()
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) r else r * 0.4f
            val angle = Math.toRadians((i * 36.0) - 90.0)
            val x = cx + (radius * cos(angle)).toFloat()
            val y = cy + (radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, paint)
    }

    private fun drawHouse(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint, bg: Paint, ac: Paint?) {
        val cx = w / 2f
        val cy = h / 2f
        val houseW = s * 0.5f
        val wallH = s * 0.3f
        val roofH = s * 0.25f
        // Wall
        val wallTop = cy
        val wallBottom = cy + wallH
        canvas.drawRect(cx - houseW / 2f, wallTop, cx + houseW / 2f, wallBottom, fg)
        // Roof
        val path = Path()
        path.moveTo(cx - houseW / 2f - s * 0.05f, wallTop)
        path.lineTo(cx, wallTop - roofH)
        path.lineTo(cx + houseW / 2f + s * 0.05f, wallTop)
        path.close()
        canvas.drawPath(path, fg)
        // Door - use accent if available
        val doorPaint = ac ?: bg
        val doorW = houseW * 0.2f
        val doorH = wallH * 0.55f
        canvas.drawRect(cx - doorW / 2f, wallBottom - doorH, cx + doorW / 2f, wallBottom, doorPaint)
        // Window
        val winSize = houseW * 0.15f
        canvas.drawRect(cx + houseW * 0.15f, wallTop + wallH * 0.15f, cx + houseW * 0.15f + winSize, wallTop + wallH * 0.15f + winSize, ac ?: bg)
    }

    private fun drawFlower(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint, bg: Paint, ac: Paint?) {
        val cx = w / 2f
        val cy = h / 2f
        val petalR = s * 0.12f
        val centerR = s * 0.08f
        val dist = s * 0.15f
        val petals = 6
        // Stem
        fg.strokeWidth = s / 30f
        fg.style = Paint.Style.STROKE
        canvas.drawLine(cx, cy + centerR, cx, cy + s * 0.35f, fg)
        fg.style = Paint.Style.FILL
        // Petals
        for (i in 0 until petals) {
            val angle = Math.toRadians(i * 360.0 / petals)
            val px = cx + (dist * cos(angle)).toFloat()
            val py = cy + (dist * sin(angle)).toFloat()
            canvas.drawCircle(px, py, petalR, fg)
        }
        // Center - use accent if available
        canvas.drawCircle(cx, cy, centerR, ac ?: bg)
    }

    private fun drawArrows(canvas: Canvas, w: Float, h: Float, s: Float, fg: Paint) {
        val arrowH = s / 4f
        val arrowW = s / 5f
        val spacing = s / 3f
        var row = 0
        var y = spacing / 2f
        while (y < h) {
            var x = if (row % 2 == 0) spacing / 2f else spacing
            while (x < w) {
                val path = Path()
                // Arrow pointing up
                path.moveTo(x, y - arrowH / 2f)
                path.lineTo(x + arrowW / 2f, y)
                path.lineTo(x + arrowW / 4f, y)
                path.lineTo(x + arrowW / 4f, y + arrowH / 2f)
                path.lineTo(x - arrowW / 4f, y + arrowH / 2f)
                path.lineTo(x - arrowW / 4f, y)
                path.lineTo(x - arrowW / 2f, y)
                path.close()
                canvas.drawPath(path, fg)
                x += spacing
            }
            y += spacing
            row++
        }
    }
}
