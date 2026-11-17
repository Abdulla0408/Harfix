package uz.harfix.app.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import uz.harfix.app.Online
import kotlin.math.abs

/**
 * The Harfix keyboard: new-alphabet letters on top (ö ğ ş ç), Latin / Uzbek Cyrillic /
 * Russian layouts, and real-time conversion (o' → ö, sh → ş ...) that Backspace undoes.
 * Runs in its own process (":keyboard"), so settings are read with MODE_MULTI_PROCESS.
 */
class HarfixKeyboard : InputMethodService(), KeyboardListener {
    private var view: KeyboardView? = null
    private val undo = Live.Undo()
    private var shift = Shift.OFF
    private var lastShiftTap = 0L
    /** Show the 🌐 key (when the system asks keyboards to offer switching). */
    private var globe = false
    private var languages: List<String> = listOf("latin")
    private var letters: Layout = Layouts.latin(null, false)
    private var current: Layout = letters
    /** A normal text field: not a password, e-mail or link field. */
    private var liveField = true
    private var password = false
    /** No text is selected. */
    private var noSelection = true
    private var live = true
    private var sound = false
    private var vibrate = true
    private val audio by lazy { getSystemService(AudioManager::class.java) }

    @Suppress("DEPRECATION")
    private fun prefs(): SharedPreferences = getSharedPreferences("harfix", Context.MODE_MULTI_PROCESS)

    private fun lettersLayout(name: String): Layout {
        val next = if (languages.size > 1) {
            Layouts.SHORT[languages[(languages.indexOf(name) + 1) % languages.size]]
        } else {
            null
        }
        return Layouts.letters(name, next, globe)
    }

