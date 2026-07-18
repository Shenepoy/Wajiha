#!/usr/bin/env bash
# Import Dan Patrick Recommended platform logos (see import_dan_patrick_logos.py).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec python3 "$ROOT/scripts/import_dan_patrick_logos.py"
