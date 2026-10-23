#!/usr/bin/env bash
# Шаг 6: запись Baseline Profile на телефоне без UiAutomator (на TECNO он зависает в ожидании простоя).
# ART сам записывает, какой код выполнялся; скрипт проходит главные экраны через adb и выгружает профиль.
#
# Перед запуском: на телефоне есть сохранённый тест, вопросы в банке и учебник; собран и поставлен
# вариант без R8:  ./gradlew :app:assembleProfiling  →  adb install --user 0 -r app/build/outputs/apk/profiling/app-profiling.apk
# Запуск:  ADB=путь/к/adb ADB_SERIAL=192.168.1.125:<порт> bash tools/baseline_profile.sh
# Результат — app/src/main/baseline-prof.txt; потом снова поставить обычную сборку.
export MSYS_NO_PATHCONV=1
# pwd -W — путь Windows в Git Bash (python не понимает /d/...)
ROOT="$(cd "$(dirname "$0")/.." && (pwd -W 2>/dev/null || pwd))"
A="${ADB:-adb} ${ADB_SERIAL:+-s $ADB_SERIAL}"
P="python -I $ROOT/tools/ui.py"
PKG=com.testogen.app

down() { for i in 1 2 3; do $A shell input swipe 360 1200 360 400 150; sleep 0.5; done; $A shell input swipe 360 400 360 1200 150; sleep 0.5; }

journey() {
  $A shell am force-stop $PKG
  $A shell am start -W -n $PKG/.MainActivity >/dev/null; sleep 2
  $P tap " вар. × " || true; sleep 2; $P tap "Вариант 2" exact || true; down
  $P tap "Скачать и отправить" || true; sleep 8; down; $P back; sleep 1; $P back; sleep 1
  $P tap "Собрать тест" || true; sleep 1; $P back; sleep 1
  $P tap "Вопросы" exact || true; sleep 1; $P tap "§ " || true; sleep 2; down; $P back; sleep 1
  $P tap "Учебники" exact || true; sleep 1; $P tap "класс · " || true; sleep 2
  $P tap "стр. " || true; sleep 2; down; $P back; sleep 1; $P back; sleep 1
  $P tap "Тесты" exact || true
}

$A shell cmd package compile --reset $PKG >/dev/null
journey
journey
$A shell pm dump-profiles --dump-classes-and-methods $PKG
$A pull /data/misc/profman/$PKG-primary.prof.txt "$ROOT/app/src/main/baseline-prof.txt"
echo "Правил: $(wc -l < "$ROOT/app/src/main/baseline-prof.txt")"
