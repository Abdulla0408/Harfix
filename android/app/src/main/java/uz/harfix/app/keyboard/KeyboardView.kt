package uz.harfix.app.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RenderNode
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import java.util.Locale

/**
 * Light and dark "liquid glass" colors: a soft gradient behind translucent keys with a bright
 * edge, like frosted glass.
 */
class KeyboardTheme(dark: Boolean) {
    val backgroundTop = if (dark) 0xFF1C222B.toInt() else 0xFFEDF1F7.toInt()
    val backgroundBottom = if (dark) 0xFF0E1116.toInt() else 0xFFD6DEE9.toInt()
    val key = if (dark) 0x38FFFFFF else 0xD9FFFFFF.toInt()
    val keyPressed = if (dark) 0x66FFFFFF else 0xFFFFFFFF.toInt()
    val func = if (dark) 0x1FFFFFFF else 0x73FFFFFF
    val funcPressed = if (dark) 0x40FFFFFF else 0xB3FFFFFF.toInt()
    val newLetter = if (dark) 0x66C9A23A else 0xCCFFE3A0.toInt()
    val newLetterPressed = if (dark) 0x99C9A23A.toInt() else 0xFFFFD36B.toInt()
    /** The bright rim of the glass and its top highlight. */
    val edge = if (dark) 0x2EFFFFFF else 0xB3FFFFFF.toInt()
    val highlight = if (dark) 0x40FFFFFF else 0xFFFFFFFF.toInt()
    val text = if (dark) 0xFFE8ECF1.toInt() else 0xFF10141A.toInt()
    val hint = if (dark) 0xFF8B96A3.toInt() else 0xFF6D7885.toInt()
    val shadow = if (dark) 0x40000000 else 0x1F1B2F5E
    val popup = if (dark) 0xF22C323B.toInt() else 0xF7FFFFFF.toInt()
    val popupBorder = if (dark) 0x40FFFFFF else 0x33000000
    val strip = if (dark) 0xFFE8ECF1.toInt() else 0xFF1B2F5E.toInt()
    val separator = if (dark) 0x26FFFFFF else 0x261B2F5E

    companion object {
        const val ACCENT = 0xFF1C6EF2.toInt()
        const val ACCENT_PRESSED = 0xFF1557C4.toInt()
        const val TRAIL = 0xCC1C6EF2.toInt()
    }
}

/** What the keyboard view reports to the input method service. */
interface KeyboardListener {
    fun onText(text: String)
    fun onKey(kind: KeyKind)
    fun onPress(kind: KeyKind)
    fun onSpaceLongPress()

    /** A word in the suggestion strip was tapped. */
    fun onSuggestion(text: String) {}

    /** A word was drawn over the letters: the [path], the letter key centres and a key's width. */
    fun onGlide(path: List<Glide.P>, centers: Map<Char, Glide.P>, keyWidth: Float) {}
}

/** The Harfix keyboard, drawn by hand: no XML keyboards, works the same on every phone. */
@SuppressLint("ViewConstructor")
class KeyboardView(context: Context) : View(context) {
    var listener: KeyboardListener? = null

    private var shift = Shift.OFF
    private var enterIcon = EnterIcon.NEWLINE
    /** Key preview bubbles (off in password fields). */
    private var showPreview = true
    private var spaceLabel = "Harfix"
    private var layout: Layout = Layouts.latin(null, false)

    private val density = resources.displayMetrics.density
    @Suppress("DEPRECATION")
    private val sp = resources.displayMetrics.scaledDensity
    private var navInset = 0
    private val keys = ArrayList<Pair<Key, RectF>>()
    private var theme = KeyboardTheme(isDark())

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    /** The long-press popup of alternative letters. */
    private class Alts(val items: List<String>, val rect: RectF, val cell: Float) {
        var selected = 0
    }

    /** One finger on the keyboard. */
    private class Touch(var key: Key, var rect: RectF) {
        /** Already handled (shift, backspace, long press...): nothing to do on release. */
        var consumed = false
        var alts: Alts? = null
    }

    private val touches = HashMap<Int, Touch>()
    private val handler = Handler(Looper.getMainLooper())
    private val timers = HashMap<Int, Runnable>()

    // suggestion strip (on top, for Latin letters in ordinary text fields)
    private var strip = false
    private var suggestions: List<String> = emptyList()
    private var stripPointer = -1
    private var stripCell = -1

