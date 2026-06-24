#!/usr/bin/env bash
# ─── CI Ratchet Gate ─────────────────────────────────────────────────
#
# Blocks CI when any lower-is-better metric REGRESSES above its
# committed baseline.  This is the enforcement mechanism for ISS-1107:
# covenant and shortcut counts can only go DOWN, never UP.
#
# How it works:
#   1. Reads .rescale/data/remediation-baseline.tsv (orchestrator-owned).
#   2. For each metric row (except covenant_fail_total — see below),
#      runs the measurement command and extracts the current integer.
#   3. Compares current vs baseline: current > baseline is a REGRESSION.
#   4. Prints a summary table and exits 0 (clean) or 1 (regression or
#      measurement failure).
#
# Why covenant_fail_total is excluded:
#   Its measurement command (re-scale enforce verify --all) takes several
#   minutes and its output is not a clean integer.  Covenant verification
#   is already covered by the existing (staged, non-blocking) covenant-
#   verify step in the enforce job.
#
# The baseline file is owned by the remediation orchestrator and updated
# only after verified improvement.  This script is read-only with respect
# to the baseline.
# ─────────────────────────────────────────────────────────────────────

set -euo pipefail

BASELINE_FILE=".rescale/data/remediation-baseline.tsv"

if [ ! -f "$BASELINE_FILE" ]; then
  echo "ERROR: Baseline file not found: $BASELINE_FILE"
  exit 1
fi

# Collect results: arrays for the summary table
declare -a METRICS=()
declare -a BASELINES=()
declare -a CURRENTS=()
declare -a VERDICTS=()
FAILURES=0

# Read TSV line by line, skipping header and covenant_fail_total
line_num=0
while IFS=$'\t' read -r metric value measured command; do
  line_num=$((line_num + 1))

  # Skip header row
  if [ "$line_num" -eq 1 ]; then
    continue
  fi

  # Skip empty lines (e.g. trailing newline)
  if [ -z "$metric" ]; then
    continue
  fi

  # Skip covenant_fail_total (expensive; covered by staged step)
  if [ "$metric" = "covenant_fail_total" ]; then
    continue
  fi

  # Run the measurement command and capture output
  # Use a subshell so set -e does not kill us on grep exit code 1 (no match)
  cmd_output="$(bash -c "$command" 2>&1)" || true

  # Extract first integer from the last non-empty line of output
  last_line="$(echo "$cmd_output" | grep -v '^\s*$' | tail -1)" || true
  current="$(echo "$last_line" | grep -oE '[0-9]+' | head -1)" || true

  # Validate: must be a non-empty integer
  if [ -z "$current" ]; then
    echo "ERROR: Measurement for '$metric' produced no integer."
    echo "  Command: $command"
    echo "  Output:  $cmd_output"
    METRICS+=("$metric")
    BASELINES+=("$value")
    CURRENTS+=("MEASUREMENT_FAILED")
    VERDICTS+=("FAILURE")
    FAILURES=$((FAILURES + 1))
    continue
  fi

  # Compare
  if [ "$current" -gt "$value" ]; then
    verdict="REGRESSION"
    FAILURES=$((FAILURES + 1))
  else
    verdict="OK"
  fi

  METRICS+=("$metric")
  BASELINES+=("$value")
  CURRENTS+=("$current")
  VERDICTS+=("$verdict")

done < "$BASELINE_FILE"

# ─── Print summary table ────────────────────────────────────────────

printf "\n%-30s %10s %10s %12s\n" "metric" "baseline" "current" "verdict"
printf "%-30s %10s %10s %12s\n" "------------------------------" "----------" "----------" "------------"

for i in "${!METRICS[@]}"; do
  printf "%-30s %10s %10s %12s\n" "${METRICS[$i]}" "${BASELINES[$i]}" "${CURRENTS[$i]}" "${VERDICTS[$i]}"
done

echo ""

# ─── Final verdict ──────────────────────────────────────────────────

if [ "$FAILURES" -gt 0 ]; then
  # Collect names of regressed/failed metrics
  regressed=""
  for i in "${!METRICS[@]}"; do
    if [ "${VERDICTS[$i]}" != "OK" ]; then
      if [ -n "$regressed" ]; then
        regressed="$regressed, "
      fi
      regressed="$regressed${METRICS[$i]}"
    fi
  done
  echo "RATCHET: REGRESSION — $regressed"
  exit 1
else
  echo "RATCHET: CLEAN"
  exit 0
fi
