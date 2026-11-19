package uz.harfix.app.keyboard

enum class KeyKind { CHAR, NEW_LETTER, SHIFT, BACKSPACE, ENTER, SPACE, SYMBOLS, MORE_SYMBOLS, LETTERS, LANG, GLOBE, SPACER, ACTION, NUMPAD }

enum class Shift { OFF, ON, LOCKED }

enum class EnterIcon { NEWLINE, GO, SEARCH, SEND, NEXT, DONE }

/**
 * One key: [label] is also the text it types; [alts] pop up on a long press. An ACTION key (the
 * panels: menu, text editing, clipboard) runs [action] instead of typing.
 */
data class Key(
    val label: String,
    val kind: KeyKind = KeyKind.CHAR,
    val width: Float = 1f,
    val alts: List<String> = emptyList(),
    val action: String = "",
) {
    val text: String get() = label
}

/** A row of keys; [height] is relative to the normal key height. */
class Row(val keys: List<Key>, val height: Float = 1f)

/** [name] is the layout id, [spaceLabel] what the space bar shows, [autoCaps] for letter layouts. */
class Layout(val name: String, val rows: List<Row>, val spaceLabel: String, val autoCaps: Boolean)

object Layouts {
    /** Short names on the language key (it shows the next layout). */
    val SHORT = mapOf("latin" to "Lot", "russian" to "Рус", "cyrillic" to "Ўзб")
    /** Names on the space bar. */
    val NAMES = mapOf("latin" to "Lotin", "russian" to "Русский", "cyrillic" to "Ўзбекча")

    private val SPACER = Key("", KeyKind.SPACER, 0.5f)

    /** Long-press letters; the first digit is also drawn small in the key's corner. */
    private val LATIN_ALTS: Map<String, List<String>> = mapOf(
        "q" to listOf("1"), "w" to listOf("2"), "e" to listOf("3", "é", "ê"), "r" to listOf("4"),
        "t" to listOf("5"), "y" to listOf("6"), "u" to listOf("7", "ü", "ú"), "i" to listOf("8", "ı", "í"),
        "o" to listOf("9", "ö", "ó"), "p" to listOf("0"),
        "a" to listOf("á", "ä", "à"), "s" to listOf("ş", "ß"), "g" to listOf("ğ"), "c" to listOf("ç"),
        "n" to listOf("ñ"), "l" to listOf("ʼ", "'"),
    )
    private val RUSSIAN_ALTS: Map<String, List<String>> = mapOf(
        "й" to listOf("1"), "ц" to listOf("2"), "у" to listOf("3"), "к" to listOf("4"), "е" to listOf("5", "ё"),
        "н" to listOf("6"), "г" to listOf("7"), "ш" to listOf("8"), "щ" to listOf("9"), "з" to listOf("0"),
        "ь" to listOf("ъ"),
    )
    private val CYRILLIC_ALTS: Map<String, List<String>> = mapOf(
        "й" to listOf("1"), "ц" to listOf("2"), "у" to listOf("3", "ў"), "к" to listOf("4", "қ"), "е" to listOf("5", "ё"),
        "н" to listOf("6"), "г" to listOf("7", "ғ"), "ш" to listOf("8", "щ"), "ў" to listOf("9"), "з" to listOf("0"),
        "х" to listOf("ҳ"), "ь" to listOf("ъ"), "и" to listOf("ы"),
    )

    private fun keys(line: String, alts: Map<String, List<String>> = emptyMap()): List<Key> =
        line.split(" ").map { Key(it, alts = alts[it] ?: emptyList()) }

    /** The top strip of big letter keys (ö ğ ş ç for Latin). */
    private fun strip(letters: List<String>) = Row(letters.map { Key(it, KeyKind.NEW_LETTER, 2.5f) }, 0.82f)

    /** A small gap: keeps a finger aiming at M (or Z) off ⌫ (or ⇧). */
    private val GAP = Key("", KeyKind.SPACER, 0.25f)

    /** ⇧ + letters + ⌫, with a gap on both sides so the letters keep their full width. */
    private fun shiftRow(line: String, alts: Map<String, List<String>>) =
        Row(listOf(Key("⇧", KeyKind.SHIFT, 1.25f), GAP) + keys(line, alts) + GAP + Key("⌫", KeyKind.BACKSPACE, 1.25f))

