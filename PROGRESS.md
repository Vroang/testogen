# PROGRESS

## Текущий шаг: 27 — Supabase + библиотека учебников
## Статус: собран, тег v38 отправлен, ждёт проверки пользователем

## Что сделано
- ЧАСТЬ 1 — подключение Supabase:
  - зависимости: BOM io.github.jan-tennert.supabase:2.6.0
    (НЕ 3.0.0 — он собран более новым Kotlin и несовместим с нашим
    2.0.20; проверено), postgrest-kt, storage-kt, gotrue-kt
    (в линейке 2.x модуль авторизации называется gotrue-kt,
    переименован в auth-kt только в 3.x), ktor-client-okhttp 2.3.12;
    kotlinx-serialization-json оставлен 1.7.2 (спека предлагала
    1.6.3 — у нас новее и совместимее);
  - SupabaseClient.kt: клиент (URL проекта, anon-ключ, плагины
    Auth/Postgrest/Storage);
  - схема таблиц сверена с живым REST API (anon-ключ): textbooks
    (id, user_id, name, format, paragraph_count, storage_path,
    uploaded_at timestamptz), paragraphs (id, textbook_id, number,
    text).
- ЧАСТЬ 2 — авторизация:
  - AuthManager.kt: signIn/signOut/isSignedIn/currentUser
    (email-провайдер Supabase Auth);
  - экран «Вход» (маршрут login): Email, пароль, кнопка «Войти»
    (teal), ошибка — Toast;
  - при запуске: не авторизован → «Вход», авторизован → главный
    экран (сессия восстанавливается автоматически на Android);
  - Настройки → «Выйти из аккаунта» с подтверждением.
- ЧАСТЬ 3 — библиотека учебников:
  - Room: сущность Textbook (локальный кэш метаданных), TextbookDao,
    база 6 → 7, миграция CREATE TABLE textbooks;
  - TextbookRepository.kt: загрузка (размер → извлечение текста →
    разбивка по «§ N» → Storage "textbooks/{id}/{имя}" → запись в
    textbooks → параграфы батчами по 100 → локальный кэш Room),
    удаление (параграфы + запись + объект Storage + Room),
    выборка параграфов диапазона (по возрастанию номеров);
    DOCX читается из ZIP (word/document.xml), TXT — UTF-8, PDF —
    PdfBox;
  - лимит: файл > 50 МБ (по OpenableColumns.SIZE) → AlertDialog
    «Файл слишком большой (X МБ)…», загрузка не запускается;
    ошибка 413 от сервера → понятный Toast;
  - экран «Мои учебники» (маршрут textbooks): список из Room
    (Flow), пустое состояние, FAB «+» (выбор PDF/DOCX/TXT),
    долгий тап → удаление с подтверждением (и из Supabase, и
    из Room); Настройки → карточка «Учебники»;
  - экран «Выбор диапазона» (textbook_range/{id}): «От §»/«До §»,
    количество (1–50), мультивыбор сложностей, «Сгенерировать»:
    параграфы диапазона → ИИ (тот же каскад, операция «pdf»,
    попадает в «Журнал ИИ») → банк с topic «<имя> § от-до»,
    source «pdf», с фильтром дублей.
- app/build.gradle.kts: versionCode = 38, versionName = "0.27-step27".
- Существующие экраны, каскад, парсер, автообновление — не тронуты.

## Известные проблемы
—
## Заметки
- Путь к локальному APK:
  C:\Users\Ivan\Desktop\TestGenerator\apk\ТестоГен-v0.27-step27.apk
- Версионирование: versionCode монотонно растёт (шаг 27 = 38).
- Автопубликация: тег v38 → GitHub Actions → релиз; у телефонов
  с версией ≤ 37 приложение предложит обновление.
- Первый вход: mama@testogen.app + пароль (знает пользователь).
- VISION.md и PLAN.md не менялись.
