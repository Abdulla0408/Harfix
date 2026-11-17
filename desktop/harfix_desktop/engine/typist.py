"""Real-time typing in the new alphabet, as pure logic (no Windows calls).

Fed one typed character at a time, it decides when to swap what was just
typed: o' -> ö, g' -> ğ, sh -> ş, ch -> ç (upper case follows the first
letter). Backspace right after a swap brings the original letters back,
like autocorrect. Words that look like links, e-mails, @names or #tags
are left alone."""
from dataclasses import dataclass

APOSTROPHES = set("'`‘’ʻʼ´")
_PAIRS = {("o", "'"): "ö", ("g", "'"): "ğ", ("s", "h"): "ş", ("c", "h"): "ç"}
_LINKISH = ("@", "#", "/", "\\", ":", "www.", "=", "_")


@dataclass(frozen=True)
class Swap:
    """Delete `delete` characters before the caret, then type `text`.
    The key being pressed is not typed."""
    delete: int
    text: str


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
        key = ("'" if ch in APOSTROPHES else ch.lower())
        out = _PAIRS.get((prev.lower(), key)) if prev else None
        if out and not self._linkish():
            if prev.isupper():
                out = out.upper()
            self.prev, self.undo = out, (prev + ch, out)
            self.word = self.word[:-1] + out
            return Swap(1, out)
        self.prev = ch
        self.word += ch
        return None

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

    def _linkish(self) -> bool:
        w = self.word.lower()
        return any(mark in w for mark in _LINKISH) or (len(w) > 1 and w[0].isdigit())
