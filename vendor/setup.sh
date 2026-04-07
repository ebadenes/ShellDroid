#!/usr/bin/env bash
# Clones vendor dependencies. Run from repo root or vendor/.
# These are intentionally NOT git submodules — kept simple to bootstrap.
# Convert to submodules in a future commit if multiple devs work on the project.
set -euo pipefail

cd "$(dirname "$0")"

clone_if_missing() {
    local dir="$1" url="$2" ref="$3"
    if [[ -d "$dir/.git" ]]; then
        echo "✓ $dir already cloned (skipping)"
        return
    fi
    echo "→ Cloning $dir from $url ($ref)"
    git clone --depth 1 --branch "$ref" "$url" "$dir"
}

# Termux terminal libs (terminal-emulator + terminal-view).
# Pinned to a specific tag to keep the local patch (vendor/patches/termux-terminal-view.patch) stable.
clone_if_missing "termux-app" "https://github.com/termux/termux-app.git" "v0.118.3"

# libssh 0.11.4 (security release, fixes Terrapin CVE-2023-48795)
clone_if_missing "libssh" "https://gitlab.com/libssh/libssh-mirror.git" "libssh-0.11.4"

# mbedTLS 3.6.x (LTS branch)
clone_if_missing "mbedtls" "https://github.com/Mbed-TLS/mbedtls.git" "v3.6.4"

# Apply local patches
if [[ -x "./apply-patches.sh" ]]; then
    ./apply-patches.sh
fi

echo
echo "✓ Vendor setup complete"
