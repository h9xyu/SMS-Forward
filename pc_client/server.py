"""ntfy.sh SSE listener for receiving SMS verification codes."""

import json
import time
import threading

import requests

import actions

_active_topic = None


def run_ntfy_listener(topic):
    global _active_topic
    if topic == _active_topic:
        return  # Already listening to this topic
    _active_topic = topic

    t = threading.Thread(target=_ntfy_loop, args=(topic,), daemon=True)
    t.start()


def _ntfy_loop(topic):
    url = f"https://ntfy.sh/{topic}/json"
    while True:
        try:
            response = requests.get(url, stream=True, timeout=90)
            for line in response.iter_lines(decode_unicode=True):
                if not line:
                    continue
                try:
                    msg = json.loads(line)
                    if msg.get("event") != "message":
                        continue
                    inner = json.loads(msg.get("message", "{}"))
                    sender = inner.get("sender", "")
                    code = inner.get("code", "")
                    content = inner.get("content", "")
                    if code:
                        actions.handle_forward(sender, code, content, channel="ntfy")
                except (json.JSONDecodeError, KeyError):
                    continue
        except Exception:
            time.sleep(5)
