"""Store screenshots taken from the real Harfix window (Microsoft Store policy 10.1.1.3).

    py desktop\\msix\\capture_screenshots.py [out_dir]

Needs Windows with Harfix installed (the Inno Setup build in %LOCALAPPDATA%\\Programs\\Harfix).
Closes the running Harfix, starts it again with WebView2 remote debugging on 127.0.0.1, fills
each page through the DevTools protocol, captures the whole window (title bar included) with
PrintWindow at 1366x768 logical pixels, then starts Harfix again in the tray as before.
Standard library only.
"""
import base64
import ctypes
import ctypes.wintypes as wt
import json
import os
import pathlib
import socket
import struct
import subprocess
import sys
import tempfile
import time
import urllib.request
import zipfile
import zlib

HERE = pathlib.Path(__file__).resolve().parent
OUT = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else HERE / "store"
ROOT = pathlib.Path(os.environ.get("LOCALAPPDATA", "")) / "Programs" / "Harfix"
PYW, PY = ROOT / "runtime" / "pythonw.exe", ROOT / "runtime" / "python.exe"
PORT = 9333
LOGICAL = (1366, 768)  # visible window size in logical pixels

SRC = ("Oʻzbekiston Respublikasi — choʻqqilar, togʻlar va shaharlar.\n"
       "Yangi alifboda yozish endi oson: sh, ch, oʻ va gʻ harflari oʻzi almashadi.")

user32 = ctypes.WinDLL("user32", use_last_error=True)
kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
gdi32 = ctypes.WinDLL("gdi32")
dwmapi = ctypes.WinDLL("dwmapi")
kernel32.OpenEventW.restype = wt.HANDLE
kernel32.OpenMutexW.restype = wt.HANDLE


# --- the installed app ---------------------------------------------------------------
def running() -> bool:
    h = kernel32.OpenMutexW(0x100000, False, "Local\\HarfixDesktop.Mutex")  # SYNCHRONIZE
    if h:
        kernel32.CloseHandle(h)
    return bool(h)


def quit_harfix():
    """The same ".Quit" event the installer uses (system.SingleInstance)."""
    h = kernel32.OpenEventW(0x2, False, "Local\\HarfixDesktop.Quit")  # EVENT_MODIFY_STATE
    if h:
        kernel32.SetEvent(h)
        kernel32.CloseHandle(h)
    for _ in range(100):
        if not running():
            time.sleep(1.0)  # the typing engine's process exits right after
            return
        time.sleep(0.2)
    raise SystemExit("Harfix did not quit")


def start(debug: bool, tray: bool):
    env = dict(os.environ)
    env.pop("WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS", None)
    if debug:
        env["WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS"] = (
            f"--remote-debugging-port={PORT} --remote-debugging-address=127.0.0.1")
    args = [str(PYW), "-m", "harfix_desktop"] + (["--tray"] if tray else [])
    subprocess.Popen(args, cwd=str(ROOT), env=env, close_fds=True,
                     creationflags=0x00000008 | 0x00000200)  # DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP


