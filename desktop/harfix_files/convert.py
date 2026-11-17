"""One entry point for converting any supported file: Office/txt, PDF (with a
text layer) and old Office formats (via LibreOffice). Used by the Telegram bot
and the desktop app. Pictures and scanned PDFs are not read (no OCR)."""
from yangi_alifbo import convert_to

from . import legacy, pdf
from .documents import SUPPORTED as OFFICE
from .documents import UnsupportedFile, convert_file

MAX_PDF_PAGES = 300


class FeatureOff(Exception):
    """A tool the file needs is missing (pdf_off, legacy_off), or the PDF is a
    scan with no text in it (pdf_scan)."""

    def __init__(self, key: str):
        super().__init__(key)
        self.key = key


def ext_of(name: str) -> str:
    return name.rsplit(".", 1)[-1].lower() if "." in name else ""


def kind_of(name: str) -> str | None:
    """file (Office/txt), pdf, legacy (old Office) or None."""
    ext = ext_of(name)
    if ext in OFFICE:
        return "file"
    if ext == "pdf":
        return "pdf"
    if ext in legacy.FORMATS:
        return "legacy"
    return None


def out_name(name: str, target: str, new_ext: str | None = None) -> str:
    """Result file name: the name itself is converted too."""
    stem, dot, ext = name.rpartition(".")
    if not dot:
        stem, ext = name, "txt"
    return f"{convert_to(stem, target)}.{new_ext or ext}"


def convert_document(data: bytes, name: str, target: str, max_pdf_pages: int = MAX_PDF_PAGES) -> dict:
    """Blocking. Returns {"kind", "file": bytes, "name": str, "how": "done" | "legacy", "ext"}.
    Raises FeatureOff, UnsupportedFile, pdf.Encrypted, pdf.TooManyPages, legacy.LegacyError."""
    kind = kind_of(name)
    if kind == "file":
        return {"kind": kind, "file": convert_file(data, name, target), "name": out_name(name, target),
                "how": "done", "ext": ext_of(name)}
    if kind == "pdf":
        if not pdf.available():
            raise FeatureOff("pdf_off")
        try:
            out = pdf.convert_pdf(data, target, max_pdf_pages)
        except pdf.NoText:
            raise FeatureOff("pdf_scan")
        return {"kind": kind, "file": out, "name": out_name(name, target), "how": "done", "ext": "pdf"}
    if kind == "legacy":
        if not legacy.available():
            raise FeatureOff("legacy_off")
        modern, new_ext = legacy.to_ooxml(data, ext_of(name))
        return {"kind": kind, "file": convert_file(modern, f"file.{new_ext}", target),
                "name": out_name(name, target, new_ext), "how": "legacy", "ext": new_ext}
    raise UnsupportedFile(name)
