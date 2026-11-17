"""Convert the text of a PDF to another alphabet.

Lines whose text changes are removed (redaction without a fill, so
backgrounds, images and drawings stay) and written again at the same
position, size and colour with a font that has ö ğ ş ç and Cyrillic.
Lines that do not change keep their original font. Scanned PDFs have no
text layer: NoText is raised (Harfix does not read scans).
"""
import functools
import os

from yangi_alifbo import convert_runs

try:
    import pymupdf
except ImportError:  # PDF support is optional
    pymupdf = None

MIN_CHARS = 30  # a page with fewer characters and a page-sized picture is a scan


def available() -> bool:
    return pymupdf is not None


class NoText(Exception):
    pass


class Encrypted(Exception):
    pass


class TooManyPages(Exception):
    def __init__(self, pages):
        super().__init__(pages)
        self.pages = pages


_WIN = os.path.join(os.environ.get("WINDIR", r"C:\Windows"), "Fonts")
_DJ = "/usr/share/fonts/truetype/dejavu"
_LIB = "/usr/share/fonts/truetype/liberation"
_MAC = "/System/Library/Fonts/Supplemental"


def _paths(win, dejavu, liberation, mac):
    return [os.path.join(_WIN, win), os.path.join(_LIB, liberation),
            os.path.join(_DJ, dejavu), os.path.join(_MAC, mac)]


# (family, bold, italic) -> candidate font files, first existing one wins.
_FONTS = {
    ("sans", False, False): _paths("arial.ttf", "DejaVuSans.ttf", "LiberationSans-Regular.ttf", "Arial.ttf"),
    ("sans", True, False): _paths("arialbd.ttf", "DejaVuSans-Bold.ttf", "LiberationSans-Bold.ttf", "Arial Bold.ttf"),
    ("sans", False, True): _paths("ariali.ttf", "DejaVuSans-Oblique.ttf", "LiberationSans-Italic.ttf", "Arial Italic.ttf"),
    ("sans", True, True): _paths("arialbi.ttf", "DejaVuSans-BoldOblique.ttf", "LiberationSans-BoldItalic.ttf",
                                 "Arial Bold Italic.ttf"),
    ("serif", False, False): _paths("times.ttf", "DejaVuSerif.ttf", "LiberationSerif-Regular.ttf", "Times New Roman.ttf"),
    ("serif", True, False): _paths("timesbd.ttf", "DejaVuSerif-Bold.ttf", "LiberationSerif-Bold.ttf",
                                   "Times New Roman Bold.ttf"),
    ("serif", False, True): _paths("timesi.ttf", "DejaVuSerif-Italic.ttf", "LiberationSerif-Italic.ttf",
                                   "Times New Roman Italic.ttf"),
    ("serif", True, True): _paths("timesbi.ttf", "DejaVuSerif-BoldItalic.ttf", "LiberationSerif-BoldItalic.ttf",
                                  "Times New Roman Bold Italic.ttf"),
    ("mono", False, False): _paths("cour.ttf", "DejaVuSansMono.ttf", "LiberationMono-Regular.ttf", "Courier New.ttf"),
    ("mono", True, False): _paths("courbd.ttf", "DejaVuSansMono-Bold.ttf", "LiberationMono-Bold.ttf",
                                  "Courier New Bold.ttf"),
    ("mono", False, True): _paths("couri.ttf", "DejaVuSansMono-Oblique.ttf", "LiberationMono-Italic.ttf",
                                  "Courier New Italic.ttf"),
    ("mono", True, True): _paths("courbi.ttf", "DejaVuSansMono-BoldOblique.ttf", "LiberationMono-BoldItalic.ttf",
                                 "Courier New Bold Italic.ttf"),
}


@functools.lru_cache(maxsize=None)
def _font(family: str, bold: bool, italic: bool):
    for key in ((family, bold, italic), (family, bold, False), (family, False, False), ("sans", False, False)):
        for path in _FONTS[key]:
            if os.path.exists(path):
                return pymupdf.Font(fontfile=path)
    raise RuntimeError("No Unicode font found for PDF text (install fonts-dejavu or fonts-liberation)")