# --- DevTools protocol over a minimal websocket --------------------------------------
class CDP:
    def __init__(self, ws_url: str):
        host_port, path = ws_url[len("ws://"):].split("/", 1)
        host, port = host_port.split(":")
        self.s = socket.create_connection((host, int(port)), timeout=15)
        key = base64.b64encode(os.urandom(16)).decode()
        self.s.sendall((f"GET /{path} HTTP/1.1\r\nHost: {host_port}\r\nUpgrade: websocket\r\n"
                        f"Connection: Upgrade\r\nSec-WebSocket-Key: {key}\r\n"
                        "Sec-WebSocket-Version: 13\r\n\r\n").encode())
        head = b""
        while b"\r\n\r\n" not in head:
            head += self.s.recv(4096)
        if b" 101 " not in head.split(b"\r\n")[0]:
            raise RuntimeError(head[:200])
        self.buf = head.split(b"\r\n\r\n", 1)[1]
        self.n = 0

    def _take(self, k):
        while len(self.buf) < k:
            self.buf += self.s.recv(65536)
        out, self.buf = self.buf[:k], self.buf[k:]
        return out

    def _frame(self):
        b1, b2 = self._take(2)
        ln = b2 & 0x7F
        if ln == 126:
            ln = struct.unpack(">H", self._take(2))[0]
        elif ln == 127:
            ln = struct.unpack(">Q", self._take(8))[0]
        return b1 & 0x0F, self._take(ln)

    def js(self, expr):
        self.n += 1
        data = json.dumps({"id": self.n, "method": "Runtime.evaluate",
                           "params": {"expression": expr, "awaitPromise": True, "returnByValue": True}}).encode()
        ln = len(data)
        head = bytes([0x81]) + (bytes([0x80 | ln]) if ln < 126 else
                                bytes([0x80 | 126]) + struct.pack(">H", ln) if ln < 65536 else
                                bytes([0x80 | 127]) + struct.pack(">Q", ln))
        mask = os.urandom(4)
        self.s.sendall(head + mask + bytes(c ^ mask[i % 4] for i, c in enumerate(data)))
        msg = b""
        while True:
            op, payload = self._frame()
            if op not in (0, 1):
                continue
            msg += payload
            try:
                obj = json.loads(msg)
            except ValueError:
                continue  # more fragments to come
            msg = b""
            if obj.get("id") == self.n:
                res = obj.get("result", {})
                if "error" in obj or res.get("exceptionDetails"):
                    raise RuntimeError(obj.get("error") or res["exceptionDetails"])
                return res.get("result", {}).get("value")


def devtools_page() -> str:
    for _ in range(150):
        try:
            with urllib.request.urlopen(f"http://127.0.0.1:{PORT}/json/list", timeout=2) as r:
                for t in json.load(r):
                    if t.get("type") == "page" and "index.html" in t.get("url", ""):
                        return t["webSocketDebuggerUrl"]
        except OSError:
            pass
        time.sleep(0.2)
    raise SystemExit("Harfix's page did not show up in DevTools")


# --- the window -------------------------------------------------------------------------
def harfix_window():
    found = []

    @ctypes.WINFUNCTYPE(wt.BOOL, wt.HWND, wt.LPARAM)
    def each(hwnd, _):
        title = ctypes.create_unicode_buffer(64)
        user32.GetWindowTextW(hwnd, title, 64)
        if title.value == "Harfix" and user32.IsWindowVisible(hwnd):
            found.append(hwnd)
        return True

    for _ in range(50):
        user32.EnumWindows(each, 0)
        if found:
            return found[0]
        time.sleep(0.2)
    raise SystemExit("Harfix window not found")


def bounds(hwnd):
    """Window rect and the visible frame inside it (without the invisible resize borders)."""
    wr, fr = wt.RECT(), wt.RECT()
    user32.GetWindowRect(hwnd, ctypes.byref(wr))
    dwmapi.DwmGetWindowAttribute(hwnd, 9, ctypes.byref(fr), ctypes.sizeof(fr))  # EXTENDED_FRAME_BOUNDS
    return wr, fr


def place(hwnd):
    scale = user32.GetDpiForWindow(hwnd) / 96
    w, h = round(LOGICAL[0] * scale), round(LOGICAL[1] * scale)
    user32.ShowWindow(hwnd, 9)  # SW_RESTORE
    wr, fr = bounds(hwnd)
    left, top = fr.left - wr.left, fr.top - wr.top
    extra_w, extra_h = (wr.right - wr.left) - (fr.right - fr.left), (wr.bottom - wr.top) - (fr.bottom - fr.top)
    user32.SetWindowPos(hwnd, 0, 40 - left, 30 - top, w + extra_w, h + extra_h, 0x0004 | 0x0010)
    time.sleep(0.8)


