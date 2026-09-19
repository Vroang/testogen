# PROGRESS

## Текущий шаг: 28.2 — фикс: ложное «Войдите в аккаунт» при работе с учебниками
## Статус: собран, тег v41 отправлен, ждёт проверки пользователем

## Что сделано
- ДИАГНОСТИКА: TextbookRepository проверял авторизацию СИНХРОННЫМ
  AuthManager.isSignedIn() (currentUserOrNull) в трёх местах —
  uploadTextbook («Войдите в аккаунт»), deleteTextbook (то же) и
  брал пользователя через currentUser() для user_id. currentUser
  пуст до завершения асинхронной инициализации Auth (та же
  причина, что в шаге 28.1, но в других точках). Случайных
  signOut нет — проверено (signOut вызывается только из меню
  «Выйти из аккаунта»).
- ФИКС:
  - AuthManager: isSignedInAsync переименован в requireSignedIn
    (awaitInitialization + currentSessionOrNull), добавлен
    currentUserAsync (пользователь из загруженной сессии);
    синхронные isSignedIn/currentUser удалены совсем — чтобы
    больше не использовать;
  - AppNavigation: стартовая проверка → requireSignedIn();
  - TextbookRepository: uploadTextbook, deleteTextbook,
    fetchParagraphText — все проверки теперь requireSignedIn();
    пользователь для user_id — currentUserAsync();
    «Войдите в аккаунт» остаётся только как честный fallback,
    когда пользователь действительно не вошёл.
- app/build.gradle.kts: versionCode = 41,
  versionName = "0.28.2-step28fix2".
- Экран «Вход», таблицы, Room, каскад, автообновление — не
  тронуты. Новых библиотек нет.

## Известные проблемы
—
## Заметки
- Путь к локальному APK:
  C:\Users\Ivan\Desktop\TestGenerator\apk\ТестоГен-v0.28.2-step28fix2.apk
- Версионирование: versionCode монотонно растёт (шаг 28.2 = 41).
- Автопубликация: тег v41 → GitHub Actions → релиз.
- VISION.md и PLAN.md не менялись.
