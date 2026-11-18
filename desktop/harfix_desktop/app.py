"""Harfix for Windows: the window, the tray icon and the typing engine."""
import argparse
import json
import logging
import multiprocessing
import sys
import threading

import webview
from harfix_files import legacy, pdf, tools
from webview.dom import DOMEventHandler
from yangi_alifbo import convert

from . import VERSION, batch, system
from .api import Api
from .engine.process import EngineProcess
from .online import Online
from .paths import ASSETS, INSTALLED, PACKAGED, UI, data_dir, log_file
from .settings import Settings
from .tray import Tray

log = logging.getLogger("harfix")
L = convert


class App:
    def __init__(self, start_hidden: bool, autostart: str | None = None):
        self.start_hidden = start_hidden
        self.settings = Settings()
        if autostart:  # chosen in the installer
            self.settings.set("autostart", autostart == "on")
        self.window = None
        self.tray = None
        self.batch = None
        self.quitting = False
        self.hotkeys = {}
        self.engine = EngineProcess(on_event=self._engine_event, log_file=str(log_file("engine")))
        self.online = Online(self.settings, VERSION, on_change=lambda: self.push("online"), store=PACKAGED)

    # --- start / stop -------------------------------------------------------------
    def run(self, single: system.SingleInstance):
        legacy.setup(tools.find_soffice(), data_dir())
        log.info("Harfix %s | PDF %s | LibreOffice %s", VERSION, pdf.available(),
                 legacy.available())
        if PACKAGED:
            self._sync_startup_task()
        elif INSTALLED and self.settings["autostart"] != system.autostart_enabled():
            system.set_autostart(self.settings["autostart"])
        self.engine.start(enabled=self.settings["realtime"], target=self.settings["target"],
                          exclude=self.settings["exclude"])
        self.window = webview.create_window(
            "Harfix", url=str(UI / "index.html"), js_api=Api(self), width=1040, height=740, min_size=(760, 560),
            hidden=self.start_hidden, background_color="#F1F3F6", text_select=True)
        self.window.events.closing += self._on_closing
        self.window.events.loaded += self._on_loaded
        self.tray = Tray(self)
        self.tray.start()
        self.online.start()  # config + daily count in the background; nothing waits for it
        single.on_wake(self.show_window)
        single.on_quit(self.quit)  # the installer asks us to close
        try:
            webview.start(http_server=True, private_mode=True, icon=str(ASSETS / "harfix.ico"))
        finally:
            self.shutdown()

    def _sync_startup_task(self):
        """Store version: the first start turns the startup task on (as the installer's ticked box
        does); later the user may switch it off here or in Windows Settings, and we follow that."""
        state = system._startup_state()
        if state == "DISABLED" and self.settings["autostart"]:
            system.set_autostart(True, ask=False)
            state = system._startup_state()
        if state:
            self.settings.set("autostart", state in ("ENABLED", "ENABLED_BY_POLICY"))

    def shutdown(self):
        self.online.stop()
        if self.batch:
            self.batch.cancel()
        self.engine.stop()
        if self.tray:
            self.tray.stop()

    def quit(self):
        self.quitting = True
        self.window.destroy()

    def _on_closing(self):
        if self.quitting or not self.settings["close_to_tray"]:
            return True
        self.window.hide()
        if not self.settings["tray_hint_shown"]:
            self.tray.notify(L("Harfix ishlashda davom etadi: soat yonidagi belgidan ochiladi. "
                               "Butunlay chiqish — oʻsha belgida “Chiqish”."))
            self.settings.set("tray_hint_shown", True)
        return False

    def _on_loaded(self):
        doc = self.window.dom.document
        doc.events.dragover += DOMEventHandler(lambda e: None, True, True)
        doc.events.drop += DOMEventHandler(self._on_drop, True, True)

    def _on_drop(self, event):
        files = (event.get("dataTransfer") or {}).get("files") or []
        paths = [f["pywebviewFullPath"] for f in files if f.get("pywebviewFullPath")]
        self.push("dropped", items=batch.expand(paths))

    # --- window -------------------------------------------------------------------
    def show_window(self):
        if self.window:
            self.window.show()
            self.window.restore()

    def push(self, kind: str, **data):
        """Sends an event to the page (window.harfixEvent)."""
        if self.window:
            payload = json.dumps({"kind": kind, **data})
            try:
                self.window.evaluate_js(f"window.harfixEvent && window.harfixEvent({payload})")
            except Exception:
                log.exception("push %s", kind)

    def state(self) -> dict:
        return {
            "version": VERSION,
            "settings": dict(self.settings.values),
            "tools": {"pdf": pdf.available(), "legacy": legacy.available()},
            "hotkeys": self.hotkeys,
            "engine": self.engine.alive,
            "installed": INSTALLED,
            "busy": bool(self.batch and self.batch.running),
        }

    # --- settings -----------------------------------------------------------------
    def apply_setting(self, key, value):
        if not self.settings.set(key, value):
            return
        if key in ("realtime", "target", "exclude"):
            self.engine.configure(**{"enabled" if key == "realtime" else key: value})
        if key == "realtime" and self.tray:
            self.tray.refresh()
        if key == "autostart":
            system.set_autostart(value)
        if key == "stats" and value:
            threading.Thread(target=self.online.ping, daemon=True).start()

    def toggle_realtime(self):
        self.apply_setting("realtime", not self.settings["realtime"])
        self.push("realtime", on=self.settings["realtime"])

    def _engine_event(self, kind, **data):
        if kind == "started":
            self.hotkeys = data.get("hotkeys", {})
            self.push("engine", hotkeys=self.hotkeys)
        elif kind == "toggled":
            self.settings.set("realtime", data["enabled"])
            self.tray.refresh()
            self.tray.notify(L("Yozayotganda oʻgirish yoqildi") if data["enabled"]
                             else L("Yozayotganda oʻgirish oʻchirildi"))
            self.push("realtime", on=data["enabled"])
        elif kind == "selection" and not data.get("ok"):
            self.tray.notify(L("Avval matnni belgilang, keyin Ctrl+Shift+H ni bosing."))

    # --- files ----------------------------------------------------------------------
    def start_batch(self, items) -> bool:
        if self.batch and self.batch.running:
            return False
        self.batch = batch.Batch(items, self.settings["target"], self.settings["out_mode"],
                                 self.settings["out_dir"], report=self.push)
        self.batch.start()
        return True

    def cancel_batch(self):
        if self.batch:
            self.batch.cancel()


def main():
    multiprocessing.freeze_support()
    parser = argparse.ArgumentParser(prog="harfix")
    parser.add_argument("--tray", action="store_true", help="start hidden (next to the clock)")
    parser.add_argument("--autostart", choices=["on", "off"], help="set by the installer")
    args, _ = parser.parse_known_args()

    single = system.SingleInstance()
    if not single.first:
        single.wake_first()  # Harfix is already running: show its window
        return 0
    logging.basicConfig(filename=log_file(), level=logging.INFO, encoding="utf-8",
                        format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    system.set_app_id()
    try:
        App(start_hidden=args.tray, autostart=args.autostart).run(single)
    except Exception:
        log.exception("Harfix crashed")
        raise
    return 0


if __name__ == "__main__":
    sys.exit(main())