class BITMAPINFOHEADER(ctypes.Structure):
    _fields_ = [("biSize", wt.DWORD), ("biWidth", wt.LONG), ("biHeight", wt.LONG), ("biPlanes", wt.WORD),
                ("biBitCount", wt.WORD), ("biCompression", wt.DWORD), ("biSizeImage", wt.DWORD),
                ("biXPelsPerMeter", wt.LONG), ("biYPelsPerMeter", wt.LONG), ("biClrUsed", wt.DWORD),
                ("biClrImportant", wt.DWORD)]


def capture(hwnd, path: pathlib.Path):
    wr, fr = bounds(hwnd)
    ww, wh = wr.right - wr.left, wr.bottom - wr.top
    hdc = user32.GetWindowDC(hwnd)
    mem = gdi32.CreateCompatibleDC(hdc)
    bmp = gdi32.CreateCompatibleBitmap(hdc, ww, wh)
    gdi32.SelectObject(mem, bmp)
    ok = user32.PrintWindow(hwnd, mem, 2)  # PW_RENDERFULLCONTENT: includes the WebView2 content
    bi = BITMAPINFOHEADER(ctypes.sizeof(BITMAPINFOHEADER), ww, -wh, 1, 32, 0, 0, 0, 0, 0, 0)
    raw = ctypes.create_string_buffer(ww * wh * 4)
    gdi32.GetDIBits(mem, bmp, 0, wh, raw, ctypes.byref(bi), 0)
    gdi32.DeleteObject(bmp)
    gdi32.DeleteDC(mem)
    user32.ReleaseDC(hwnd, hdc)
    if not ok:
        raise RuntimeError("PrintWindow failed")
    x0, y0, cw, ch = fr.left - wr.left, fr.top - wr.top, fr.right - fr.left, fr.bottom - fr.top
    rows = []
    for y in range(y0, y0 + ch):
        bgra = raw.raw[(y * ww + x0) * 4:(y * ww + x0 + cw) * 4]
        rgb = bytearray(cw * 3)
        rgb[0::3], rgb[1::3], rgb[2::3] = bgra[2::4], bgra[1::4], bgra[0::4]
        rows.append(b"\x00" + bytes(rgb))

    def chunk(tag, body):
        return struct.pack(">I", len(body)) + tag + body + struct.pack(">I", zlib.crc32(tag + body))

    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", cw, ch, 8, 2, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(b"".join(rows), 9)) + chunk(b"IEND", b""))
    print(path.name, f"{cw}x{ch}")


