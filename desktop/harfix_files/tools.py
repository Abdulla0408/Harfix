"""Finding LibreOffice (old Office formats). The environment variable wins
over the usual install paths."""
import os
import pathlib
import shutil


def _first_existing(*paths):
    for p in paths:
        if p and pathlib.Path(p).exists():
            return str(p)
    return None


def find_soffice(*extra) -> str | None:
    return _first_existing(
        os.environ.get("SOFFICE_PATH"),
        *extra,
        shutil.which("soffice"),
        shutil.which("libreoffice"),
        r"C:\Program Files\LibreOffice\program\soffice.exe",
        r"C:\Program Files (x86)\LibreOffice\program\soffice.exe",
        "/usr/bin/soffice",
        "/usr/lib/libreoffice/program/soffice",
        "/Applications/LibreOffice.app/Contents/MacOS/soffice",
    )
