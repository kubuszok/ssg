#!/usr/bin/env bash
# Run an sbt command line, retrying it (up to 3 times, with backoff) when it failed on a dependency
# download before any test ran or anything was published. Coursier does not retry a download cut
# off partway (`Premature EOF`), and Maven Central sometimes answers 404/502 for artifacts it has
# long held; a rerun fixes both. A failure with no download error, or after a test ran, is final.
#
# usage: sbt-download-retry.sh <log file> <command line>
# The log file holds the LAST attempt's output. When every attempt failed on a download,
# `<log file>.download-failed` is created, so a caller can tell that apart from a crash.
set -uo pipefail

log="$1"
cmd="$2"
download='Premature EOF|Error fetching artifacts|Error downloading|ResolveException|could not retrieve sbt'
# output proving the command got past dependency resolution and did work that must not be redone
progress='Passed: Total|Failed: Total|==> X |published '
delays=(20 60 120)

# the coursier caches of the three runner OSes; a `.*.error` file records a failed download, and
# coursier would answer from it instead of trying again
caches=("${COURSIER_CACHE:-}" "$HOME/.cache/coursier" "$HOME/Library/Caches/Coursier")
if [ -n "${LOCALAPPDATA:-}" ]; then
  if command -v cygpath > /dev/null; then
    caches+=("$(cygpath -u "$LOCALAPPDATA")/Coursier/Cache")
  else
    caches+=("$LOCALAPPDATA/Coursier/Cache")
  fi
fi

rm -f "$log.download-failed"
attempt=0
while :; do
  bash -c "$cmd" 2>&1 | tee "$log"
  code=${PIPESTATUS[0]}
  if [ "$code" -eq 0 ] || ! grep -qE "$download" "$log" || grep -qE "$progress" "$log"; then
    exit "$code"
  fi
  if [ "$attempt" -ge "${#delays[@]}" ]; then
    echo "::error::dependency download failed ${#delays[@]} retries in a row (a network or repository failure, not a build or test failure)"
    touch "$log.download-failed"
    exit "$code"
  fi
  delay=${delays[$attempt]}
  attempt=$((attempt + 1))
  echo "::warning::dependency download failed, retrying ($attempt/${#delays[@]}) in ${delay}s"
  for dir in "${caches[@]}"; do
    if [ -n "$dir" ] && [ -d "$dir" ]; then
      find "$dir" -type f -name '.*.error' -delete
    fi
  done
  sleep "$delay"
done
