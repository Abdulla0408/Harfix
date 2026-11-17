"""User settings, kept as JSON in %APPDATA%\\Harfix\\settings.json."""
import json
import logging
import re
import threading

from yangi_alifbo import TARGETS

from .engine.hook import DEFAULT_EXCLUDE
from .paths import INSTALLED, data_dir

log = logging.getLogger("harfix")

DEFAULTS = {
    "target": "new",  # alphabet for text, files and the selection hotkey
    "realtime": True,  # convert while typing in every program
    "exclude": sorted(DEFAULT_EXCLUDE),  # programs (exe names) left alone
    "autostart": INSTALLED,  # start with Windows (installed app only by default)
    "out_mode": "beside",  # beside: a "Harfix" folder next to each file; folder: one chosen folder
    "out_dir": "",
    "close_to_tray": True,
    "tray_hint_shown": False,
    "quiz_best": 0,
    # online layer (online.py)
    "stats": True,  # the anonymous daily count; the user can turn it off
    "install_id": "",  # random id, made on the first count
    "first_day": "",  # first day Harfix ran (new users see no ads in their first days)
    "ping_day": "",  # last day the count was sent
    "dismissed": [],  # announcements / new version notes the user closed
}


_DAY = re.compile(r"^(\d{4}-\d\d-\d\d)?$")
_ID = re.compile(r"^([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})?$")


def _valid(key, value):
    if key in ("first_day", "ping_day"):
        return isinstance(value, str) and bool(_DAY.match(value))
    if key == "install_id":
        return isinstance(value, str) and bool(_ID.match(value))
    if key == "dismissed":
        return isinstance(value, list) and len(value) <= 50 and all(isinstance(x, str) and len(x) <= 100 for x in value)
    if key == "target":
        return value in TARGETS
    if key in ("realtime", "autostart", "close_to_tray", "tray_hint_shown", "stats"):
        return isinstance(value, bool)
    if key == "exclude":
        return isinstance(value, list) and all(isinstance(x, str) for x in value)
    if key == "out_mode":
        return value in ("beside", "folder")
    if key == "out_dir":
        return isinstance(value, str)
    if key == "quiz_best":
        return isinstance(value, int) and 0 <= value <= 100
    return False


class Settings:
    def __init__(self, path=None):
        self.path = path or data_dir() / "settings.json"
        self._lock = threading.Lock()
        self.values = dict(DEFAULTS)
        try:
            stored = json.loads(self.path.read_text(encoding="utf-8"))
            self.values.update({k: v for k, v in stored.items() if k in DEFAULTS and _valid(k, v)})
        except FileNotFoundError:
            pass
        except Exception:
            log.warning("settings.json is damaged; using defaults")

    def __getitem__(self, key):
        return self.values[key]

    def set(self, key, value) -> bool:
        if key not in DEFAULTS or not _valid(key, value):
            return False
        with self._lock:
            self.values[key] = value
            tmp = self.path.with_suffix(".tmp")
            tmp.write_text(json.dumps(self.values, ensure_ascii=False, indent=2), encoding="utf-8")
            tmp.replace(self.path)
        return True
