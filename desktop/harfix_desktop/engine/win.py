"""The Windows API pieces the typing engine needs, through ctypes."""
import ctypes
import time
from ctypes import wintypes as w

user32 = ctypes.WinDLL("user32", use_last_error=True)
kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
advapi32 = ctypes.WinDLL("advapi32", use_last_error=True)

LRESULT = ctypes.c_ssize_t
ULONG_PTR = ctypes.c_size_t

WH_KEYBOARD_LL, WH_MOUSE_LL = 13, 14
HC_ACTION = 0
WM_KEYDOWN, WM_KEYUP, WM_SYSKEYDOWN, WM_SYSKEYUP = 0x100, 0x101, 0x104, 0x105
WM_HOTKEY, WM_QUIT, WM_APP = 0x312, 0x12, 0x8000
MOUSE_DOWNS = {0x201, 0x204, 0x207, 0x20B, 0x203, 0x206, 0x209}  # button down / double click
LLKHF_INJECTED = 0x10
INPUT_KEYBOARD = 1
KEYEVENTF_KEYUP, KEYEVENTF_UNICODE = 0x2, 0x4
MOD_ALT, MOD_CONTROL, MOD_SHIFT, MOD_WIN, MOD_NOREPEAT = 0x1, 0x2, 0x4, 0x8, 0x4000

VK_BACK, VK_TAB, VK_RETURN, VK_SHIFT, VK_CONTROL, VK_MENU, VK_CAPITAL = 0x08, 0x09, 0x0D, 0x10, 0x11, 0x12, 0x14
VK_LWIN, VK_RWIN = 0x5B, 0x5C
MODIFIER_KEYS = {VK_SHIFT, VK_CONTROL, VK_MENU, VK_CAPITAL, 0xA0, 0xA1, 0xA2, 0xA3, 0xA4, 0xA5, VK_LWIN, VK_RWIN}

# Marks keys we inject ourselves so the hook lets them through untouched.
MAGIC = 0x48415246  # "HARF"


class KBDLLHOOKSTRUCT(ctypes.Structure):
    _fields_ = [("vkCode", w.DWORD), ("scanCode", w.DWORD), ("flags", w.DWORD), ("time", w.DWORD),
                ("dwExtraInfo", ULONG_PTR)]


class MOUSEINPUT(ctypes.Structure):
    _fields_ = [("dx", w.LONG), ("dy", w.LONG), ("mouseData", w.DWORD), ("dwFlags", w.DWORD),
                ("time", w.DWORD), ("dwExtraInfo", ULONG_PTR)]


class KEYBDINPUT(ctypes.Structure):
    _fields_ = [("wVk", w.WORD), ("wScan", w.WORD), ("dwFlags", w.DWORD), ("time", w.DWORD),
                ("dwExtraInfo", ULONG_PTR)]


class _INPUTUNION(ctypes.Union):
    _fields_ = [("mi", MOUSEINPUT), ("ki", KEYBDINPUT)]


class INPUT(ctypes.Structure):
    _fields_ = [("type", w.DWORD), ("u", _INPUTUNION)]


class GUITHREADINFO(ctypes.Structure):
    _fields_ = [("cbSize", w.DWORD), ("flags", w.DWORD), ("hwndActive", w.HWND), ("hwndFocus", w.HWND),
                ("hwndCapture", w.HWND), ("hwndMenuOwner", w.HWND), ("hwndMoveSize", w.HWND),
                ("hwndCaret", w.HWND), ("rcCaret", w.RECT)]


class MONITORINFO(ctypes.Structure):
    _fields_ = [("cbSize", w.DWORD), ("rcMonitor", w.RECT), ("rcWork", w.RECT), ("dwFlags", w.DWORD)]


HOOKPROC = ctypes.WINFUNCTYPE(LRESULT, ctypes.c_int, w.WPARAM, w.LPARAM)

