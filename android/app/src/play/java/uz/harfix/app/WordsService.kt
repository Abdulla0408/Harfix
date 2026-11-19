package uz.harfix.app

import android.accessibilityservice.AccessibilityService
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * "Odatiy klaviaturada": converts oʻ gʻ sh ch to ö ğ ş ç while the user types with any keyboard
 * (Gboard, Samsung...). It only listens to text changes of the field being typed in, skips
 * password, e-mail and address fields, and keeps nothing: no text is stored or sent anywhere.
 * The decisions are in [WordsLogic]; this class reads the field and writes the result back.
 *
 * Google Play: this is not an accessibility tool (isAccessibilityTool="false" in
 * res/xml/accessibility.xml), so the app shows what it does before the user turns it on
 * (#words-dialog in assets/ui) and the Play Console declaration explains it (android/PLAY.md).
 */
class WordsService : AccessibilityService(), SharedPreferences.OnSharedPreferenceChangeListener {
    private var prefs: SharedPreferences? = null
    private var on = true
    private var live = true

    /** The last change we made, and in which field (for Backspace). */
    private var last: WordsLogic.Undo? = null
    private var lastField = 0

    /** The text we just wrote: its own change event is not typing. */
    private var ours: String? = null

    override fun onServiceConnected() {
        val p = getSharedPreferences("harfix", MODE_PRIVATE)
        prefs = p
        read(p)
        p.registerOnSharedPreferenceChangeListener(this)
        Words.refreshTile(this)
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences, key: String?) {
        read(p)
        if (key == "words") Words.refreshTile(this)
    }

    private fun read(p: SharedPreferences) {
        on = p.getBoolean("words", true)
        live = p.getBoolean("words_live", true)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (!on || e.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED || e.isPassword) return
        if (e.packageName == packageName) return // the app converts its own text boxes
        val node = e.source ?: return
        try {
            handle(e, node)
        } catch (_: RuntimeException) {
            last = null // the field went away or refused the change: just leave it
        } finally {
            @Suppress("DEPRECATION")
            if (Build.VERSION.SDK_INT < 33) node.recycle()
        }
    }

    private fun handle(e: AccessibilityEvent, node: AccessibilityNodeInfo) {
        if (!node.isEditable || node.isPassword) return
        if (Build.VERSION.SDK_INT >= 26 && node.isShowingHintText) return
        val type = node.inputType
        val cls = type and InputType.TYPE_MASK_CLASS
        if (type != 0 && cls != InputType.TYPE_CLASS_TEXT) return // numbers, phone, dates
        if ((type and InputType.TYPE_MASK_VARIATION) in Words.SKIP_VARIATIONS) return

        val text = node.text?.toString() ?: return
        if (text == ours) {
            ours = null
            return
        }
        ours = null
        val field = node.hashCode()
        if (field != lastField) {
            last = null
            lastField = field
        }
        val start = node.textSelectionStart
        val end = node.textSelectionEnd
        val cursor = when {
            start >= 0 && start == end -> end
            start < 0 -> e.fromIndex + e.addedCount
            else -> return // a selection: not typing
        }
        val edit = WordsLogic.onChange(e.beforeText?.toString(), text, cursor, e.addedCount, e.removedCount, live, last)
        last = edit?.undo
        if (edit != null) write(node, edit)
    }

    private fun write(node: AccessibilityNodeInfo, edit: WordsLogic.Edit) {
        ours = edit.text
        val text = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, edit.text)
        }
        if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text)) {
            ours = null
            last = null
            return
        }
        val selection = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, edit.cursor)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, edit.cursor)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selection)
    }

    override fun onInterrupt() {
        last = null
    }

    override fun onDestroy() {
        prefs?.unregisterOnSharedPreferenceChangeListener(this)
        Words.refreshTile(this)
        super.onDestroy()
    }
}
