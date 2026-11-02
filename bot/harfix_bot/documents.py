"""Convert Word / Excel / PowerPoint files to the new alphabet.

Only the text inside text nodes is changed. Fonts, styles, sizes, colours,
images, formulas and every other part of the file are copied as they are.
"""
import io
import re
import zipfile

from lxml import etree
from yangi_alifbo import convert, convert_runs

W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
A = "http://schemas.openxmlformats.org/drawingml/2006/main"
S = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"

# text tag -> paragraph tag that groups runs of that text
_TEXT_TO_PARA = {
    f"{{{W}}}t": f"{{{W}}}p",
    f"{{{A}}}t": f"{{{A}}}p",
    f"{{{S}}}t": (f"{{{S}}}si", f"{{{S}}}is"),
}

# Which parts of each format hold user text.
_PARTS = {
    "docx": re.compile(r"^word/(document|header\d*|footer\d*|footnotes|endnotes|comments|diagrams/data\d*)\.xml$"),
    "pptx": re.compile(r"^ppt/(slides/slide\d+|notesSlides/notesSlide\d+|diagrams/data\d+)\.xml$"),
    "xlsx": re.compile(r"^xl/(sharedStrings|worksheets/sheet\d+)\.xml$"),
}
SUPPORTED = {"docx", "docm", "pptx", "pptm", "xlsx", "xlsm", "txt"}
_FAMILY = {"docx": "docx", "docm": "docx", "pptx": "pptx", "pptm": "pptx", "xlsx": "xlsx", "xlsm": "xlsx"}


class UnsupportedFile(Exception):
    pass


def _paragraph_of(el):
    para = _TEXT_TO_PARA[el.tag]
    paras = para if isinstance(para, tuple) else (para,)
    p = el.getparent()
    while p is not None and p.tag not in paras:
        p = p.getparent()
    return p


def _convert_xml(data: bytes) -> bytes:
    parser = etree.XMLParser(remove_blank_text=False, resolve_entities=False, huge_tree=True)
    root = etree.fromstring(data, parser)
    groups = {}  # paragraph element -> [text elements] in document order
    order = []
    for el in root.iter(*_TEXT_TO_PARA.keys()):
        key = _paragraph_of(el)
        if key is None:
            key = el
        if key not in groups:
            groups[key] = []
            order.append(key)
        groups[key].append(el)
    changed = False
    for key in order:
        els = groups[key]
        old = [e.text or "" for e in els]
        new = convert_runs(old)
        for e, o, n in zip(els, old, new):
            if o != n:
                e.text = n
                changed = True
    if not changed:
        return data
    standalone = b'standalone="yes"' in data[:200]
    return etree.tostring(root, xml_declaration=True, encoding="UTF-8",
                          standalone=True if standalone else None)


def convert_office(data: bytes, ext: str) -> bytes:
    family = _FAMILY[ext]
    parts = _PARTS[family]
    src = zipfile.ZipFile(io.BytesIO(data))
    out = io.BytesIO()
    with zipfile.ZipFile(out, "w") as dst:
        for info in src.infolist():
            content = src.read(info.filename)
            if parts.match(info.filename):
                content = _convert_xml(content)
            dst.writestr(info, content, compress_type=info.compress_type)
    return out.getvalue()


def _decode_text(data: bytes) -> tuple[str, str]:
    for enc in ("utf-8-sig", "utf-8", "cp1251"):
        try:
            return data.decode(enc), ("utf-8" if enc.startswith("utf-8") else enc)
        except UnicodeDecodeError:
            continue
    raise UnsupportedFile("encoding")


def convert_file(data: bytes, filename: str) -> bytes:
    ext = filename.rsplit(".", 1)[-1].lower() if "." in filename else ""
    if ext not in SUPPORTED:
        raise UnsupportedFile(ext)
    if ext == "txt":
        text, _ = _decode_text(data)
        return convert(text).encode("utf-8")
    try:
        return convert_office(data, ext)
    except (zipfile.BadZipFile, KeyError, etree.XMLSyntaxError) as e:
        raise UnsupportedFile(ext) from e
