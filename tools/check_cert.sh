#!/usr/bin/env bash
# Подпись релиза должна совпадать с подписью старых версий (v49 и раньше), иначе обновление
# у мамы не встанет поверх. Сертификат — публичная часть подписи, его отпечаток не секрет.
# Запуск: bash tools/check_cert.sh путь/к/app-release.apk   (нужен ANDROID_HOME с build-tools)
set -euo pipefail
# SHA-256 сертификата Testogen-v49.apk (apksigner verify --print-certs)
EXPECTED="fd9ad3dc04030068b6a4b5136ecefbf42510e1e9694084c9f07b90d56a9d2dfd"
APKSIGNER=$(ls "$ANDROID_HOME"/build-tools/*/apksigner | sort -V | tail -1)
DIGEST=$("$APKSIGNER" verify --print-certs "$1" | sed -n 's/.*certificate SHA-256 digest: //p' | head -1)
echo "::notice title=Сертификат подписи::SHA-256 $DIGEST"
if [ "$DIGEST" != "$EXPECTED" ]; then
  echo "::error title=Чужая подпись::сертификат $DIGEST, а у v49 — $EXPECTED"
  exit 1
fi
echo "Подпись совпадает с v49"
