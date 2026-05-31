#!/usr/bin/env bash
# Build a signed release AAB for Play Store.
# Loads signing secrets from .env (gitignored), validates the keystore,
# then runs the Gradle release bundle inside the dev container.
set -euo pipefail

cd "$(dirname "$0")"

# --- Load .env ---------------------------------------------------------------
if [[ ! -f .env ]]; then
    echo "ERROR: .env not found. Copy .env.example to .env and fill the passwords." >&2
    exit 1
fi
set -a
# shellcheck disable=SC1091
source .env
set +a

# --- Validate required vars --------------------------------------------------
: "${SHELLDROID_KEYSTORE:?missing in .env}"
: "${SHELLDROID_KEYSTORE_PASS:?missing in .env}"
: "${SHELLDROID_KEY_PASS:?missing in .env}"

if [[ ! -f "$SHELLDROID_KEYSTORE" ]]; then
    echo "ERROR: keystore '$SHELLDROID_KEYSTORE' not found (relative to project root)." >&2
    exit 1
fi

IMAGE="${SHELLDROID_BUILD_IMAGE:-shelldroid-build:dev}"

# --- Verify keystore + alias before wasting a full build ---------------------
echo "→ Verifying keystore and 'upload' alias..."
docker run --rm \
    --user "$(id -u):$(id -g)" \
    -v "$PWD:/work" -w /work -e HOME=/work \
    "$IMAGE" \
    keytool -list -keystore "$SHELLDROID_KEYSTORE" \
        -storepass "$SHELLDROID_KEYSTORE_PASS" -alias upload >/dev/null
echo "  ✓ keystore OK, alias 'upload' present"

# --- Build signed release bundle ---------------------------------------------
echo "→ Building signed release AAB..."
mkdir -p .docker-cache/gradle .docker-cache/android
docker run --rm \
    --user "$(id -u):$(id -g)" \
    -v "$PWD:/work" \
    -v "$PWD/.docker-cache/gradle:/work/.gradle-home" \
    -v "$PWD/.docker-cache/android:/work/.android-home" \
    -w /work \
    -e GRADLE_USER_HOME=/work/.gradle-home \
    -e ANDROID_USER_HOME=/work/.android-home \
    -e ANDROID_HOME=/home/circleci/android-sdk \
    -e ANDROID_SDK_ROOT=/home/circleci/android-sdk \
    -e ANDROID_NDK_HOME=/home/circleci/android-sdk/ndk \
    -e HOME=/work \
    -e SHELLDROID_KEYSTORE="$SHELLDROID_KEYSTORE" \
    -e SHELLDROID_KEYSTORE_PASS="$SHELLDROID_KEYSTORE_PASS" \
    -e SHELLDROID_KEY_PASS="$SHELLDROID_KEY_PASS" \
    "$IMAGE" ./gradlew :app:bundleRelease

AAB="app/build/outputs/bundle/release/app-release.aab"
if [[ -f "$AAB" ]]; then
    echo
    echo "✓ Release AAB ready: $AAB ($(du -h "$AAB" | cut -f1))"
    echo "  versionName=1.0.0  versionCode=5"
    echo "  Next: upload to Play Console (production track)."
else
    echo "ERROR: build finished but AAB not found at $AAB" >&2
    exit 1
fi
