#!/usr/bin/env bash
# Build (optional) and install the debug APK on the AYN Thor, then launch MainActivity.
# Usage:
#   ./scripts/install-thor.sh              # assembleDebug, then install + launch
#   ./scripts/install-thor.sh path/to.apk  # install existing APK + launch
# Env:
#   THOR_SERIAL   adb serial (default: 10.0.0.174:5555)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

THOR_SERIAL="${THOR_SERIAL:-10.0.0.174:5555}"
PACKAGE="${PACKAGE:-com.wajiha}"
ACTIVITY="${ACTIVITY:-com.wajiha/.android.MainActivity}"

adb_thor() {
  adb -s "$THOR_SERIAL" "$@"
}

if [[ $# -ge 1 ]]; then
  APK="$1"
  if [[ ! -f "$APK" ]]; then
    echo "APK not found: $APK" >&2
    exit 1
  fi
else
  echo "Building debug APK..."
  ./gradlew :androidApp:assembleDebug
  APK="$(ls androidApp/build/outputs/apk/debug/*.apk | head -n1)"
fi

echo "Installing $APK on $THOR_SERIAL..."
adb_thor wait-for-device
adb_thor install -r -d "$APK"
adb_thor shell am force-stop "$PACKAGE"
adb_thor shell am start -n "$ACTIVITY"
echo "Launched $ACTIVITY"
