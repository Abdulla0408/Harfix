"""Builds the Harfix keyboard dictionary from the Uzbek Wikipedia.

    python android/tools/build_dictionary.py uzwiki-latest-pages-articles.xml.bz2 [--top 80000]

Download the dump from https://dumps.wikimedia.org/uzwiki/latest/ (not kept in git). Articles are
stripped of wiki markup, split into words, every word is converted to the new alphabet with
core/yangi_alifbo (old Latin and Cyrillic both), and the most frequent words are written to
android/app/src/main/assets/dict/uz.txt as "word<TAB>weight" lines, most frequent first.
weight is 1..255 on a log scale. Words written with a capital letter nearly always (names)
keep it. Tutuq is stored as an ASCII apostrophe, as keyboards type it.

Source text: Wikipedia contributors, CC BY-SA 4.0. The app and the README say so.
"""
import argparse
import bz2
import html
import collections
import math
import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "core"))
from yangi_alifbo import convert_to  # noqa: E402

OUT = ROOT / "android" / "app" / "src" / "main" / "assets" / "dict" / "uz.txt"

APOS = "'ʻʼ‘’`´"
LAT = "A-Za-zÖöĞğŞşÇçÑñ"
CYR = "А-Яа-яЁёЎўҚқҒғҲҳ"
WORD = re.compile(f"[{LAT}{CYR}]+(?:[{APOS}][{LAT}{CYR}]+)*")

_REF = re.compile(r"<ref[^>/]*/>|<ref[^>]*>.*?</ref>", re.S | re.I)
_COMMENT = re.compile(r"<!--.*?-->", re.S)
_TEMPLATE = re.compile(r"\{\{[^{}]*\}\}")
_TABLE = re.compile(r"\{\|.*?\|\}", re.S)
_FILE_LINK = re.compile(r"\[\[(?:File|Fayl|Rasm|Image|Category|Turkum|Файл|Расм|Категория|Тоифа):[^\[\]]*(?:\[\[[^\]]*\]\][^\[\]]*)*\]\]", re.I)
_LINK = re.compile(r"\[\[(?:[^|\]]*\|)?([^\]]*)\]\]")
_EXT_LINK = re.compile(r"\[(?:https?:)?//\S+\s*([^\]]*)\]")
_URL = re.compile(r"https?://\S+")
_TAG = re.compile(r"<[^>]+>")
_MARKS = re.compile(r"'{2,}|={2,}|__[A-Z]+__")


def plain(wikitext: str) -> str:
    t = _COMMENT.sub(" ", html.unescape(wikitext))
    t = _REF.sub(" ", t)
    for _ in range(6):  # nested templates, innermost first
        t, n = _TEMPLATE.subn(" ", t)
        if not n:
            break
    t = _TABLE.sub(" ", t)
    t = _FILE_LINK.sub(" ", t)
    t = _LINK.sub(r"\1", t)
    t = _EXT_LINK.sub(r"\1", t)
    t = _URL.sub(" ", t)
    t = _TAG.sub(" ", t)
    return _MARKS.sub(" ", t)


def pages(path, limit=0):
    """(namespace, text) of every page that is not a redirect. A cut-off download is read up
    to where it stops."""
    try:
        yield from _pages(path, limit)
    except (EOFError, ET.ParseError) as e:
        print(f"dump ends early ({e}); using what was read", file=sys.stderr)


def _pages(path, limit):
    seen = 0
    with bz2.open(path, "rb") as f:
        ns = text = None
        redirect = False
        for _, el in ET.iterparse(f, events=("end",)):
            tag = el.tag.rsplit("}", 1)[-1]
            if tag == "ns":
                ns = el.text
            elif tag == "redirect":
                redirect = True
            elif tag == "text":
                text = el.text or ""
            elif tag == "page":
                if not redirect:
                    yield ns, text
                    seen += 1
                    if limit and seen >= limit:
                        return
                ns, text, redirect = None, None, False
                el.clear()


# wiki words that survive markup stripping
NOISE = {"turkum", "nbsp", "fayl", "rasm", "thumb", "px", "left", "right", "center", "ref", "url",
         "isbn", "doi", "commons", "infobox", "wikidata",
         # common words of other languages in references and names
         "the", "of", "and", "in", "de", "al", "la", "le", "des", "du", "von", "van", "der", "to", "on",
         "is", "by", "for", "at", "an", "as", "or", "from", "et", "el", "il", "di", "del", "los", "las",
         "und", "die", "das", "den", "ooo", "ii", "iii", "iv", "vi", "vii", "viii", "ix", "xi", "xii",
         "xiii", "xiv", "xv", "xvi", "xvii", "xviii", "xix", "xx", "xxi"}


def foreign(word: str) -> bool:
    """Letters the Uzbek Latin alphabet does not have: w, and c outside ç (English, Russian names)."""
    return bool(re.search(r"[wc]", word))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dump")
    ap.add_argument("--top", type=int, default=80000)
    ap.add_argument("--min", type=int, default=5)
    ap.add_argument("--pages", type=int, default=0, help="stop after this many pages (quick test)")
    ap.add_argument("--out", default=str(OUT))
    args = ap.parse_args()

    counts = collections.Counter()  # lower-case token -> count
    capital = collections.Counter()  # how often it was capitalised (not at a sentence start)
    n_pages = 0
    out = pathlib.Path(args.out)
    for ns, text in pages(args.dump, args.pages):
        if ns != "0":
            continue
        n_pages += 1
        body = plain(text)
        prev_end = 0
        for m in WORD.finditer(body):
            tok = m.group(0)
            low = tok.lower()
            counts[low] += 1
            if tok[0].isupper() and not tok.isupper():
                # a capital right after . ! ? or a line start says nothing about names
                gap = body[prev_end:m.start()]
                if not re.search(r"[.!?\n]\s*$", gap) and prev_end:
                    capital[low] += 1
            prev_end = m.end()
        if n_pages % 20000 == 0:
            print(f"{n_pages} pages, {len(counts)} distinct", file=sys.stderr)

    print(f"{n_pages} articles, {len(counts)} distinct tokens", file=sys.stderr)
    merged = collections.Counter()
    caps = collections.Counter()
    for low, c in counts.items():
        if c < args.min or len(low) > 24:
            continue
        new = convert_to(low, "new").lower()
        for a in APOS:
            new = new.replace(a, "'")
        if new in NOISE or foreign(new) or not re.fullmatch(r"[a-zöğşçñ]+(?:'[a-zöğşçñ]+)*", new):
            continue
        if len(new) == 1 and new not in ("u", "o", "e"):
            continue
        merged[new] += c
        caps[new] += capital[low]

    top = merged.most_common(args.top)
    peak = math.log(top[0][1])
    floor = math.log(args.min)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("w", encoding="utf-8", newline="\n") as f:
        f.write("# Harfix lugʻati: oʻzbekcha Vikipediya maqolalari (uzwiki), CC BY-SA 4.0. "
                "android/tools/build_dictionary.py yasaydi.\n")
        for word, c in top:
            weight = max(1, min(255, round(1 + 254 * (math.log(c) - floor) / (peak - floor))))
            if caps[word] > 0.6 * c:
                word = word[0].upper() + word[1:]
            f.write(f"{word}\t{weight}\n")
    print(f"wrote {len(top)} words to {out} ({out.stat().st_size // 1024} KB)", file=sys.stderr)


if __name__ == "__main__":
    main()
