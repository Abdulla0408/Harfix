"""Convert Uzbek text between alphabets.

Main direction: old Latin or Cyrillic -> the new Latin alphabet defined by
Law O'RQ-1184 (21.10.2026): oʻ→ö, gʻ→ğ, sh→ş, ch→ç, tutuq → ʼ.
Reverse directions: any of them -> old Latin (oʻ, gʻ, sh, ch) or Cyrillic.

The same rules must be ported to Kotlin (Android), C# (Windows) and
JavaScript (Mini App); tests/cases*.json are the shared test sets every port
must pass.
"""
import re

TUTUQ = "ʼ"  # U+02BC
OKINA = "ʻ"  # U+02BB, the official mark in old Latin oʻ / gʻ
# Every character people type for the oʻ/gʻ mark or for the tutuq.
APOSTROPHES = "'`‘’ʻʼ´"
_AP = "[" + re.escape(APOSTROPHES) + "]"

TARGETS = ("new", "old", "cyr")

# --- Old Latin -> new ------------------------------------------------------
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

# --- Cyrillic -> new -------------------------------------------------------
_CYR = {
    "а": "a", "б": "b", "в": "v", "г": "g", "д": "d", "ё": "yo", "ж": "j",
    "з": "z", "и": "i", "й": "y", "к": "k", "л": "l", "м": "m", "н": "n",
    "о": "o", "п": "p", "р": "r", "с": "s", "т": "t", "у": "u", "ф": "f",
    "х": "x", "ч": "ç", "ш": "ş", "щ": "şç", "ъ": TUTUQ, "ы": "i", "ь": "",
    "э": "e", "ю": "yu", "я": "ya", "ў": "ö", "қ": "q", "ғ": "ğ", "ҳ": "h",
}
_CYR_VOWELS = set("аеёиоуэюяўАЕЁИОУЭЮЯЎ")
_CYR_RE = re.compile("[Ѐ-ӿ]")
_LAT_RE = re.compile("[A-Za-zÖöĞğŞşÇç]")


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
    elif low == "с" and i + 1 < len(text) and text[i + 1].lower() == "ҳ":
        # Исҳоқ → Isʼhoq: two letters, not "sh".
        out = "s" + TUTUQ
    elif low in _CYR:
        out = _CYR[low]
    else:
        return ch
    if ch == low or not out:
        return out
    return _case_like(out, text, i)


def _case_like(out, text, i):
    """Uppercase `out` (1-2 letters) for the uppercase letter text[i]:
    whole word in caps → all caps (ЁЗ → YOZ), else Title (Ёз → Yoz)."""
    if len(out) == 1:
        return out.upper()
    prev = text[i - 1] if i > 0 else ""
    nxt = text[i + 1] if i + 1 < len(text) else ""
    all_caps = (nxt.isalpha() and nxt.isupper()) or (prev.isalpha() and prev.isupper())
    return out.upper() if all_caps else out[0].upper() + out[1:]


def _from_cyrillic(text):
    return "".join(_cyr_char(text, i) for i in range(len(text)))


def _to_new(text):
    if _CYR_RE.search(text):
        text = _from_cyrillic(text)
    for pattern, repl in _LATIN_RULES:
        text = pattern.sub(repl, text)
    return text


# --- New -> old Latin ------------------------------------------------------
_OLD = {"ö": "o" + OKINA, "ğ": "g" + OKINA, "ş": "sh", "ç": "ch"}
# "s"+"h" that are two letters (Isʼhoq) get the tutuq in old Latin,
# otherwise they would read as "sh".
_SH_SPLIT = re.compile("([sScC])([hH])")


def _new_to_old(text):
    text = _SH_SPLIT.sub(r"\1" + TUTUQ + r"\2", text)
    out = []
    for i, ch in enumerate(text):
        low = ch.lower()
        if low in _OLD:
            rep = _OLD[low]
            out.append(rep if ch == low else _case_like(rep, text, i))
        else:
            out.append(ch)
    return "".join(out)


# --- New -> Cyrillic -------------------------------------------------------
_TO_CYR = {
    "a": "а", "b": "б", "c": "с", "d": "д", "e": "е", "f": "ф", "g": "г",
    "h": "ҳ", "i": "и", "j": "ж", "k": "к", "l": "л", "m": "м", "n": "н",
    "o": "о", "p": "п", "q": "қ", "r": "р", "s": "с", "t": "т", "u": "у",
    "v": "в", "w": "в", "x": "х", "y": "й", "z": "з",
    "ö": "ў", "ğ": "ғ", "ş": "ш", "ç": "ч",
}
_Y_PAIRS = {"a": "я", "o": "ё", "u": "ю", "e": "е"}
_LAT_VOWELS = set("aeiouö")
# -tsiya / -tsion are Russian loanwords written with ц: militsiya → милиция.
_TS_CYR = re.compile("(?i)ts(?=i(?:ya|on))")


