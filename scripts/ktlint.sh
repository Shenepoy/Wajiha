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
  echo "  check   Apply format, then fail if any .kt/.kts still differ from HEAD (CI gate)" >&2
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

# -F applies fixes. --ignore-autocorrect-failures keeps exit 0 despite naming/etc.
# noise that cannot be auto-fixed (Compose PascalCase, backing props, …).
# Include sources, then negate Study/ and build (globs apply right-to-left).
args=(
  --relative
  -F
  --ignore-autocorrect-failures
  "**/*.kt"
  "**/*.kts"
  "!Study/**"
  "!**/build/**"
)

"$KTLINT_BIN" "${args[@]}"

if [[ "$MODE" == format ]]; then
  exit 0
fi

# CI gate: if formatting changed anything vs HEAD, the tree was out of style.
if ! git diff --quiet -- '*.kt' '*.kts'; then
  echo "ktlint found formatting issues. Run: ./scripts/ktlint.sh format && commit the result." >&2
  git --no-pager diff --stat -- '*.kt' '*.kts' >&2
  exit 1
fi
