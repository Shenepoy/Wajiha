# CI / CD and testing

## Workflows

| Workflow | Trigger | What it does |
|----------|---------|----------------|
| [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) | `push` to `main`, all PRs, manual `workflow_dispatch`, and reusable `workflow_call` | **lint** (ktlint), **unit** (`:composeApp:testAndroidHostTest` + `:androidApp:test`), **assemble** (`:androidApp:assembleRelease -Pwajiha.abiSplits=true`, then `scripts/verify-apk-abis.sh`), **instrumented** (API 30 x86_64 emulator → `:androidApp:connectedDebugAndroidTest`, one retry) |
| [`.github/workflows/release.yml`](../.github/workflows/release.yml) | Tags `vX.Y.Z` (+ `-alpha.N` / `-beta.N` / `-rc.N`) | Runs full `ci` first, then builds **signed** universal and per-ABI release APKs and publishes a GitHub Release |

## Local commands

```bash
./scripts/ktlint.sh check
./gradlew :composeApp:testAndroidHostTest :androidApp:test
./gradlew :androidApp:assembleRelease -Pwajiha.abiSplits=true
./scripts/verify-apk-abis.sh androidApp/build/outputs/apk/release
./gradlew :androidApp:connectedDebugAndroidTest   # device or emulator; one APK, splits off

# Thor (not CI)
./scripts/install-thor.sh
./scripts/thor-e2e.sh
```

## Version injection

Gradle reads optional properties (defaults: `0.3.0` / `3`):

```bash
./gradlew :androidApp:assembleRelease \
  -Pwajiha.versionName=1.2.3 \
  -Pwajiha.versionCode=42
```

The release workflow sets `versionName` from the git tag (without `v`) and `versionCode` from `github.run_number`.

`-Pwajiha.abiSplits=true` (CI assemble and tag release) emits five APKs. The universal APK keeps that version code. Each ABI split uses `versionCode * 1000 + offset`:

| ABI | Offset |
|-----|--------|
| `armeabi-v7a` | 1 |
| `arm64-v8a` | 2 |
| `x86` | 3 |
| `x86_64` | 4 |

Obtainium installs the matching ABI APK because that version code is higher. Debug and instrumented builds leave the property unset, so they stay one APK. `ndk.abiFilters` still packages all four ABIs into that single APK.

## Release signing secrets

Tag releases require these **repository secrets**:

| Secret | Contents |
|--------|----------|
| `WAJIHA_KEYSTORE_BASE64` | Base64 of the upload `.jks` / `.keystore` file |
| `WAJIHA_KEYSTORE_PASSWORD` | Keystore password |
| `WAJIHA_KEY_ALIAS` | Key alias |
| `WAJIHA_KEY_PASSWORD` | Key password |

Create a keystore once (keep the file offline / in a password manager):

```bash
keytool -genkeypair -v \
  -keystore wajiha-upload.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias wajiha
base64 -w0 wajiha-upload.jks   # paste into WAJIHA_KEYSTORE_BASE64
```

Local signed builds can use the same values via Gradle properties or env:

```properties
# ~/.gradle/gradle.properties (do not commit)
wajiha.keystore.path=/absolute/path/wajiha-upload.jks
wajiha.keystore.password=...
wajiha.key.alias=wajiha
wajiha.key.password=...
```

The tag workflow passes the keystore path and passwords as `WAJIHA_KEYSTORE_PATH`, `WAJIHA_KEYSTORE_PASSWORD`, `WAJIHA_KEY_ALIAS`, and `WAJIHA_KEY_PASSWORD`. It does not put those values on the Gradle command line.

Without signing config, `:androidApp:assembleRelease` still builds (unsigned) for the CI R8 gate. `scripts/verify-apk-abis.sh` then checks that the five APKs contain the expected ABIs and pass `zipalign -P 16`.

## Cutting a release

1. Ensure `main` is green on `ci`.
2. Secrets above are configured.
3. Tag and push:

```bash
git tag v0.3.0
git push origin v0.3.0
```

4. GitHub Actions publishes the universal APK and one APK per ABI (Obtainium tracks GitHub Releases and picks the ABI split).

Prerelease tags (`v0.2.0-beta.1`, etc.) mark the GitHub Release as prerelease and do not become “latest”.

## Test layers

| Layer | Where | Scope |
|-------|--------|--------|
| Unit / host | CI + local Gradle | Scrapers, parsers, ROM probes, gamepad helpers |
| Instrumented / smoke | CI emulator | `AppSmokeTest` (MainActivity launch), Compose focus test. API 30 x86_64, 2 GB RAM, 600s boot timeout, one retry. Failure uploads logcat and `androidTests` reports. |
| Thor e2e | Local / agent only | `scripts/thor-e2e.sh` + PolyScreen (dual-display) |

## Artifact names

Signed release assets:

| File | Contents |
|------|----------|
| `Wajiha-<version>.apk` | Universal (all four ABIs). Same name Obtainium already tracks. Manual install for any device. |
| `Wajiha-<version>-arm64-v8a.apk` | arm64 |
| `Wajiha-<version>-armeabi-v7a.apk` | 32-bit ARM |
| `Wajiha-<version>-x86_64.apk` | x86_64 |
| `Wajiha-<version>-x86.apk` | x86 |
| `SHA256SUMS` | Checksums of the five APKs |

A device that already installed an ABI split will not later switch to the universal file of a newer tag: the universal version code stays lower, so Obtainium keeps updating the ABI APK. Older tags may still have a single `Wajiha-<version>.apk` or `*-debug.apk`.
