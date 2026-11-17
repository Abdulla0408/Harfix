"""Functions the window (JavaScript) can call: pywebview.api.<name>(...)."""
import logging
import os

import webview

from . import batch, online, system

log = logging.getLogger("harfix")

# pywebview wants "Description (*.ext;*.ext2)" for every entry, or the dialog does not open.
FILE_TYPES = ("Harfix (*.docx;*.docm;*.xlsx;*.xlsm;*.pptx;*.pptm;*.txt;*.pdf;*.doc;*.xls;*.ppt;*.rtf;*.odt;*.ods;"
              "*.odp)", "Barcha fayllar (*.*)")


class Api:
    def __init__(self, app):
        self._app = app

    def state(self) -> dict:
        return self._app.state()

    def set(self, key: str, value) -> dict:
        self._app.apply_setting(key, value)
        return self._app.state()

    # --- files ----------------------------------------------------------------------
    def pick_files(self) -> list[dict]:
        paths = self._app.window.create_file_dialog(webview.FileDialog.OPEN, allow_multiple=True,
                                                    file_types=FILE_TYPES)
        return batch.expand(paths or [])

    def pick_folder(self) -> list[dict]:
        paths = self._app.window.create_file_dialog(webview.FileDialog.FOLDER)
        return batch.expand(paths or [])

    def pick_out_dir(self) -> str:
        paths = self._app.window.create_file_dialog(webview.FileDialog.FOLDER)
        if paths:
            self._app.apply_setting("out_dir", paths[0])
            self._app.apply_setting("out_mode", "folder")
            return paths[0]
        return ""

    def start(self, items: list[dict]) -> bool:
        return self._app.start_batch(items)

    def cancel(self):
        self._app.cancel_batch()

    def open(self, path: str):
        if path and os.path.exists(path):
            system.open_path(path)

    def reveal(self, path: str):
        if path and os.path.exists(path):
            system.reveal(path)

    # --- online (online.py) ------------------------------------------------------------
    def online(self) -> dict:
        return self._app.online.state()

    def dismiss(self, item: str):
        self._app.online.dismiss(item)

    def open_url(self, url: str):
        """Sponsor / announcement links: plain https only, in the default browser."""
        if online.safe_url(url):
            os.startfile(url)

    # --- other ----------------------------------------------------------------------
    def save_quiz(self, score: int) -> int:
        best = max(int(score), self._app.settings["quiz_best"])
        self._app.settings.set("quiz_best", best)
        return best

    def hide(self):
        self._app.window.hide()

    def quit(self):
        self._app.quit()