    // glide typing: one finger drawing over the letters
    private var glideOn = false
    private var glidePointer = -1
    private var gliding = false
    private val glidePath = ArrayList<Glide.P>()
    private var downX = 0f
    private var downY = 0f
    /** Width of a letter key, measured in [computeKeys]. */
    private var letterWidth = 0f
    private val bgPaint = Paint()
    /** The background gradient is made once per size and theme, not on every frame. */
    private var bgKey: Pair<Int, KeyboardTheme>? = null
    private val pressedRect = RectF()
    /** The keys at rest, recorded once (API 29+); [baseDirty] when they must be recorded again. */
    private var baseNode: Any? = null // a RenderNode; typed loosely so API 26-28 never loads the class
    private var baseDirty = true

    private fun isDark() =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private val padTop get() = 6 * density
    private val padX get() = 3 * density
    private val padBottom get() = 4 * density + navInset
    private val stripHeight get() = if (strip) 40 * density else 0f

    /** Normal key height: 54dp, 40dp in landscape. */
    private fun keyHeight(): Float =
        (if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 40f else 54f) * density

    fun setLayout(l: Layout) {
        layout = l
        baseDirty = true
        cancelAll()
        requestLayout()
        computeKeys()
        invalidate()
    }

    fun setShift(s: Shift) {
        if (s != shift) baseDirty = true
        shift = s
        invalidate()
    }

    fun setEnterIcon(icon: EnterIcon) {
        if (icon != enterIcon) baseDirty = true
        enterIcon = icon
        invalidate()
    }

    fun setShowPreview(show: Boolean) {
        showPreview = show
    }

    fun setSpaceLabel(text: String) {
        if (text != spaceLabel) baseDirty = true
        spaceLabel = text
        invalidate()
    }

    /** Shows or hides the suggestion strip above the keys. */
    fun setStrip(on: Boolean) {
        if (on == strip) return
        strip = on
        baseDirty = true
        if (!on) suggestions = emptyList()
        requestLayout()
        computeKeys()
        invalidate()
    }

    /** Up to three words: the first is shown in the middle. */
    fun setSuggestions(words: List<String>) {
        if (words == suggestions) return
        suggestions = words
        if (strip) invalidate()
    }

    fun setGlide(on: Boolean) {
        glideOn = on
    }

    private val shifted get() = shift != Shift.OFF

    /** The text a key types now (upper case while shifted). */
    private fun shiftText(text: String): String =
        if (shifted && text.any { it.isLetter() }) text.uppercase(Locale.ROOT) else text

    // --- geometry ----------------------------------------------------------------------------

    private fun computeKeys() {
        keys.clear()
        if (width == 0) return
        val gapX = 3 * density
        val gapY = 5 * density
        var y = padTop + stripHeight
        letterWidth = 0f
        for (row in layout.rows) {
            val h = keyHeight() * row.height
            val unit = (width - padX * 2) / row.keys.sumOf { it.width.toDouble() }.toFloat()
            var x = padX
            for (k in row.keys) {
                val w = k.width * unit
                if (k.kind != KeyKind.SPACER) {
                    keys.add(k to RectF(x + gapX / 2, y + gapY / 2, x + w - gapX / 2, y + h - gapY / 2))
                    if (k.kind == KeyKind.CHAR && letterWidth == 0f) letterWidth = w
                }
                x += w
            }
            y += h
        }
    }

    /** Centres of the single-letter keys (lower case), for glide typing. */
    private fun letterCenters(): Map<Char, Glide.P> {
        val out = HashMap<Char, Glide.P>()
        for ((k, r) in keys) {
            if (k.kind == KeyKind.CHAR && k.label.length == 1 && k.label[0].isLetter()) {
                out[k.label[0].lowercaseChar()] = Glide.P(r.centerX(), r.centerY())
            }
        }
        return out
    }

    /** The three strip cells: left, middle (the best word), right. */
    private fun stripCells(): List<Pair<String, RectF>> {
        val top = padTop / 2
        val bottom = padTop + stripHeight - 2 * density
        val w = (width - padX * 2) / 3f
        val order = listOf(1, 0, 2)
        return order.mapIndexed { i, idx ->
            (suggestions.getOrNull(idx) ?: "") to RectF(padX + i * w, top, padX + (i + 1) * w, bottom)
        }
    }