user32.SetWindowsHookExW.argtypes = [ctypes.c_int, HOOKPROC, w.HINSTANCE, w.DWORD]
user32.SetWindowsHookExW.restype = w.HHOOK
user32.CallNextHookEx.argtypes = [w.HHOOK, ctypes.c_int, w.WPARAM, w.LPARAM]
user32.CallNextHookEx.restype = LRESULT
user32.UnhookWindowsHookEx.argtypes = [w.HHOOK]
user32.GetMessageW.argtypes = [ctypes.POINTER(w.MSG), w.HWND, w.UINT, w.UINT]
user32.PostThreadMessageW.argtypes = [w.DWORD, w.UINT, w.WPARAM, w.LPARAM]
user32.RegisterHotKey.argtypes = [w.HWND, ctypes.c_int, w.UINT, w.UINT]
user32.UnregisterHotKey.argtypes = [w.HWND, ctypes.c_int]
user32.SendInput.argtypes = [w.UINT, ctypes.POINTER(INPUT), ctypes.c_int]
user32.GetAsyncKeyState.restype = ctypes.c_short
user32.GetKeyState.restype = ctypes.c_short
user32.GetForegroundWindow.restype = w.HWND
user32.GetWindowThreadProcessId.argtypes = [w.HWND, ctypes.POINTER(w.DWORD)]
user32.GetWindowThreadProcessId.restype = w.DWORD
user32.GetKeyboardLayout.argtypes = [w.DWORD]
user32.GetKeyboardLayout.restype = w.HKL
user32.ToUnicodeEx.argtypes = [w.UINT, w.UINT, ctypes.POINTER(ctypes.c_ubyte), w.LPWSTR, ctypes.c_int, w.UINT,
                               w.HKL]
user32.GetGUIThreadInfo.argtypes = [w.DWORD, ctypes.POINTER(GUITHREADINFO)]
user32.GetClassNameW.argtypes = [w.HWND, w.LPWSTR, ctypes.c_int]
user32.GetWindowLongPtrW.argtypes = [w.HWND, ctypes.c_int]
user32.GetWindowLongPtrW.restype = ctypes.c_ssize_t
user32.GetWindowRect.argtypes = [w.HWND, ctypes.POINTER(w.RECT)]
user32.MonitorFromWindow.argtypes = [w.HWND, w.DWORD]
user32.MonitorFromWindow.restype = w.HMONITOR
user32.GetMonitorInfoW.argtypes = [w.HMONITOR, ctypes.POINTER(MONITORINFO)]
user32.OpenClipboard.argtypes = [w.HWND]
user32.GetClipboardData.argtypes = [w.UINT]
user32.GetClipboardData.restype = w.HANDLE
user32.SetClipboardData.argtypes = [w.UINT, w.HANDLE]
user32.SetClipboardData.restype = w.HANDLE
user32.GetClipboardSequenceNumber.restype = w.DWORD
user32.IsClipboardFormatAvailable.argtypes = [w.UINT]
kernel32.GetModuleHandleW.argtypes = [w.LPCWSTR]
kernel32.GetModuleHandleW.restype = w.HMODULE
kernel32.OpenProcess.argtypes = [w.DWORD, w.BOOL, w.DWORD]
kernel32.OpenProcess.restype = w.HANDLE
kernel32.CloseHandle.argtypes = [w.HANDLE]
kernel32.QueryFullProcessImageNameW.argtypes = [w.HANDLE, w.DWORD, w.LPWSTR, ctypes.POINTER(w.DWORD)]
kernel32.GlobalAlloc.argtypes = [w.UINT, ctypes.c_size_t]
kernel32.GlobalAlloc.restype = w.HGLOBAL
kernel32.GlobalLock.argtypes = [w.HGLOBAL]
kernel32.GlobalLock.restype = ctypes.c_void_p
kernel32.GlobalUnlock.argtypes = [w.HGLOBAL]
kernel32.GlobalFree.argtypes = [w.HGLOBAL]
kernel32.GetCurrentThreadId.restype = w.DWORD
kernel32.GetCurrentProcess.restype = w.HANDLE
advapi32.OpenProcessToken.argtypes = [w.HANDLE, w.DWORD, ctypes.POINTER(w.HANDLE)]
advapi32.GetTokenInformation.argtypes = [w.HANDLE, ctypes.c_int, ctypes.c_void_p, w.DWORD, ctypes.POINTER(w.DWORD)]


# --- keys ---------------------------------------------------------------------
def key_down(vk: int) -> bool:
    return bool(user32.GetAsyncKeyState(vk) & 0x8000)


def foreground_thread() -> int:
    return user32.GetWindowThreadProcessId(user32.GetForegroundWindow(), None)


def char_of(vk: int, scan: int) -> str | None:
    """The character a key press types in the foreground window's keyboard
    layout (None for keys that type nothing, or dead keys). Does not disturb
    the keyboard state (flag 4, Windows 10 1607+)."""
    state = (ctypes.c_ubyte * 256)()
    for key in (VK_SHIFT, VK_CONTROL, VK_MENU, 0xA0, 0xA1, 0xA2, 0xA3, 0xA4, 0xA5):
        if key_down(key):
            state[key] = 0x80
    if user32.GetKeyState(VK_CAPITAL) & 1:
        state[VK_CAPITAL] = 0x01
    buf = ctypes.create_unicode_buffer(8)
    hkl = user32.GetKeyboardLayout(foreground_thread())
    n = user32.ToUnicodeEx(vk, scan, state, buf, 8, 4, hkl)
    if n == 1 and (buf.value[:1].isprintable() or buf.value[:1] in "\r\t"):
        return buf.value[:1]
    return None


