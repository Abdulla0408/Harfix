package uz.harfix.app.keyboard

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Draws the keyboard to PNGs (build/keyboard-shots) to check it by eye; no emulator needed. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class KeyboardShotTest {
    private fun shot(
        name: String, layout: Layout, shift: Shift = Shift.OFF, enter: EnterIcon = EnterIcon.NEWLINE,
        words: List<String>? = null, toolbar: Boolean = false, theme: Int = 0, photo: Bitmap? = null,
    ) {
        val ctx = RuntimeEnvironment.getApplication()
        val v = KeyboardView(ctx)
        if (theme != 0) v.setLook(0, 1f, theme)
        if (photo != null) v.setPhoto(photo)
        v.setLayout(layout)
        v.setShift(shift)
        v.setEnterIcon(enter)
        v.setSpaceLabel(layout.spaceLabel + if (layout.name == "latin") " ⚡" else "")
        if (words != null || toolbar) {
            v.setStrip(true)
            v.setSuggestions(words ?: emptyList())
        }
        val w = ctx.resources.displayMetrics.widthPixels
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, v.measuredWidth, v.measuredHeight)
        val bmp = Bitmap.createBitmap(v.measuredWidth, v.measuredHeight, Bitmap.Config.ARGB_8888)
        v.draw(Canvas(bmp))
        val dir = File("build/keyboard-shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun light() {
        val latin = Layouts.latin("Рус", true)
        shot("1-latin", latin)
        shot("1b-latin-suggestions", latin, words = listOf("Toşkent", "toşkentlik", "Toşkentda"))
        shot("1c-latin-toolbar", latin, toolbar = true)
        shot("10-menu", Layouts.menu(), toolbar = true)
        shot("11-edit", Layouts.edit(true), toolbar = true)
        shot("12-clipboard", Layouts.clipboard(listOf("Salom, qalaysiz?", "https://harfix.uz", "Ertaga soat 10 da uçraşamiz")), toolbar = true)
        shot("2-latin-shift", latin, Shift.LOCKED, EnterIcon.SEND)
        shot("3-cyrillic", Layouts.cyrillic("Lot", false), enter = EnterIcon.SEARCH)
        shot("4-russian", Layouts.russian("Ўзб", false), enter = EnterIcon.DONE)
        shot("5-symbols", Layouts.symbols(latin, true), enter = EnterIcon.GO)
        shot("6-more", Layouts.more(latin, false))
        shot("7-numbers", Layouts.numbers(false))
        shot("8-phone", Layouts.numbers(true))
        shot("13-numpad", Layouts.numpad(), toolbar = true)
    }

    @Test
    fun vividThemes() {
        val latin = Layouts.latin("Рус", true)
        val add = listOf("uçun", Suggest.ADD + "ucun", "uçinçi")
        shot("14-lojuvard", latin, words = add, theme = 3)
        shot("14b-lojuvard-numpad", Layouts.numpad(), toolbar = true, theme = 3)
        shot("15-shafaq", latin, toolbar = true, theme = 4)
        shot("16-tun", Layouts.menu("Tun"), toolbar = true, theme = 5)
        shot("17-photo", latin, toolbar = true, theme = 6, photo = picture())
    }

    /** A made-up "photo": a sunset gradient with a few soft circles. */
    private fun picture(): Bitmap {
        val b = Bitmap.createBitmap(900, 1200, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        p.shader = android.graphics.LinearGradient(0f, 0f, 900f, 1200f, 0xFF1E3C72.toInt(), 0xFFFF7E5F.toInt(), android.graphics.Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 900f, 1200f, p)
        p.shader = null
        p.color = 0x55FFFFFF
        c.drawCircle(220f, 700f, 160f, p)
        c.drawCircle(640f, 520f, 240f, p)
        p.color = 0x55FFD36B
        c.drawCircle(480f, 820f, 120f, p)
        return b
    }

    @Test
    @Config(qualifiers = "+night")
    fun dark() {
        shot("9-latin-dark", Layouts.latin("Рус", true))
        shot("9b-latin-dark-suggestions", Layouts.latin("Рус", true), words = listOf("uçun", "ucun", "uçinçi"))
    }
}