    /** ?123 , [🌐] [next language] space . ⏎ */
    fun bottomRow(next: String?, globe: Boolean): Row {
        val k = ArrayList<Key>()
        k.add(Key("?123", KeyKind.SYMBOLS, 1.5f))
        k.add(Key(",", alts = listOf("ʼ", "'", "!", "?", ";", ":", "-")))
        if (globe) k.add(Key("🌐", KeyKind.GLOBE))
        if (next != null) k.add(Key(next, KeyKind.LANG))
        var space = if (globe) 3.5f else 4.5f
        if (next == null) space += 1f
        k.add(Key(" ", KeyKind.SPACE, space))
        k.add(Key(".", alts = listOf("?", "!", ",", "…", "-", ":", ";", "\"", "(", ")")))
        k.add(Key("⏎", KeyKind.ENTER, 1.5f))
        return Row(k)
    }

    fun latin(next: String?, globe: Boolean) = Layout(
        "latin",
        listOf(
            strip(listOf("ö", "ğ", "ş", "ç")),
            Row(keys("q w e r t y u i o p", LATIN_ALTS)),
            Row(listOf(SPACER) + keys("a s d f g h j k l", LATIN_ALTS) + SPACER),
            shiftRow("z x c v b n m", LATIN_ALTS),
            bottomRow(next, globe),
        ),
        NAMES.getValue("latin"),
        true,
    )

    fun cyrillic(next: String?, globe: Boolean) = Layout(
        "cyrillic",
        listOf(
            strip(listOf("ғ", "ҳ", "ъ", "ё")),
            Row(keys("й ц у к е н г ш ў з х", CYRILLIC_ALTS)),
            Row(keys("ф қ в а п р о л д ж э", CYRILLIC_ALTS)),
            shiftRow("я ч с м и т ь б ю", CYRILLIC_ALTS),
            bottomRow(next, globe),
        ),
        NAMES.getValue("cyrillic"),
        true,
    )

    fun russian(next: String?, globe: Boolean) = Layout(
        "russian",
        listOf(
            strip(listOf("ё", "ъ", "«", "»")),
            Row(keys("й ц у к е н г ш щ з х", RUSSIAN_ALTS)),
            Row(keys("ф ы в а п р о л д ж э", RUSSIAN_ALTS)),
            shiftRow("я ч с м и т ь б ю", RUSSIAN_ALTS),
            bottomRow(next, globe),
        ),
        NAMES.getValue("russian"),
        true,
    )

    fun letters(name: String, next: String?, globe: Boolean) = when (name) {
        "cyrillic" -> cyrillic(next, globe)
        "russian" -> russian(next, globe)
        else -> latin(next, globe)
    }

    /** ABC , [🌐] number pad, space . ⏎ (space is wider without the globe key) */
    private fun symbolsBottom(left: String, right: String, globe: Boolean): Row {
        val k = ArrayList<Key>()
        k.add(Key("ABC", KeyKind.LETTERS, 1.5f))
        k.add(Key(left))
        if (globe) k.add(Key("🌐", KeyKind.GLOBE))
        k.add(Key("", KeyKind.NUMPAD))
        k.add(Key(" ", KeyKind.SPACE, if (globe) 3.5f else 4.5f))
        k.add(Key(right))
        k.add(Key("⏎", KeyKind.ENTER, 1.5f))
        return Row(k)
    }

    /** ?123: digits and common symbols, under the strip of the current letters. */
    fun symbols(letters: Layout, globe: Boolean) = Layout(
        "symbols",
        listOf(
            letters.rows.first(),
            Row(keys("1 2 3 4 5 6 7 8 9 0")),
            Row(keys("@ # № % & - + ( ) /")),
            Row(listOf(Key("=\\<", KeyKind.MORE_SYMBOLS, 1.5f)) + keys("* \" ' ʼ : ! ?") + Key("⌫", KeyKind.BACKSPACE, 1.5f)),
            symbolsBottom(",", ".", globe),
        ),
        "",
        false,
    )

    /** =\<: more symbols. */
    fun more(letters: Layout, globe: Boolean) = Layout(
        "more",
        listOf(
            letters.rows.first(),
            Row(keys("~ ` | • √ π ÷ × ¶ ∆")),
            Row(keys("€ $ £ ¥ ^ ° = { } \\")),
            Row(listOf(Key("?123", KeyKind.SYMBOLS, 1.5f)) + keys("© ® ™ ; < > _") + Key("⌫", KeyKind.BACKSPACE, 1.5f)),
            symbolsBottom("[", "]", globe),
        ),
        "",
        false,
    )

