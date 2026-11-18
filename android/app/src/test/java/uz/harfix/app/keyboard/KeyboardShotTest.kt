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
    private fun shot(name: String, layout: Layout, shift: Shift = Shift.OFF, enter: EnterIcon = EnterIcon.NEWLINE, words: List<String>? = null) {
        val ctx = RuntimeEnvironment.getApplication()
        val v = KeyboardView(ctx)
        v.setLayout(layout)
        v.setShift(shift)
        v.setEnterIcon(enter)
        v.setSpaceLabel(layout.spaceLabel + if (layout.name == "latin") " ⚡" else "")
        if (words != null) {
            v.setStrip(true)
            v.setSuggestions(words)
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
        shot("2-latin-shift", latin, Shift.LOCKED, EnterIcon.SEND)
        shot("3-cyrillic", Layouts.cyrillic("Lot", false), enter = EnterIcon.SEARCH)
        shot("4-russian", Layouts.russian("Ўзб", false), enter = EnterIcon.DONE)
        shot("5-symbols", Layouts.symbols(latin, true), enter = EnterIcon.GO)
        shot("6-more", Layouts.more(latin, false))
        shot("7-numbers", Layouts.numbers(false))
        shot("8-phone", Layouts.numbers(true))
    }

    @Test
    @Config(qualifiers = "+night")
    fun dark() {
        shot("9-latin-dark", Layouts.latin("Рус", true))
        shot("9b-latin-dark-suggestions", Layouts.latin("Рус", true), words = listOf("uçun", "ucun", "uçinçi"))
    }
}
