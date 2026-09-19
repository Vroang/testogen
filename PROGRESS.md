# PROGRESS

## Текущий шаг: 28.4 — RLS-ошибка при загрузке учебника
## Статус: собран, тег v43 отправлен, ждёт SQL-скрипта и проверки пользователем

## Что сделано
- ДИАГНОСТИКА (код): при вставке в textbooks user_id ПЕРЕДАЁТСЯ
  (TextbookRow.userId ← currentSessionOrNull()?.user.id,
  @SerialName("user_id")), сессия аутентифицирована и ждёт
  инициализации (requireSignedIn), параграфы ссылаются через
  textbook_id (колонки user_id в paragraphs нет — проверено
  по живой схеме). Код соответствует правильному сценарию,
  значит «new row violates row-level security policy» —
  отсутствие/неполнота RLS-политик в самом Supabase
  (таблицы textbooks/paragraphs и/или storage.objects).
- КОД: mapError теперь распознаёт RLS-ошибку и показывает
  понятное сообщение («Supabase заблокировал запись (RLS).
  Выполните SQL-скрипт…») вместо сырого текста PostgREST.
  Всё остальное (передача user_id, порядок Storage → textbooks
  → paragraphs, upsert, ASCII-ключ) — без изменений.
- SUPABASE: пользователю выдан идемпотентный SQL-скрипт
  (RLS включён + политики select/insert/update/delete для
  textbooks по auth.uid() = user_id; select/insert для
  paragraphs через связь с textbooks; insert/select/delete
  для storage.objects бакета textbooks) — выполняется в
  SQL Editor один раз.
- app/build.gradle.kts: versionCode = 43,
  versionName = "0.28.4-step28fix4".
- Логика входа, RLS (не отключаем), каскад, Room,
  автообновление — не тронуты. Новых библиотек нет.

## Известные проблемы
—
## Заметки
- Путь к локальному APK:
  C:\Users\Ivan\Desktop\TestGenerator\apk\ТестоГен-v0.28.4-step28fix4.apk
- Версионирование: versionCode монотонно растёт (шаг 28.4 = 43).
- Автопубликация: тег v43 → GitHub Actions → релиз.
- VISION.md и PLAN.md не менялись.
