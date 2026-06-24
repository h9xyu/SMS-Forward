"""Toast notification, clipboard copy, and database storage."""

import subprocess
import threading
import time
import database
from winotify import Notification

_DEDUP_WINDOW = 10
_lock = threading.Lock()
_last_code = ""
_last_time = 0.0


def handle_forward(sender, code, content, channel):
    global _last_code, _last_time
    with _lock:
        now = time.time()
        if code == _last_code and (now - _last_time) < _DEDUP_WINDOW:
            return
        _last_code = code
        _last_time = now

    _show_toast(code, sender)
    _copy_to_clipboard(code)
    database.add_record(sender, code, content, channel)
    database.delete_older_than(30)


def _show_toast(code, sender):
    Notification(
        app_id="SMS Forward",
        title=f"验证码: {code}",
        msg=f"来自: {sender}  |  已复制到剪贴板，Ctrl+V 粘贴",
    ).show()


def _copy_to_clipboard(code):
    try:
        subprocess.run(
            ["clip"],
            input=code,
            text=True,
            timeout=2,
            creationflags=subprocess.CREATE_NO_WINDOW,
        )
    except (subprocess.TimeoutExpired, OSError):
        pass
