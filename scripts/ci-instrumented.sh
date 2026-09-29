#!/usr/bin/env bash
# android-emulator-runner executes each script line in its own shell, so the
# Gradle status and the logcat dump have to live in this one process.
set +e
./gradlew :androidApp:connectedDebugAndroidTest -Dorg.gradle.jvmargs="-Xmx2g -Dfile.encoding=UTF-8"
status=$?
mkdir -p tmp
adb logcat -d > tmp/instrumented-logcat.txt || true
exit "$status"
