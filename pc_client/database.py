"""SQLite history storage for SMS Forward PC client."""

import sqlite3
from datetime import datetime, timezone
from pathlib import Path

DB_PATH = Path.home() / ".sms_forward" / "history.db"

# SQLite-compatible: YYYY-MM-DD HH:MM:SS (matches datetime('now') format)
FMT = "%Y-%m-%d %H:%M:%S"


def _connect():
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(str(DB_PATH))
    conn.execute(
        """CREATE TABLE IF NOT EXISTS history (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            timestamp TEXT NOT NULL,
            sender TEXT NOT NULL,
            code TEXT NOT NULL,
            content TEXT NOT NULL,
            channel TEXT NOT NULL
        )"""
    )
    conn.commit()
    return conn


def add_record(sender, code, content, channel):
    conn = _connect()
    ts = datetime.now(timezone.utc).strftime(FMT)
    conn.execute(
        "INSERT INTO history (timestamp, sender, code, content, channel) VALUES (?, ?, ?, ?, ?)",
        (ts, sender, code, content, channel),
    )
    conn.commit()
    conn.close()


def get_recent(limit=50):
    conn = _connect()
    rows = conn.execute(
        "SELECT timestamp, sender, code, content, channel FROM history ORDER BY id DESC LIMIT ?",
        (limit,),
    ).fetchall()
    conn.close()
    return rows


def delete_all():
    conn = _connect()
    conn.execute("DELETE FROM history")
    conn.commit()
    conn.close()


def delete_older_than(days=30):
    conn = _connect()
    conn.execute(
        "DELETE FROM history WHERE timestamp < datetime('now', ?)",
        (f"-{days} days",),
    )
    conn.commit()
    conn.close()
