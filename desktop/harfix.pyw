"""Runs Harfix from the source folder without a console window: pythonw harfix.pyw [--tray]

"Start with Windows" uses this file when Harfix runs from source (system.launch_command);
the installed app runs `pythonw -m harfix_desktop` instead.
"""
import pathlib
import sys

if __name__ == "__main__":  # the typing engine's process imports this file again (multiprocessing spawn)
    here = pathlib.Path(__file__).resolve().parent
    sys.path.insert(0, str(here))
    sys.path.insert(1, str(here.parent / "core"))

    from harfix_desktop.app import main

    sys.exit(main())
