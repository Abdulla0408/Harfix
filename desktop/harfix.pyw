"""Runs Harfix from source without a console window: pythonw harfix.pyw [--tray]

"Start with Windows" uses this file when Harfix runs from source (system.launch_command);
the installed app runs `pythonw -m harfix_desktop` instead.
"""
import sys

if __name__ == "__main__":  # the typing engine's process imports this file again (multiprocessing spawn)
    from harfix_desktop.app import main

    sys.exit(main())