# Common document fonts: family -> (regular, bold, italic, bold italic) files.
# Metric-compatible free fonts (Carlito, Caladea, Liberation) are used when
# the original is not installed, e.g. on a Linux server.
_NAMED = {
    "calibri": [("calibri.ttf", "calibrib.ttf", "calibrii.ttf", "calibriz.ttf"),
                ("Carlito-Regular.ttf", "Carlito-Bold.ttf", "Carlito-Italic.ttf", "Carlito-BoldItalic.ttf")],
    "carlito": [("Carlito-Regular.ttf", "Carlito-Bold.ttf", "Carlito-Italic.ttf", "Carlito-BoldItalic.ttf")],
    "cambria": [("cambria.ttc", "cambriab.ttf", "cambriai.ttf", "cambriaz.ttf"),
                ("Caladea-Regular.ttf", "Caladea-Bold.ttf", "Caladea-Italic.ttf", "Caladea-BoldItalic.ttf")],
    "caladea": [("Caladea-Regular.ttf", "Caladea-Bold.ttf", "Caladea-Italic.ttf", "Caladea-BoldItalic.ttf")],
    "georgia": [("georgia.ttf", "georgiab.ttf", "georgiai.ttf", "georgiaz.ttf")],
    "verdana": [("verdana.ttf", "verdanab.ttf", "verdanai.ttf", "verdanaz.ttf")],
    "tahoma": [("tahoma.ttf", "tahomabd.ttf", "tahoma.ttf", "tahomabd.ttf")],
    "segoeui": [("segoeui.ttf", "segoeuib.ttf", "segoeuii.ttf", "segoeuiz.ttf")],
    "trebuchet": [("trebuc.ttf", "trebucbd.ttf", "trebucit.ttf", "trebucbi.ttf")],
    "garamond": [("GARA.TTF", "GARABD.TTF", "GARAIT.TTF", "GARABD.TTF")],
    "bookantiqua": [("BKANT.TTF", "ANTQUAB.TTF", "ANTQUAI.TTF", "ANTQUABI.TTF")],
    "centurygothic": [("GOTHIC.TTF", "GOTHICB.TTF", "GOTHICI.TTF", "GOTHICBI.TTF")],
    "liberationsans": [("LiberationSans-Regular.ttf", "LiberationSans-Bold.ttf", "LiberationSans-Italic.ttf",
                        "LiberationSans-BoldItalic.ttf")],
    "liberationserif": [("LiberationSerif-Regular.ttf", "LiberationSerif-Bold.ttf", "LiberationSerif-Italic.ttf",
                         "LiberationSerif-BoldItalic.ttf")],
    "dejavusans": [("DejaVuSans.ttf", "DejaVuSans-Bold.ttf", "DejaVuSans-Oblique.ttf", "DejaVuSans-BoldOblique.ttf")],
    "dejavuserif": [("DejaVuSerif.ttf", "DejaVuSerif-Bold.ttf", "DejaVuSerif-Italic.ttf",
                     "DejaVuSerif-BoldItalic.ttf")],
}
_FONT_DIRS = [_WIN, os.path.expanduser(r"~\AppData\Local\Microsoft\Windows\Fonts"),
              "/usr/share/fonts/truetype/crosextra", _LIB, _DJ, "/usr/share/fonts/truetype/msttcorefonts",
              _MAC, "/Library/Fonts"]


def _family_key(name: str) -> str:
    name = name.split("+", 1)[-1]          # drop the subset prefix "ABCDEF+"
    base = name.split("-")[0].split(",")[0]
    base = base.lower().replace(" ", "")
    for suffix in ("psmt", "mt", "ps", "bold", "italic", "regular"):
        if base.endswith(suffix) and len(base) > len(suffix) + 2:
            base = base[: -len(suffix)]
    return base


@functools.lru_cache(maxsize=None)
def _named_font(key: str, bold: bool, italic: bool):
    idx = (2 if italic else 0) + (1 if bold else 0)
    for files in _NAMED.get(key, []):
        for d in _FONT_DIRS:
            path = os.path.join(d, files[idx])
            if os.path.exists(path):
                try:
                    return pymupdf.Font(fontfile=path)
                except Exception:
                    continue
    return None


def _font_for(span, text: str):
    """The original font family if it is installed and has every letter
    of `text`, otherwise a generic font of the same style."""
    family, bold, italic = _style(span)
    named = _named_font(_family_key(span["font"]), bold, italic)
    if named is not None and all(named.has_glyph(ord(c)) for c in set(text) if not c.isspace()):
        return named
    return _font(family, bold, italic)


_SERIF = ("times", "serif", "georgia", "cambria", "garamond", "roman", "antiqua", "minion", "palatino")
_MONO = ("courier", "mono", "consol")
_BOLD = ("bold", "black", "heavy", "semibold", "demi")
_ITALIC = ("italic", "oblique")


def _style(span) -> tuple[str, bool, bool]:
    name, flags = span["font"].lower(), span["flags"]
    mono = bool(flags & 8) or any(k in name for k in _MONO)
    serif = not mono and (bool(flags & 4) or any(k in name for k in _SERIF))
    bold = bool(flags & 16) or any(k in name for k in _BOLD)
    italic = bool(flags & 2) or any(k in name for k in _ITALIC)
    return ("mono" if mono else "serif" if serif else "sans"), bold, italic


