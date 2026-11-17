"""Runs the typing engine in a small process of its own.

Windows waits for a keyboard hook on every key press. In its own process
the hook never waits behind the window, or file conversion, so typing
stays instant. The window talks to it through a pipe."""
import logging
import multiprocessing as mp
import threading

log = logging.getLogger("harfix")


def _child(conn, config: dict, log_file: str | None):
    if log_file:
        logging.basicConfig(filename=log_file, level=logging.INFO, encoding="utf-8",
                            format="%(asctime)s %(levelname)s %(name)s: %(message)s")
    from .hook import Engine

    lock = threading.Lock()

    def send(kind, **data):
        with lock:
            try:
                conn.send({"event": kind, **data})
            except (OSError, EOFError, BrokenPipeError):
                pass

    engine = Engine(on_event=send)
    engine.configure(**config)
    engine.start()
    send("started", hotkeys={str(k): v for k, v in engine.hotkeys_ok.items()})
    try:
        while True:
            msg = conn.recv()
            cmd = msg.pop("cmd")
            if cmd == "quit":
                break
            if cmd == "config":
                engine.configure(**msg)
            elif cmd == "status":
                send("status", enabled=engine.enabled, blocked=engine.blocked, swaps=engine.swaps)
    except (EOFError, OSError):
        pass  # the window process is gone
    finally:
        engine.stop()


class EngineProcess:
    def __init__(self, on_event=None, log_file: str | None = None):
        self.on_event = on_event or (lambda kind, **data: None)
        self.log_file = log_file
        self.proc = None
        self.conn = None
        self._lock = threading.Lock()

    def start(self, **config):
        ctx = mp.get_context("spawn")
        self.conn, child = ctx.Pipe()
        self.proc = ctx.Process(target=_child, args=(child, config, self.log_file), name="harfix-engine",
                                daemon=True)
        self.proc.start()
        threading.Thread(target=self._listen, name="harfix-engine-events", daemon=True).start()

    def _listen(self):
        while True:
            try:
                event = self.conn.recv()
            except (EOFError, OSError):
                return
            try:
                self.on_event(event.pop("event"), **event)
            except Exception:
                log.exception("engine event")

    def _send(self, msg: dict):
        with self._lock:
            try:
                self.conn.send(msg)
            except (OSError, AttributeError):
                log.warning("typing engine is not running")

    def configure(self, **config):
        self._send({"cmd": "config", **config})

    def ask_status(self):
        self._send({"cmd": "status"})

    @property
    def alive(self) -> bool:
        return bool(self.proc and self.proc.is_alive())

    def stop(self):
        if not self.proc:
            return
        self._send({"cmd": "quit"})
        self.proc.join(3)
        if self.proc.is_alive():
            self.proc.terminate()