# --- files for the "Fayllar" page --------------------------------------------------------
def sample_files(folder: pathlib.Path) -> list[str]:
    xml = '<?xml version="1.0" encoding="UTF-8" standalone="yes"?>'
    rels = (xml + '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">'
            '<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/'
            'officeDocument" Target="{}"/></Relationships>')
    types = (xml + '<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">'
             '<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>'
             '<Default Extension="xml" ContentType="application/xml"/><Override PartName="/{}" ContentType="{}"/></Types>')
    paras = "".join(f"<w:p><w:r><w:t>{t}</w:t></w:r></w:p>" for t in
                    ["Yillik hisobot", "Oʻzbekiston boʻylab natijalar.", "Shaharlar va qishloqlar."] * 30)
    with zipfile.ZipFile(folder / "Hisobot.docx", "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", types.format(
            "word/document.xml", "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"))
        z.writestr("_rels/.rels", rels.format("word/document.xml"))
        z.writestr("word/document.xml", xml + '<w:document xmlns:w="http://schemas.openxmlformats.org/'
                   f'wordprocessingml/2006/main"><w:body>{paras}</w:body></w:document>')
    slides = "".join(f"<a:p><a:r><a:t>Taqdimot {i}: choʻqqilar va togʻlar</a:t></a:r></a:p>" for i in range(200))
    with zipfile.ZipFile(folder / "Taqdimot.pptx", "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("[Content_Types].xml", types.format(
            "ppt/slides/slide1.xml", "application/vnd.openxmlformats-officedocument.presentationml.slide+xml"))
        z.writestr("_rels/.rels", rels.format("ppt/presentation.xml"))
        z.writestr("ppt/slides/slide1.xml", xml + '<p:sld xmlns:p="http://schemas.openxmlformats.org/'
                   'presentationml/2006/main" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main">'
                   f"<p:cSld><p:spTree><p:sp><p:txBody>{slides}</p:txBody></p:sp></p:spTree></p:cSld></p:sld>")
    text = "\n".join(f"BT /F1 11 Tf 72 {760 - 16 * k} Td (Tomonlar shartlar asosida kelishib oldilar.) Tj ET"
                     for k in range(44)).encode()
    objs = [b"<< /Type /Catalog /Pages 2 0 R >>", b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
            b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R "
            b"/Resources << /Font << /F1 5 0 R >> >> >>",
            b"<< /Length %d >>\nstream\n" % len(text) + text + b"\nendstream",
            b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>"]
    pdf, offsets = b"%PDF-1.4\n", []
    for i, body in enumerate(objs, 1):
        offsets.append(len(pdf))
        pdf += b"%d 0 obj\n" % i + body + b"\nendobj\n"
    xref = len(pdf)
    pdf += b"xref\n0 %d\n0000000000 65535 f \n" % (len(objs) + 1)
    pdf += b"".join(b"%010d 00000 n \n" % o for o in offsets)
    pdf += b"trailer\n<< /Size %d /Root 1 0 R >>\nstartxref\n%d\n%%%%EOF\n" % (len(objs) + 1, xref)
    (folder / "Shartnoma.pdf").write_bytes(pdf)
    return [str(folder / n) for n in ("Hisobot.docx", "Taqdimot.pptx", "Shartnoma.pdf")]


def queue_items(paths: list[str]) -> list[dict]:
    """The same items the app makes from dropped files (harfix_desktop.batch.expand)."""
    code = "import json,sys; from harfix_desktop.batch import expand; print(json.dumps(expand(sys.argv[1:])))"
    out = subprocess.run([str(PY), "-c", code, *paths], capture_output=True, text=True, check=True, cwd=str(ROOT))
    return json.loads(out.stdout)


def main():
    if not PYW.exists():
        raise SystemExit(f"Harfix is not installed: {PYW}")
    user32.SetProcessDpiAwarenessContext(ctypes.c_void_p(-4))  # per-monitor v2: real pixels
    OUT.mkdir(parents=True, exist_ok=True)
    samples = pathlib.Path(tempfile.mkdtemp(prefix="harfix-shots-"))
    items = queue_items(sample_files(samples))
    was_running = running()
    if was_running:
        quit_harfix()
    try:
        start(debug=True, tray=False)
        page = CDP(devtools_page())
        for _ in range(100):
            if page.js("document.readyState === 'complete' && !!(window.pywebview && window.pywebview.api)"):
                break
            time.sleep(0.2)
        time.sleep(1.5)
        hwnd = harfix_window()
        place(hwnd)

        def tab(name):
            page.js(f"document.querySelector('[data-tab={name}]').click()")
            time.sleep(0.9)

        tab("convert")
        page.js("(() => { const t = document.getElementById('src'); t.value = %s;"
                " t.dispatchEvent(new Event('input', {bubbles: true})); t.blur(); })()" % json.dumps(SRC))
        time.sleep(0.9)
        capture(hwnd, OUT / "1-matn.png")
        tab("files")
        page.js("window.harfixEvent({kind: 'dropped', items: %s})" % json.dumps(items))
        time.sleep(0.9)
        capture(hwnd, OUT / "2-fayllar.png")
        tab("learn")
        capture(hwnd, OUT / "3-alifbo.png")
        tab("settings")
        capture(hwnd, OUT / "4-sozlamalar.png")
    finally:
        if running():
            quit_harfix()  # never leave the DevTools port open
        if was_running:
            start(debug=False, tray=True)


if __name__ == "__main__":
    main()
