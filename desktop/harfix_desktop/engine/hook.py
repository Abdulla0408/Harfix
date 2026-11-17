"""The typing engine: a system-wide keyboard hook that swaps o' g' sh ch for
ö ğ ş ç as you type in any program, plus two hotkeys:

    Ctrl+Shift+Y  turn real-time typing on/off
    Ctrl+Shift+H  convert the selected text in any program

It steps aside in password fields, programs on the exclusion list, full
screen apps (games, slide shows) and programs running as administrator."""
import ctypes
import logging
import queue
import threading
import time
from ctypes import wintypes as w

from yangi_alifbo import convert_to

from . import win
from .typist import Typist

log = logging.getLogger("harfix.engine")

HOTKEY_TOGGLE, HOTKEY_SELECTION = 1, 2
HOTKEYS = {HOTKEY_TOGGLE: (win.MOD_CONTROL | win.MOD_SHIFT, ord("Y")),
           HOTKEY_SELECTION: (win.MOD_CONTROL | win.MOD_SHIFT, ord("H"))}
GUARD_EVERY = 0.25  # seconds between checks of the focused window
# Keys that move the caret or change text in ways we cannot follow
_RESET_KEYS = {0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x2D, 0x2E, 0x1B}  # PgUp/Dn End Home arrows Ins Del Esc

DEFAULT_EXCLUDE = {
    # terminals and code editors: commands and code must stay as typed
    "windowsterminal.exe", "cmd.exe", "powershell.exe", "pwsh.exe", "conhost.exe", "openconsole.exe",
    "code.exe", "devenv.exe", "pycharm64.exe", "idea64.exe", "webstorm64.exe", "studio64.exe",
    # password managers
    "keepass.exe", "keepassxc.exe", "1password.exe", "bitwarden.exe",
}


