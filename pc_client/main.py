"""SMS Forward PC Client — system tray application.

Receives SMS verification codes from Android via ntfy.sh.
Shows Windows toast notifications and copies codes to clipboard.
"""

import os
import threading
import tkinter as tk
import subprocess
from tkinter import messagebox

import pystray
from PIL import Image, ImageDraw, ImageFont
from winotify import Notification

import config
import database
import server


def _create_icon_image():
    img = Image.new("RGBA", (64, 64), (0x12, 0xB7, 0xF5, 255))
    draw = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("segoeui.ttf", 20)
    except OSError:
        font = ImageFont.load_default()
    draw.text((32, 32), "SMS", fill="white", anchor="mm", font=font)
    return img


def _show_history_window():
    win = tk.Tk()
    win.title("SMS Forward - 历史记录")
    win.geometry("500x400")

    frame = tk.Frame(win)
    frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=10)

    scrollbar = tk.Scrollbar(frame)
    scrollbar.pack(side=tk.RIGHT, fill=tk.Y)

    listbox = tk.Listbox(
        frame,
        yscrollcommand=scrollbar.set,
        font=("Consolas", 10),
    )
    listbox.pack(fill=tk.BOTH, expand=True)
    scrollbar.config(command=listbox.yview)

    rows = database.get_recent(50)
    for ts, sender, code, content, channel in rows:
        label = f"[{channel}] {ts[:16]}  验证码: {code}  <- {sender}"
        listbox.insert(tk.END, label)
    if not rows:
        listbox.insert(tk.END, "(暂无历史记录)")

    btn_frame = tk.Frame(win)
    btn_frame.pack(fill=tk.X, padx=10, pady=(0, 10))

    tk.Button(
        btn_frame,
        text="清空",
        command=lambda: [database.delete_all(), listbox.delete(0, tk.END), listbox.insert(tk.END, "(暂无历史记录)")],
    ).pack(side=tk.RIGHT)

    win.mainloop()


def _setup_tray(icon):
    icon.visible = True


def _make_menu(icon):
    cfg = config.load_config()
    ntfy_topic = cfg["ntfy_topic"]
    _status_thread = None

    def on_status():
        nonlocal _status_thread
        if _status_thread and _status_thread.is_alive():
            return
        cfg = config.load_config()

        def _show():
            root = tk.Tk()
            root.geometry("1x1+-10000+-10000")
            messagebox.showinfo(
                "SMS Forward 状态",
                f'ntfy 主题: {cfg["ntfy_topic"]}\n'
                f'配置目录: {config.CONFIG_DIR}',
            )
            root.destroy()

        _status_thread = threading.Thread(target=_show, daemon=True)
        _status_thread.start()

    def on_history():
        threading.Thread(target=_show_history_window, daemon=True).start()

    def on_set_ntfy():
        current = config.load_config().get("ntfy_topic", "")
        ps_script = (
            f'Add-Type -AssemblyName Microsoft.VisualBasic; '
            f'$result = [Microsoft.VisualBasic.Interaction]::InputBox('
            f'"输入 ntfy.sh 主题（与 Android 端一致）", '
            f'"设置 ntfy 主题", '
            f'"{current}"); '
            f'if ($result -ne "") {{ Write-Output $result }}'
        )
        r = subprocess.run(
            ["powershell", "-NoProfile", "-Command", ps_script],
            capture_output=True,
            text=True,
            timeout=30,
            creationflags=subprocess.CREATE_NO_WINDOW,
        )
        new_topic = r.stdout.strip() if r.stdout else ""
        if new_topic:
            cfg = config.load_config()
            cfg["ntfy_topic"] = new_topic
            config.save_config(cfg)
            server.run_ntfy_listener(new_topic)
            icon.update_menu()

    def on_toggle_startup(item):
        enabled = not config.is_startup_enabled()
        config.toggle_startup(enabled)
        cfg = config.load_config()
        cfg["run_on_startup"] = enabled
        config.save_config(cfg)
        icon.update_menu()

    def on_settings():
        os.startfile(str(config.CONFIG_FILE))

    def on_quit(icon, item):
        config.release_lock()
        icon.stop()

    return pystray.Menu(
        pystray.MenuItem("状态", on_status, default=True),
        pystray.MenuItem("设置 ntfy 主题", on_set_ntfy),
        pystray.MenuItem(
            "开机自启",
            on_toggle_startup,
            checked=lambda item: config.is_startup_enabled(),
        ),
        pystray.MenuItem("历史记录", on_history),
        pystray.MenuItem("配置文件", on_settings),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("退出", on_quit),
    )


def main():
    if not config.acquire_lock():
        root = tk.Tk()
        root.geometry("1x1+-10000+-10000")
        messagebox.showinfo("提示", "SMS Forward 已在运行中")
        root.destroy()
        return

    cfg = config.load_config()
    ntfy_topic = cfg["ntfy_topic"]

    server.run_ntfy_listener(ntfy_topic)

    icon = pystray.Icon(
        "sms_forward",
        _create_icon_image(),
        f"SMS Forward\nntfy: {ntfy_topic}",
    )
    icon.menu = _make_menu(icon)

    # Startup toast
    Notification(
        app_id="SMS Forward",
        title="SMS Forward 已启动",
        msg="验证码将实时推送到此电脑",
    ).show()

    icon.run(setup=_setup_tray)


if __name__ == "__main__":
    main()