def _new_to_cyr(text):
    out = []
    i, n = 0, len(text)
    while i < n:
        ch = text[i]
        low = ch.lower()
        prev = text[i - 1] if i > 0 else ""
        nxt = text[i + 1] if i + 1 < n else ""
        if low == "y" and nxt.lower() in _Y_PAIRS:
            rep = _Y_PAIRS[nxt.lower()]
            out.append(rep.upper() if ch.isupper() else rep)
            i += 2
            continue
        if low == "t" and nxt.lower() == "s" and _TS_CYR.match(text, i):
            out.append("Ц" if ch.isupper() else "ц")
            i += 2
            continue
        if ch == TUTUQ:
            after = nxt.lower()
            # Isʼhoq → Исҳоқ: the separator is not written in Cyrillic.
            if prev and prev.lower() in "sc" and after == "h":
                i += 1
                continue
            out.append("ъ" if prev.isalpha() and nxt.isalpha() else ch)
            i += 1
            continue
        if low == "e":
            word_start = not prev.isalpha()
            rep = "э" if word_start or prev.lower() in _LAT_VOWELS else "е"
            out.append(rep.upper() if ch.isupper() else rep)
            i += 1
            continue
        rep = _TO_CYR.get(low)
        if rep is None:
            out.append(ch)
        else:
            out.append(rep.upper() if ch.isupper() else rep)
        i += 1
    return "".join(out)


def _to_old(text):
    return _new_to_old(_to_new(text))


def _to_cyr(text):
    # Text that is already Cyrillic stays as written (keeps ц, ь, щ).
    if _CYR_RE.search(text) and not _LAT_RE.search(text):
        return text
    return _new_to_cyr(_to_new(text))


_PLAIN = {"new": _to_new, "old": _to_old, "cyr": _to_cyr}

# --- Tokens we must never touch -------------------------------------------
_SKIP_RE = re.compile(
    r"(https?://\S+|www\.\S+|\S+@\S+\.\S+|[@#][\w.]+|`[^`]*`)",
    re.IGNORECASE,
)


def convert_to(text: str, target: str = "new") -> str:
    """Convert a whole text to `target` ("new", "old" or "cyr"), leaving
    URLs, e-mails, @mentions, #tags and `code` unchanged. The input may be
    in any of the three alphabets, even mixed."""
    fn = _PLAIN[target]
    out, pos = [], 0
    for m in _SKIP_RE.finditer(text):
        out.append(fn(text[pos:m.start()]))
        out.append(m.group(0))
        pos = m.end()
    out.append(fn(text[pos:]))
    return "".join(out)


def convert(text: str) -> str:
    """Convert a whole text to the new alphabet."""
    return convert_to(text, "new")


def to_old_latin(text: str) -> str:
    return convert_to(text, "old")


def to_cyrillic(text: str) -> str:
    return convert_to(text, "cyr")


def convert_word(word: str) -> str:
    """Convert a single word; used by keyboards for real-time conversion."""
    return convert(word)


def convert_segment(text: str, prev: str = "", target: str = "new") -> str:
    """Convert a piece of text that follows `prev` (e.g. one formatted run
    inside a Word paragraph), so rules that look at the previous character
    (Cyrillic е/ц, tutuq) still work across run boundaries."""
    if not prev:
        return convert_to(text, target)
    ctx = prev[-1]
    full, pre = convert_to(ctx + text, target), convert_to(ctx, target)
    return full[len(pre):] if full.startswith(pre) else convert_to(text, target)


_PAIRS = {"sh", "ch"} | {c + a for c in "og" for a in APOSTROPHES}
_PAIRS_CYR = _PAIRS | {"ya", "yo", "yu", "ye", "ts"}


def _is_pair(a: str, b: str, target: str) -> bool:
    return (a + b).lower() in (_PAIRS_CYR if target == "cyr" else _PAIRS)


def convert_runs(runs: list[str], target: str = "new") -> list[str]:
    """Convert a paragraph that is split into formatted runs, keeping the
    same number of runs. A digraph split across runs ("s" | "h") is moved
    into the first run so it becomes one letter there."""
    runs = list(runs)
    for i in range(len(runs) - 1):
        if runs[i] and runs[i + 1] and _is_pair(runs[i][-1], runs[i + 1][0], target):
            runs[i] += runs[i + 1][0]
            runs[i + 1] = runs[i + 1][1:]
    out, prev = [], ""
    for r in runs:
        out.append(convert_segment(r, prev, target) if r else r)
        if r:
            prev = r
    return out
