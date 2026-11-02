"""Builds real Word/Excel/PowerPoint files, converts them and checks that the
text changed while fonts, sizes, bold and formulas stayed the same."""
import io
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parents[1]))

import docx  # noqa: E402
import openpyxl  # noqa: E402
import pptx  # noqa: E402
from docx.shared import Pt  # noqa: E402

from harfix_bot.documents import convert_file  # noqa: E402


def _save(obj) -> bytes:
    b = io.BytesIO()
    obj.save(b)
    return b.getvalue()


def test_docx():
    d = docx.Document()
    p = d.add_paragraph()
    r1 = p.add_run("Toshkent s")
    r1.font.name, r1.font.size, r1.bold = "Times New Roman", Pt(14), True
    r2 = p.add_run("hahri — Oʻzbekiston poytaxti")  # "sh" split across runs
    r2.font.name, r2.italic = "Arial", True
    d.add_paragraph("Ўзбекистон Республикаси")
    d.sections[0].header.paragraphs[0].text = "Choʻl"
    t = d.add_table(rows=1, cols=1)
    t.cell(0, 0).text = "Gʻalaba"

    out = docx.Document(io.BytesIO(convert_file(_save(d), "test.docx")))
    p0 = out.paragraphs[0]
    assert p0.text == "Toşkent şahri — Özbekiston poytaxti"
    a, b = p0.runs
    assert (a.font.name, a.font.size, a.bold) == ("Times New Roman", Pt(14), True)
    assert (b.font.name, b.italic) == ("Arial", True)
    assert out.paragraphs[1].text == "Özbekiston Respublikasi"
    assert out.sections[0].header.paragraphs[0].text == "Çöl"
    assert out.tables[0].cell(0, 0).text == "Ğalaba"


def test_xlsx():
    wb = openpyxl.Workbook()
    ws = wb.active
    ws["A1"] = "Shahar"
    ws["A1"].font = openpyxl.styles.Font(name="Calibri", size=16, bold=True)
    ws["A2"] = "Тошкент"
    ws["B1"] = 5
    ws["B2"] = "=B1*2"
    out = openpyxl.load_workbook(io.BytesIO(convert_file(_save(wb), "t.xlsx")))
    ws = out.active
    assert ws["A1"].value == "Şahar"
    assert (ws["A1"].font.name, ws["A1"].font.size, ws["A1"].font.bold) == ("Calibri", 16, True)
    assert ws["A2"].value == "Toşkent"
    assert ws["B2"].value == "=B1*2"


def test_pptx():
    prs = pptx.Presentation()
    s = prs.slides.add_slide(prs.slide_layouts[1])
    s.shapes.title.text = "Oʻzbekiston"
    body = s.placeholders[1].text_frame
    body.text = "Chiroyli shahar"
    body.paragraphs[0].runs[0].font.size = pptx.util.Pt(28)
    s.notes_slide.notes_text_frame.text = "eslatma: gʻoya"
    out = pptx.Presentation(io.BytesIO(convert_file(_save(prs), "t.pptx")))
    s = out.slides[0]
    assert s.shapes.title.text == "Özbekiston"
    run = s.placeholders[1].text_frame.paragraphs[0].runs[0]
    assert run.text == "Çiroyli şahar" and run.font.size == pptx.util.Pt(28)
    assert s.notes_slide.notes_text_frame.text == "eslatma: ğoya"


def test_txt():
    assert convert_file("Салом, oʻrtoq!".encode(), "a.txt").decode() == "Salom, örtoq!"
    assert convert_file("шаҳар".encode("cp1251", errors="ignore") or b"", "a.txt")


if __name__ == "__main__":
    for name, fn in list(globals().items()):
        if name.startswith("test_"):
            fn()
            print("ok", name)
