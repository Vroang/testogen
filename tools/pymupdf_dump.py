"""Строки PDF с размером шрифта и положением — в том же виде, что даёт PdfExtractor на телефоне."""
import sys, json, collections, pymupdf
doc = pymupdf.open(sys.argv[1])
out = open(sys.argv[2], "w", encoding="utf-8")
for i, page in enumerate(doc):
    lines = []
    for b in page.get_text("dict")["blocks"]:
        for l in b.get("lines", []):
            spans = [s for s in l["spans"] if s["text"].strip()]
            if not spans: continue
            text = "".join(s["text"] for s in l["spans"]).strip()
            sizes = collections.Counter()
            for s in spans: sizes[round(s["size"], 1)] += len(s["text"])
            font = spans[0]["font"]
            lines.append({"t": text, "s": sizes.most_common(1)[0][0],
                          "b": any(k in font for k in ("Bold", "Demi", "Black", "Heavy")),
                          "i": ("Italic" in font or "Oblique" in font),
                          "x": round(l["bbox"][0], 1), "y": round(l["bbox"][1], 1)})
    out.write(json.dumps({"p": i, "w": page.rect.width, "h": page.rect.height, "lines": lines}, ensure_ascii=False) + "\n")