    override fun onCreateInputView(): View {
        val v = KeyboardView(this)
        v.listener = this
        v.setLayout(current)
        view = v
        return v
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        Online.touch(this)
        globe = if (Build.VERSION.SDK_INT >= 28) {
            shouldOfferSwitchingToNextInputMethod()
        } else {
            val token = window.window?.attributes?.token
            @Suppress("DEPRECATION")
            token != null && getSystemService(InputMethodManager::class.java).shouldOfferSwitchingToNextInputMethod(token)
        }

        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        password = (cls == InputType.TYPE_CLASS_TEXT && variation in PASSWORD_VARIATIONS) ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        liveField = cls == InputType.TYPE_CLASS_TEXT && !password && variation !in ADDRESS_VARIATIONS

        val p = prefs()
        live = p.getBoolean("live", true)
        sound = p.getBoolean("sound", false)
        vibrate = p.getBoolean("vibrate", true)
        noSelection = info.initialSelStart == info.initialSelEnd

        languages = buildList {
            add("latin")
            if (p.getBoolean("lang_ru", true)) add("russian")
            if (p.getBoolean("lang_uz_cyrl", true)) add("cyrillic")
        }
        var saved = p.getString("layout", null) ?: if (p.getBoolean("cyrillic", false)) "cyrillic" else "latin"
        if (saved !in languages) saved = "latin"
        letters = lettersLayout(saved)
        current = when (cls) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> Layouts.numbers(false)
            InputType.TYPE_CLASS_PHONE -> Layouts.numbers(true)
            else -> letters
        }
        shift = Shift.OFF
        undo.pending = null
        view?.apply {
            setLayout(current)
            setShift(Shift.OFF)
            setEnterIcon(enterIcon(info.imeOptions))
            setShowPreview(!password)
        }
        updateSpaceLabel()
        updateAutoCaps()
    }

    private fun enterIcon(options: Int): EnterIcon {
        if (options and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return EnterIcon.NEWLINE
        return when (options and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> EnterIcon.GO
            EditorInfo.IME_ACTION_SEARCH -> EnterIcon.SEARCH
            EditorInfo.IME_ACTION_SEND -> EnterIcon.SEND
            EditorInfo.IME_ACTION_NEXT, EditorInfo.IME_ACTION_PREVIOUS -> EnterIcon.NEXT
            EditorInfo.IME_ACTION_DONE -> EnterIcon.DONE
            else -> EnterIcon.NEWLINE
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        undo.pending = null
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        noSelection = newSelStart == newSelEnd
        // typing moves the cursor by a character or two; anything else ends the undo chance
        if (noSelection && abs(newSelStart - oldSelStart) <= 2) return
        undo.pending = null
        updateAutoCaps()
    }

    // --- KeyboardListener ----------------------------------------------------------------------

    override fun onPress(kind: KeyKind) {
        val v = view ?: return
        if (vibrate) v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        if (sound) {
            val fx = when (kind) {
                KeyKind.BACKSPACE -> AudioManager.FX_KEYPRESS_DELETE
                KeyKind.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                KeyKind.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            audio.playSoundEffect(fx, -1f)
        }
    }

    override fun onText(text: String) {
        val ic = currentInputConnection ?: return
        val c = text.singleOrNull()
        var swap: Swap? = null
        if (c != null && liveField && live && noSelection && Live.isTrigger(c)) {
            val before = ic.getTextBeforeCursor(48, 0) ?: ""
            swap = Live.swap(before, c)
            undo.pending = swap?.let { before.last().toString() + c to it.text }
        } else {
            undo.pending = null
        }
        if (swap != null) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(swap.delete, 0)
            ic.commitText(swap.text, 1)
            ic.endBatchEdit()
        } else {
            ic.commitText(text, 1)
        }
        if (shift == Shift.ON) setShift(Shift.OFF)
        if (c == null || !c.isLetterOrDigit()) updateAutoCaps()
    }

    override fun onSpaceLongPress() {
        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
    }

    override fun onKey(kind: KeyKind) {
        when (kind) {
            KeyKind.SHIFT -> {
                val now = SystemClock.uptimeMillis()
                val next = when (shift) {
                    Shift.OFF -> Shift.ON
                    Shift.ON -> if (now - lastShiftTap < 350) Shift.LOCKED else Shift.OFF
                    Shift.LOCKED -> Shift.OFF
                }
                setShift(next)
                lastShiftTap = now
            }
            KeyKind.BACKSPACE -> {
                val ic = currentInputConnection ?: return
                if (undo.pending != null && noSelection) {
                    val s = undo.undo(ic.getTextBeforeCursor(8, 0) ?: "")
                    if (s != null) {
                        ic.beginBatchEdit()
                        ic.deleteSurroundingText(s.delete, 0)
                        ic.commitText(s.text, 1)
                        ic.endBatchEdit()
                        return
                    }
                }
                undo.pending = null
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                updateAutoCaps()
            }
            KeyKind.ENTER -> {
                val info = currentInputEditorInfo ?: return
                val ic = currentInputConnection ?: return
                undo.pending = null
                val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
                val noAction = info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
                val multiLine = info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
                if (noAction || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
                    if (multiLine) ic.commitText("\n", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                } else {
                    ic.performEditorAction(action)
                }
            }
            KeyKind.SPACE -> {
                undo.pending = null
                currentInputConnection?.commitText(" ", 1)
                if (shift == Shift.ON) setShift(Shift.OFF)
                updateAutoCaps()
            }
            KeyKind.SYMBOLS -> show(Layouts.symbols(letters, globe))
            KeyKind.MORE_SYMBOLS -> show(Layouts.more(letters, globe))
            KeyKind.LETTERS -> show(letters)
            KeyKind.LANG -> {
                val next = languages[(languages.indexOf(letters.name) + 1) % languages.size]
                prefs().edit().putString("layout", next).apply()
                letters = lettersLayout(next)
                show(letters)
                updateSpaceLabel()
            }
            KeyKind.GLOBE -> {
                if (Build.VERSION.SDK_INT >= 28) {
                    switchToNextInputMethod(false)
                } else {
                    val token = window.window?.attributes?.token ?: return
                    @Suppress("DEPRECATION")
                    getSystemService(InputMethodManager::class.java).switchToNextInputMethod(token, false)
                }
            }
            else -> {}
        }
    }

    // --- state ---------------------------------------------------------------------------------

    private fun setShift(s: Shift) {
        shift = s
        view?.setShift(s)
    }

    private fun show(layout: Layout) {
        current = layout
        view?.setLayout(layout)
        if (layout.autoCaps) updateAutoCaps() else setShift(Shift.OFF)
    }

    /** Capital letter at the start of a sentence, as the text field asks. */
    private fun updateAutoCaps() {
        if (shift == Shift.LOCKED || !current.autoCaps) return
        val info = currentInputEditorInfo ?: return
        val ic = currentInputConnection ?: return
        setShift(if (ic.getCursorCapsMode(info.inputType) != 0) Shift.ON else Shift.OFF)
    }

    /** "Lotin ⚡" when real-time conversion is on. */
    private fun updateSpaceLabel() {
        val lightning = liveField && live && letters.name == "latin"
        view?.setSpaceLabel(letters.spaceLabel + if (lightning) " ⚡" else "")
    }

    companion object {
        private val PASSWORD_VARIATIONS = setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
        )
        private val ADDRESS_VARIATIONS = setOf(
            InputType.TYPE_TEXT_VARIATION_URI,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        )
    }
}
