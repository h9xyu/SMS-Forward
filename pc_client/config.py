"""Configuration management for SMS Forward PC client."""

import json
import os
import sys
from pathlib import Path

CONFIG_DIR = Path.home() / ".sms_forward"
CONFIG_FILE = CONFIG_DIR / "config.json"
LOCK_FILE = CONFIG_DIR / ".lock"

DEFAULTS = {
    "ntfy_topic": "sms-forward-app",
    "run_on_startup": False,
}


def load_config():
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    config = dict(DEFAULTS)
    if CONFIG_FILE.exists():
        try:
            with open(CONFIG_FILE, "r", encoding="utf-8") as f:
                saved = json.load(f)
            config.update(saved)
        except (json.JSONDecodeError, OSError):
            pass
    return config


def save_config(config):
    CONFIG_DIR.mkdir(parents=True, exist_ok=True)
    with open(CONFIG_FILE, "w", encoding="utf-8") as f:
        json.dump(config, f, indent=2, ensure_ascii=False)


def acquire_lock():
    """Return True if we are the only instance, False if another is running."""
    try:
        if LOCK_FILE.exists():
            pid = int(LOCK_FILE.read_text().strip())
            # Check if the process still exists
            try:
                os.kill(pid, 0)
                return False  # Process still alive
            except OSError:
                pass  # Process dead, stale lock
        LOCK_FILE.write_text(str(os.getpid()))
        return True
    except OSError:
        return True


def release_lock():
    try:
        if LOCK_FILE.exists():
            LOCK_FILE.unlink()
    except OSError:
        pass


STARTUP_DIR = Path(os.environ.get("APPDATA", "")) / "Microsoft" / "Windows" / "Start Menu" / "Programs" / "Startup"
STARTUP_BAT = STARTUP_DIR / "sms_forward.bat"


def _exe_path():
    exe_dir = Path(sys.executable).parent
    built_exe = exe_dir / "SMSForward.exe"
    if built_exe.exists():
        return str(built_exe)
    main_py = Path(__file__).parent / "main.py"
    return str(main_py)


def is_startup_enabled():
    return STARTUP_BAT.exists()


def toggle_startup(enabled):
    if enabled:
        STARTUP_DIR.mkdir(parents=True, exist_ok=True)
        target = _exe_path()
        if target.endswith(".exe"):
            content = f'@echo off\nstart "" "{target}"'
        else:
            content = f'@echo off\nstart "" "{sys.executable}" "{target}"'
        STARTUP_BAT.write_text(content)
    else:
        if STARTUP_BAT.exists():
            STARTUP_BAT.unlink()
