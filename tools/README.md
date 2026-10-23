# Инструменты для отладки разбора учебников

Тест `TextbookAnalyzerTest` гоняет анализатор на настоящем учебнике по выгрузкам строк
из `examples/` (в git их нет — учебник под авторским правом).

- `PdfBoxDump.java` — выгрузка тем же способом, что `PdfExtractor` на телефоне (PDFBox 2.0):
  ```bash
  java -cp pdfbox-app-2.0.32.jar tools/PdfBoxDump.java examples/учебник.pdf examples/book_lines_pdfbox.jsonl
  ```
  `pdfbox-app-2.0.32.jar` — с Maven Central (org.apache.pdfbox:pdfbox-app).
- `pymupdf_dump.py` — то же через PyMuPDF (`pip install pymupdf`), для сравнения:
  ```bash
  python tools/pymupdf_dump.py examples/учебник.pdf examples/book_lines.jsonl
  ```
