#!/usr/bin/env bash
# Fail unless a split release directory has the universal APK plus one APK per ABI.
set -euo pipefail

DIR="${1:-androidApp/build/outputs/apk/release}"
ABIS=(armeabi-v7a arm64-v8a x86 x86_64)

if [[ ! -d "$DIR" ]]; then
  echo "APK directory not found: $DIR" >&2
  exit 1
fi

shopt -s nullglob
apks=("$DIR"/*.apk)
if [[ ${#apks[@]} -ne 5 ]]; then
  echo "Expected 5 release APKs in $DIR, found ${#apks[@]}:" >&2
  printf '  %s\n' "${apks[@]:-}" >&2
  exit 1
fi

declare -A found=()

classify() {
  local base="$1"
  if [[ "$base" == *universal* ]]; then
    echo universal
  elif [[ "$base" == *x86_64* ]]; then
    echo x86_64
  elif [[ "$base" == *armeabi-v7a* ]]; then
    echo armeabi-v7a
  elif [[ "$base" == *arm64-v8a* ]]; then
    echo arm64-v8a
  elif [[ "$base" == *x86* ]]; then
    echo x86
  fi
}

for apk in "${apks[@]}"; do
  kind="$(classify "$(basename "$apk")")"
  if [[ -z "$kind" ]]; then
    echo "Unrecognized APK name: $(basename "$apk")" >&2
    exit 1
  fi
  if [[ -n "${found[$kind]:-}" ]]; then
    echo "Duplicate $kind APK: $(basename "$apk")" >&2
    exit 1
  fi
  found["$kind"]="$apk"
done

for kind in universal "${ABIS[@]}"; do
  if [[ -z "${found[$kind]:-}" ]]; then
    echo "Missing $kind APK" >&2
    exit 1
  fi
done

so_count() {
  local apk="$1"
  local abi="$2"
  unzip -Z1 "$apk" | awk -v prefix="lib/${abi}/" '
    index($0, prefix) == 1 && $0 ~ /\.so$/ { count++ }
    END { print count + 0 }
  '
}

abis_in() {
  unzip -Z1 "$1" | awk '
    $0 ~ /^lib\/[^/]+\// {
      split($0, parts, "/")
      if (!seen[parts[2]]++) print parts[2]
    }
  ' | sort
}

expect_abis() {
  local apk="$1"
  shift
  local -a expected=("$@")
  declare -A allow=()
  local abi count got

  for abi in "${expected[@]}"; do
    allow["$abi"]=1
    count="$(so_count "$apk" "$abi")"
    if [[ "$count" -lt 1 ]]; then
      echo "$(basename "$apk"): lib/${abi} has no .so" >&2
      return 1
    fi
  done

  while IFS= read -r abi; do
    [[ -z "$abi" ]] && continue
    if [[ -z "${allow[$abi]:-}" ]]; then
      echo "$(basename "$apk"): unexpected ABI ${abi}" >&2
      return 1
    fi
  done < <(abis_in "$apk")
}

expect_abis "${found[universal]}" "${ABIS[@]}"
for abi in "${ABIS[@]}"; do
  expect_abis "${found[$abi]}" "$abi"
done

find_zipalign() {
  if command -v zipalign >/dev/null 2>&1; then
    command -v zipalign
    return
  fi
  local sdk="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
  if [[ -z "$sdk" && -f local.properties ]]; then
    sdk="$(sed -n 's/^sdk\.dir=//p' local.properties | head -n1)"
  fi
  if [[ -n "$sdk" && -d "$sdk/build-tools" ]]; then
    find "$sdk/build-tools" -type f -name zipalign | sort -V | tail -n1
  fi
}

zipalign_bin="$(find_zipalign)"
if [[ -z "$zipalign_bin" || ! -x "$zipalign_bin" ]]; then
  echo "zipalign not found (ANDROID_SDK_ROOT, ANDROID_HOME, or local.properties sdk.dir)" >&2
  exit 1
fi

for apk in "${apks[@]}"; do
  if ! "$zipalign_bin" -P 16 -c 4 "$apk"; then
    echo "16 KB zipalign check failed: $(basename "$apk")" >&2
    exit 1
  fi
done

echo "ABI splits ok: ${#apks[@]} APKs in $DIR"