def _rgb(c: int):
    return ((c >> 16) & 255) / 255, ((c >> 8) & 255) / 255, (c & 255) / 255


def _apply_redactions(page):
    try:
        page.apply_redactions(images=pymupdf.PDF_REDACT_IMAGE_NONE,
                              graphics=pymupdf.PDF_REDACT_LINE_ART_NONE)
    except (TypeError, AttributeError):  # older PyMuPDF without `graphics`
        page.apply_redactions(images=pymupdf.PDF_REDACT_IMAGE_NONE)


def _page_edits(page, target):
    """[(spans, new texts)] for horizontal lines whose text changes, and
    the number of real characters on the page."""
    edits, chars, boxes = [], 0, []
    flags = pymupdf.TEXT_PRESERVE_WHITESPACE | pymupdf.TEXT_MEDIABOX_CLIP
    for block in page.get_text("dict", flags=flags)["blocks"]:
        if block.get("type") != 0:
            continue
        for line in block["lines"]:
            spans = line["spans"]
            boxes.append(pymupdf.Rect(line["bbox"]))
            old = [s["text"] for s in spans]
            chars += sum(len(x.strip()) for x in old)
            dx, dy = line["dir"]
            if abs(dy) > 1e-3 or dx <= 0 or not spans:
                continue  # rotated text is left as it is
            new = convert_runs(old, target)
            if new != old:
                edits.append((spans, new, pymupdf.Rect(line["bbox"])))
    page_right = page.rect.x1 - 28  # keep about 1 cm from the page edge
    out = []
    for spans, new, box in edits:
        right = page_right
        for other in boxes:
            same_row = min(other.y1, box.y1) - max(other.y0, box.y0) > 0.5 * min(other.height, box.height)
            if same_row and other.x0 >= box.x1 - 1:
                right = min(right, other.x0 - 4)  # next text on the same row (e.g. a table cell)
        out.append((spans, new, max(right, box.x1)))
    return out, chars


def _rewrite(page, edits):
    for spans, _, _ in edits:
        for s in spans:
            r = pymupdf.Rect(s["bbox"])
            pad = r.height * 0.2  # do not touch the lines above and below
            page.add_redact_annot(pymupdf.Rect(r.x0, r.y0 + pad, r.x1, r.y1 - pad), fill=False)
    _apply_redactions(page)
    for spans, new, right in edits:
        _write_line(page, spans, new, right)


def _write_line(page, spans, texts, right):
    """Write one line again. Spans keep their original gaps, so a word that
    got shorter (sh → ş) does not leave a hole before the next span. A line
    that got longer may use the free space up to `right`; only if it does not
    fit there is its font size reduced."""
    fonts = [_font_for(s, t) for s, t in zip(spans, texts)]
    widths = [f.text_length(t, fontsize=s["size"]) for f, s, t in zip(fonts, spans, texts)]
    gaps = [0.0] + [max(spans[i]["bbox"][0] - spans[i - 1]["bbox"][2], 0.0) for i in range(1, len(spans))]
    room = right - spans[0]["origin"][0]
    new_width = sum(widths) + sum(gaps)
    k = 1.0
    if room > 0 and new_width > room:
        k = max(room / new_width, 0.6)
    x = spans[0]["origin"][0]
    for s, text, font, width, gap in zip(spans, texts, fonts, widths, gaps):
        x += gap * k
        if text.strip():
            tw = pymupdf.TextWriter(page.rect, color=_rgb(s["color"]))
            tw.append((x, s["origin"][1]), text, font=font, fontsize=s["size"] * k)
            tw.write_text(page)
        x += width * k


def _picture_page(page) -> bool:
    """True if one picture covers at least half of the page (a scan)."""
    area = abs(page.rect)
    for info in page.get_image_info():
        if area and abs(pymupdf.Rect(info["bbox"]) & page.rect) >= 0.5 * area:
            return True
    return False


def convert_pdf(data: bytes, target: str = "new", max_pages: int = 300) -> bytes:
    doc = pymupdf.open(stream=data, filetype="pdf")
    try:
        if doc.needs_pass:
            raise Encrypted()
        if doc.page_count > max_pages:
            raise TooManyPages(doc.page_count)
        chars, changed, scans = 0, False, 0
        for page in doc:
            if page.rotation and hasattr(page, "remove_rotation"):
                page.remove_rotation()
            edits, n = _page_edits(page, target)
            chars += n
            if n < MIN_CHARS and _picture_page(page):
                scans += 1
            if edits:
                _rewrite(page, edits)
                changed = True
        if chars == 0 or scans == doc.page_count:
            raise NoText()
        if not changed:
            return data
        try:
            doc.subset_fonts()
        except Exception:  # subsetting is only an optimisation
            pass
        return doc.tobytes(garbage=3, deflate=True)
    finally:
        doc.close()
