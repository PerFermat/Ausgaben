#!/usr/bin/env bash
# Packt KMyMoney-Test-XML in je eine gleichnamige .kmy-Datei (gzip), damit sie sich in der App
# öffnen lassen. Quellen sind die echten KMyMoney-Testdateien und die Fixtures dieses Projekts.
#
#   tools/make-test-kmy.sh [ZIEL] [QUELLE …]
#   tools/make-test-kmy.sh --push          # zusätzlich auf das angeschlossene Gerät kopieren
#
# Ohne Angaben: Ziel „build/kmy-testdaten", Quellen siehe DEFAULT_SRC.
set -euo pipefail

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

DEFAULT_SRC=(
  "$HOME/git/kmymoney/kmymoney/plugins/views/reports/core/tests/data"
  "$HOME/git/kmymoney/kmymoney/plugins/xml/tests"
  "$repo/app/src/test/resources/kmy"
)

push=0
if [ "${1:-}" = "--push" ]; then
  push=1
  shift
fi

out="${1:-$repo/build/kmy-testdaten}"
[ $# -gt 0 ] && shift || true
if [ $# -gt 0 ]; then
  sources=("$@")
else
  sources=("${DEFAULT_SRC[@]}")
fi

mkdir -p "$out"
packed=0
skipped=0

for dir in "${sources[@]}"; do
  [ -d "$dir" ] || { echo "übersprungen (nicht vorhanden): $dir" >&2; continue; }
  while IFS= read -r -d '' xml; do
    # Im selben Ordner liegen auch Berichtsdefinitionen – die sind keine Datei, nur ein Fragment.
    if ! head -c 4096 "$xml" | grep -q "<KMYMONEY-FILE"; then
      skipped=$((skipped + 1))
      continue
    fi
    name="$(basename "$xml" .xml)"
    gzip -9 -c "$xml" > "$out/$name.kmy"
    packed=$((packed + 1))
  done < <(find "$dir" -maxdepth 1 -name '*.xml' -print0 | sort -z)
done

echo "$packed Dateien nach $out gepackt ($skipped ohne KMYMONEY-FILE übergangen)"

if [ "$push" = "1" ]; then
  # Auf dem Gerät unter Download/kmy-testdaten – von dort holt sie der Dateidialog der App.
  target=/sdcard/Download/kmy-testdaten
  adb shell mkdir -p "$target"
  adb push "$out/." "$target" > /dev/null
  echo "auf das Gerät kopiert: $target"
fi
