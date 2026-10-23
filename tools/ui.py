"""Мини-помощник для проверки на телефоне: нажать по тексту, ввести текст, снимок экрана.
Использование: python ui.py tap "Текст" | type "текст" | shot имя | find "Текст" | key N | back
"""
import os, re, subprocess, sys, time
sys.stdout.reconfigure(encoding="utf-8")

# Путь к adb и телефон — из окружения (ADB, ADB_SERIAL), снимки — в UI_OUT (по умолчанию рядом со скриптом)
ADB = [os.environ.get("ADB", "adb")] + (["-s", os.environ["ADB_SERIAL"]] if os.environ.get("ADB_SERIAL") else [])
OUT = os.environ.get("UI_OUT", os.path.dirname(os.path.abspath(__file__)))
env = dict(os.environ, MSYS_NO_PATHCONV="1")


def adb(*args, capture=False):
    r = subprocess.run(ADB + list(args), capture_output=True, env=env)
    return r.stdout if capture else None


def dump():
    adb("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    return adb("shell", "cat", "/sdcard/ui.xml", capture=True).decode("utf-8", "replace")


def nodes(xml):
    for m in re.finditer(r'<node [^>]*?text="([^"]*)"[^>]*?content-desc="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        t, d, x1, y1, x2, y2 = m.groups()
        yield t, d, (int(x1) + int(x2)) // 2, (int(y1) + int(y2)) // 2


def find(text, exact=False):
    xml = dump()
    for t, d, x, y in nodes(xml):
        for v in (t, d):
            if (v == text) if exact else (text.lower() in v.lower()):
                return x, y
    return None


def main():
    cmd, *rest = sys.argv[1:]
    if cmd == "tap":
        pos = find(rest[0], exact=len(rest) > 1 and rest[1] == "exact")
        if not pos:
            print("NOT FOUND:", rest[0]); sys.exit(1)
        adb("shell", "input", "tap", str(pos[0]), str(pos[1]))
        print("tap", rest[0], pos)
    elif cmd == "find":
        print(find(rest[0]))
    elif cmd == "texts":
        print("\n".join(f"{t or d} @{x},{y}" for t, d, x, y in nodes(dump()) if t or d))
    elif cmd == "type":
        # adb input text не умеет кириллицу — через буфер ADBKeyboard нет, поэтому только латиница/цифры
        adb("shell", "input", "text", rest[0].replace(" ", "%s"))
    elif cmd == "key":
        adb("shell", "input", "keyevent", rest[0])
    elif cmd == "back":
        adb("shell", "input", "keyevent", "4")
    elif cmd == "swipe":
        adb("shell", "input", "swipe", *rest)
    elif cmd == "shot":
        data = adb("exec-out", "screencap", "-p", capture=True)
        path = os.path.join(OUT, rest[0] + ".png")
        open(path, "wb").write(data)
        try:
            from PIL import Image
            im = Image.open(path)
            im.resize((im.width // 2, im.height // 2)).save(os.path.join(OUT, rest[0] + "_s.png"))
        except Exception:
            pass
        print(path)
    time.sleep(0.6)


main()