def _key(vk=0, scan=0, flags=0) -> INPUT:
    return INPUT(type=INPUT_KEYBOARD, u=_INPUTUNION(ki=KEYBDINPUT(wVk=vk, wScan=scan, dwFlags=flags, time=0,
                                                                   dwExtraInfo=MAGIC)))


def send(inputs: list[INPUT]) -> int:
    arr = (INPUT * len(inputs))(*inputs)
    return user32.SendInput(len(inputs), arr, ctypes.sizeof(INPUT))


def type_swap(delete: int, text: str) -> int:
    """Presses Backspace `delete` times, then types `text` (as Unicode)."""
    inputs = []
    for _ in range(delete):
        inputs += [_key(VK_BACK), _key(VK_BACK, flags=KEYEVENTF_KEYUP)]
    for ch in text:
        inputs += [_key(scan=ord(ch), flags=KEYEVENTF_UNICODE),
                   _key(scan=ord(ch), flags=KEYEVENTF_UNICODE | KEYEVENTF_KEYUP)]
    return send(inputs)


def press_combo(*vks: int) -> int:
    """Presses keys together, e.g. press_combo(VK_CONTROL, ord("C"))."""
    downs = [_key(vk) for vk in vks]
    ups = [_key(vk, flags=KEYEVENTF_KEYUP) for vk in reversed(vks)]
    return send(downs + ups)


def release_modifiers():
    """Lifts Shift/Ctrl/Alt/Win if they are held (so a Ctrl+C we send is not Ctrl+Shift+C)."""
    held = [vk for vk in (0xA0, 0xA1, 0xA2, 0xA3, 0xA4, 0xA5, VK_LWIN, VK_RWIN) if key_down(vk)]
    if held:
        send([_key(vk, flags=KEYEVENTF_KEYUP) for vk in held])


# --- windows and processes --------------------------------------------------------
def process_name(hwnd) -> str:
    pid = w.DWORD()
    user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
    h = kernel32.OpenProcess(0x1000, False, pid.value)  # PROCESS_QUERY_LIMITED_INFORMATION
    if not h:
        return ""
    try:
        buf = ctypes.create_unicode_buffer(1024)
        size = w.DWORD(1024)
        if kernel32.QueryFullProcessImageNameW(h, 0, buf, ctypes.byref(size)):
            return buf.value.rsplit("\\", 1)[-1].lower()
        return ""
    finally:
        kernel32.CloseHandle(h)


def is_elevated(hwnd) -> bool:
    """True if the window's process runs as administrator (or we cannot tell):
    Windows does not let us type into it, so we must not swallow keys there."""
    pid = w.DWORD()
    user32.GetWindowThreadProcessId(hwnd, ctypes.byref(pid))
    h = kernel32.OpenProcess(0x1000, False, pid.value)
    if not h:
        return True
    token = w.HANDLE()
    try:
        if not advapi32.OpenProcessToken(h, 0x0008, ctypes.byref(token)):  # TOKEN_QUERY
            return True
        elevated, size = w.DWORD(), w.DWORD()
        ok = advapi32.GetTokenInformation(token, 20, ctypes.byref(elevated), 4, ctypes.byref(size))  # TokenElevation
        kernel32.CloseHandle(token)
        return bool(ok and elevated.value)
    finally:
        kernel32.CloseHandle(h)


_self_elevated = None


def self_elevated() -> bool:
    global _self_elevated
    if _self_elevated is None:
        token = w.HANDLE()
        elevated, size = w.DWORD(), w.DWORD()
        advapi32.OpenProcessToken(kernel32.GetCurrentProcess(), 0x0008, ctypes.byref(token))
        advapi32.GetTokenInformation(token, 20, ctypes.byref(elevated), 4, ctypes.byref(size))
        kernel32.CloseHandle(token)
        _self_elevated = bool(elevated.value)
    return _self_elevated


def class_name(hwnd) -> str:
    buf = ctypes.create_unicode_buffer(256)
    user32.GetClassNameW(hwnd, buf, 256)
    return buf.value


def focused_control(hwnd):
    info = GUITHREADINFO(cbSize=ctypes.sizeof(GUITHREADINFO))
    tid = user32.GetWindowThreadProcessId(hwnd, None)
    if user32.GetGUIThreadInfo(tid, ctypes.byref(info)):
        return info.hwndFocus
    return None


