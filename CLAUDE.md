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

## Окружение сборки (всё на диске D, как в AudCut)

```bash
export JAVA_HOME="D:/Dev/Programs/AndroidStudio/jbr"
export ANDROID_HOME="D:/Dev/AndroidSDK"
export GRADLE_USER_HOME="D:/Dev/Programs/Gradle"
export ANDROID_USER_HOME="D:/Dev/Programs/AndroidUserHome"
export GRADLE_OPTS="-Duser.home=D:/Dev/Programs/AndroidUserHome -Djava.io.tmpdir=D:/Dev/Temp"
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
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

## Скиллы

`.claude/skills`: jetpack-compose-performance, edge-to-edge, r8-analyzer, anr-audit-and-fix
(официальные, github.com/android/skills).
