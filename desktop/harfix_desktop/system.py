"""Windows integration: start with Windows, one running copy, opening files."""
import ctypes
import logging
import os
import subprocess
import threading
import winreg
from ctypes import wintypes as w

from .paths import APP, APP_ID, DESKTOP_DIR, INSTALLED, PACKAGED, pythonw

log = logging.getLogger("harfix")
kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
kernel32.CreateMutexW.argtypes = [ctypes.c_void_p, w.BOOL, w.LPCWSTR]
kernel32.CreateMutexW.restype = w.HANDLE
kernel32.CreateEventW.argtypes = [ctypes.c_void_p, w.BOOL, w.BOOL, w.LPCWSTR]
kernel32.CreateEventW.restype = w.HANDLE
kernel32.WaitForSingleObject.argtypes = [w.HANDLE, w.DWORD]
kernel32.SetEvent.argtypes = [w.HANDLE]

RUN_KEY = r"Software\Microsoft\Windows\CurrentVersion\Run"
ERROR_ALREADY_EXISTS = 183


# --- start with Windows -------------------------------------------------------------
def launch_command(*args: str) -> str:
    if INSTALLED:
        parts = [f'"{pythonw()}"', "-m", "harfix_desktop"]
    else:  # from source
        parts = [f'"{pythonw()}"', f'"{DESKTOP_DIR / "harfix.pyw"}"']
    return " ".join(parts + list(args))


def set_app_id():
    """Our own taskbar button and icon instead of Python's. (The Store version already
    has the package's identity and icons; its own ID would split the taskbar button.)"""
    if PACKAGED:
        return
    try:
        ctypes.windll.shell32.SetCurrentProcessExplicitAppUserModelID(APP_ID)
    except Exception:
        pass


def set_autostart(on: bool, ask: bool = True):
    if PACKAGED:
        return _set_startup_task(on, ask)
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY, 0, winreg.KEY_SET_VALUE) as key:
            if on:
                winreg.SetValueEx(key, APP, 0, winreg.REG_SZ, launch_command("--tray"))
            else:
                try:
                    winreg.DeleteValue(key, APP)
                except FileNotFoundError:
                    pass
    except OSError:
        log.exception("autostart")


def autostart_enabled() -> bool:
    if PACKAGED:
        return _startup_state() in ("ENABLED", "ENABLED_BY_POLICY")
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, RUN_KEY) as key:
            winreg.QueryValueEx(key, APP)
            return True
    except OSError:
        return False


# The Store (MSIX) version can't use the Run key (its registry writes stay inside the
# package), so it starts with Windows through the package's startup task instead.
STARTUP_TASK = "HarfixStartup"  # desktop/msix/AppxManifest.xml


# Asked straight through the Windows Runtime with ctypes: this process has the package's
# identity (a child process such as PowerShell would not), and no extra native library
# (one more file for Smart App Control to judge) is needed.
_STATES = ("DISABLED", "DISABLED_BY_USER", "ENABLED", "DISABLED_BY_POLICY", "ENABLED_BY_POLICY")
_IID_STARTUP_TASK_STATICS = "{EE5B60BD-A148-41A7-B26E-E8B88A1E62F8}"
_IID_ASYNC_INFO = "{00000036-0000-0000-C000-000000000046}"


def _startup_task(action: str = "") -> str:
    """Reads the startup task's state, or enables/disables it first; returns the state's name.
    Runs on its own thread so the Windows Runtime can be set up there as it needs."""
    out = {}

    def work():
        try:
            out["state"] = _startup_task_winrt(action)
        except Exception as e:  # noqa: BLE001 - handed to the caller
            out["error"] = e
    t = threading.Thread(target=work, name="harfix-startup-task", daemon=True)
    t.start()
    t.join(30)
    if "error" in out:
        raise out["error"]
    if "state" not in out:
        raise TimeoutError("startup task")
    return out["state"]


