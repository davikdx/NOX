#!/usr/bin/env bash
# Dedicated emulator only. The instrumentation enforces an empty dev profile.
set -euo pipefail
[[ "${ANDROID_SERIAL:-}" == emulator-* ]]
dst=build/device-checks
adb shell am instrument -w -e reboot 1 br.app.nox.dev.test/br.app.nox.NoxDeviceChecks | tee "$dst/reinicio-preparo.txt"
grep -q 'nox_result=ARMED' "$dst/reinicio-preparo.txt"
adb reboot
timeout 120 adb wait-for-device
timeout 150 bash -c 'until [[ "$(adb shell getprop sys.boot_completed | tr -d "\r")" == "1" ]]; do sleep 1; done'
adb shell input keyevent 224
adb shell wm dismiss-keyguard
adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >/dev/null || true
adb shell am kill br.app.nox.dev
adb shell input keyevent 223
adb shell dumpsys battery unplug
trap 'adb shell dumpsys deviceidle unforce >/dev/null; adb shell dumpsys battery reset >/dev/null' EXIT
adb shell dumpsys deviceidle force-idle | tee "$dst/idle.txt"
grep -iq 'idle' "$dst/idle.txt"
for _ in {1..120}; do
  adb shell dumpsys activity services br.app.nox.dev > "$dst/servico.txt"
  if grep -q 'RingService' "$dst/servico.txt" && grep -q 'isForeground=true' "$dst/servico.txt"; then
    echo "PASS: após reiniciar, desbloquear uma vez, encerrar processo em segundo plano e apagar a tela, o serviço de alarme retornou ao primeiro plano." | tee "$dst/reinicio.txt"
    exit 0
  fi
  sleep 2
done
echo "FAIL: o serviço não foi observado após reinicialização." | tee "$dst/reinicio.txt"
exit 1