def is_win32_password(hwnd) -> bool:
    """A classic Windows text box that hides what is typed (ES_PASSWORD)."""
    focus = focused_control(hwnd)
    if not focus:
        return False
    return "edit" in class_name(focus).lower() and bool(user32.GetWindowLongPtrW(focus, -16) & 0x20)


def is_fullscreen(hwnd) -> bool:
    """Games and slide shows cover the whole screen; we stay out of those."""
    if not hwnd or class_name(hwnd) in ("Progman", "WorkerW", "Shell_TrayWnd"):
        return False
    rect = w.RECT()
    if not user32.GetWindowRect(hwnd, ctypes.byref(rect)):
        return False
    info = MONITORINFO(cbSize=ctypes.sizeof(MONITORINFO))
    if not user32.GetMonitorInfoW(user32.MonitorFromWindow(hwnd, 2), ctypes.byref(info)):
        return False
    m = info.rcMonitor
    return rect.left <= m.left and rect.top <= m.top and rect.right >= m.right and rect.bottom >= m.bottom


# --- clipboard ----------------------------------------------------------------------
CF_UNICODETEXT = 13


def _open_clipboard() -> bool:
    for _ in range(20):
        if user32.OpenClipboard(None):
            return True
        time.sleep(0.02)
    return False


def get_clipboard_text() -> str | None:
    if not user32.IsClipboardFormatAvailable(CF_UNICODETEXT) or not _open_clipboard():
        return None
    try:
        h = user32.GetClipboardData(CF_UNICODETEXT)
        if not h:
            return None
        p = kernel32.GlobalLock(h)
        try:
            return ctypes.wstring_at(p) if p else None
        finally:
            kernel32.GlobalUnlock(h)
    finally:
        user32.CloseClipboard()


def set_clipboard_text(text: str) -> bool:
    if not _open_clipboard():
        return False
    try:
        user32.EmptyClipboard()
        data = ctypes.create_unicode_buffer(text)
        size = ctypes.sizeof(data)
        h = kernel32.GlobalAlloc(0x0002, size)  # GMEM_MOVEABLE
        p = kernel32.GlobalLock(h)
        ctypes.memmove(p, data, size)
        kernel32.GlobalUnlock(h)
        if not user32.SetClipboardData(CF_UNICODETEXT, h):
            kernel32.GlobalFree(h)
            return False
        return True
    finally:
        user32.CloseClipboard()


def clipboard_seq() -> int:
    return user32.GetClipboardSequenceNumber()


user32.EnumClipboardFormats.argtypes = [w.UINT]
user32.EnumClipboardFormats.restype = w.UINT
kernel32.GlobalSize.argtypes = [w.HGLOBAL]
kernel32.GlobalSize.restype = ctypes.c_size_t
# Formats held as GDI handles (not memory) or drawn by their owner; Windows
# rebuilds CF_BITMAP from CF_DIB by itself.
_HANDLE_FORMATS = {2, 3, 9, 14, 0x80, 0x82, 0x83, 0x8E}
BACKUP_LIMIT = 64 * 1024 * 1024


def clipboard_backup() -> list[tuple[int, bytes]] | None:
    """Everything on the clipboard (text, pictures, files...), to put back
    after we borrow the clipboard. None if it could not be read."""
    if not _open_clipboard():
        return None
    try:
        items, fmt, total = [], 0, 0
        while True:
            fmt = user32.EnumClipboardFormats(fmt)
            if not fmt:
                break
            if fmt in _HANDLE_FORMATS:
                continue
            h = user32.GetClipboardData(fmt)
            size = kernel32.GlobalSize(h) if h else 0
            if not size or total + size > BACKUP_LIMIT:
                continue
            p = kernel32.GlobalLock(h)
            if not p:
                continue
            try:
                items.append((fmt, ctypes.string_at(p, size)))
                total += size
            finally:
                kernel32.GlobalUnlock(h)
        return items
    finally:
        user32.CloseClipboard()


def clipboard_restore(items: list[tuple[int, bytes]] | None):
    if items is None or not _open_clipboard():
        return
    try:
        user32.EmptyClipboard()
        for fmt, data in items:
            h = kernel32.GlobalAlloc(0x0002, len(data))
            p = kernel32.GlobalLock(h)
            ctypes.memmove(p, data, len(data))
            kernel32.GlobalUnlock(h)
            if not user32.SetClipboardData(fmt, h):
                kernel32.GlobalFree(h)
    finally:
        user32.CloseClipboard()
