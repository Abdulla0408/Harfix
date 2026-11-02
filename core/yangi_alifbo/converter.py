"""Convert Uzbek text (old Latin or Cyrillic) to the new Latin alphabet
defined by Law O'RQ-1184 (21.10.2026): oʻ→ö, gʻ→ğ, sh→ş, ch→ç, tutuq → ʼ.

The same rules must be ported to Kotlin (Android) and C# (Windows);
tests/cases.json is the shared test set every port must pass.
"""
import re

TUTUQ = "ʼ"  # ʼ
# Every character people type for the oʻ/gʻ mark or for the tutuq.
APOSTROPHES = "'`‘’ʻʼ´"
_AP = "[" + re.escape(APOSTROPHES) + "]"

VOWELS_LAT = set("aeiouAEIOUöÖ")

# --- Old Latin -------------------------------------------------------------
# U+02BC after o/g is an explicit tutuq (e.g. "moʼjiza" is rare), so it is
# excluded from the oʻ/gʻ marks; everything else counts as the oʻ/gʻ mark.
_OG_MARK = "[" + re.escape("'`‘’ʻ´") + "]"
_LATIN_RULES = [
    (re.compile("O" + _OG_MARK), "Ö"),
    (re.compile("o" + _OG_MARK), "ö"),
    (re.compile("G" + _OG_MARK), "Ğ"),
    (re.compile("g" + _OG_MARK), "ğ"),
    (re.compile("S[Hh]"), "Ş"),
    (re.compile("s[Hh]"), "ş"),
    (re.compile("C[Hh]"), "Ç"),
    (re.compile("c[Hh]"), "ç"),
    # Remaining apostrophes between letters are the tutuq: maʼno, sheʼr.
    (re.compile(r"(?<=\w)" + _AP + r"(?=\w)"), TUTUQ),
]

# --- Cyrillic --------------------------------------------------------------
_CYR = {
    "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "ё": "yo", "ж": "j",
    "з": "z", "и": "i", "й": "y", "к": "k", "л": "l", "м": "m", "н": "n",
    "о": "o", "п": "p", "р": "r", "с": "s", "т": "t", "у": "u", "ф": "f",
    "х": "x", "ч": "ç", "ш": "ş", "щ": "şç", "ъ": TUTUQ, "ы": "i", "ь": "",
    "э": "e", "ю": "yu", "я": "ya", "ў": "ö", "қ": "q", "ғ": "ğ", "ҳ": "h",
}
_CYR_VOWELS = set("аеёиоуэюяўАЕЁИОУЭЮЯЎ")
_CYR_RE = re.compile("[Ѐ-ӿ]")


def _cyr_char(text, i):
    ch = text[i]
    low = ch.lower()
    prev = text[i - 1] if i > 0 else ""
    word_start = not prev.isalpha()
    if low == "е":
        # ye at word start or after a vowel / ъ / ь; e elsewhere.
        out = "ye" if (word_start or prev in _CYR_VOWELS or prev.lower() in "ъь") else "e"
    elif low == "ц":
        # s at word start or after a consonant; ts after a vowel.
        out = "ts" if prev in _CYR_VOWELS else "s"
    elif low in _CYR:
        out = _CYR[low]
    else:
        return ch
    if ch == low or not out:
        return out
    # Uppercase: whole word caps → all caps (ЁЗ → YOZ), else Title (Ёз → Yoz).
    nxt = text[i + 1] if i + 1 < len(text) else ""
    all_caps = (nxt.isalpha() and nxt.isupper()) or (prev.isalpha() and prev.isupper())
    return out.upper() if all_caps else out[0].upper() + out[1:]


def _from_cyrillic(text):
    return "".join(_cyr_char(text, i) for i in range(len(text)))


# --- Tokens we must never touch -------------------------------------------
_SKIP_RE = re.compile(
    r"(https?://\S+|www\.\S+|\S+@\S+\.\S+|[@#][\w.]+|`[^`]*`)",
    re.IGNORECASE,
)


def _convert_plain(text):
    if _CYR_RE.search(text):
        text = _from_cyrillic(text)
    for pattern, repl in _LATIN_RULES:
        text = pattern.sub(repl, text)
    return text


def convert(text: str) -> str:
    """Convert a whole text, leaving URLs, e-mails, @mentions, #tags and
    `code` unchanged."""
    out, pos = [], 0
    for m in _SKIP_RE.finditer(text):
        out.append(_convert_plain(text[pos:m.start()]))
        out.append(m.group(0))
        pos = m.end()
    out.append(_convert_plain(text[pos:]))
    return "".join(out)


def convert_word(word: str) -> str:
    """Convert a single word; used by keyboards for real-time conversion."""
    return convert(word)


def convert_segment(text: str, prev: str = "") -> str:
    """Convert a piece of text that follows `prev` (e.g. one formatted run
    inside a Word paragraph), so rules that look at the previous character
    (Cyrillic е/ц, tutuq) still work across run boundaries."""
    if not prev:
        return convert(text)
    ctx = prev[-1]
    full, pre = convert(ctx + text), convert(ctx)
    return full[len(pre):] if full.startswith(pre) else convert(text)


_PAIR_FIRST = set("sScCoOgG")
_PAIR_SECOND = set("hH" + APOSTROPHES)


def convert_runs(runs: list[str]) -> list[str]:
    """Convert a paragraph that is split into formatted runs, keeping the
    same number of runs. A digraph split across runs ("s" | "h") is moved
    into the first run so it becomes one letter there."""
    runs = list(runs)
    for i in range(len(runs) - 1):
        if runs[i] and runs[i + 1] and runs[i][-1] in _PAIR_FIRST and runs[i + 1][0] in _PAIR_SECOND:
            runs[i] += runs[i + 1][0]
            runs[i + 1] = runs[i + 1][1:]
    out, prev = [], ""
    for r in runs:
        out.append(convert_segment(r, prev) if r else r)
        if r:
            prev = r
    return out
