#!/usr/bin/env bash

# 2. Debug-Fassung hineinspielen

./gradlew assembleFullDebug
adb -s emulator-5554 install -r app/build/outputs/apk/full/debug/app-full-debug.apk

# 3. Aufnahmehelfer
tools/screenshots.py            # deutscher Satz