def _startup_task_winrt(action: str) -> str:
    import time
    import uuid
    combase = ctypes.WinDLL("combase")
    vp, hr_t = ctypes.c_void_p, ctypes.c_long

    def guid(text):
        g = (ctypes.c_ubyte * 16).from_buffer_copy(uuid.UUID(text).bytes_le)
        return g

    def call(obj, index, *args, argtypes=()):
        fn = ctypes.WINFUNCTYPE(hr_t, vp, *argtypes)(ctypes.cast(obj, ctypes.POINTER(ctypes.POINTER(vp)))[0][index])
        hr = fn(obj, *args)
        if hr < 0:
            raise OSError(f"WinRT call {index} failed: 0x{hr & 0xFFFFFFFF:08X}")

    def release(obj):
        if obj:
            ctypes.WINFUNCTYPE(ctypes.c_ulong, vp)(ctypes.cast(obj, ctypes.POINTER(ctypes.POINTER(vp)))[0][2])(obj)

    def wait(op):  # IAsyncInfo.Status: 0 started, 1 completed, 2 canceled, 3 error
        info, status = vp(), ctypes.c_int(0)
        call(op, 0, ctypes.byref(guid(_IID_ASYNC_INFO)), ctypes.byref(info), argtypes=(vp, vp))
        try:
            for _ in range(1000):
                call(info, 7, ctypes.byref(status), argtypes=(vp,))
                if status.value:
                    break
                time.sleep(0.02)
        finally:
            release(info)
        if status.value != 1:
            raise OSError(f"WinRT operation ended with status {status.value}")

    def hstring(text):
        h = vp()
        if combase.WindowsCreateString(ctypes.c_wchar_p(text), len(text), ctypes.byref(h)) < 0:
            raise OSError("WindowsCreateString")
        return h

    combase.RoInitialize(1)  # multithreaded
    statics = task = op = None
    names = [hstring("Windows.ApplicationModel.StartupTask"), hstring(STARTUP_TASK)]
    try:
        statics = vp()
        hr = combase.RoGetActivationFactory(names[0], ctypes.byref(guid(_IID_STARTUP_TASK_STATICS)),
                                            ctypes.byref(statics))
        if hr < 0:
            raise OSError(f"RoGetActivationFactory: 0x{hr & 0xFFFFFFFF:08X}")
        op = vp()
        call(statics, 7, names[1], ctypes.byref(op), argtypes=(vp, vp))  # GetAsync(taskId)
        wait(op)
        task = vp()
        call(op, 8, ctypes.byref(task), argtypes=(vp,))  # GetResults
        release(op)
        op = None
        state = ctypes.c_int(0)
        call(task, 8, ctypes.byref(state), argtypes=(vp,))  # get_State
        if action == "on" and state.value == 0:
            op = vp()
            call(task, 6, ctypes.byref(op), argtypes=(vp,))  # RequestEnableAsync
            wait(op)
            call(op, 8, ctypes.byref(state), argtypes=(vp,))
        elif action == "off" and state.value == 2:
            call(task, 7)  # Disable
            state.value = 0
        return _STATES[state.value] if 0 <= state.value < len(_STATES) else ""
    finally:
        for obj in (op, task, statics):
            release(obj)
        for h in names:
            combase.WindowsDeleteString(h)
        combase.RoUninitialize()


def _startup_state() -> str:
    try:
        return _startup_task()
    except Exception:
        log.exception("startup task")
        return ""


def _set_startup_task(on: bool, ask: bool):
    try:
        state = _startup_task("on" if on else "off")
    except Exception:
        log.exception("startup task")
        return
    if on and ask and state == "DISABLED_BY_USER":
        open_path("ms-settings:startupapps")  # turned off in Windows Settings: only the user can turn it back on


# --- one copy at a time ----------------------------------------------------------------
class SingleInstance:
    """The first copy owns a mutex; later copies wake it up (show the
    window) and quit. The installer uses the ".Quit" event to close Harfix
    before updating or removing it (installer/harfix.iss)."""

    def __init__(self, name="HarfixDesktop"):
        self.mutex = kernel32.CreateMutexW(None, False, f"Local\\{name}.Mutex")
        self.first = ctypes.get_last_error() != ERROR_ALREADY_EXISTS
        self.show_event = kernel32.CreateEventW(None, False, False, f"Local\\{name}.Show")
        self.quit_event = kernel32.CreateEventW(None, False, False, f"Local\\{name}.Quit")

    def wake_first(self):
        kernel32.SetEvent(self.show_event)

    def on_wake(self, callback):
        self._watch(self.show_event, callback, "harfix-wake")

    def on_quit(self, callback):
        self._watch(self.quit_event, callback, "harfix-quit")

    @staticmethod
    def _watch(event, callback, name):
        def loop():
            while True:
                if kernel32.WaitForSingleObject(event, 0xFFFFFFFF) == 0:
                    try:
                        callback()
                    except Exception:
                        log.exception(name)
        threading.Thread(target=loop, name=name, daemon=True).start()


# --- files -------------------------------------------------------------------------------
def open_path(path: str):
    os.startfile(path)  # noqa: S606 - opening the user's own file/folder


def reveal(path: str):
    """Opens Explorer with the file selected."""
    subprocess.Popen(["explorer", "/select,", os.path.normpath(path)])
