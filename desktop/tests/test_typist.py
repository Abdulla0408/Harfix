"""The live typing logic (engine/typist.py), without Windows: a pretend text
box gets the same calls the keyboard hook and its worker thread make.

    python desktop/tests/test_typist.py"""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))
from harfix_desktop.engine.typist import (LATE_KEYS, Swap, Typist, is_shown,  # noqa: E402
                                          late_swap, pair_swap)


class Box:
    """A text box with a caret, typed into the way engine/hook.py does it."""

    def __init__(self):
        self.text, self.caret = "", 0
        self.typist = Typist()

    def _edit(self, delete: int, insert: str):
        self.text = self.text[:self.caret - delete] + insert + self.text[self.caret:]
        self.caret += len(insert) - delete

    def type(self, keys: str):
        for ch in keys:
            blind = self.typist.prev is None
            swap = self.typist.feed(ch)
            if swap:  # the hook swallows the key and types the swap instead
                self._edit(swap.delete, swap.text)
                continue
            self._edit(0, ch)
            if blind and ch in LATE_KEYS:  # the worker reads the text back and swaps late
                found = late_swap(self.text[max(0, self.caret - 32):self.caret], ch)
                if found:
                    typed, out, word = found
                    self._edit(2, out)
                    self.typist.adopt(typed, out, word)
        return self

    def backspace(self):
        swap = self.typist.backspace()
        if swap:
            self._edit(swap.delete, swap.text)
        else:
            self._edit(1, "")
        return self

    def move_to(self, caret: int):
        """A click or the arrow keys: the hook forgets what is before the caret."""
        self.caret = caret
        self.typist.reset()
        return self

    def __str__(self):
        return self.text[:self.caret] + "|" + self.text[self.caret:]


def typed(keys: str) -> str:
    return Box().type(keys).text


def test_pairs():
    assert Typist().feed("o") is None
    t = Typist()
    t.feed("o")
    assert t.feed("'") == Swap(1, "ö")
    assert typed("toshkent") == "toşkent"
    assert typed("o'zbek") == "özbek"
    assert typed("g'alaba") == "ğalaba"
    assert typed("chiroyli choʻl") == "çiroyli çöl"
    for ap in "'`‘’ʻʼ´":
        assert typed(f"o{ap}g{ap}il") == "öğil", ap
    assert typed("o 'q") == "o 'q"  # a space breaks the pair
    assert typed("bog'lash") == "boğlaş"


def test_upper_case():
    assert typed("O'zbekiston") == "Özbekiston"
    assert typed("SHAHAR") == "ŞAHAR"
    assert typed("Shahar") == "Şahar"
    assert typed("CHO'L") == "ÇÖL"
    assert typed("G'") == "Ğ"
    assert pair_swap("O", "’") == Swap(1, "Ö")
    assert pair_swap("S", "h") == Swap(1, "Ş")
    assert pair_swap("s", "H") == Swap(1, "ş")


def test_linkish():
    for word in ("@go'zal", "#sho'x", "https://shahar.uz", "www.shahar", "a_sh", "x=go'", "C:\\cho"):
        assert typed(word) == word, word
    assert typed("2o'") == "2o'"
    assert typed("bo'lsa @go'zal") == "bölsa @go'zal"


def test_pair_swap():
    assert pair_swap("o", "'") == Swap(1, "ö")
    assert pair_swap("g", "ʻ") == Swap(1, "ğ")
    assert pair_swap("s", "h") == Swap(1, "ş")
    assert pair_swap("c", "h") == Swap(1, "ç")
    assert pair_swap("a", "'") is None
    assert pair_swap("o", "h") is None
    assert pair_swap(None, "'") is None
    assert pair_swap("", "h") is None
    assert pair_swap(" ", "h") is None
    assert pair_swap("o", "'", "to") == Swap(1, "ö")
    assert pair_swap("o", "'", "@go") is None
    assert pair_swap("s", "h", "http://s") is None