class Engine:
    def __init__(self, on_event=None):
        self.on_event = on_event or (lambda kind, **data: None)
        self.enabled = True
        self.target = "new"  # alphabet for the selection hotkey
        self.exclude = set(DEFAULT_EXCLUDE)
        self.typist = Typist()
        self.blocked = ""  # why we step aside right now ("" = we don't)
        self.swaps = 0
        self.hotkeys_ok: dict[int, bool] = {}
        self._jobs: queue.Queue = queue.Queue()
        self._thread_id = 0
        self._ready = threading.Event()
        self._stop = threading.Event()
        self._procs = []  # keep ctypes callbacks alive

    # --- life cycle -----------------------------------------------------------------
    def start(self):
        threading.Thread(target=self._hook_loop, name="harfix-hook", daemon=True).start()
        threading.Thread(target=self._guard_loop, name="harfix-guard", daemon=True).start()
        threading.Thread(target=self._worker, name="harfix-worker", daemon=True).start()
        self._ready.wait(5)

    def stop(self):
        self._stop.set()
        self._jobs.put(None)
        if self._thread_id:
            win.user32.PostThreadMessageW(self._thread_id, win.WM_QUIT, 0, 0)

    def configure(self, enabled=None, target=None, exclude=None):
        if enabled is not None:
            self.enabled = bool(enabled)
        if target is not None:
            self.target = target
        if exclude is not None:
            self.exclude = {x.strip().lower() for x in exclude if x.strip()}
        self.typist.reset()

    # --- keyboard hook (keep it fast: Windows waits for us on every key) ------------------
    def _hook_loop(self):
        self._thread_id = win.kernel32.GetCurrentThreadId()
        module = win.kernel32.GetModuleHandleW(None)
        kb_proc, mouse_proc = win.HOOKPROC(self._on_keyboard), win.HOOKPROC(self._on_mouse)
        self._procs = [kb_proc, mouse_proc]
        kb = win.user32.SetWindowsHookExW(win.WH_KEYBOARD_LL, kb_proc, module, 0)
        mouse = win.user32.SetWindowsHookExW(win.WH_MOUSE_LL, mouse_proc, module, 0)
        if not kb:
            log.error("keyboard hook failed: %s", ctypes.get_last_error())
        for hid, (mods, vk) in HOTKEYS.items():
            self.hotkeys_ok[hid] = bool(win.user32.RegisterHotKey(None, hid, mods | win.MOD_NOREPEAT, vk))
            if not self.hotkeys_ok[hid]:
                log.warning("hotkey %s is taken by another program", hid)
        self._ready.set()
        msg = w.MSG()
        while win.user32.GetMessageW(ctypes.byref(msg), None, 0, 0) > 0:
            if msg.message == win.WM_HOTKEY:
                self._jobs.put(("hotkey", msg.wParam))
        for hid in HOTKEYS:
            win.user32.UnregisterHotKey(None, hid)
        if kb:
            win.user32.UnhookWindowsHookEx(kb)
        if mouse:
            win.user32.UnhookWindowsHookEx(mouse)

    def _on_mouse(self, code, wparam, lparam):
        if code == win.HC_ACTION and wparam in win.MOUSE_DOWNS:
            self.typist.reset()  # a click may have moved the caret
        return win.user32.CallNextHookEx(None, code, wparam, lparam)

    def _on_keyboard(self, code, wparam, lparam):
        try:
            if code == win.HC_ACTION and wparam in (win.WM_KEYDOWN, win.WM_SYSKEYDOWN):
                kb = ctypes.cast(lparam, ctypes.POINTER(win.KBDLLHOOKSTRUCT)).contents
                if kb.dwExtraInfo != win.MAGIC and self._handle_key(kb.vkCode, kb.scanCode):
                    return 1  # we typed something else instead of this key
        except Exception:
            log.exception("keyboard hook")
            self.typist.reset()
        return win.user32.CallNextHookEx(None, code, wparam, lparam)

    def _handle_key(self, vk: int, scan: int) -> bool:
        """True if the key was replaced (and must be swallowed)."""
        if vk in win.MODIFIER_KEYS:
            return False
        if not self.enabled or self.blocked:
            self.typist.reset()
            return False
        ctrl, alt = win.key_down(win.VK_CONTROL), win.key_down(win.VK_MENU)
        if ctrl != alt or win.key_down(win.VK_LWIN) or win.key_down(win.VK_RWIN):
            self.typist.reset()  # a shortcut (Ctrl+V, Alt+Tab...), not typing; Ctrl+Alt = AltGr is typing
            return False
        if vk == win.VK_BACK:
            return self._apply(self.typist.backspace())
        if vk in _RESET_KEYS:
            self.typist.reset()
            return False
        ch = win.char_of(vk, scan)
        if ch is None:
            self.typist.reset()
            return False
        return self._apply(self.typist.feed("\n" if ch == "\r" else ch))

    def _apply(self, swap) -> bool:
        if swap is None:
            return False
        win.type_swap(swap.delete, swap.text)
        self.swaps += 1
        return True

    # --- guard: is it safe to type here? ----------------------------------------
    def _guard_loop(self):
        from .uia import Automation
        try:
            uia = Automation()
        except Exception:
            log.warning("UI Automation is not available; password fields are found only in classic apps")
            uia = None
        last_window, last_focus, app = None, None, ("", False)
        while not self._stop.wait(GUARD_EVERY):
            try:
                hwnd = win.user32.GetForegroundWindow()
                focus = win.focused_control(hwnd) if hwnd else None
                if hwnd != last_window or focus != last_focus:
                    self.typist.reset()
                    if hwnd != last_window:
                        app = (win.process_name(hwnd), not win.self_elevated() and win.is_elevated(hwnd)) \
                            if hwnd else ("", False)
                    last_window, last_focus = hwnd, focus
                if not self.enabled or not hwnd:
                    continue
                self.blocked = self._why_blocked(hwnd, app, uia)
            except Exception:
                log.exception("guard")
        if uia:
            uia.close()

    def _why_blocked(self, hwnd, app, uia) -> str:
        name, admin = app
        if name in self.exclude:
            return "excluded"
        if admin:
            return "admin"
        if win.is_fullscreen(hwnd):
            return "fullscreen"
        if win.is_win32_password(hwnd) or (uia and uia.focused_is_password()):
            return "password"
        return ""

    # --- hotkeys --------------------------------------------------------------------
    def _worker(self):
        while True:
            job = self._jobs.get()
            if job is None:
                return
            try:
                if job == ("hotkey", HOTKEY_TOGGLE):
                    self.enabled = not self.enabled
                    self.typist.reset()
                    self.on_event("toggled", enabled=self.enabled)
                elif job == ("hotkey", HOTKEY_SELECTION):
                    self.on_event("selection", **self.convert_selection())
            except Exception:
                log.exception("hotkey job")

    def convert_selection(self) -> dict:
        """Copies the selected text, converts it and pastes it back. Whatever
        was on the clipboard (text, picture, files) is put back afterwards."""
        for _ in range(50):  # wait (up to 1 s) until the hotkey keys are let go
            if not any(win.key_down(vk) for vk in (win.VK_SHIFT, win.VK_CONTROL, ord("H"))):
                break
            time.sleep(0.02)
        win.release_modifiers()
        saved = win.clipboard_backup()
        seq = win.clipboard_seq()
        win.press_combo(win.VK_CONTROL, ord("C"))
        for _ in range(40):
            time.sleep(0.02)
            if win.clipboard_seq() != seq:
                break
        else:
            return {"ok": False, "reason": "nothing"}
        text = win.get_clipboard_text()
        if not text:
            self._restore(saved)
            return {"ok": False, "reason": "nothing"}
        new = convert_to(text, self.target)
        if new == text:
            self._restore(saved)
            return {"ok": True, "changed": False}
        win.set_clipboard_text(new)
        win.press_combo(win.VK_CONTROL, ord("V"))
        time.sleep(0.4)  # let the program paste before the clipboard changes back
        self._restore(saved)
        self.typist.reset()
        return {"ok": True, "changed": True, "chars": len(new)}

    @staticmethod
    def _restore(saved):
        win.clipboard_restore(saved)
