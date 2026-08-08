# CI / CD and testing

## Workflows

| Workflow | Trigger | What it does |
|----------|---------|----------------|
| [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) | `push` to `main`, all PRs, and reusable `workflow_call` | **lint** (ktlint), **unit** (`:composeApp:testAndroidHostTest` + `:androidApp:test`), **assemble** (`:androidApp:assembleRelease` R8 gate, unsigned OK), **instrumented** (API 30 emulator → `:androidApp:connectedDebugAndroidTest`) |
| [`.github/workflows/release.yml`](../.github/workflows/release.yml) | Tags `vX.Y.Z` (+ `-alpha.N` / `-beta.N` / `-rc.N`) | Runs full `ci` first, then builds a **signed** release APK and publishes a GitHub Release |

## Local commands

```bash
./scripts/ktlint.sh check
./gradlew :composeApp:testAndroidHostTest :androidApp:test
./gradlew :androidApp:assembleRelease
./gradlew :androidApp:connectedDebugAndroidTest   # device or emulator

# Thor (not CI)
./scripts/install-thor.sh
./scripts/thor-e2e.sh
```

## Version injection

Gradle reads optional properties (defaults: `0.1.0` / `1`):

```bash
./gradlew :androidApp:assembleRelease \
  -Pwajiha.versionName=1.2.3 \
  -Pwajiha.versionCode=42
```

The release workflow sets `versionName` from the git tag (without `v`) and `versionCode` from `github.run_number`.

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

Without signing config, `:androidApp:assembleRelease` still builds (unsigned) for the CI R8 gate.

## Cutting a release

1. Ensure `main` is green on `ci`.
2. Secrets above are configured.
3. Tag and push:

```bash
git tag v0.2.0
git push origin v0.2.0
```

4. GitHub Actions publishes `Wajiha-0.2.0.apk` on the release (Obtainium tracks GitHub Releases).

Prerelease tags (`v0.2.0-beta.1`, etc.) mark the GitHub Release as prerelease and do not become “latest”.

## Test layers

| Layer | Where | Scope |
|-------|--------|--------|
| Unit / host | CI + local Gradle | Scrapers, parsers, ROM probes, gamepad helpers |
| Instrumented / smoke | CI emulator | `AppSmokeTest` (MainActivity launch), Compose focus test |
| Thor e2e | Local / agent only | `scripts/thor-e2e.sh` + PolyScreen (dual-display) |

## Artifact name

Release assets are named `Wajiha-<version>.apk` (signed release). Older tags may still have `*-debug.apk`.
