# PROGRESS

## Текущий шаг: 26.1 — фикс скачивания APK в автообновлении
## Статус: собран, тег v37 отправлен, ждёт проверки пользователем

## Что сделано
- ДИАГНОСТИКА: downloadAndInstall() ставил задачу в системный
  DownloadManager (папка «Downloads», уведомление VISIBLE,
  mimeType пакета) с приёмником ACTION_DOWNLOAD_COMPLETE.
  Слабые места: 1) запрос БЕЗ заголовка User-Agent — GitHub
  может отдать 403; 2) любые сбои системной загрузки были
  НЕВИДИМЫ — статус задачи нигде не опрашивался, приёмник
  срабатывал только на success-пути, ошибки глотались; 3) путь
  в общий Downloads зависит от системных ограничений хранилища.
  Разрешения в манифесте в порядке: INTERNET и
  REQUEST_INSTALL_PACKAGES есть; WRITE_EXTERNAL_STORAGE не нужен
  (не используется).
- ЧАСТЬ 2 — downloadAndInstall() переписан целиком (UpdaterClient.kt):
  - скачивание своим OkHttp в кэш приложения
    (cacheDir/downloads/<имя APK>), корутина на Dispatchers.IO;
  - заголовок User-Agent: TestogenUpdater/1.0;
  - редиректы включены явно (followRedirects/followSslRedirects);
  - диалог «Обновление … / Скачивание… N%» (по contentLength),
    некликабельный во время загрузки;
  - явные ошибки Toast: 403/404 → «Файл недоступен», прочие
    HTTP → код; таймаут → «Превышено время ожидания»;
    нет сети → «Нет соединения»; мало места → «Недостаточно
    места на устройстве»; прочее — текст ошибки. Ничего не глотается;
  - установка: FileProvider.getUriForFile (authority
    com.testogen.app.fileprovider, cache-path уже покрывает кэш)
    → Intent ACTION_VIEW с mimeType пакета,
    FLAG_GRANT_READ_URI_PERMISSION + FLAG_ACTIVITY_NEW_TASK →
    системный вопрос «Установить приложение?»;
  - старые файлы в cacheDir/downloads удаляются перед загрузкой;
  - checkLatestRelease() НЕ тронут (проверка работала).
- app/build.gradle.kts: versionCode = 37,
  versionName = "0.26.1-step26fix".
- Логика приложения не тронута, новых библиотек нет
  (OkHttp и FileProvider уже были).

## Известные проблемы
—
## Заметки
- Путь к локальному APK:
  C:\Users\Ivan\Desktop\TestGenerator\apk\ТестоГен-v0.26.1-step26fix.apk
- Версионирование: versionCode монотонно растёт (шаг 26.1 = 37).
- Тест по чек-листу: с установленной v35 запустить v37 → появится
  диалог «Обновление v36» (последний релиз на GitHub) → «Обновить»
  → «Скачивание… N%» → системный установщик → после установки v36
  приложение при следующем запуске само предложит v37.
- VISION.md и PLAN.md не менялись.
