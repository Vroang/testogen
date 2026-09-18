# PROGRESS

## Текущий шаг: 28.1 — фикс: сессия Supabase сохраняется между запусками
## Статус: собран, тег v40 отправлен, ждёт проверки пользователем

## Что сделано
- ДИАГНОСТИКА: при старте AppNavigation СРАЗУ вызывал
  синхронную AuthManager.isSignedIn() (currentUserOrNull()).
  Supabase Auth загружает сохранённую сессию из внутреннего
  хранилища АСИНХРОННО — в первые мгновения currentUserOrNull()
  возвращает null → стартовым маршрутом ставился «login», и
  пользователь видел экран входа при каждом запуске. Сессия
  при этом сохранялась — проблема была только в моменте
  проверки. awaitInitialization() нигде не вызывался.
- ФИКС:
  - AuthManager.isSignedInAsync(): suspend —
    awaitInitialization() (дождаться загрузки сессии с диска)
    → currentSessionOrNull() != null; ошибки → false;
  - AppNavigation: стартовый маршрут теперь nullable-состояние,
    решается в LaunchedEffect через isSignedInAsync(); пока идёт
    проверка — CircularProgressIndicator по центру (не пустой
    экран); после проверки — «main» или «login»;
  - синхронная isSignedIn() оставлена для проверок внутри уже
    работающего экрана (TextbookRepository), для стартового
    решения больше не используется;
  - signOut случайно нигде не вызывается — проверено;
  - хранилище сессии — стандартное для Supabase SDK на Android,
    работает из коробки.
- app/build.gradle.kts: versionCode = 40,
  versionName = "0.28.1-step28fix".
- Логика входа (email+пароль), экран «Вход», таблицы, Room,
  каскад, автообновление — не тронуты. Новых библиотек нет.

## Известные проблемы
—
## Заметки
- Путь к локальному APK:
  C:\Users\Ivan\Desktop\TestGenerator\apk\ТестоГен-v0.28.1-step28fix.apk
- Версионирование: versionCode монотонно растёт (шаг 28.1 = 40).
- Автопубликация: тег v40 → GitHub Actions → релиз.
- VISION.md и PLAN.md не менялись.
