"""Old and OpenDocument formats (.doc .xls .ppt .rtf .odt .ods .odp) are
opened with LibreOffice (headless) and saved as .docx/.xlsx/.pptx, which
documents.py then converts."""
import pathlib
import subprocess
import tempfile
import threading

FORMATS = {"doc": "docx", "rtf": "docx", "odt": "docx",
           "xls": "xlsx", "ods": "xlsx",
           "ppt": "pptx", "odp": "pptx"}

_cfg = {"soffice": None, "profile": None}
# One LibreOffice profile can be used by one process at a time.
_lock = threading.Lock()


def setup(soffice, data_dir):
    _cfg["soffice"] = soffice
    _cfg["profile"] = pathlib.Path(data_dir) / "libreoffice-profile"


def available() -> bool:
    return bool(_cfg["soffice"])


class LegacyError(Exception):
    pass


def to_ooxml(data: bytes, ext: str) -> tuple[bytes, str]:
    """Return (file bytes, new extension)."""
    new_ext = FORMATS[ext]
    with tempfile.TemporaryDirectory(prefix="harfix-") as tmp:
        tmp = pathlib.Path(tmp)
        src = tmp / f"input.{ext}"
        src.write_bytes(data)
        cmd = [
            _cfg["soffice"],
            f"-env:UserInstallation={_cfg['profile'].resolve().as_uri()}",
            "--headless", "--norestore", "--nolockcheck", "--nodefault",
            "--convert-to", new_ext, "--outdir", str(tmp), str(src),
        ]
        with _lock:
            try:
                subprocess.run(cmd, capture_output=True, timeout=180, check=False)
            except subprocess.TimeoutExpired as e:
                raise LegacyError("timeout") from e
        out = tmp / f"input.{new_ext}"
        if not out.exists() or out.stat().st_size == 0:
            raise LegacyError("conversion failed")
        return out.read_bytes(), new_ext
