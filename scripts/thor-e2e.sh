#!/usr/bin/env bash
# Local Thor smoke/e2e: install debug APK, launch, assert process is alive.
# Dual-display UI checks stay agent/PolyScreen — this script only verifies boot health.
# Usage:
#   ./scripts/thor-e2e.sh
#   ./scripts/thor-e2e.sh path/to.apk
# Env:
#   THOR_SERIAL   adb serial (default: 10.0.0.174:5555)
#   WAIT_SECONDS  max wait for process (default: 30)
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

THOR_SERIAL="${THOR_SERIAL:-10.0.0.174:5555}"
PACKAGE="${PACKAGE:-com.wajiha}"
WAIT_SECONDS="${WAIT_SECONDS:-30}"

adb_thor() {
  adb -s "$THOR_SERIAL" "$@"
}

echo "==> Install + launch on Thor ($THOR_SERIAL)"
if [[ $# -ge 1 ]]; then
  "$ROOT/scripts/install-thor.sh" "$1"
else
  "$ROOT/scripts/install-thor.sh"
fi

echo "==> Wait for package process"
deadline=$((SECONDS + WAIT_SECONDS))
while (( SECONDS < deadline )); do
  if adb_thor shell pidof "$PACKAGE" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

pid="$(adb_thor shell pidof "$PACKAGE" 2>/dev/null | tr -d '\r' || true)"
if [[ -z "${pid:-}" ]]; then
  echo "FAIL: $PACKAGE is not running after ${WAIT_SECONDS}s" >&2
  exit 1
fi
echo "OK: $PACKAGE pid=$pid"

echo "==> Package present"
if ! adb_thor shell pm path "$PACKAGE" | grep -q "package:"; then
  echo "FAIL: pm path $PACKAGE" >&2
  exit 1
fi

echo "==> Debug session dump (best-effort; debug APK only)"
if ! adb_thor shell am broadcast -a com.wajiha.DEBUG_DUMP_SESSIONS >/dev/null 2>&1; then
  echo "WARN: DEBUG_DUMP_SESSIONS failed (release APK or receiver unavailable)" >&2
else
  echo "OK: DEBUG_DUMP_SESSIONS sent"
fi

echo "==> Thor e2e smoke passed"
