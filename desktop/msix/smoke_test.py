"""Checks the bundled runtime without a window: run it with the package's own Python,
    out\\stage\\runtime\\python.exe msix\\smoke_test.py
Every module Harfix needs must import from the package, .NET (pythonnet) must load
WinForms and the WebView2 control, and a Word, Excel, PowerPoint, txt and PDF file must
convert. Exits non-zero on the first failure."""
import io
import sys
import zipfile

print("python", sys.version, sys.executable)
assert sys.version_info[:2] == (3, 12), sys.version
assert "site-packages" in " ".join(sys.path), sys.path

import fitz  # noqa: E402
import lxml.etree  # noqa: E402,F401
import PIL.Image  # noqa: E402,F401
import pystray  # noqa: E402,F401
import webview  # noqa: E402,F401
from webview.platforms import winforms  # noqa: E402,F401  (loads .NET, WinForms and WebView2)

import harfix_desktop.app  # noqa: E402,F401
from harfix_desktop import VERSION, paths  # noqa: E402
from harfix_files import convert, pdf  # noqa: E402
from yangi_alifbo import convert as to_new  # noqa: E402

print("harfix", VERSION, "| installed layout:", paths.INSTALLED, "| pdf:", pdf.available())
assert paths.INSTALLED, "runtime\\pythonw.exe / app\\ layout not recognised"
assert to_new("Oʻzbekiston gʻalaba shahar choy") == "Özbekiston ğalaba şahar çoy", to_new("Oʻzbekiston")

TEXT = "Oʻzbekiston gʻalaba"
WANT = "Özbekiston ğalaba"


def office(kind: str) -> bytes:
    parts = {
        "docx": ("word/document.xml",
                 '<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body>'
                 f"<w:p><w:r><w:t>{TEXT}</w:t></w:r></w:p></w:body></w:document>"),
        "xlsx": ("xl/sharedStrings.xml",
                 '<sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">'
                 f"<si><t>{TEXT}</t></si></sst>"),
        "pptx": ("ppt/slides/slide1.xml",
                 '<p:sld xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main" '
                 'xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"><p:cSld><p:spTree><p:sp><p:txBody>'
                 f"<a:p><a:r><a:t>{TEXT}</a:t></a:r></a:p></p:txBody></p:sp></p:spTree></p:cSld></p:sld>"),
    }
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w") as z:
        z.writestr("[Content_Types].xml", "<Types/>")
        z.writestr(*parts[kind])
    return buf.getvalue()


for kind in ("docx", "xlsx", "pptx"):
    out = convert.convert_document(office(kind), f"hisobot.{kind}", "new")
    with zipfile.ZipFile(io.BytesIO(out["file"])) as z:
        body = "".join(z.read(n).decode("utf-8") for n in z.namelist())
    assert WANT in body, (kind, body[:300])
    print("ok", kind, out["name"])

out = convert.convert_document(TEXT.encode("utf-8"), "matn.txt", "new")
assert out["file"].decode("utf-8") == WANT, out["file"]
print("ok txt")

doc = fitz.open()
doc.new_page().insert_text((72, 72), "Oʻzbekiston", fontsize=14, fontname="arial",
                           fontfile=r"C:\Windows\Fonts\arial.ttf")
out = convert.convert_document(doc.tobytes(), "hujjat.pdf", "new")
text = fitz.open(stream=out["file"], filetype="pdf")[0].get_text()
assert "Özbekiston" in text, text
print("ok pdf")
print("SMOKE TEST PASSED")