def test_late_swap():
    """The text before the caret, read back after the key went through."""
    assert late_swap("to'", "'") == ("o'", "ö", "tö")
    assert late_swap("Bu to'", "'") == ("o'", "ö", "tö")
    assert late_swap("Bu\nto'", "'") == ("o'", "ö", "tö")
    assert late_swap("to’", "'") == ("o’", "ö", "tö")  # Word turned ' into ’
    assert late_swap("o'", "'") == ("o'", "ö", "ö")
    assert late_swap("Tosh", "h") == ("sh", "ş", "Toş")
    assert late_swap("TOSH", "H") == ("SH", "Ş", "TOŞ")
    assert late_swap("ch", "h") == ("ch", "ç", "ç")
    assert late_swap("to", "'") is None  # the program has not shown the key yet
    assert late_swap("tos", "h") is None
    assert late_swap("ta'", "'") is None  # no pair
    assert late_swap("o '", "'") is None
    assert late_swap("'", "'") is None
    assert late_swap("", "h") is None
    assert late_swap("@go'", "'") is None
    assert late_swap("site.uz/sh", "h") is None
    assert is_shown("to’", "'") and is_shown("sh", "h")
    assert not is_shown("sH", "h") and not is_shown("", "'") and not is_shown("to", "'")


def test_late_apostrophe_in_box():
    """The bug: "toliq" typed, caret moved back after the o, ' typed."""
    box = Box().type("toliq")
    assert box.text == "toliq"
    box.move_to(2).type("'")
    assert str(box) == "tö|liq"
    box = Box().type("galaba").move_to(1).type("'")
    assert str(box) == "ğ|alaba"
    box = Box().type("sahar").move_to(1).type("h")
    assert str(box) == "ş|ahar"
    box = Box().type("ciroyli").move_to(1).type("h")
    assert str(box) == "ç|iroyli"
    box = Box().type("bu toliq").move_to(5).type("’")
    assert str(box) == "bu tö|liq"
    box = Box().type("toliq").move_to(2).type("'ra")  # typing on after the late swap
    assert str(box) == "töra|liq"


def test_late_upper_case():
    box = Box().type("OZBEKISTON").move_to(1).type("'")
    assert str(box) == "Ö|ZBEKISTON"
    box = Box().type("Sahar").move_to(1).type("h")
    assert str(box) == "Ş|ahar"
    box = Box().type("TOSKENT").move_to(3).type("H")
    assert str(box) == "TOŞ|KENT"


def test_late_linkish():
    box = Box().type("@gozal").move_to(3).type("'")
    assert str(box) == "@go'|zal"
    box = Box().type("http://sahar").move_to(8).type("h")
    assert str(box) == "http://sh|ahar"
    box = Box().type("to liq").move_to(3).type("'")
    assert str(box) == "to '|liq"  # a space before: no pair
    box = Box().type("taliq").move_to(2).type("'")
    assert str(box) == "ta'|liq"


def test_backspace_after_adopt():
    t = Typist()
    t.feed("'")  # the late key, fed with no context
    t.adopt("o'", "ö", "tö")
    assert t.backspace() == Swap(1, "o'")
    assert (t.prev, t.word, t.undo) == ("'", "to'", None)
    assert t.backspace() is None  # only right after the swap

    box = Box().type("toliq").move_to(2).type("'")
    assert str(box) == "tö|liq"
    box.backspace()
    assert str(box) == "to'|liq"
    box.backspace()
    assert str(box) == "to|liq"

    box = Box().type("sahar").move_to(1).type("h").backspace()
    assert str(box) == "sh|ahar"
    box = Box().type("TOSKENT").move_to(3).type("H").backspace()
    assert str(box) == "TOSH|KENT"

    box = Box().type("toliq").move_to(2).type("'r").backspace()  # another key came first
    assert str(box) == "tö|liq"


def test_normal_backspace_undo():
    box = Box().type("o'")
    assert str(box) == "ö|"
    box.backspace()
    assert str(box) == "o'|"
    box = Box().type("sh").backspace().backspace().backspace()
    assert str(box) == "|"


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print("ok", name)
