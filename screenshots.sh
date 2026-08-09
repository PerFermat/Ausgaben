#!/bin/bash

# Bei Fehlern sofort abbrechen
set -e

# Konfiguration
PROJECT_DIR="$HOME/git/Ausgaben"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/full/debug/app-full-debug.apk"
PACKAGE_NAME="de.michaelspahr.ausgaben"   # ggf. anpassen
SCREENSHOT_SCRIPT="$PROJECT_DIR/screenshots.py"

echo "=== Ausgaben-App bauen ==="

cd "$PROJECT_DIR"

./gradlew assembleFullDebug

echo "=== Emulator prüfen ==="

DEVICE=$(adb devices | awk 'NR>1 && $2=="device" {print $1}' | head -n1)

if [ -z "$DEVICE" ]; then
    echo "Kein laufender Emulator oder Android-Gerät gefunden."
    exit 1
fi

echo "Gerät: $DEVICE"

echo "=== APK installieren ==="

adb -s "$DEVICE" install -r "$APK_PATH"

echo "=== App starten ==="

adb -s "$DEVICE" shell monkey -p "$PACKAGE_NAME" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1

sleep 3

echo "=== Screenshots erstellen ==="

python3 "$SCREENSHOT_SCRIPT"

echo "=== Fertig ==="