    /**
     * The number pad from ?123. Its rows are groups that the view lays out as a pad: [0] the
     * arithmetic column on the left, [1] the digits 1-9, [2] % space ⌫ on the right, [3] the
     * bottom row. The heights add up to the letters' height (only the digits and bottom count).
     */
    fun numpad() = Layout(
        "numpad",
        listOf(
            Row(listOf(Key("+", alts = listOf("±")), Key("-", alts = listOf("_")), Key("*", alts = listOf("×")), Key("/", alts = listOf("÷"))), 0f),
            Row(keys("1 2 3 4 5 6 7 8 9"), 3.9f),
            Row(listOf(Key("%", alts = listOf("‰")), Key(" ", KeyKind.SPACE), Key("⌫", KeyKind.BACKSPACE)), 0f),
            Row(listOf(Key("ABC", KeyKind.LETTERS), Key(","), Key("!?#", KeyKind.SYMBOLS), Key("0"), Key("="), Key("."), Key("⏎", KeyKind.ENTER)), 0.92f),
        ),
        "",
        false,
    )

    /** Number fields; [phone] adds + * # ( instead of - space , . */
    fun numbers(phone: Boolean) = Layout(
        "numbers",
        listOf(
            Row(keys("1 2 3") + Key(if (phone) "+" else "-")),
            Row(keys("4 5 6") + if (phone) Key("*") else Key(" ", KeyKind.SPACE)),
            Row(keys("7 8 9") + Key("⌫", KeyKind.BACKSPACE)),
            Row(listOf(Key(if (phone) "#" else ","), Key("0"), Key(if (phone) "(" else "."), Key("⏎", KeyKind.ENTER))),
        ),
        "",
        false,
    )

    // --- panels: opened from the toolbar above the keys --------------------------------------

    /** Panel layouts (not letters): the toolbar shows ✕ to close them. */
    val PANELS = setOf("menu", "edit", "clipboard")

    private fun act(label: String, action: String, width: Float = 1f) = Key(label, KeyKind.ACTION, width, action = action)

    /** Panels are as tall as the letters (0.82 + 4 rows), so the keyboard does not jump. */
    private const val PANEL_HEIGHT = 4.82f
    private const val PANEL_ROW = PANEL_HEIGHT / 4
    private const val CLIP_ROW = (PANEL_HEIGHT - 1f) / 5

    /** The menu: what the keyboard can do besides typing; [theme] is the current theme's name. */
    fun menu(theme: String = "") = Layout(
        "menu",
        listOf(
            Row(listOf(act("🌐  Keyingi til", "lang"), act("✋  Bir qöl", "onehand")), PANEL_ROW),
            Row(listOf(act(if (theme.isEmpty()) "🎨  Mavzu" else "🎨  Mavzu: $theme", "theme"), act("✍️  Tahrirlaş", "edit")), PANEL_ROW),
            Row(listOf(act("🔤  Ögiriş", "convert"), act("📋  Bufer", "clipboard")), PANEL_ROW),
            Row(listOf(act("↕️  Ölçam", "resize"), act("⚙️  Sozlamalar", "settings")), PANEL_ROW),
        ),
        "",
        false,
    )

    /** Text editing: move the cursor, select, copy, cut, paste. [selecting]: arrows extend the selection. */
    fun edit(selecting: Boolean) = Layout(
        "edit",
        listOf(
            Row(listOf(act("Hammasi", "edit:all"), act("Nusxa", "edit:copy"), act("Kesiş", "edit:cut"), act("Qöyiş", "edit:paste")), PANEL_ROW),
            Row(listOf(act("⇤", "edit:home"), act("▲", "edit:up"), act("⇥", "edit:end")), PANEL_ROW),
            Row(listOf(act("◀", "edit:left"), act(if (selecting) "✓ Tanlaş" else "Tanlaş", "edit:select"), act("▶", "edit:right")), PANEL_ROW),
            Row(listOf(act("ABC", "back"), act("▼", "edit:down"), Key("⌫", KeyKind.BACKSPACE)), PANEL_ROW),
        ),
        "",
        false,
    )

    /** Recently copied texts (newest first); a tap types one. */
    fun clipboard(clips: List<String>): Layout {
        val rows = ArrayList<Row>()
        if (clips.isEmpty()) {
            rows.add(Row(listOf(act("Bufer böş: biror matnni nusxalang", "back")), CLIP_ROW))
        } else {
            for ((i, clip) in clips.take(5).withIndex()) {
                val oneLine = clip.replace('\n', ' ').trim()
                rows.add(Row(listOf(act(if (oneLine.length > 38) oneLine.take(37) + "…" else oneLine, "paste:$i")), CLIP_ROW))
            }
        }
        // empty space up to the letters' height
        val used = rows.size * CLIP_ROW
        if (used < PANEL_HEIGHT - 1f - 0.01f) rows.add(Row(listOf(Key("", KeyKind.SPACER)), PANEL_HEIGHT - 1f - used))
        rows.add(Row(listOf(act("ABC", "back"), act("Tozalaş", "clip:clear"))))
        return Layout("clipboard", rows, "", false)
    }
}
