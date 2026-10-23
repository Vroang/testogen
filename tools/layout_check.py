"""Сверка предпросмотра с файлом Word через LibreOffice.

LayoutCheckTest пишет в app/build/layout-check пары <имя>.docx + <имя>.json (где каждый блок должен
оказаться по модели Paginator). Скрипт переводит .docx в PDF (LibreOffice), находит в PDF заголовки
вариантов и вопросы и сравнивает: число страниц, страницу каждого блока, высоту (с поправкой на
постоянный сдвиг базовой линии), наложения текста и вылезание за поля.

    python tools/layout_check.py app/build/layout-check [--png]

--png — ещё и картинки страниц (<имя>-<страница>.png) для просмотра глазами.
Нужны LibreOffice и PyMuPDF (pip install pymupdf).
"""
import json
import os
import subprocess
import sys

import pymupdf

SOFFICE = os.environ.get("SOFFICE", r"C:\Program Files\LibreOffice\program\soffice.exe")


def norm(s):
    return " ".join(s.replace("\u00a0", " ").split())


def pdf_lines(path):
    """Строки PDF: (страница, x0, верх, низ, текст)."""
    doc = pymupdf.open(path)
    out = []
    for i, page in enumerate(doc):
        for b in page.get_text("dict")["blocks"]:
            for l in b.get("lines", []):
                t = norm("".join(s["text"] for s in l["spans"]))
                if t:
                    x0, y0, x1, y1 = l["bbox"]
                    out.append((i + 1, x0, y0, y1, t))
    return doc, out


def check(name, folder, png):
    exp = json.load(open(os.path.join(folder, name + ".json"), encoding="utf-8"))
    doc, lines = pdf_lines(os.path.join(folder, name + ".pdf"))
    problems = []
    if len(doc) != exp["pages"]:
        problems.append(f"страниц: в файле {len(doc)}, в предпросмотре {exp['pages']}")
    diffs = []
    used = set()
    for b in exp["blocks"]:
        want = norm(b["text"])
        prefix = want[:18]
        cands = []
        for k, (pg, x0, y0, y1, t) in enumerate(lines):
            if k in used:
                continue
            if b["kind"] == "title":
                ok = t == want and b["x"] - 2 <= x0 <= b["x"] + b["width"]
            else:
                ok = t.startswith(prefix) and abs(x0 - b["x"]) < 6
            if ok:
                cands.append((0 if pg == b["page"] else 1, abs(y0 - b["top"]), k, pg, y0))
        if not cands:
            problems.append(f"не найден блок «{want[:40]}» (стр. {b['page']})")
            continue
        cands.sort()
        _, _, k, pg, y0 = cands[0]
        used.add(k)
        if pg != b["page"]:
            problems.append(f"«{want[:40]}»: в файле стр. {pg}, в предпросмотре стр. {b['page']}")
        else:
            diffs.append((y0 - b["top"], want[:40], pg))
    if diffs:
        base = sorted(d for d, _, _ in diffs)[len(diffs) // 2]
        worst = max(diffs, key=lambda d: abs(d[0] - base))
        if abs(worst[0] - base) > 1.5:
            problems.append(f"сдвиг по высоте {worst[0] - base:+.1f} пт у «{worst[1]}» (стр. {worst[2]})")
    # Наложения строк и вылезание за лист
    for i, page in enumerate(doc):
        boxes = [(x0, y0, y1, t) for pg, x0, y0, y1, t in lines if pg == i + 1]
        for x0, y0, y1, t in boxes:
            if y1 > page.rect.height - 5:
                problems.append(f"стр. {i + 1}: текст у самого края листа «{t[:30]}»")
        boxes.sort(key=lambda b: (round(b[0] / 40), b[1]))
        for (ax, ay0, ay1, at), (bx, by0, by1, bt) in zip(boxes, boxes[1:]):
            if abs(ax - bx) < 20 and by0 < ay1 - 3 and by0 > ay0:
                problems.append(f"стр. {i + 1}: строки наезжают «{at[:25]}» / «{bt[:25]}»")
    if png:
        for i, page in enumerate(doc):
            page.get_pixmap(dpi=60).save(os.path.join(folder, f"{name}-{i + 1}.png"))
    return len(doc), exp["pages"], problems


def main():
    folder = os.path.abspath(sys.argv[1])
    png = "--png" in sys.argv
    docs = sorted(f for f in os.listdir(folder) if f.endswith(".docx"))
    subprocess.run([SOFFICE, "--headless", "--convert-to", "pdf", "--outdir", folder] + [os.path.join(folder, d) for d in docs],
                   check=True, stdout=subprocess.DEVNULL)
    bad = 0
    for d in docs:
        name = d[:-5]
        real, model, problems = check(name, folder, png)
        status = "OK " if not problems else "ПЛОХО"
        print(f"{status} {name}: {real} стр. (модель {model})")
        for p in problems[:8]:
            print("     ", p)
        bad += bool(problems)
    print(f"\nВсего {len(docs)}, с расхождениями {bad}")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
