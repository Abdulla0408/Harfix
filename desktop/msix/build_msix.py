"""Builds the Microsoft Store package (MSIX) of Harfix for Windows.

    py -3.12 desktop\\msix\\build_msix.py        ->  desktop\\msix\\out\\Harfix-1.2.2.0.msix

The package holds the same layout as the installer (harfix_desktop/paths.py):

    runtime\\pythonw.exe, python312.dll ...   official, signed Python 3.12 (embeddable)
    runtime\\Lib\\site-packages\\              requirements-store.txt
    app\\harfix_desktop, app\\harfix_files, app\\yangi_alifbo
    harfix.ico, Assets\\*.png, AppxManifest.xml

Needs Python 3.12 (it compiles the .py files for the bundled 3.12) and, for the last
step, makeappx.exe from the Windows SDK. Without makeappx (or with --no-pack) it stops
after filling out\\stage, which also works on Linux for checking the contents.

The package identity comes from identity.json (Partner Center → Product identity) or
from --name/--publisher/--publisher-name. With none given it builds a TEST package.
"""
import argparse
import compileall
import glob
import hashlib
import json
import os
import pathlib
import py_compile
import re
import shutil
import subprocess
import sys
import urllib.request
import zipfile

HERE = pathlib.Path(__file__).resolve().parent
DESKTOP = HERE.parent
REPO = DESKTOP.parent
OUT = HERE / "out"
CACHE = HERE / "cache"

PY_VERSION = "3.12.10"  # the last 3.12 release with Windows binaries
EMBED_URL = f"https://www.python.org/ftp/python/{PY_VERSION}/python-{PY_VERSION}-embed-amd64.zip"
EMBED_SHA256 = "4acbed6dd1c744b0376e3b1cf57ce906f9dc9e95e68824584c8099a63025a3c3"

TEST_IDENTITY = {"name": "Harfix.Test", "publisher": "CN=HarfixTest", "publisher_display_name": "Harfix (test)"}
APP_PACKAGES = [DESKTOP / "harfix_desktop", DESKTOP / "harfix_files", REPO / "core" / "yangi_alifbo"]
# On PyPI only as source; pure Python, so a wheel built here is the same on Windows.
SOURCE_ONLY = ["proxy_tools==0.1.0"]

# Searched in this order: python312.zip is the standard library; ..\app is Harfix itself.
PTH = "python312.zip\n.\nLib\\site-packages\n..\\app\nimport site\n"


def log(msg):
    print(f"[msix] {msg}", flush=True)


def app_version() -> str:
    text = (DESKTOP / "harfix_desktop" / "__init__.py").read_text(encoding="utf-8")
    m = re.search(r'^VERSION = "(\d+)\.(\d+)\.(\d+)"', text, re.M)
    if not m:
        sys.exit("VERSION not found in harfix_desktop/__init__.py")
    return ".".join(m.groups()) + ".0"  # the Store wants the last number to be 0


def identity(args) -> tuple[dict, bool]:
    ident = json.loads((HERE / "identity.json").read_text(encoding="utf-8"))
    for key in ("name", "publisher", "publisher_display_name"):
        value = getattr(args, key)
        if value:
            ident[key] = value
    if not all(ident.get(k) for k in TEST_IDENTITY):
        return dict(TEST_IDENTITY), True
    if not re.fullmatch(r"[A-Za-z0-9.\-]{3,50}", ident["name"]):
        sys.exit(f"identity name looks wrong: {ident['name']!r}")
    if not ident["publisher"].startswith("CN="):
        sys.exit(f"publisher must start with CN=: {ident['publisher']!r}")
    return ident, False


def download(url: str, sha256: str | None) -> pathlib.Path:
    CACHE.mkdir(parents=True, exist_ok=True)
    path = CACHE / url.rsplit("/", 1)[1]
    if not path.exists():
        log(f"downloading {url}")
        tmp = path.with_suffix(".part")
        with urllib.request.urlopen(url, timeout=120) as r, open(tmp, "wb") as f:
            shutil.copyfileobj(r, f)
        tmp.replace(path)
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    if sha256 and digest != sha256:
        path.unlink()
        sys.exit(f"{path.name}: SHA-256 {digest} does not match the expected {sha256}")
    log(f"{path.name} sha256 {digest}")
    return path


def make_runtime(stage: pathlib.Path):
    runtime = stage / "runtime"
    embed = download(EMBED_URL, EMBED_SHA256)
    with zipfile.ZipFile(embed) as z:
        z.extractall(runtime)
    pth = runtime / "python312._pth"
    if not pth.exists():
        sys.exit("python312._pth missing: not a Python 3.12 embeddable zip?")
    pth.write_text(PTH, encoding="ascii")
    return runtime


def install_packages(site: pathlib.Path):
    """Windows wheels for Python 3.12 x64, whatever this machine is; every dependency is
    pinned in requirements-store.txt, so pip resolves nothing on its own (--no-deps)."""
    wheels = CACHE / "wheels"
    wheels.mkdir(parents=True, exist_ok=True)
    pip = [sys.executable, "-m", "pip", "--disable-pip-version-check"]
    for req in SOURCE_ONLY:
        name = req.split("==")[0]
        if not list(wheels.glob(f"{name}-*.whl")):
            subprocess.run(pip + ["wheel", "--no-deps", "-w", str(wheels), req], check=True)
    subprocess.run(pip + [
        "install", "--target", str(site), "--no-deps", "--no-compile", "--only-binary=:all:",
        "--platform", "win_amd64", "--python-version", "3.12", "--implementation", "cp",
        "--find-links", str(wheels), "-r", str(HERE / "requirements-store.txt"),
    ], check=True)
    shutil.rmtree(site / "bin", ignore_errors=True)  # console scripts: not used, and Linux-style


