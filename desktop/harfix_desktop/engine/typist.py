"""Real-time typing in the new alphabet, as pure logic (no Windows calls).

Fed one typed character at a time, it decides when to swap what was just
typed: o' -> ö, g' -> ğ, sh -> ş, ch -> ç (upper case follows the first
letter). Backspace right after a swap brings the original letters back,
like autocorrect. Words that look like links, e-mails, @names or #tags
are left alone."""
import re
from dataclasses import dataclass

APOSTROPHES = set("'`‘’ʻʼ´")
LATE_KEYS = APOSTROPHES | set("hH")  # second letters of a pair
_PAIRS = {("o", "'"): "ö", ("g", "'"): "ğ", ("s", "h"): "ş", ("c", "h"): "ç"}
_LINKISH = ("@", "#", "/", "\\", ":", "www.", "=", "_")
_LAST_WORD = re.compile(r"\S*$")


@dataclass(frozen=True)
class Swap:
    """Delete `delete` characters before the caret, then type `text`.
    The key being pressed is not typed."""
    delete: int
    text: str


def is_linkish(word: str) -> bool:
    """Links, e-mails, @names, #tags, paths, and words starting with a digit."""
    w = word.lower()
    return any(mark in w for mark in _LINKISH) or (len(w) > 1 and w[0].isdigit())


def pair_swap(before: str | None, ch: str, word: str = "") -> Swap | None:
    """The swap for typing `ch` right after the character `before`: o + ' -> ö,
    g + ' -> ğ, s + h -> ş, c + h -> ç, in upper case when `before` is.
    `word` is the word up to and including `before`: in words that look like
    links nothing is swapped. The Swap deletes `before`; `ch` is not typed."""
    if not before or not ch:
        return None
    out = _PAIRS.get((before.lower(), "'" if ch in APOSTROPHES else ch.lower()))
    if not out or is_linkish(word):
        return None
    return Swap(1, out.upper() if before.isupper() else out)


def is_shown(text: str, ch: str) -> bool:
    """True if `text` ends with the typed key `ch`. Any apostrophe counts for
    an apostrophe: programs like Word turn ' into ’ as you type."""
    last = text[-1:]
    return bool(last) and (last == ch or (ch in APOSTROPHES and last in APOSTROPHES))


def late_swap(text: str, ch: str) -> tuple[str, str, str] | None:
    """`ch` was typed while the Typist knew nothing about the text (the caret
    had been moved back into "to|liq") and went through as typed. `text` is
    what is now just before the caret, read back from the program. If `ch`
    completes a pair there, returns (typed, out, word): replace the two
    characters `typed` by `out` (delete 2, type `out`); `word` is then the word
    before the caret, for Typist.adopt()."""
    if len(text) < 2 or not is_shown(text, ch):
        return None  # the program has not shown the key (yet), or changed it
    word = _LAST_WORD.search(text, 0, len(text) - 1).group()
    swap = pair_swap(text[-2], text[-1], word)
    if swap is None:
        return None
    return text[-2:], swap.text, word[:-1] + swap.text


class Typist:
    def __init__(self):
        self.reset()

    def reset(self):
        """Forget everything: the caret moved, the window changed, a shortcut..."""
        self.prev: str | None = None  # character just before the caret
        self.word = ""  # the current word as typed so far
        self.undo: tuple[str, str] | None = None  # (typed, swapped) of the last swap

    def feed(self, ch: str) -> Swap | None:
        """A character is about to be typed. Returns a Swap to do instead, or None."""
        prev, self.undo = self.prev, None
        if ch.isspace():
            self.prev, self.word = ch, ""
            return None
        swap = pair_swap(prev, ch, self.word)
        if swap:
            self.adopt(prev + ch, swap.text, self.word[:-1] + swap.text)
            return swap
        self.prev = ch
        self.word += ch
        return None

    def adopt(self, typed: str, out: str, word: str | None = None):
        """Take over a swap of `typed` for `out` that was made outside feed()
        (a late apostrophe, see late_swap), as if feed() had made it: the caret
        is right after `out`, and Backspace now brings `typed` back. `word` is
        the word before the caret, ending with `out`."""
        self.prev, self.undo = out, (typed, out)
        self.word = out if word is None else word

    def backspace(self) -> Swap | None:
        """Backspace pressed. Right after a swap it restores the typed letters."""
        if self.undo:
            typed, _ = self.undo
            self.undo = None
            self.prev = typed[-1]
            self.word = self.word[:-1] + typed
            return Swap(1, typed)
        self.prev = None  # we no longer know what is before the caret
        self.word = self.word[:-1]
        return None
