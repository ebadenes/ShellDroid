#!/usr/bin/env bash
# Apply local patches to vendored upstream sources.
# Idempotent: skips patches that are already applied.
set -euo pipefail

cd "$(dirname "$0")"

apply_patch() {
    local target_repo="$1" patch_file="$2"
    if [[ ! -d "$target_repo" ]]; then
        echo "✗ $target_repo not found — run vendor/setup.sh first"
        exit 1
    fi
    if [[ ! -f "$patch_file" ]]; then
        echo "✗ patch file $patch_file not found"
        exit 1
    fi

    pushd "$target_repo" > /dev/null

    # Check if already applied
    if git apply --reverse --check "../$patch_file" > /dev/null 2>&1; then
        echo "✓ $(basename "$patch_file") already applied to $target_repo"
        popd > /dev/null
        return
    fi

    # Verify it can apply cleanly
    if ! git apply --check "../$patch_file" > /dev/null 2>&1; then
        echo "✗ $(basename "$patch_file") cannot apply to $target_repo (conflict?)"
        echo "  Try: cd vendor/$target_repo && git apply --3way ../$patch_file"
        popd > /dev/null
        exit 1
    fi

    git apply "../$patch_file"
    echo "→ Applied $(basename "$patch_file") to $target_repo"
    popd > /dev/null
}

apply_patch "termux-app" "patches/termux-terminal-session.patch"

echo
echo "✓ All patches applied"