    private fun stripCellAt(x: Float): Int {
        val w = (width - padX * 2) / 3f
        return ((x - padX) / w).toInt().coerceIn(0, 2)
    }

    /** The key under (x, y), or the nearest one within 12dp. */
    private fun keyAt(x: Float, y: Float): Pair<Key, RectF>? {
        var best: Pair<Key, RectF>? = null
        var bestD = Float.MAX_VALUE
        for (kr in keys) {
            val r = kr.second
            val dx = maxOf(0f, r.left - x, x - r.right)
            val dy = maxOf(0f, r.top - y, y - r.bottom)
            val d = dx * dx + dy * dy
            if (d < bestD) {
                bestD = d
                best = kr
            }
        }
        val max = 12 * density
        return if (bestD < max * max) best else null
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rows = layout.rows.sumOf { (keyHeight() * it.height).toDouble() }.toFloat()
        setMeasuredDimension(w, (padTop + stripHeight + rows + padBottom).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        baseDirty = true
        computeKeys()
    }

    override fun onApplyWindowInsets(insets: WindowInsets): WindowInsets {
        val bottom = if (Build.VERSION.SDK_INT >= 30) {
            insets.getInsets(WindowInsets.Type.navigationBars()).bottom
        } else {
            @Suppress("DEPRECATION")
            insets.systemWindowInsetBottom
        }
        if (bottom != navInset) {
            navInset = bottom
            requestLayout()
        }
        return insets
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        theme = KeyboardTheme(isDark())
        baseDirty = true
        requestLayout()
    }

    override fun onDetachedFromWindow() {
        cancelAll()
        super.onDetachedFromWindow()
    }

    // --- drawing -----------------------------------------------------------------------------

    private fun textSize(k: Key, r: RectF): Float = when (k.kind) {
        KeyKind.CHAR -> if (k.label.length <= 1) minOf(23 * sp, r.height() * 0.46f) else minOf(16 * sp, r.height() * 0.32f)
        KeyKind.NEW_LETTER -> minOf(22 * sp, r.height() * 0.52f)
        KeyKind.SPACE -> minOf(12 * sp, r.height() * 0.26f)
        else -> minOf(15 * sp, r.height() * 0.32f)
    }

    private fun text(c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean) {
        label.textSize = size
        label.color = color
        label.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        c.drawText(s, x, y - (label.ascent() + label.descent()) / 2, label)
    }

    /** A rounded glass pane: soft shadow, translucent fill, bright rim and a highlight on top. */
    private fun glass(c: Canvas, r: RectF, radius: Float, color: Int, shadow: Boolean) {
        val t = theme
        if (shadow) {
            fill.color = t.shadow
            val off = 1.5f * density
            c.drawRoundRect(r.left, r.top + off, r.right, r.bottom + off, radius, radius, fill)
        }
        fill.color = color
        c.drawRoundRect(r, radius, radius, fill)
        stroke.color = t.edge
        stroke.strokeWidth = density
        c.drawRoundRect(r.left + density / 2, r.top + density / 2, r.right - density / 2, r.bottom - density / 2, radius, radius, stroke)
        stroke.color = t.highlight
        stroke.strokeWidth = 1.2f * density
        val y = r.top + 1.4f * density
        c.drawLine(r.left + radius, y, r.right - radius, y, stroke)
    }

    private fun drawStrip(c: Canvas) {
        val t = theme
        val cells = stripCells()
        cells.forEachIndexed { i, (word, r) ->
            if (i == stripCell && stripPointer >= 0 && word.isNotEmpty()) {
                fill.color = t.keyPressed
                c.drawRoundRect(r, 10 * density, 10 * density, fill)
            }
            if (word.isNotEmpty()) {
                val middle = i == 1
                // long words get a smaller font so they fit their third of the strip
                label.typeface = if (middle) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                label.textSize = 16 * sp
                val room = r.width() - 12 * density
                val w = label.measureText(word)
                val size = if (w > room) maxOf(10 * sp, 16 * sp * room / w) else 16 * sp
                text(c, word, r.centerX(), r.centerY(), size, t.strip, middle)
            }
            if (i > 0) {
                fill.color = t.separator
                val x = r.left
                c.drawRect(x - density / 2, r.top + r.height() * 0.25f, x + density / 2, r.bottom - r.height() * 0.25f, fill)
            }
        }
    }

    private fun drawTrail(c: Canvas) {
        val n = glidePath.size
        if (!gliding || n < 2) return
        val from = maxOf(0, n - 80)
        stroke.strokeWidth = 6 * density
        for (i in from + 1 until n) {
            val a = glidePath[i - 1]
            val b = glidePath[i]
            val age = (i - from).toFloat() / (n - from)
            stroke.color = (((0xCC * age).toInt() shl 24) or (KeyboardTheme.TRAIL and 0xFFFFFF))
            c.drawLine(a.x, a.y, b.x, b.y, stroke)
        }
    }

    /** One key: its glass and its label; [down] while a finger is on it. */
    private fun drawKey(c: Canvas, k: Key, rect: RectF, down: Boolean) {
        val t = theme
        val radius = 9 * density
        val (normal, pressedColor) = when (k.kind) {
            KeyKind.CHAR, KeyKind.SPACE -> t.key to t.keyPressed
            KeyKind.NEW_LETTER -> t.newLetter to t.newLetterPressed
            KeyKind.ENTER -> KeyboardTheme.ACCENT to KeyboardTheme.ACCENT_PRESSED
            else -> t.func to t.funcPressed
        }
        // a pressed key gives a little, like a soft glass button
        val r = if (down) {
            val dx = rect.width() * 0.04f
            val dy = rect.height() * 0.05f
            pressedRect.set(rect.left + dx, rect.top + dy, rect.right - dx, rect.bottom - dy)
            pressedRect
        } else {
            rect
        }
        glass(c, r, radius, if (down) pressedColor else normal, !down)

        val color = if (k.kind == KeyKind.ENTER) 0xFFFFFFFF.toInt() else t.text
        val s = minOf(r.width(), r.height()) * 0.42f
        val cx = r.centerX()
        val cy = r.centerY()
        when (k.kind) {
            KeyKind.CHAR, KeyKind.NEW_LETTER -> {
                text(c, shiftText(k.label), cx, cy, textSize(k, r), color, k.kind == KeyKind.NEW_LETTER)
                val hint = k.alts.firstOrNull()
                if (k.kind == KeyKind.CHAR && hint != null && hint.isNotEmpty() && hint[0].isDigit()) {
                    text(c, hint, r.right - radius, r.top + 9 * density, 10 * sp, t.hint, false)
                }
            }
            KeyKind.SHIFT -> drawShift(c, cx, cy, s, color)
            KeyKind.BACKSPACE -> drawBackspace(c, cx, cy, s, color)
            KeyKind.ENTER -> drawEnter(c, cx, cy, s, color)
            KeyKind.SPACE -> text(c, spaceLabel, cx, cy, textSize(k, r), t.hint, false)
            KeyKind.GLOBE -> drawGlobe(c, cx, cy, s * 0.9f, color)
            else -> text(c, k.label, cx, cy, textSize(k, r), color, false)
        }
    }

    /** The background and every key at rest: the part that changes only with the layout. */
    private fun drawBase(c: Canvas) {
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        for ((k, rect) in keys) drawKey(c, k, rect, false)
    }

    override fun onDraw(c: Canvas) {
        val t = theme
        if (bgKey?.first != height || bgKey?.second !== t) {
            bgPaint.shader = LinearGradient(0f, 0f, 0f, height.toFloat(), t.backgroundTop, t.backgroundBottom, Shader.TileMode.CLAMP)
            bgKey = height to t
            baseDirty = true
        }
        if (keys.isEmpty()) computeKeys()

        // The keys at rest are recorded once and replayed by the GPU on every frame; a key press
        // only draws that key again. Without hardware drawing (tests) everything is drawn.
        if (Build.VERSION.SDK_INT >= 29 && c.isHardwareAccelerated) {
            val node = baseNode as RenderNode? ?: RenderNode("harfix-keys").also { baseNode = it }
            if (baseDirty || node.width != width || node.height != height) {
                node.setPosition(0, 0, width, height)
                val rc = node.beginRecording(width, height)
                try {
                    drawBase(rc)
                } finally {
                    node.endRecording()
                }
                baseDirty = false
            }
            c.drawRenderNode(node)
        } else {
            drawBase(c)
        }

        if (!gliding) {
            val pad = 3 * density
            for (touch in touches.values) {
                val r = touch.rect
                // cover the key at rest (and its shadow), then draw it pressed
                c.drawRect(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad, bgPaint)
                drawKey(c, touch.key, r, true)
            }
        }
        if (strip) drawStrip(c)

        if (showPreview) {
            for (touch in touches.values) {
                val k = touch.key
                if (touch.alts != null || touch.consumed || k.kind !in PREVIEW) continue
                val r = touch.rect
                val w = maxOf(r.width() * 1.25f, 44 * density)
                val h = r.height() * 1.25f
                val left = (r.centerX() - w / 2).coerceIn(0f, maxOf(0f, width - w))
                val bottom = r.top + 4 * density
                val box = RectF(left, maxOf(0f, bottom - h), left + w, bottom)
                val rr = 12 * density
                glass(c, box, rr, t.popup, true)
                text(c, shiftText(k.label), box.centerX(), box.centerY(), minOf(30 * sp, h * 0.55f), t.text, k.kind == KeyKind.NEW_LETTER)
            }
        }

        for (touch in touches.values) {
            val a = touch.alts ?: continue
            val rr = 12 * density
            glass(c, a.rect, rr, t.popup, true)
            a.items.forEachIndexed { i, item ->
                val cell = RectF(a.rect.left + i * a.cell, a.rect.top, a.rect.left + (i + 1) * a.cell, a.rect.bottom)
                if (i == a.selected) {
                    fill.color = KeyboardTheme.ACCENT
                    val inset = 3 * density
                    val cr = 8 * density
                    c.drawRoundRect(cell.left + inset, cell.top + inset, cell.right - inset, cell.bottom - inset, cr, cr, fill)
                }
                text(
                    c, item, cell.centerX(), cell.centerY(), minOf(22 * sp, cell.height() * 0.45f),
                    if (i == a.selected) 0xFFFFFFFF.toInt() else t.text, false,
                )
            }
        }
        drawTrail(c)
    }

    private fun drawShift(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        val pts = listOf(0f to -0.95f, 0.85f to -0.05f, 0.4f to -0.05f, 0.4f to 0.6f, -0.4f to 0.6f, -0.4f to -0.05f, -0.85f to -0.05f)
        path.reset()
        pts.forEachIndexed { i, (x, y) ->
            if (i == 0) path.moveTo(cx + x * s, cy + y * s) else path.lineTo(cx + x * s, cy + y * s)
        }
        path.close()
        if (shift == Shift.OFF) {
            stroke.color = color
            stroke.strokeWidth = s * 0.14f
            c.drawPath(path, stroke)
        } else {
            fill.color = color
            c.drawPath(path, fill)
            if (shift == Shift.LOCKED) {
                c.drawRect(cx - 0.45f * s, cy + 0.78f * s, cx + 0.45f * s, cy + 0.95f * s, fill)
            }
        }
    }

    private fun drawBackspace(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        stroke.color = color
        stroke.strokeWidth = s * 0.14f
        path.reset()
        path.moveTo(cx - s, cy)
        path.lineTo(cx - 0.45f * s, cy - 0.68f * s)
        path.lineTo(cx + 0.95f * s, cy - 0.68f * s)
        path.lineTo(cx + 0.95f * s, cy + 0.68f * s)
        path.lineTo(cx - 0.45f * s, cy + 0.68f * s)
        path.close()
        c.drawPath(path, stroke)
        val x1 = cx + 0.05f * s
        val x2 = cx + 0.6f * s
        val y1 = cy - 0.3f * s
        val y2 = cy + 0.3f * s
        c.drawLine(x1, y1, x2, y2, stroke)
        c.drawLine(x2, y1, x1, y2, stroke)
    }

    private fun drawEnter(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        stroke.color = color
        stroke.strokeWidth = s * 0.14f
        path.reset()
        when (enterIcon) {
            EnterIcon.NEWLINE -> {
                val x = cx + 0.8f * s
                val y = cy + 0.15f * s
                path.moveTo(x, cy - 0.65f * s)
                path.lineTo(x, y)
                path.lineTo(cx - 0.75f * s, y)
                c.drawPath(path, stroke)
                c.drawLine(cx - 0.75f * s, y, cx - 0.35f * s, cy - 0.25f * s, stroke)
                c.drawLine(cx - 0.75f * s, y, cx - 0.35f * s, cy + 0.55f * s, stroke)
            }
            EnterIcon.SEARCH -> {
                val o = 0.15f * s
                c.drawCircle(cx - o, cy - o, 0.5f * s, stroke)
                c.drawLine(cx + 0.22f * s, cy + 0.22f * s, cx + 0.8f * s, cy + 0.8f * s, stroke)
            }
            EnterIcon.SEND -> {
                path.moveTo(cx - 0.8f * s, cy - 0.75f * s)
                path.lineTo(cx + 0.9f * s, cy)
                path.lineTo(cx - 0.8f * s, cy + 0.75f * s)
                path.lineTo(cx - 0.45f * s, cy)
                path.close()
                fill.color = color
                c.drawPath(path, fill)
            }
            EnterIcon.DONE -> {
                path.moveTo(cx - 0.75f * s, cy)
                path.lineTo(cx - 0.2f * s, cy + 0.55f * s)
                path.lineTo(cx + 0.8f * s, cy - 0.55f * s)
                c.drawPath(path, stroke)
            }
            EnterIcon.GO, EnterIcon.NEXT -> {
                val x = cx + 0.8f * s
                c.drawLine(cx - 0.8f * s, cy, x, cy, stroke)
                c.drawLine(x, cy, cx + 0.3f * s, cy - 0.5f * s, stroke)
                c.drawLine(x, cy, cx + 0.3f * s, cy + 0.5f * s, stroke)
            }
        }
    }

    private fun drawGlobe(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        stroke.color = color
        stroke.strokeWidth = s * 0.11f
        val r = s * 0.9f
        c.drawCircle(cx, cy, r, stroke)
        val e = s * 0.4f
        c.drawOval(cx - e, cy - r, cx + e, cy + r, stroke)
        c.drawLine(cx - r, cy, cx + r, cy, stroke)
    }

    // --- touch -------------------------------------------------------------------------------

    private fun schedule(pointer: Int, delay: Long, r: Runnable) {
        timers.remove(pointer)?.let { handler.removeCallbacks(it) }
        timers[pointer] = r
        handler.postDelayed(r, delay)
    }

    private fun cancel(pointer: Int) {
        timers.remove(pointer)?.let { handler.removeCallbacks(it) }
    }

    private fun cancelAll() {
        for (r in timers.values) handler.removeCallbacks(r)
        timers.clear()
        touches.clear()
        glidePointer = -1
        gliding = false
        glidePath.clear()
    }

    /** Long press: alternative letters, or the keyboard picker on the space bar. */
    private fun armLongPress(pointer: Int, touch: Touch) {
        val k = touch.key
        if (k.kind == KeyKind.SPACE) {
            schedule(pointer, 500) {
                touch.consumed = true
                listener?.onSpaceLongPress()
            }
        } else if (k.kind == KeyKind.CHAR && k.alts.isNotEmpty()) {
            schedule(pointer, 380) { openAlts(touch) }
        }
    }

    private fun openAlts(touch: Touch) {
        val items = touch.key.alts.map { shiftText(it) }
        val r = touch.rect
        val cell = maxOf(r.width(), 34 * density)
        val total = items.size * cell
        val left = (r.centerX() - total / 2).coerceIn(padX, maxOf(padX, width - padX - total))
        val top = maxOf(0f, r.top - keyHeight() - 4 * density)
        touch.alts = Alts(items, RectF(left, top, left + total, top + keyHeight()), cell)
        listener?.onPress(KeyKind.CHAR)
        invalidate()
    }

    /** Releases a key: types its text or runs its action. */
    private fun fire(touch: Touch) {
        val k = touch.key
        if (k.kind == KeyKind.CHAR || k.kind == KeyKind.NEW_LETTER) listener?.onText(shiftText(k.text))
        else listener?.onKey(k.kind)
    }

    private val backspaceRepeat = object : Runnable {
        var pointer = 0
        override fun run() {
            listener?.onKey(KeyKind.BACKSPACE)
            timers[pointer] = this
            handler.postDelayed(this, 50)
        }
    }

    private fun endGlide() {
        glidePointer = -1
        gliding = false
        glidePath.clear()
    }

    /** Glide starts once the finger has gone more than a key's width from where it landed. */
    private fun followGlide(e: MotionEvent, i: Int, t: Touch) {
        for (h in 0 until e.historySize) glidePath.add(Glide.P(e.getHistoricalX(i, h), e.getHistoricalY(i, h)))
        val x = e.getX(i)
        val y = e.getY(i)
        glidePath.add(Glide.P(x, y))
        if (!gliding) {
            val dx = x - downX
            val dy = y - downY
            val far = letterWidth * 1.2f
            if (dx * dx + dy * dy > far * far) {
                gliding = true
                cancel(glidePointer)
                t.consumed = true
                t.alts = null
            }
        }
        if (gliding) invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                val id = e.getPointerId(i)
                if (gliding) return true // one word at a time
                if (glidePointer >= 0) glidePointer = -1 // a second finger: two-thumb typing, not glide
                // the suggestion strip
                if (strip && e.getY(i) < padTop + stripHeight - 2 * density && touches.isEmpty()) {
                    stripPointer = id
                    stripCell = stripCellAt(e.getX(i))
                    listener?.onPress(KeyKind.CHAR)
                    invalidate()
                    return true
                }
                // fast two-thumb typing: a new finger releases the keys still held
                for ((tid, t) in touches) {
                    if (!t.consumed && t.alts == null && t.key.kind in FIRE_ON_NEXT) {
                        cancel(tid)
                        fire(t)
                        t.consumed = true
                    }
                }
                val kr = keyAt(e.getX(i), e.getY(i)) ?: return true
                val touch = Touch(kr.first, kr.second)
                if (glideOn && touches.isEmpty() && kr.first.kind == KeyKind.CHAR && layout.name == "latin" && letterWidth > 0f) {
                    glidePointer = id
                    gliding = false
                    glidePath.clear()
                    downX = e.getX(i)
                    downY = e.getY(i)
                    glidePath.add(Glide.P(downX, downY))
                }
                touches[id] = touch
                listener?.onPress(touch.key.kind)
                when (touch.key.kind) {
                    KeyKind.SHIFT -> {
                        listener?.onKey(KeyKind.SHIFT)
                        touch.consumed = true
                    }
                    KeyKind.BACKSPACE -> {
                        listener?.onKey(KeyKind.BACKSPACE)
                        touch.consumed = true
                        backspaceRepeat.pointer = id
                        schedule(id, 400, backspaceRepeat)
                    }
                    else -> armLongPress(id, touch)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.pointerCount) {
                    val id = e.getPointerId(i)
                    if (id == stripPointer) {
                        val cell = stripCellAt(e.getX(i))
                        if (cell != stripCell) {
                            stripCell = cell
                            invalidate()
                        }
                        continue
                    }
                    val t = touches[id] ?: continue
                    if (id == glidePointer && t.alts == null) {
                        followGlide(e, i, t)
                        if (gliding) continue
                    }
                    val a = t.alts
                    if (a != null) {
                        a.selected = ((e.getX(i) - a.rect.left) / a.cell).toInt().coerceIn(0, a.items.size - 1)
                        invalidate()
                    } else if (!t.consumed && t.key.kind in SLIDE) {
                        val kr = keyAt(e.getX(i), e.getY(i)) ?: continue
                        if (kr.first != t.key && kr.first.kind in SLIDE) {
                            cancel(id)
                            t.key = kr.first
                            t.rect = kr.second
                            armLongPress(id, t)
                            invalidate()
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val i = e.actionIndex
                val id = e.getPointerId(i)
                if (id == stripPointer) {
                    val cell = stripCellAt(e.getX(i))
                    val word = stripCells().getOrNull(cell)?.first.orEmpty()
                    stripPointer = -1
                    stripCell = -1
                    if (word.isNotEmpty()) listener?.onSuggestion(word)
                    invalidate()
                    return true
                }
                cancel(id)
                val t = touches.remove(id)
                if (id == glidePointer) {
                    val path = ArrayList(glidePath)
                    val wasGliding = gliding
                    endGlide()
                    if (wasGliding) {
                        listener?.onGlide(path, letterCenters(), letterWidth)
                        invalidate()
                        return true
                    }
                }
                if (t != null) {
                    val a = t.alts
                    if (a != null) listener?.onText(a.items[a.selected])
                    else if (!t.consumed) fire(t)
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelAll()
                endGlide()
                stripPointer = -1
                stripCell = -1
                invalidate()
            }
        }
        return true
    }

    companion object {
        private val FIRE_ON_NEXT = setOf(KeyKind.CHAR, KeyKind.NEW_LETTER, KeyKind.SPACE)
        private val SLIDE = setOf(KeyKind.CHAR, KeyKind.NEW_LETTER)
        private val PREVIEW = SLIDE
    }
}
