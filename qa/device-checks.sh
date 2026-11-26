#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/device-checks
adb shell am instrument -w -e alarm "${NOX_TEST_ALARM:-0}" br.app.nox.dev.test/br.app.nox.NoxDeviceChecks | tee build/device-checks/testes.txt
for name in $(adb shell run-as br.app.nox.dev ls files/qa-nox | tr -d '\r'); do
  [[ "$name" =~ ^[a-z-]+\.png$ ]] || continue
  adb exec-out run-as br.app.nox.dev cat "files/qa-nox/$name" > "build/device-checks/$name"
done
grep -Eq '^PASS[[:space:]]*$' build/device-checks/testes.txt

