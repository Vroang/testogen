# PROGRESS

## Текущий шаг: 23 — настройка автообновления через GitHub
## Статус: собран, ждёт проверки пользователем

## Что сделано
- НАЙДЕН KEYSTORE: C:\Users\Ivan\.android\debug.keystore
  (стандартный debug-ключ: алиас androiddebugkey, пароль android).
  Все предыдущие сборки подписаны им — автообновление будет работать,
  только если релизы подписаны ТЕМ ЖЕ ключом (инструкция по секретам
  GitHub — в отчёте к шагу).
- settings.gradle.kts: добавлен репозиторий JitPack.
- app/build.gradle.kts:
  - зависимость com.github.supersu-man:apkupdater-library:v2.1.0
    (v2.2.0 несовместима: собрана Kotlin 2.3 — метаданные);
  - buildFeatures { buildConfig = true } (нужен для
    BuildConfig.VERSION_NAME);
  - signingConfigs "release" из переменных окружения
    (KEYSTORE_PATH/KEYSTORE_PASSWORD/KEY_ALIAS/KEY_PASSWORD) —
    для CI-сборки; локальный release без env остаётся неподписанным;
  - versionCode = 32, versionName = "0.23-step23".
- AndroidManifest: добавлено разрешение REQUEST_INSTALL_PACKAGES
  (INTERNET уже был); FileProvider уже подключён (Шаг 5).
- res/xml/file_paths.xml: добавлены external-path и files-path
  (для apkupdater и FileProvider).
- MainScreen: проверка обновлений — один раз за запуск приложения
  (companion-флаг updateCheckStarted), в фоновом потоке
  (withContext(Dispatchers.IO)):
  - ApkUpdater по URL https://github.com/Vroang/testogen/releases/latest;
  - isNewUpdateAvailable() == true → updateAvailable = true;
  - AlertDialog «Доступно обновление» / «Установить новую версию?»
    с кнопками «Обновить» (requestDownload) и «Позже».
- .github/workflows/release.yml: GitHub Actions workflow —
  по тегу v*: checkout → JDK 17 (temurin) → декодирование
  keystore из секрета KEYSTORE_BASE64 → assembleRelease
  (подпись из секретов) → копирование APK в output →
  создание GitHub Release (softprops/action-gh-release).
- Логика приложения (генерация, банк, черновик, экспорт,
  инструкции) — не тронута.

## Известные проблемы
—
## Заметки
- Путь к APK: C:\Users\Ivan\Desktop\TestGenerator\apk\ТестоГен-v0.23-step23.apk
- Версионирование: versionCode монотонно растёт (шаг 23 = 32).
- ДЛЯ АВТООБНОВЛЕНИЯ: все будущие релизы должны быть подписаны
  ТЕМ ЖЕ ключом, что и установленный APK. Сейчас это debug-ключ
  C:\Users\Ivan\.android\debug.keystore (алиас androiddebugkey,
  пароли android) — его base64 добавляется в GitHub Secrets
  (KEYSTORE_BASE64), тогда CI-сборки будут совместимы с уже
  установленным приложением.
- Новых библиотек сверх apkupdater нет. VISION.md и PLAN.md
  не менялись.
- Следующий этап — финальный тест 40×40 пользователем и сдача.
