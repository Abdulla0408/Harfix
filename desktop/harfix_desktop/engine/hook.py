"""The typing engine: a system-wide keyboard hook that swaps o' g' sh ch for
ö ğ ş ç as you type in any program, plus two hotkeys:

    Ctrl+Shift+Y  turn real-time typing on/off
    Ctrl+Shift+H  convert the selected text in any program

It steps aside in password fields, programs on the exclusion list, full
screen apps (games, slide shows) and programs running as administrator.

After a click or an arrow key the hook no longer knows what is before the
caret. If the next key is ' or h (the user went back into "to|liq" to add
the apostrophe), it lets the key through and the worker thread reads the
text before the caret through UI Automation a moment later, then makes the
swap it would have made right away."""
import ctypes
import logging
import queue
import threading
import time
from ctypes import wintypes as w

from yangi_alifbo import convert_to

from . import win
from .typist import LATE_KEYS, Typist, is_shown, late_swap

log = logging.getLogger("harfix.engine")

HOTKEY_TOGGLE, HOTKEY_SELECTION = 1, 2
HOTKEYS = {HOTKEY_TOGGLE: (win.MOD_CONTROL | win.MOD_SHIFT, ord("Y")),
           HOTKEY_SELECTION: (win.MOD_CONTROL | win.MOD_SHIFT, ord("H"))}
GUARD_EVERY = 0.25  # seconds between checks of the focused window
# Late swaps: when to look at the text after the key (seconds; again if the
# program has not shown the key yet), and how many characters to read
LATE_CHECKS = (0.03, 0.1, 0.25)
LATE_CONTEXT = 32
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
        self._inputs = 0  # key presses and clicks so far: a waiting late swap checks nothing came after it
        self._uia = None  # the worker thread's UI Automation (False: not available)
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
            self._inputs += 1
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
        self._inputs += 1
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
        ch = "\n" if ch == "\r" else ch
        blind = self.typist.prev is None  # the caret moved: we do not know what is before it
        if self._apply(self.typist.feed(ch)):
            return True
        if blind and ch in LATE_KEYS:
            # Maybe "to|liq" + ': the key goes through, the worker looks at the text (never UIA in here)
            self._jobs.put(("late", self._inputs, ch, win.user32.GetForegroundWindow(), time.monotonic()))
        return False

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
                        app = self._app_of(hwnd) if hwnd else ("", False)
                    last_window, last_focus = hwnd, focus
                if not self.enabled or not hwnd:
                    continue
                self.blocked = self._why_blocked(hwnd, app, uia)
            except Exception:
                log.exception("guard")
        if uia:
            uia.close()

    @staticmethod
    def _app_of(hwnd) -> tuple[str, bool]:
        """(program file name, runs as administrator while we do not)."""
        return win.process_name(hwnd), not win.self_elevated() and win.is_elevated(hwnd)

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

    # --- worker: hotkeys and late swaps ------------------------------------------------
    def _worker(self):
        while True:
            job = self._jobs.get()
            if job is None:
                break
            try:
                if job[0] == "late":
                    self._late_swap(*job[1:])
                elif job == ("hotkey", HOTKEY_TOGGLE):
                    self.enabled = not self.enabled
                    self.typist.reset()
                    self.on_event("toggled", enabled=self.enabled)
                elif job == ("hotkey", HOTKEY_SELECTION):
                    self.on_event("selection", **self.convert_selection())
            except Exception:
                log.exception("worker job %s", job[0])
        if self._uia:
            self._uia.close()

    def _worker_uia(self):
        """UI Automation for the worker thread (COM objects stay in the thread that made them)."""
        if self._uia is None:
            from .uia import Automation
            try:
                self._uia = Automation()
            except Exception:
                log.warning("UI Automation is not available; a late ' or h is not swapped")
                self._uia = False
        return self._uia or None

    def _late_swap(self, inputs: int, ch: str, hwnd, pressed: float):
        """`ch` (' or h) went through while the typist knew nothing about the
        text, because the caret had moved (a click, the arrow keys...). Read
        what is now before the caret and, if `ch` completes a pair there
        (to|liq + ' -> to'|liq), swap it as feed() would have: tö|liq."""
        uia = self._worker_uia()
        if uia is None or not self._still_late(inputs, hwnd) or self._why_blocked(hwnd, self._app_of(hwnd), uia):
            return
        for after in LATE_CHECKS:
            wait = pressed + after - time.monotonic()
            if wait > 0:
                time.sleep(wait)
            if not self._still_late(inputs, hwnd):
                return
            text = uia.text_before_caret(LATE_CONTEXT)
            if text is None:
                return  # this program does not show us its text
            if is_shown(text, ch):
                break
        else:
            return
        found = late_swap(text, ch)
        if not found or not self._still_late(inputs, hwnd):
            return
        typed, out, word = found
        win.type_swap(2, out)
        self.typist.adopt(typed, out, word)
        self.swaps += 1

    def _still_late(self, inputs: int, hwnd) -> bool:
        """Nothing happened since the key: no other key or click, same window, still allowed to type."""
        return (inputs == self._inputs and self.enabled and not self.blocked
                and win.user32.GetForegroundWindow() == hwnd)

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
