# ТестоГен

Генератор школьных тестов по истории и обществознанию для преподавателя.
Android-приложение (Jetpack Compose, Material 3): банк вопросов, сборка черновика
с вариантами, ИИ-дополнение тем, экспорт в PDF и DOCX.

## Сборка

- JDK 17, Android SDK 34.
- `./gradlew assembleDebug` — debug-сборка.
- Готовые APK — в разделе [Releases](https://github.com/Vroang/testogen/releases).

Release-APK собирается автоматически workflow-ом `.github/workflows/release.yml`
при пуше тега `v*` и публикуется в Releases.

## Документы проекта

- `VISION.md` — задумка приложения.
- `PLAN.md` — план разработки по шагам.
- `PROGRESS.md` — журнал выполненных шагов.
