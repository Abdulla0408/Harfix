"""Converting many files and whole folders at once.

Results go to a "Harfix" folder next to each file (or to one chosen
folder, keeping the sub-folders). Originals are never changed."""
import logging
import os
import pathlib
import threading

from harfix_files import legacy, pdf
from harfix_files.convert import FeatureOff, convert_document, kind_of
from harfix_files.documents import UnsupportedFile

log = logging.getLogger("harfix")

OUT_FOLDER = "Harfix"  # results folder; skipped when a folder is scanned again
MAX_FILES = 5000


def expand(paths) -> list[dict]:
    """Files to convert from dropped/picked files and folders (folders are
    searched inside, skipping earlier results)."""
    items, seen = [], set()

    def add(p: pathlib.Path, root):
        key = os.path.normcase(str(p))
        if key in seen or len(items) >= MAX_FILES or not kind_of(p.name):
            return
        seen.add(key)
        try:
            size = p.stat().st_size
        except OSError:
            return
        items.append({"path": str(p), "name": p.name, "kind": kind_of(p.name), "size": size,
                      "root": str(root) if root else ""})

    for raw in paths:
        p = pathlib.Path(raw)
        if p.is_dir():
            for dirpath, dirnames, filenames in os.walk(p):
                dirnames[:] = sorted(d for d in dirnames if d != OUT_FOLDER and not d.startswith("."))
                for f in sorted(filenames):
                    if not f.startswith("~$"):  # Office lock files
                        add(pathlib.Path(dirpath) / f, p)
        elif p.is_file():
            add(p, None)
    return items


def _unique(path: pathlib.Path) -> pathlib.Path:
    if not path.exists():
        return path
    for i in range(2, 1000):
        candidate = path.with_name(f"{path.stem} ({i}){path.suffix}")
        if not candidate.exists():
            return candidate
    return path


def out_path(item: dict, name: str, out_mode: str, out_dir: str) -> pathlib.Path:
    src = pathlib.Path(item["path"])
    if out_mode == "folder" and out_dir:
        base = pathlib.Path(out_dir)
        if item.get("root"):
            root = pathlib.Path(item["root"])
            base = base / root.name / src.parent.relative_to(root)
    else:
        base = src.parent / OUT_FOLDER
    base.mkdir(parents=True, exist_ok=True)
    return _unique(base / name)


def convert_one(item: dict, target: str, out_mode: str, out_dir: str) -> dict:
    """Converts one file. Returns {"ok": True, "out": path, "how": ...} or
    {"ok": False, "error": key}."""
    try:
        data = pathlib.Path(item["path"]).read_bytes()
        result = convert_document(data, item["name"], target)
        dest = out_path(item, result["name"], out_mode, out_dir)
        dest.write_bytes(result["file"])
        return {"ok": True, "out": str(dest), "how": result["how"]}
    except FeatureOff as e:
        return {"ok": False, "error": e.key}
    except UnsupportedFile:
        return {"ok": False, "error": "unsupported"}
    except pdf.Encrypted:
        return {"ok": False, "error": "pdf_encrypted"}
    except pdf.TooManyPages:
        return {"ok": False, "error": "pdf_too_many"}
    except legacy.LegacyError:
        return {"ok": False, "error": "legacy_failed"}
    except PermissionError:
        return {"ok": False, "error": "locked"}
    except OSError:
        return {"ok": False, "error": "io"}
    except Exception:
        log.exception("conversion failed (%s)", item.get("kind"))
        return {"ok": False, "error": "failed"}


class Batch:
    """Runs in a background thread; reports each file through `report`."""

    def __init__(self, items, target, out_mode, out_dir, report):
        self.items, self.target, self.out_mode, self.out_dir = items, target, out_mode, out_dir
        self.report = report
        self.cancelled = threading.Event()
        self.thread = threading.Thread(target=self._run, name="harfix-batch", daemon=True)

    def start(self):
        self.thread.start()

    def cancel(self):
        self.cancelled.set()

    @property
    def running(self) -> bool:
        return self.thread.is_alive()

    def _run(self):
        ok = failed = 0
        for i, item in enumerate(self.items):
            if self.cancelled.is_set():
                break
            self.report("file", index=i, status="working")
            res = convert_one(item, self.target, self.out_mode, self.out_dir)
            ok += res["ok"]
            failed += not res["ok"]
            self.report("file", index=i, status="done" if res["ok"] else "error", **res)
        self.report("finished", ok=ok, failed=failed, cancelled=self.cancelled.is_set())
