#!/bin/bash
# PreToolUse hook: delegates to ssg-dev hook, compiling if needed
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
SSG_DEV="$SCRIPT_DIR/scripts/bin/ssg-dev"
SRC_DIR="$SCRIPT_DIR/scripts/src"

if [ ! -x "$SSG_DEV" ]; then
  scala-cli package "$SRC_DIR" -o "$SSG_DEV" -f 1>&2
fi

exec "$SSG_DEV" hook
