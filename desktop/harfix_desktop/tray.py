"""The icon next to the clock (grey while real-time typing is off)."""
import logging

import pystray
from PIL import Image, ImageEnhance
from yangi_alifbo import convert

from .paths import ASSETS

log = logging.getLogger("harfix")
L = convert  # menu texts are written in the current Latin alphabet, shown in the new one


def _images():
    color = Image.open(ASSETS / "icon-64.png").convert("RGBA")
    grey = ImageEnhance.Color(color).enhance(0.0)
    grey = ImageEnhance.Brightness(grey).enhance(0.8)
    return color, grey


class Tray:
    def __init__(self, app):
        self.app = app
        self.color, self.grey = _images()
        menu = pystray.Menu(
            pystray.MenuItem(L("Harfixʼni ochish"), lambda: app.show_window(), default=True),
            pystray.MenuItem(L("Yozayotganda oʻgirish (Ctrl+Shift+Y)"), lambda: app.toggle_realtime(),
                             checked=lambda item: app.settings["realtime"]),
            pystray.MenuItem(L("Belgilangan matn: Ctrl+Shift+H"), None, enabled=False),
            pystray.Menu.SEPARATOR,
            pystray.MenuItem(L("Chiqish"), lambda: app.quit()),
        )
        self.icon = pystray.Icon("Harfix", self.image(), self.title(), menu)

    def image(self):
        return self.color if self.app.settings["realtime"] else self.grey

    def title(self):
        state = L("yoqilgan") if self.app.settings["realtime"] else L("oʻchiq")
        return f"Harfix — {L('yozayotganda oʻgirish')}: {state}"

    def start(self):
        self.icon.run_detached()

    def refresh(self):
        try:
            self.icon.icon = self.image()
            self.icon.title = self.title()
            self.icon.update_menu()
        except Exception:
            log.exception("tray refresh")

    def notify(self, text: str):
        try:
            self.icon.notify(text, "Harfix")
        except Exception:
            log.exception("tray notify")

    def stop(self):
        try:
            self.icon.stop()
        except Exception:
            pass
