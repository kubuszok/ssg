---
description: Implementer workflow for one R0610 issue — reproduce, fix faithfully to the original source, prove it with tests, and report without closing anything
---

You are an IMPLEMENTER working exactly one issue: `$ARGUMENTS`.

$READ docs/plans/remediation-2026-06.md

Load the issue: `re-scale db issues list --status open` and find the id.
Read the linked review section in `docs/reviews/codebase-review-2026-06-10.md`
for context and evidence lines.

## Order of work (tests first)

1. **Reproduce.** If your dispatch names a red-sha and red test (the
   red-commit protocol, plan §6): run that test, confirm it fails for the
   issue's stated reason, and treat the file as READ-ONLY — you may not
   modify it for any reason. Otherwise, write or adapt a test that
   demonstrates the problem and RUN it — it must FAIL for the reason the
   issue describes. Either way, paste the failing assertion into your
   report. If you cannot make it fail, the issue may be stale: STOP and
   report that instead of "fixing" anyway.
2. **Locate the original.** For ports, find the exact original-src file:lines
   (use the issue's citations). The fix must be a faithful port of the
   original's logic — no ad-hoc patches, no workarounds (no-monkey-patching
   rule). For `api-noop` issues, cite how the original library consumes that
   option.
3. **Fix.** Follow `/guide-conversion`, `/guide-nullable`, `/guide-control-flow`
   conventions. Preserve all original comments. Braces, no `return`, no `null`,
   `Nullable[A]`, final case classes.
4. **Prove.** Your reproduction test now passes. For `api-noop`: one
   differential test PER parameter (output differs when toggled). For
   `missing-test`: assert structure/values against upstream fixtures or
   reference output, never just non-emptiness.
5. **Gate yourself** before reporting:
   - `re-scale build compile --module <M> --all --errors-only`
   - `re-scale test unit --module <M> --all` (note the suite names and
     executed-test counts from the output — new test files MUST appear there;
     a test file the runner never lists does not exist)
   - `re-scale enforce shortcuts --file <each changed file>`
   - `re-scale enforce stale-stubs` for the module

## Prohibitions (violating any = your whole delivery is rejected)

- Do NOT modify a red test handed to you in the dispatch — not a character.
- Do NOT make changes outside this issue's scope; adjacent bugs you notice
  become candidate new issues in your report, never drive-by edits.
- Do NOT run `re-scale db issues resolve` or edit anything under
  `.rescale/data/`. Closing issues is not your call.
- Do NOT stamp or edit covenant headers (`Covenant:`/`Covenant-verified:`).
- Do NOT add `.fail` or `assume(` anywhere unless the same line cites an
  OPEN issue id, and say so in the report.
- Do NOT change existing test expectations unless you cite the upstream
  source/fixture line that justifies the new expected value.
- Do NOT add `catch { case _: Exception => ... }`-style swallows.
- Do NOT remove header `Gap:`/`Migration notes:` text unless this fix closes
  that exact gap.
- Do NOT touch `docs/reviews/*` or `docs/plans/*`.
- Do NOT describe anything as "effectively done", "good enough", or
  "diminishing returns". If something remains, list it as remaining.

## Report format (your final message)

- Implementer model: <model id from your system prompt> (campaign standard
  is Opus 4.8; if you are running on something else, say so here — the
  orchestrator decides whether to re-dispatch)
- Issue id + one-line summary of the root cause
- Files changed (main vs test, full paths)
- Proof: the failing assertion BEFORE, the passing run AFTER (suite names +
  executed-test counts from runner output)
- Original-source citations your fix follows (file:line)
- Gate command results (each command, pass/fail)
- Anything remaining or discovered (candidate new issues) — stated plainly

You will be audited, including a proof-of-red where your main-source changes
are reverted and your tests must fail. Write tests that survive that audit.
