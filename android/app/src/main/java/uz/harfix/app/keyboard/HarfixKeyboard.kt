package uz.harfix.app.keyboard

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import uz.harfix.app.Converter
import uz.harfix.app.Online
import java.io.File
import java.util.Locale
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

    // dictionary: suggestions, autocorrect, glide typing (Latin letters, ordinary text fields)
    @Volatile private var dict: Dictionary? = null
    private lateinit var user: UserWords
    private var autocorrect = true
    private var glide = true
    private var learn = true
    /** Ordinary text field with the new alphabet on: the strip may show (on the Latin letters). */
    private var stripAllowed = false
    private var stripOn = false
    /** The last autocorrection (typed word, what it became), so Backspace can undo it. */
    private var autoUndo: Pair<String, String>? = null
    /** Words the last glide found: the first was typed, the rest stay in the strip. */
    private var glided: List<String>? = null
    /** The last suggestions and the word they are for (main thread only). */
    private var cached: Pair<String, Suggest.Result>? = null
    /** Dictionary work runs here, never on the main thread, so typing stays smooth. */
    private val worker = HandlerThread("harfix-suggest").apply { start() }
    private val workerHandler = Handler(worker.looper)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var requestId = 0
    /** Suggestions follow a key press a moment later, after its frame is drawn. */
    private val refreshLater = Runnable { refreshSuggestions() }

    // toolbar and panels
    /** Texts copied while the keyboard runs, newest first; kept in memory only, never saved or sent. */
    private val clips = ArrayList<String>()
    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { rememberClip() }
    /** Text-editing panel: the arrows extend the selection. */
    private var selecting = false
    private var oneHand = 0
    private var sizeStep = 0
    private var themeMode = 0

    override fun onCreate() {
        super.onCreate()
        try {
            getSystemService(ClipboardManager::class.java)?.addPrimaryClipChangedListener(clipListener)
        } catch (_: Exception) {
        }
        user = UserWords(File(filesDir, UserWords.FILE), File(filesDir, UserWords.DICT_FILE))
        workerHandler.post {
            user.load()
            user.loadDictionary()
            try {
                dict = assets.open("dict/uz.txt").use { main ->
                    assets.open("dict/uz-extra.txt").use { extra -> Dictionary.load(main, extra) }
                }
            } catch (_: Exception) {
            }
            mainHandler.post { refreshSuggestions() }
        }
    }

    override fun onDestroy() {
        try {
            getSystemService(ClipboardManager::class.java)?.removePrimaryClipChangedListener(clipListener)
        } catch (_: Exception) {
        }
        worker.quitSafely()
        super.onDestroy()
    }

    /** Suggestions for [word]: from the cache, or worked out now (only when a word ends). */
    private fun resultFor(d: Dictionary, word: String): Suggest.Result {
        cached?.let { (w, r) -> if (w == word) return r }
        val r = Suggest.forWord(d, if (learn) user else null, word)
        cached = word to r
        return r
    }

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
        v.setLook(oneHand, SIZES[sizeStep], themeMode)
        v.setLayout(current)
        view = v
        photoStamp = 0L // the new view has no picture yet
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
        autocorrect = p.getBoolean("autocorrect", true)
        glide = p.getBoolean("glide", true)
        learn = p.getBoolean("learn", true)
        if (!learn) user.clear()
        oneHand = p.getInt("one_hand", 0)
        sizeStep = p.getInt("size", 0)
        themeMode = p.getInt("theme", 0).coerceIn(0, KeyboardTheme.NAMES.size - 1)
        selecting = false
        workerHandler.post { user.loadDictionary() }
        loadPhoto()
        stripAllowed = liveField && live && (autocorrect || glide)
        autoUndo = null
        glided = null
        cached = null
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
            setLook(oneHand, SIZES[sizeStep], themeMode)
            setLayout(current)
            setShift(Shift.OFF)
            setEnterIcon(enterIcon(info.imeOptions))
            setShowPreview(!password)
        }
        updateStrip()
        updateSpaceLabel()
        updateAutoCaps()
        refreshSuggestions()
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
        autoUndo = null
        user.save()
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        noSelection = newSelStart == newSelEnd
        mainHandler.removeCallbacks(refreshLater)
        mainHandler.postDelayed(refreshLater, 30)
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
        glided = null
        if (c != null && isWordEnd(c) && finishWord(ic, text)) {
            undo.pending = null
            if (shift == Shift.ON) setShift(Shift.OFF)
            updateAutoCaps()
            return
        }
        autoUndo = null
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
                glided = null
                if (undoAutocorrect(ic)) return
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
                glided = null
                finishWord(ic, "")
                autoUndo = null
                val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
                val noAction = info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
                val multiLine = info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
                if (noAction || action == EditorInfo.IME_ACTION_NONE || action == EditorInfo.IME_ACTION_UNSPECIFIED) {
                    if (multiLine) ic.commitText("\n", 1) else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                } else {
                    ic.performEditorAction(action)
                }
                // a new line starts a sentence: capital letter, as after ". "
                updateAutoCaps()
            }
            KeyKind.SPACE -> {
                undo.pending = null
                glided = null
                val ic = currentInputConnection ?: return
                if (!finishWord(ic, " ")) ic.commitText(" ", 1)
                if (shift == Shift.ON) setShift(Shift.OFF)
                updateAutoCaps()
            }
            KeyKind.SYMBOLS -> show(Layouts.symbols(letters, globe))
            KeyKind.MORE_SYMBOLS -> show(Layouts.more(letters, globe))
            KeyKind.NUMPAD -> show(Layouts.numpad())
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
        updateStrip()
        if (layout.autoCaps) updateAutoCaps() else setShift(Shift.OFF)
        refreshSuggestions()
    }

    // --- dictionary --------------------------------------------------------------------------

    /** The strip and glide work on the Latin letters in ordinary text fields with the new alphabet on. */
    private fun updateStrip() {
        val latin = current === letters && letters.name == "latin"
        stripOn = stripAllowed && latin
        view?.setStrip(current.name != "numbers")
        view?.setGlide(glide && stripOn)
        if (!stripOn) view?.setSuggestions(emptyList())
    }

    /** The word around the cursor: the part before it and, when the cursor is inside it, the rest. */
    private fun wordAt(ic: InputConnection): Pair<String, String> {
        val before = Suggest.wordBefore(ic.getTextBeforeCursor(40, 0) ?: "")
        val after = ic.getTextAfterCursor(24, 0) ?: ""
        var end = 0
        while (end < after.length && (after[end].isLetter() || Converter.APOSTROPHES.indexOf(after[end]) >= 0)) end++
        return before to after.substring(0, end)
    }

    private fun refreshSuggestions() {
        val v = view ?: return
        if (!stripOn) return
        val d = dict
        val ic = currentInputConnection
        if (d == null || ic == null) {
            v.setSuggestions(emptyList())
            return
        }
        val (head, tail) = wordAt(ic)
        val word = head + tail
        val g = glided
        if (g != null && tail.isEmpty() && word == g.first()) {
            v.setSuggestions(g.take(3))
            return
        }
        if (word.isEmpty()) {
            requestId++
            v.setSuggestions(emptyList())
            return
        }
        cached?.let { (w, r) ->
            if (w == word) {
                v.setSuggestions(r.words)
                return
            }
        }
        // only the newest word matters: older requests are dropped
        val id = ++requestId
        val u = if (learn) user else null
        workerHandler.removeCallbacksAndMessages(SUGGEST)
        workerHandler.postAtTime({
            val r = Suggest.forWord(d, u, word)
            mainHandler.post {
                if (id != requestId) return@post
                cached = word to r
                view?.setSuggestions(r.words)
            }
        }, SUGGEST, SystemClock.uptimeMillis())
    }

    /** Space, punctuation and the like end a word; apostrophes and hyphens are inside words. */
    private fun isWordEnd(c: Char) =
        !c.isLetterOrDigit() && !c.isWhitespace() && c != '-' && Converter.APOSTROPHES.indexOf(c) < 0

    /**
     * A word ends with [end] (" ", punctuation, or "" before Enter): autocorrect it when sure, else
     * learn it if the dictionary does not know it. Returns true if [end] was typed here.
     */
    private fun finishWord(ic: InputConnection, end: String): Boolean {
        autoUndo = null
        if (!stripOn || !noSelection) return false
        val d = dict ?: return false
        val (word, tail) = wordAt(ic)
        if (word.length < 2 || tail.isNotEmpty()) return false // editing inside a word: leave it
        val fix = if (autocorrect) resultFor(d, word).autocorrect else null
        if (fix != null && fix != word) {
            ic.beginBatchEdit()
            ic.deleteSurroundingText(word.length, 0)
            ic.commitText(fix + end, 1)
            ic.endBatchEdit()
            autoUndo = word to fix + end
            return end.isNotEmpty()
        }
        if (learn && d.lookup(word) == null) user.learn(word)
        return false
    }

    /** Backspace right after an autocorrection: the typed word comes back and is learned. */
    private fun undoAutocorrect(ic: InputConnection): Boolean {
        val (typed, became) = autoUndo ?: return false
        autoUndo = null
        if (!noSelection || ic.getTextBeforeCursor(became.length, 0)?.toString() != became) return false
        ic.beginBatchEdit()
        ic.deleteSurroundingText(became.length, 0)
        ic.commitText(typed, 1)
        ic.endBatchEdit()
        if (learn) user.learn(typed, 2)
        cached = null
        return true
    }

    override fun onSuggestion(text: String) {
        val ic = currentInputConnection ?: return
        if (text.startsWith(Suggest.ADD)) {
            val word = text.removePrefix(Suggest.ADD)
            if (user.add(word)) Toast.makeText(this, "«$word» luğatga qöşildi", Toast.LENGTH_SHORT).show()
            cached = null
            onSuggestion(word)
            return
        }
        val (head, tail) = wordAt(ic)
        ic.beginBatchEdit()
        if (head.isNotEmpty() || tail.isNotEmpty()) ic.deleteSurroundingText(head.length, tail.length)
        ic.commitText(if (tail.isEmpty()) "$text " else text, 1)
        ic.endBatchEdit()
        undo.pending = null
        autoUndo = null
        glided = null
        if (learn && dict?.lookup(text) == null) user.learn(text, 2)
        cached = null
        if (shift == Shift.ON) setShift(Shift.OFF)
        updateAutoCaps()
    }

    // --- toolbar and panels ----------------------------------------------------------------------

    private fun rememberClip() {
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        val clip = try {
            cm.primaryClip
        } catch (_: Exception) {
            null
        } ?: return
        // passwords copied from password managers are marked sensitive: never keep them
        if (Build.VERSION.SDK_INT >= 33 &&
            clip.description?.extras?.getBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE) == true
        ) return
        val text = clip.getItemAt(0)?.coerceToText(this)?.toString()?.takeIf { it.isNotBlank() } ?: return
        clips.remove(text)
        clips.add(0, text.take(2000))
        while (clips.size > 10) clips.removeAt(clips.size - 1)
    }

    private fun savePref(key: String, value: Int) = prefs().edit().putInt(key, value).apply()

    /** The background picture the app saves ("Klaviatura foni"). */
    private fun photoFile() = File(filesDir, "keyboard_bg.jpg")
    private var photoStamp = 0L

    /** Loads the picture for the "Rasm" theme (on the worker), or drops it. */
    private fun loadPhoto() {
        val f = photoFile()
        if (themeMode != KeyboardTheme.NAMES.size - 1 || !f.exists()) {
            photoStamp = 0L
            view?.setPhoto(null)
            return
        }
        val stamp = f.lastModified()
        if (stamp == photoStamp) return
        photoStamp = stamp
        workerHandler.post {
            val bmp = try {
                android.graphics.BitmapFactory.decodeFile(f.path)
            } catch (_: Throwable) {
                null
            }
            mainHandler.post { view?.setPhoto(bmp) }
        }
    }

    private fun applyLook() {
        view?.setLook(oneHand, SIZES[sizeStep], themeMode)
    }

    /** A key with Shift held while selecting (text editing panel). */
    private fun moveKey(code: Int) {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        val meta = if (selecting) KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON else 0
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0, meta))
    }

    /** The whole text (or the selection) to the new alphabet, in place. */
    private fun convertField() {
        val ic = currentInputConnection ?: return
        if (password) return
        val selected = ic.getSelectedText(0)?.toString()
        if (!selected.isNullOrEmpty()) {
            val out = Converter.convert(selected, "new")
            if (out != selected) ic.commitText(out, 1)
            return
        }
        val text = ic.getExtractedText(ExtractedTextRequest(), 0)?.text?.toString() ?: return
        val out = Converter.convert(text, "new")
        if (out == text) return
        ic.beginBatchEdit()
        ic.setSelection(0, text.length)
        ic.commitText(out, 1)
        ic.endBatchEdit()
    }

    /** Google voice typing (or another voice keyboard), if the phone has one. */
    private fun startVoice() {
        val imm = getSystemService(InputMethodManager::class.java)
        val voice = imm.shortcutInputMethodsAndSubtypes.entries.firstOrNull() ?: return
        val subtype = voice.value.firstOrNull()
        if (Build.VERSION.SDK_INT >= 28) {
            switchInputMethod(voice.key.id, subtype)
        } else {
            val token = window.window?.attributes?.token ?: return
            @Suppress("DEPRECATION")
            imm.setInputMethodAndSubtype(token, voice.key.id, subtype)
        }
    }

    private fun openSettings() {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        try {
            startActivity(intent)
        } catch (_: Exception) {
        }
    }

    override fun onAction(action: String) {
        val ic = currentInputConnection
        when {
            action == "back" -> show(letters)
            action == "menu" -> show(if (current.name == "menu") letters else Layouts.menu(KeyboardTheme.NAMES[themeMode]))
            action == "lang" -> onKey(KeyKind.LANG)
            action == "onehand" -> {
                oneHand = if (oneHand == 0) 2 else 0
                savePref("one_hand", oneHand)
                applyLook()
                show(letters)
            }
            action == "onehand:switch" -> {
                oneHand = if (oneHand == 1) 2 else 1
                savePref("one_hand", oneHand)
                applyLook()
            }
            action == "onehand:off" -> {
                oneHand = 0
                savePref("one_hand", 0)
                applyLook()
            }
            action == "theme" -> {
                val steps = if (photoFile().exists()) KeyboardTheme.NAMES.size else KeyboardTheme.NAMES.size - 1
                themeMode = (themeMode + 1) % steps
                savePref("theme", themeMode)
                loadPhoto()
                applyLook()
                show(Layouts.menu(KeyboardTheme.NAMES[themeMode]))
            }
            action == "resize" -> {
                sizeStep = (sizeStep + 1) % SIZES.size
                savePref("size", sizeStep)
                applyLook()
            }
            action == "edit" -> {
                selecting = false
                show(Layouts.edit(false))
            }
            action == "clipboard" -> {
                rememberClip()
                show(Layouts.clipboard(clips))
            }
            action == "convert" -> convertField()
            action == "settings" -> openSettings()
            action == "voice" -> startVoice()
            action == "clip:clear" -> {
                clips.clear()
                show(Layouts.clipboard(clips))
            }
            action.startsWith("paste:") -> {
                val clip = clips.getOrNull(action.removePrefix("paste:").toIntOrNull() ?: -1) ?: return
                ic?.commitText(clip, 1)
                show(letters)
            }
            action == "edit:select" -> {
                selecting = !selecting
                show(Layouts.edit(selecting))
            }
            action == "edit:left" -> moveKey(KeyEvent.KEYCODE_DPAD_LEFT)
            action == "edit:right" -> moveKey(KeyEvent.KEYCODE_DPAD_RIGHT)
            action == "edit:up" -> moveKey(KeyEvent.KEYCODE_DPAD_UP)
            action == "edit:down" -> moveKey(KeyEvent.KEYCODE_DPAD_DOWN)
            action == "edit:home" -> moveKey(KeyEvent.KEYCODE_MOVE_HOME)
            action == "edit:end" -> moveKey(KeyEvent.KEYCODE_MOVE_END)
            action == "edit:all" -> ic?.performContextMenuAction(android.R.id.selectAll)
            action == "edit:copy" -> ic?.performContextMenuAction(android.R.id.copy)
            action == "edit:cut" -> ic?.performContextMenuAction(android.R.id.cut)
            action == "edit:paste" -> ic?.performContextMenuAction(android.R.id.paste)
        }
    }

    override fun onGlide(path: List<Glide.P>, centers: Map<Char, Glide.P>, keyWidth: Float) {
        val d = dict ?: return
        val caseNow = shift
        val id = ++requestId
        // the search runs on the worker; the word is typed when it is ready
        workerHandler.post {
            val found = Glide.decode(path, centers, keyWidth, d)
            mainHandler.post {
                if (id != requestId || found.isEmpty()) return@post
                val ic = currentInputConnection ?: return@post
                val words = found.map {
                    when (caseNow) {
                        Shift.LOCKED -> it.word.uppercase(Locale.ROOT)
                        Shift.ON -> it.word.replaceFirstChar { ch -> ch.uppercaseChar() }
                        Shift.OFF -> it.word
                    }
                }.distinct()
                val before = ic.getTextBeforeCursor(1, 0) ?: ""
                val space = if (before.isNotEmpty() && (before.last().isLetterOrDigit() || before.last() in ".,!?;:")) " " else ""
                undo.pending = null
                autoUndo = null
                glided = words
                ic.commitText(space + words.first(), 1)
                if (shift == Shift.ON) setShift(Shift.OFF)
                view?.setSuggestions(words.take(3))
            }
        }
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
        /** Token for the dictionary jobs on the worker thread. */
        private val SUGGEST = Any()

        /** Key heights of the "Ölçam" steps: normal, large, small. */
        private val SIZES = floatArrayOf(1f, 1.12f, 0.88f)

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
