#!/usr/bin/env bash
set -euo pipefail
IMAGE="${SHELLDROID_BUILD_IMAGE:-shelldroid-build:dev}"
if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
    echo "Building dev image..."
    docker build -t "$IMAGE" -f Dockerfile.dev .
fi
exec docker run --rm -v "$PWD:/work" -v "$HOME/.gradle:/home/circleci/.gradle" \
    -w /work "$IMAGE" ./gradlew "$@"
