#!/usr/bin/env bash
# Format or check Kotlin/KTS with ktlint, excluding Study/ and build outputs.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

KTLINT_VERSION="${KTLINT_VERSION:-1.8.0}"
TOOLS_DIR="${ROOT}/.tools"
KTLINT_BIN="${TOOLS_DIR}/ktlint-${KTLINT_VERSION}"
MODE="${1:-check}"

usage() {
  echo "Usage: $0 [check|format]" >&2
  echo "  check   Fail on autocorrectable style issues (default; CI-friendly)" >&2
  echo "  format  Auto-fix style issues in place" >&2
  exit 2
}

case "$MODE" in
  check|format) ;;
  -h|--help|help) usage ;;
  *) usage ;;
esac

if [[ ! -x "$KTLINT_BIN" ]]; then
  mkdir -p "$TOOLS_DIR"
  echo "Downloading ktlint ${KTLINT_VERSION}..." >&2
  curl -fsSL \
    "https://github.com/pinterest/ktlint/releases/download/${KTLINT_VERSION}/ktlint" \
    -o "$KTLINT_BIN"
  chmod +x "$KTLINT_BIN"
fi

# Include sources, then negate Study/ and build (globs apply right-to-left).
args=(
  --relative
  --ignore-autocorrect-failures
  "**/*.kt"
  "**/*.kts"
  "!Study/**"
  "!**/build/**"
)

if [[ "$MODE" == format ]]; then
  args=(-F "${args[@]}")
fi

exec "$KTLINT_BIN" "${args[@]}"