def copy_app(stage: pathlib.Path):
    app = stage / "app"
    for pkg in APP_PACKAGES:
        shutil.copytree(pkg, app / pkg.name, ignore=shutil.ignore_patterns("__pycache__", "*.pyc", "*.pyo"))
    shutil.copy2(DESKTOP / "harfix_desktop" / "assets" / "harfix.ico", stage / "harfix.ico")


def compile_python(stage: pathlib.Path):
    """The package folder is read-only, so Python could never write its caches there: ship
    them. unchecked-hash: valid whatever the file times become after installing."""
    if sys.version_info[:2] != (3, 12):
        log(f"WARNING: Python {sys.version_info[0]}.{sys.version_info[1]} cannot compile for 3.12: "
            "no .pyc in the package (it still works, it just starts slower)")
        return
    for folder in (stage / "app", stage / "runtime" / "Lib" / "site-packages"):
        compileall.compile_dir(str(folder), quiet=2, workers=0,
                               invalidation_mode=py_compile.PycInvalidationMode.UNCHECKED_HASH)


def write_manifest(stage: pathlib.Path, ident: dict, version: str):
    text = (HERE / "AppxManifest.xml").read_text(encoding="utf-8")
    values = {"NAME": ident["name"], "PUBLISHER": ident["publisher"],
              "PUBLISHER_DISPLAY_NAME": ident["publisher_display_name"], "VERSION": version}
    for key, value in values.items():
        esc = value.replace("&", "&amp;").replace('"', "&quot;").replace("<", "&lt;")
        text = text.replace(f"${key}$", esc)
    left = re.findall(r"\$[A-Z_]+\$", text)
    if left:
        sys.exit(f"manifest values not filled: {left}")
    (stage / "AppxManifest.xml").write_text(text, encoding="utf-8")
    shutil.copytree(HERE / "Assets", stage / "Assets")


def make_pri(makepri: pathlib.Path, stage: pathlib.Path):
    """resources.pri: lets Windows pick Assets\\X.scale-200.png etc. for "Assets\\X.png".
    Indexed from a folder holding only the images, not the thousands of runtime files."""
    root = OUT / "pri"
    shutil.rmtree(root, ignore_errors=True)
    shutil.copytree(stage / "Assets", root / "Assets")
    shutil.copy2(stage / "AppxManifest.xml", root / "AppxManifest.xml")
    cfg = root / "priconfig.xml"
    quiet([str(makepri), "createconfig", "/cf", str(cfg), "/dq", "uz-Latn", "/pv", "10.0.0", "/o"])
    quiet([str(makepri), "new", "/pr", str(root), "/cf", str(cfg), "/mn", str(root / "AppxManifest.xml"),
           "/of", str(stage / "resources.pri"), "/o"])


def quiet(cmd: list[str]):
    """Runs an SDK tool; its long per-file report is shown only if it fails."""
    r = subprocess.run(cmd, capture_output=True)
    if r.returncode:
        for out in (r.stdout, r.stderr):  # makepri writes UTF-16
            text = out.decode("utf-16-le" if out[1:2] == b"\0" else "utf-8", errors="replace")
            print(text[-20000:])
        sys.exit(f"{pathlib.Path(cmd[0]).name} failed ({r.returncode})")


def find_makeappx() -> str | None:
    if shutil.which("makeappx"):
        return shutil.which("makeappx")
    kits = os.environ.get("ProgramFiles(x86)", r"C:\Program Files (x86)") + r"\Windows Kits\10\bin"
    found = sorted(glob.glob(kits + r"\10.*\x64\makeappx.exe"),
                   key=lambda p: [int(x) for x in re.findall(r"\d+", pathlib.Path(p).parent.parent.name)])
    return found[-1] if found else None


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--name", help="Package/Identity/Name (Partner Center)")
    ap.add_argument("--publisher", help="Package/Identity/Publisher, CN=...")
    ap.add_argument("--publisher-name", dest="publisher_display_name", help="Package/Properties/PublisherDisplayName")
    ap.add_argument("--no-pack", action="store_true", help="only fill out/stage")
    args = ap.parse_args()

    version = app_version()
    ident, test = identity(args)
    if test:
        log("WARNING: identity.json is empty: building a TEST package (the Store will refuse it)")
    log(f"Harfix {version}  {ident['name']}  {ident['publisher']}")

    stage = OUT / "stage"
    shutil.rmtree(stage, ignore_errors=True)
    stage.mkdir(parents=True)
    runtime = make_runtime(stage)
    install_packages(runtime / "Lib" / "site-packages")
    copy_app(stage)
    compile_python(stage)
    write_manifest(stage, ident, version)
    log(f"stage ready: {stage}")

    if args.no_pack:
        return
    makeappx = find_makeappx()
    if not makeappx:
        sys.exit("makeappx.exe not found: install the Windows SDK, or use --no-pack")
    make_pri(pathlib.Path(makeappx).with_name("makepri.exe"), stage)
    msix = OUT / f"Harfix-{version}{'-TEST' if test else ''}.msix"
    quiet([makeappx, "pack", "/d", str(stage), "/p", str(msix), "/o", "/h", "SHA256"])  # a line per file
    log(f"done: {msix} ({msix.stat().st_size / 1e6:.1f} MB)")
    if not EMBED_SHA256:
        embed = CACHE / EMBED_URL.rsplit("/", 1)[1]
        log(f"python embeddable sha256 (not pinned yet): {hashlib.sha256(embed.read_bytes()).hexdigest()}")


if __name__ == "__main__":
    main()
