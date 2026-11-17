"""Where things are: the user's data folder and the files shipped with the app.

Installed layout (made by installer/make_bundle.py):
    <install dir>/runtime/pythonw.exe   official, signed Python (Windows trusts it)
    <install dir>/app/harfix_desktop    this package (+ yangi_alifbo, harfix_files)
    <install dir>/harfix.ico
"""
import os
import pathlib

APP = "Harfix"
APP_ID = "Harfix.Desktop"  # Windows taskbar / Start menu identity

PACKAGE = pathlib.Path(__file__).resolve().parent
ROOT = PACKAGE.parent.parent  # the install dir when installed
INSTALLED = PACKAGE.parent.name == "app" and (ROOT / "runtime" / "pythonw.exe").exists()


def _packaged() -> bool:
    """True when Windows runs us from the Microsoft Store (MSIX) package (desktop/msix)."""
    try:
        import ctypes
        n = ctypes.c_uint32(0)
        return ctypes.windll.kernel32.GetCurrentPackageFullName(ctypes.byref(n), None) != 15700  # NO_PACKAGE
    except (AttributeError, OSError):
        return False


PACKAGED = INSTALLED and _packaged()
UI = PACKAGE / "ui"
ASSETS = PACKAGE / "assets"
DESKTOP_DIR = PACKAGE.parent  # .../desktop when running from source


def pythonw() -> pathlib.Path:
    """The windowless Python that runs Harfix."""
    if INSTALLED:
        return ROOT / "runtime" / "pythonw.exe"
    import sys
    exe = pathlib.Path(sys.executable)
    return exe.with_name("pythonw.exe") if exe.with_name("pythonw.exe").exists() else exe


def data_dir() -> pathlib.Path:
    """%APPDATA%\\Harfix: settings, logs, LibreOffice profile."""
    base = pathlib.Path(os.environ.get("APPDATA") or pathlib.Path.home())
    d = base / APP
    d.mkdir(parents=True, exist_ok=True)
    return d


def log_file(name: str = "harfix") -> pathlib.Path:
    d = data_dir() / "logs"
    d.mkdir(exist_ok=True)
    return d / f"{name}.log"

