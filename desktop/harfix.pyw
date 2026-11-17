"""Starts Harfix from the source folder without a console window (pythonw harfix.pyw).
"Start with Windows" points here when Harfix runs from source (system.launch_command)."""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
sys.path.insert(1, str(pathlib.Path(__file__).resolve().parent.parent / "core"))

from harfix_desktop.app import main  # noqa: E402

sys.exit(main())
