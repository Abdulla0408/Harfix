"""Converts android/tools/extra_words.txt (old Latin) to the new alphabet:
android/app/src/main/assets/dict/uz-extra.txt ("word<TAB>weight"), loaded next to uz.txt.

    python android/tools/build_extra.py
"""
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "core"))
from yangi_alifbo import convert_to  # noqa: E402

SRC = ROOT / "android" / "tools" / "extra_words.txt"
OUT = ROOT / "android" / "app" / "src" / "main" / "assets" / "dict" / "uz-extra.txt"

rows = []
for line in SRC.read_text(encoding="utf-8").splitlines():
    line = line.strip()
    if not line or line.startswith("#"):
        continue
    parts = line.split()
    word = convert_to(parts[0].replace("'", "ʻ"), "new").lower().replace("ʼ", "'")
    weight = int(parts[1]) if len(parts) > 1 else 170
    rows.append((word, weight))
OUT.write_text("# Harfix: everyday words missing from the Wikipedia dictionary (android/tools/extra_words.txt, CC0).\n"
               + "".join(f"{w}\t{n}\n" for w, n in rows), encoding="utf-8", newline="\n")
print(f"{len(rows)} words -> {OUT}")
