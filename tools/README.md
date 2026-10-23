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

# Baseline Profile и проверка на телефоне

- `ui.py` — нажать по тексту, снимок экрана, список надписей (`python tools/ui.py tap "Текст" | texts | shot имя | back`).
  Путь к adb и телефон берутся из переменных `ADB` и `ADB_SERIAL`.
- `baseline_profile.sh` — записать `app/src/main/baseline-prof.txt` (порядок действий — в начале скрипта).
  Проверка эффекта: `cmd package compile -f -m verify` против `-m speed-profile` и `am start -W` (TotalTime).

# Сверка файла Word с предпросмотром

- `layout_check.py` — `LayoutCheckTest` (`./gradlew testDebugUnitTest`) пишет в `app/build/layout-check` файлы .docx
  для всех сочетаний раскладки и рядом .json — где по модели Paginator стоит каждый блок. Скрипт переводит .docx
  в PDF через LibreOffice и сравнивает: число страниц, страницу и высоту каждого вопроса, наложения строк.
  ```bash
  pip install pymupdf
  python tools/layout_check.py app/build/layout-check [--png]
  ```
  С телефона: debug-сборка при «Отправить оба файла» кладёт `cache/shared/layout.json` рядом с файлом теста
  (`run-as com.testogen.app cat cache/shared/...`) — пару .docx + .json можно проверить тем же скриптом.
- `check_cert.sh` — сертификат подписи APK должен совпадать с v49 (иначе обновление у мамы не встанет);
  вызывается в `check.yml` и `release.yml`.
