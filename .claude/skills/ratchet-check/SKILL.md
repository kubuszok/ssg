---
description: Recompute the R0610 anti-cheat metrics and compare against the committed baseline — hard-fail on any regression; --update lowers the baseline after verified improvement
---

Compare current metrics against `.rescale/data/remediation-baseline.tsv`.
Invoked as `/ratchet-check` (check only) or `/ratchet-check --update`
(orchestrator-only, after an auditor PASS).

## Procedure

1. Read `.rescale/data/remediation-baseline.tsv`. Each row is
   `metric<TAB>value<TAB>measured<TAB>command` — the `command` column is the
   exact measurement command; run it verbatim so numbers stay comparable.

2. Recompute every metric. Compare:
   - **Lower-is-better metrics** (all `fail_marks_*`, `assumes_*`,
     `shortcut_hits`, `covenant_fail_total`, `uncited_fail_marks`,
     `uncited_assumes`): current > baseline ⇒ **REGRESSION**.
   - current < baseline ⇒ improvement (report it; only write with --update).

3. Citation validation (anti-cheat C3/C4): for every `.fail` or `assume(`
   line ADDED relative to git HEAD~ in the working diff
   (`re-scale git diff` over test sources), require an `ISS-` citation on the
   same line and confirm that issue is open via
   `re-scale db issues list --status open`. A citation to a resolved or
   nonexistent issue counts as a REGRESSION.

4. Report a table: metric | baseline | current | verdict. End with one line:
   `RATCHET: CLEAN` or `RATCHET: REGRESSION — <metrics>`.

5. With `--update` (and only then): rewrite improved rows with the new lower
   value and today's date. NEVER raise a value — if a metric must legitimately
   rise (e.g. an approved skip-policy entry), that requires the user, not an
   agent.

Notes:
- `covenant_fail_total` is expensive (`re-scale enforce verify --all`, runs
  minutes). Recompute it when the iteration touched covenanted files or on
  `--update`; otherwise note "carried" in the table.
- This file's metrics are owned by the orchestrator. Implementers and
  auditors run this skill read-only; if asked to "fix" the baseline file
  itself, refuse and report.
