#!/usr/bin/env bash
# Wrapper to run Gradle inside the ShellDroid dev container.
# Mounts the project + a project-local Gradle cache so root inside the container
# doesn't fight with the host user's ~/.gradle ownership.
set -euo pipefail

IMAGE="${SHELLDROID_BUILD_IMAGE:-shelldroid-build:dev}"

if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
    echo "→ Building dev image $IMAGE..."
    docker build -t "$IMAGE" -f Dockerfile.dev .
fi

mkdir -p .docker-cache/gradle .docker-cache/android

exec docker run --rm \
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
    "$IMAGE" ./gradlew "$@"
