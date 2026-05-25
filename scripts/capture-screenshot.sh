#!/usr/bin/env bash
set -euo pipefail

usage() {
    echo "Usage: $0 <ios> <name>" >&2
    exit 1
}

[[ $# -ne 2 ]] && usage

PLATFORM="$1"
NAME="$2"

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$REPO_ROOT/docs/images/$PLATFORM"
mkdir -p "$OUT_DIR"

# Determine next sequential number
NEXT=1
if compgen -G "$OUT_DIR"/[0-9][0-9]-*.png &>/dev/null; then
    LAST=$(ls "$OUT_DIR"/[0-9][0-9]-*.png 2>/dev/null | sort | tail -1)
    LAST_N=$(basename "$LAST" | grep -oE '^[0-9]+')
    NEXT=$(( 10#$LAST_N + 1 ))
fi
NUM=$(printf '%02d' "$NEXT")
OUT="$OUT_DIR/$NUM-$NAME.png"

case "$PLATFORM" in
    ios)
        VENV="$REPO_ROOT/.venv/pymobiledevice3"
        PYMOBILE="$VENV/bin/pymobiledevice3"
        if [[ ! -x "$PYMOBILE" ]]; then
            echo "Creating pymobiledevice3 venv at $VENV..." >&2
            python3 -m venv "$VENV"
            "$VENV/bin/pip" install --quiet pymobiledevice3
        fi
        "$PYMOBILE" developer dvt screenshot "$OUT" 2>&1 | grep -v WARNING || true
        ;;
    *)
        echo "Unknown platform: $PLATFORM (expected ios or android)" >&2
        usage
        ;;
esac

echo "$OUT"
