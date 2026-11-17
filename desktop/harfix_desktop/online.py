"""The online layer: the remote settings (ads, announcements, "new version"
note) and the anonymous daily count. The settings are edited in the admin
panel (Mini App) and kept in Supabase; miniapp/config.json on GitHub Pages is
the backup copy.

Everything here is optional. Without internet Harfix works exactly as before
and shows no ads: ads appear only if the config switches them on AND it was
loaded online within the last day. Nothing typed or converted is ever sent;
the daily count is only {random install id, "windows", version}.
"""
import datetime as dt
import json
import logging
import re
import threading
import time
import urllib.request
import uuid

from .paths import data_dir

log = logging.getLogger("harfix")

CONFIG_RPC = "https://lucopmrphagzxticypmq.supabase.co/rest/v1/rpc/harfix_config"
CONFIG_URL = "https://abdulla0408.github.io/harfix-app/config.json"  # backup
STATS_URL = "https://lucopmrphagzxticypmq.supabase.co/rest/v1/rpc/harfix_ping"
STATS_KEY = "sb_publishable_mI40X75CzoVl7fyHziSoDQ_-rLXnRyt"  # publishable: safe to ship
PLATFORM = "windows"

CHECK_EVERY = 30 * 60  # Harfix runs for weeks in the tray: look again now and then
REFRESH_AFTER = 6 * 3600  # load the config again after this long
FRESH_FOR = 24 * 3600  # ads only with a config loaded online within a day
TIMEOUT = 10

_HTTPS = re.compile(r"^https://[^\s\"'<>\\]+$", re.I)


def today() -> str:
    return dt.date.today().isoformat()


def _obj(x) -> dict:
    return x if isinstance(x, dict) else {}


def stats_target(config) -> tuple[str, str]:
    """Where the daily count goes; the config may move it."""
    s = _obj(_obj(config).get("stats"))
    url, key = s.get("url"), s.get("key")
    if isinstance(url, str) and _HTTPS.match(url) and len(url) <= 500 and isinstance(key, str) and key.strip():
        return url, key.strip()[:200]
    return STATS_URL, STATS_KEY


def safe_url(url) -> bool:
    return isinstance(url, str) and len(url) <= 500 and bool(_HTTPS.match(url))


def _request(url, body=None, key=None):
    headers = {"User-Agent": "Harfix-Windows", "Accept": "application/json"}
    data = None
    if body is not None:
        data = json.dumps(body).encode()
        headers.update({"Content-Type": "application/json", "apikey": key})
    req = urllib.request.Request(url, data=data, headers=headers, method="POST" if data else "GET")
    with urllib.request.urlopen(req, timeout=TIMEOUT) as r:
        raw = r.read(256 * 1024)
        return r.status, raw


class Online:
    def __init__(self, settings, version: str, on_change=lambda: None, cache=None):
        self.settings = settings
        self.version = version
        self.on_change = on_change
        self.cache = cache or data_dir() / "online.json"
        self.fetched_at = 0.0  # last successful load (wall clock)
        self.tried_at = 0.0
        self.config = self._read_cache()
        self._stop = threading.Event()
        if not self.settings["first_day"]:
            self.settings.set("first_day", today())

    # --- background ---------------------------------------------------------------
    def start(self):
        threading.Thread(target=self._loop, name="online", daemon=True).start()

    def stop(self):
        self._stop.set()

    def _loop(self):
        if self._stop.wait(5):  # let the window start first
            return
        while True:
            if time.time() - self.tried_at >= REFRESH_AFTER:
                self.refresh()
            self.ping()
            if self._stop.wait(CHECK_EVERY):
                return

    def refresh(self) -> bool:
        self.tried_at = time.time()
        config = None
        for url, body in ((CONFIG_RPC, {}), (CONFIG_URL, None)):  # Supabase, then the backup copy
            try:
                status, raw = _request(url, body, STATS_KEY if body is not None else None)
                data = json.loads(raw.decode("utf-8"))
            except Exception as e:  # offline, site down, bad file: try the next / keep what we have
                log.info("online config %s: %s", url, e)
                continue
            if status == 200 and isinstance(data, dict):
                config = data
                break
        if config is None:
            return False
        self.config = config
        self.fetched_at = time.time()
        try:
            self.cache.write_text(json.dumps(config, ensure_ascii=False), encoding="utf-8")
        except OSError:
            pass
        self.on_change()
        return True

    def ping(self) -> bool:
        """The anonymous "Harfix was used today" count, once a day."""
        day = today()
        if not self.settings["stats"] or self.settings["ping_day"] == day:
            return False
        if _obj(_obj(self.config).get("stats")).get("enabled") is False:
            return False
        url, key = stats_target(self.config)
        body = {"p_id": self.install_id(), "p_platform": PLATFORM, "p_version": self.version}
        try:
            status, _ = _request(url, body, key)
        except Exception as e:
            log.info("online ping: %s", e)
            return False
        if 200 <= status < 300:
            self.settings.set("ping_day", day)
            return True
        return False

    # --- state for the window -------------------------------------------------------
    def install_id(self) -> str:
        if not self.settings["install_id"]:
            self.settings.set("install_id", str(uuid.uuid4()))
        return self.settings["install_id"]

    def install_days(self) -> int:
        try:
            first = dt.date.fromisoformat(self.settings["first_day"])
        except ValueError:
            return 0
        return max(0, (dt.date.today() - first).days)

    def state(self) -> dict:
        return {
            "config": self.config,
            "fresh": time.time() - self.fetched_at < FRESH_FOR if self.fetched_at else False,
            "platform": PLATFORM,
            "version": self.version,
            "install_days": self.install_days(),
            "dismissed": list(self.settings["dismissed"]),
        }

    def dismiss(self, item: str):
        if isinstance(item, str) and 0 < len(item) <= 100:
            items = [x for x in self.settings["dismissed"] if x != item] + [item]
            self.settings.set("dismissed", items[-50:])

    def _read_cache(self) -> dict | None:
        try:
            config = json.loads(self.cache.read_text(encoding="utf-8"))
            return config if isinstance(config, dict) else None
        except (OSError, ValueError):
            return None
