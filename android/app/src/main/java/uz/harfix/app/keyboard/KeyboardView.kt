package uz.harfix.app.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import java.util.Locale

/** Light and dark key colors. */
class KeyboardTheme(dark: Boolean) {
    val background = if (dark) 0xFF15181D.toInt() else 0xFFE3E7ED.toInt()
    val key = if (dark) 0xFF3A404A.toInt() else 0xFFFFFFFF.toInt()
    val keyPressed = if (dark) 0xFF525A67.toInt() else 0xFFD3DAE3.toInt()
    val func = if (dark) 0xFF262B33.toInt() else 0xFFC6CED9.toInt()
    val funcPressed = if (dark) 0xFF363C46.toInt() else 0xFFAEB8C6.toInt()
    val newLetter = if (dark) 0xFF4A3E1C.toInt() else 0xFFFFE7A8.toInt()
    val newLetterPressed = if (dark) 0xFF66552A.toInt() else 0xFFFFD36B.toInt()
    val text = if (dark) 0xFFE8ECF1.toInt() else 0xFF10141A.toInt()
    val hint = if (dark) 0xFF8B96A3.toInt() else 0xFF6D7885.toInt()
    val shadow = if (dark) 0 else 0x26000000
    val popup = if (dark) 0xFF2C323B.toInt() else 0xFFFFFFFF.toInt()
    val popupBorder = if (dark) 0xFF4A515C.toInt() else 0xFFC6CED9.toInt()

    companion object {
        const val ACCENT = 0xFF1C6EF2.toInt()
        const val ACCENT_PRESSED = 0xFF1557C4.toInt()
    }
}

/** What the keyboard view reports to the input method service. */
interface KeyboardListener {
    fun onText(text: String)
    fun onKey(kind: KeyKind)
    fun onPress(kind: KeyKind)
    fun onSpaceLongPress()
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

    private fun isDark() =
        resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private val padTop get() = 6 * density
    private val padX get() = 3 * density
    private val padBottom get() = 4 * density + navInset

    /** Normal key height: 54dp, 40dp in landscape. */
    private fun keyHeight(): Float =
        (if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) 40f else 54f) * density

    fun setLayout(l: Layout) {
        layout = l
        cancelAll()
        requestLayout()
        computeKeys()
        invalidate()
    }

    fun setShift(s: Shift) {
        shift = s
        invalidate()
    }

    fun setEnterIcon(icon: EnterIcon) {
        enterIcon = icon
        invalidate()
    }

    fun setShowPreview(show: Boolean) {
        showPreview = show
    }

    fun setSpaceLabel(text: String) {
        spaceLabel = text
        invalidate()
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
        var y = padTop
        for (row in layout.rows) {
            val h = keyHeight() * row.height
            val unit = (width - padX * 2) / row.keys.sumOf { it.width.toDouble() }.toFloat()
            var x = padX
            for (k in row.keys) {
                val w = k.width * unit
                if (k.kind != KeyKind.SPACER) {
                    keys.add(k to RectF(x + gapX / 2, y + gapY / 2, x + w - gapX / 2, y + h - gapY / 2))
                }
                x += w
            }
            y += h
        }
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
        setMeasuredDimension(w, (padTop + rows + padBottom).toInt())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = computeKeys()

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

    override fun onDraw(c: Canvas) {
        val t = theme
        c.drawColor(t.background)
        if (keys.isEmpty()) computeKeys()
        val pressed = touches.values.map { it.key }.toSet()
        val radius = 7 * density

        for ((k, r) in keys) {
            val (normal, down) = when (k.kind) {
                KeyKind.CHAR, KeyKind.SPACE -> t.key to t.keyPressed
                KeyKind.NEW_LETTER -> t.newLetter to t.newLetterPressed
                KeyKind.ENTER -> KeyboardTheme.ACCENT to KeyboardTheme.ACCENT_PRESSED
                else -> t.func to t.funcPressed
            }
            if (t.shadow != 0) {
                fill.color = t.shadow
                val off = 1.2f * density
                c.drawRoundRect(r.left, r.top + off, r.right, r.bottom + off, radius, radius, fill)
            }
            fill.color = if (k in pressed) down else normal
            c.drawRoundRect(r, radius, radius, fill)

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
                val rr = 9 * density
                fill.color = t.popupBorder
                c.drawRoundRect(box.left - 1, box.top - 1, box.right + 1, box.bottom + 1, rr, rr, fill)
                fill.color = t.popup
                c.drawRoundRect(box, rr, rr, fill)
                text(c, shiftText(k.label), box.centerX(), box.centerY(), minOf(30 * sp, h * 0.55f), t.text, k.kind == KeyKind.NEW_LETTER)
            }
        }

        for (touch in touches.values) {
            val a = touch.alts ?: continue
            val rr = 10 * density
            fill.color = t.popupBorder
            c.drawRoundRect(a.rect.left - 1, a.rect.top - 1, a.rect.right + 1, a.rect.bottom + 1, rr, rr, fill)
            fill.color = t.popup
            c.drawRoundRect(a.rect, rr, rr, fill)
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

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                // fast two-thumb typing: a new finger releases the keys still held
                for ((id, t) in touches) {
                    if (!t.consumed && t.alts == null && t.key.kind in FIRE_ON_NEXT) {
                        cancel(id)
                        fire(t)
                        t.consumed = true
                    }
                }
                val i = e.actionIndex
                val id = e.getPointerId(i)
                val kr = keyAt(e.getX(i), e.getY(i)) ?: return true
                val touch = Touch(kr.first, kr.second)
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
                    val t = touches[id] ?: continue
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
                val id = e.getPointerId(e.actionIndex)
                cancel(id)
                val t = touches.remove(id)
                if (t != null) {
                    val a = t.alts
                    if (a != null) listener?.onText(a.items[a.selected])
                    else if (!t.consumed) fire(t)
                }
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelAll()
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
