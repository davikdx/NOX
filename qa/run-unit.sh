#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
dest="$(mktemp -d)"
trap 'rm -rf -- "$dest"' EXIT
sources=(app/src/main/java/br/app/nox/{Sleep,DiaryMath,AlarmTimes}.java)
mapfile -t tests < <(find qa -type f -name '*Test.java' | sort)
if (( ${#tests[@]} == 0 )); then
  echo 'Nenhum teste de domínio foi encontrado.' >&2
  exit 1
fi
javac -encoding UTF-8 -d "$dest" "${sources[@]}" "${tests[@]}"
for test in "${tests[@]}"; do
  class="$(basename "$test" .java)"
  java -ea -cp "$dest" "br.app.nox.$class"
done
