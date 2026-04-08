#!/bin/bash
# PreToolUse hook: delegates to re-scale hook (formerly ssg-dev hook).
#
# re-scale is the cross-project successor to the worktree-local
# scripts/src/ssg-dev tool. Install with:
#
#   /Users/dev/Workspaces/GitHub/re-scale/scripts/install.sh
#
# That copies the binary + wrapper into $HOME/bin/. The wrapper sets
# SCALANATIVE_MAX_HEAP_SIZE so this hook can never accidentally
# allocate unbounded memory.
#
# Fallback path: if re-scale isn't on $PATH (e.g. fresh checkout
# before install.sh has run), fall back to the legacy ssg-dev binary
# in scripts/bin/ for compatibility. Once re-scale is installed
# everywhere, the fallback can be removed.

set -euo pipefail

if command -v re-scale >/dev/null 2>&1; then
  exec re-scale hook
fi

# -- Legacy fallback ---------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
SSG_DEV="$SCRIPT_DIR/scripts/bin/ssg-dev"
SRC_DIR="$SCRIPT_DIR/scripts/src"

if [ ! -x "$SSG_DEV" ]; then
  scala-cli package "$SRC_DIR" -o "$SSG_DEV" -f 1>&2
fi

exec "$SSG_DEV" hook
