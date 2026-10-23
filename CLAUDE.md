# ТестоГен — заметки для Claude

Android-приложение для одного пользователя (мама — учитель истории и обществознания).
Собирает тесты из банка вопросов, пополняет банк вопросами от нейросети
(по теме или по учебнику) и экспортирует тест и ответы в .docx.
Идёт переписывание в ветке `rewrite` — план и статус этапов в `PLAN.md` (читать первым).
Исходная задумка — `VISION.md`.

## Правила работы

- Работаем этапами из `PLAN.md`. В конце этапа: сборка без ошибок → проверка на телефоне →
  коммит → короткий отчёт (для Claude в чате, без пересказа кода) → стоп до «продолжай».
- Не пушить в `main`, не создавать теги `vN` (запускают релиз для мамы), не трогать GitHub Releases.
  Пушить ветку `rewrite` можно.
- `examples/` (образец docx и учебник под авторским правом) — в `.gitignore`, никогда не коммитить.
- API-ключи никогда не класть в файлы проекта. Для проверки генерации попросить пользователя
  вписать ключ в настройках приложения на телефоне.
- `apk/` — старые сборки, не анализировать и не коммитить.
- Репозиторий на GitHub **публичный**.
- `C:\Users\Ivan\Desktop\TestGenerator` — запасная копия, не трогать.
- Комментарии и строки интерфейса — на русском.
- **minSdk = 26 (Android 8.0) — не повышать.** Какой Android у мамы, неизвестно; старая версия
  (minSdk 26) у неё работала. Не использовать API выше 26 без проверки версии и запасного пути
  (`Build.VERSION.SDK_INT`), библиотеки — только с minSdk ≤ 26.
- `versionCode` всегда больше, чем у последнего релиза (старая ветка закончилась на 49),
  иначе обновление не встанет поверх.

## Архитектура

Один модуль `:app`, пакет `com.testogen.app`. Ручное внедрение зависимостей: `AppContainer`
(создаётся в `TestoGenApp`, в Compose доступен через `LocalAppContainer`).

| Где | Что |
|---|---|
| `data/db/` | Room: `Entities.kt` (вопросы, тесты и их элементы, правила ИИ, причины замен, журнал запросов), `AppDatabase.kt` (DAO). База `testogen2.db`, схема в `app/schemas`. До первого релиза — destructive migration |
| `data/prefs/` | DataStore: `prefs` (настройки), `secrets` (ключи DeepSeek/OpenRouter, исключён из бэкапа); `LegacyCleanup` — разовая уборка данных старой версии ≤ 49 и перенос её ключа OpenRouter |
| `ai/` | `AiClient` (DeepSeek → OpenRouter, журнал с токенами и ценой, понятные ошибки, баланс), `Prompts` (неизменные system-промпты), `QuestionQuality` (разбор JSON, проверки, отсев повторов — покрыто юнит-тестами), `QuestionGenerator` (порции, рецензент, оценка цены), `Pricing`, `RulesUpdater` |
| `work/GenerationJobs.kt` | Задания генерации в базе + `GenerationWorker` (WorkManager, foreground-уведомление); при запуске приложения незавершённые задания продолжаются |
| `data/QuestionRepository.kt` | Сохранение вопросов, `topicKey`/`cleanTopic` |
| `update/Updater.kt` | Автообновление из GitHub Releases (`StateFlow` состояния, диалог в `ui/main/UpdateDialog.kt`) |
| `ui/theme/` | Только светлая тёплая палитра (`Palette`), шрифт Golos Text (`res/font`, OFL — `licenses/`), типографика крупнее стандартной, `AppIcons` — свои векторные иконки |
| `ui/components/` | Общие элементы: `SoftCard`, `PrimaryButton`, `SecondaryButton`, `ScreenHeader`, `BackHeader`, `EmptyState`, `DifficultyTag` |
| `ui/main/` | `AppRoot` (NavHost, type-safe маршруты, переходы), `MainScreen` (вкладки Тесты / Вопросы / Учебники) |
| `ui/tests`, `ui/bank`, `ui/generate`, `ui/rules`, `ui/textbooks`, `ui/settings`, `ui/ailog` | Экраны; ViewModel рядом с экраном, создаётся через `viewModel { … }` |
| `util/Format.kt` | Даты, доллары, русское склонение |

Плавность: тяжёлое — только вне главного потока; ленивые списки с `key` и `contentType`;
анимации нажатий — в фазе отрисовки (`graphicsLayer`). Замеры делать на варианте `benchmark`
(R8, без отладки, debug-подпись): `./gradlew assembleBenchmark`.

## Окружение сборки (всё на диске D, как в AudCut)

```bash
export JAVA_HOME="D:/Dev/Programs/AndroidStudio/jbr"
export ANDROID_HOME="D:/Dev/AndroidSDK"
export GRADLE_USER_HOME="D:/Dev/Programs/Gradle"
export ANDROID_USER_HOME="D:/Dev/Programs/AndroidUserHome"
export GRADLE_OPTS="-Duser.home=D:/Dev/Programs/AndroidUserHome -Djava.io.tmpdir=D:/Dev/Temp"
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk` (`benchmark` — `app/build/outputs/apk/benchmark/`).
Инструменты: AGP 9.4.1, Kotlin 2.4.21, Gradle 9.8.1, Compose BOM 2026.09.00, compileSdk/targetSdk 37 — версии в `gradle/libs.versions.toml`.
Release с подписью собирает только GitHub Actions (`.github/workflows/release.yml`) по тегу
`v<versionCode>`; ключ — в GitHub Secrets. `applicationId` (`com.testogen.app`) и подпись
не менять: обновление ставится маме поверх старой версии через встроенный апдейтер,
который сравнивает число из тега с `versionCode`.

## Телефон (проверка)

- adb: `C:/Users/Ivan/AppData/Local/Android/Sdk/platform-tools/adb.exe` (есть и в `D:/Dev/AndroidSDK/platform-tools`).
- Телефон пользователя: TECNO KM4k, Android 15, 720×1600, 320 dpi, IP `192.168.1.125`.
  Порт «Отладки по Wi-Fi» меняется. `adb mdns services` его не видит — найти открытый порт
  в диапазоне 30000–50000 (быстрый TCP-скан) и `adb connect 192.168.1.125:<порт>`;
  если не выходит — попросить IP и порт с экрана «Отладка по Wi-Fi».
- На телефоне есть рабочий профиль (user 10): ставить с `--user 0`, `pm` вызывать с `--user 0`.
- Не удалять приложение и не стирать его данные без спроса.
- Плавность: `adb shell dumpsys gfxinfo com.testogen.app`, скриншоты: `adb exec-out screencap -p`.
- adb не вводит кириллицу: debug-сборка открывает генерацию по теме из
  `adb shell am start -S --user 0 -n com.testogen.app/.MainActivity --es debug_topic "'Тема'"`.
  Базу debug-сборки можно читать через `run-as` (в benchmark — нельзя).
- В Git Bash для adb ставить `MSYS_NO_PATHCONV=1` (иначе `/data/...` превращается в путь Windows).
- Play Protect спрашивает о проверке при каждой установке по adb — ставить реже, правки собирать пачками.
- Юнит-тесты: `./gradlew testDebugUnitTest`.

## Скиллы

`.claude/skills`: jetpack-compose-performance, edge-to-edge, r8-analyzer, anr-audit-and-fix
(официальные, github.com/android/skills).
