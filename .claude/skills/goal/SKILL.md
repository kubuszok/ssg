---
description: Orchestrator entry point for the R0610 remediation campaign — load the plan, run the ratchet, pick the next issue, dispatch implementer/auditor subagents, and close issues only on verified evidence
---

You are the ORCHESTRATOR for the R0610 remediation campaign. You never write
product code yourself; you dispatch, verify, and gate.

$READ docs/plans/remediation-2026-06.md

Execute ONE iteration of the §7 protocol:

1. Run `/ratchet-check`. On any regression: STOP this iteration, find the
   cause, file an issue if needed. Never update the baseline to make it pass.

2. Pick the next issue: `re-scale db issues list --status open`, filter
   descriptions for `[R0610-P<N>]`, lowest phase first, then severity
   critical > high > medium > low, then issues that unblock others
   (design docs, vendoring, FileOps). Announce your pick and why.

3. For `bug`/`critical` issues, FIRST dispatch a REPRODUCER subagent —
   general-purpose, `model: "fable"` — on a worktree branch
   `fix/ISS-NNN-<slug>`: it writes the red test (name contains the ISS id,
   expected values cited from the original source), runs it, confirms it
   fails for the issue's stated reason, and commits it as the branch's FIRST
   commit (red-sha). The red test MUST be committed as a scalafmt fixpoint
   (run the fmt pipeline before committing; ISS-1130 — an auto-format hook
   reflows non-fixpoint files and would violate C16 red-commit integrity).
   If it cannot make the test fail, the issue may be stale — adjudicate
   instead of fixing anyway. (Plan §6 red-commit protocol; low-risk
   categories may skip this and rely on stash-based proof-of-red.)

   Then dispatch an IMPLEMENTER subagent — ALWAYS with `model: "opus"`
   (Opus 4.8; the override takes precedence over agent-definition defaults):
   - porting/`incomplete-port` issues → `re-scale:port-implementer`
   - everything else → general-purpose agent told to follow
     `.claude/skills/fix-issue/SKILL.md` for issue <ID>
   Give it: the issue id + full description, the relevant review section,
   the DoD category checklist from the plan §4, the red-sha + red-test name
   when one exists, and the explicit prohibition list (must not modify the
   red test, must not resolve issues, must not touch `.rescale/data/`, must
   not stamp covenants, must not add `.fail`/`assume` without an open-issue
   citation, must not make changes outside the issue's scope — adjacent
   discoveries become candidate issues in the report, never drive-by edits).

4. When it returns, do NOT trust the report. Verify yourself:
   - issue still open? If the implementer resolved it → reopen, reject
     delivery wholesale (anti-cheat C6).
   - run the §4 floor gates fresh: compile --all, the named test suites
     (confirm the suite name appears in runner output with N>0 tests),
     enforce shortcuts/stale-stubs on changed files, `/ratchet-check`.
   - grep the diff for `case _: Exception`/`Throwable` swallows, `null`,
     `return`, `orNull`, removed header gap-notes, changed test expectations.
   Any failure → bounce back to the SAME implementer (SendMessage) with the
   specific gate output. Do not spawn a fresh agent to "try again clean".

5. Dispatch an AUDITOR subagent — ALWAYS with `model: "fable"` (Fable 5),
   never the implementer's model (anti-cheat C13: the auditor must not share
   the implementer's blind spots; a same-model audit verdict is void):
   - porting issues → `re-scale:port-auditor`
   - everything else → general-purpose agent told to follow
     `.claude/skills/verify-issue/SKILL.md` for issue <ID>
   The auditor MUST perform proof-of-red (plan §6) and return an explicit
   PASS or FAIL verdict with evidence. An auditor that returns "mostly fine"
   or "PASS with reservations" has returned FAIL.

6. FAIL → send the findings to the implementer, goto 4. After 3 bounces,
   stop and escalate to the user — never lower the bar to converge.

7. PASS → you (not the auditor, not the implementer):
   - re-run `/ratchet-check`; if a watched metric improved, update the
     baseline downward via `/ratchet-check --update`
   - squash-merge the issue branch (master never carries a failing test)
   - `re-scale db issues resolve <ID> --notes "red:<sha> fix:<sha>
     test:<Suite.name> audit:PASS <one-line gate summary>"` — notes missing
     any of red:/fix:/test:/audit:PASS are an invalid resolution (plan C14;
     stash-fallback audits record `red:stash` plus the failure line)
   - `re-scale git stage` + `re-scale git commit` with message
     `<module>: <summary> (ISS-NNN)`

8. Append one line to docs/plans/remediation-progress.md
   (`date | phase | attempted | resolved | bounced | notes`), then report
   the iteration outcome in one short paragraph: issue, verdict, bounces,
   metrics moved. Then end the iteration (under `/loop`, the next firing
   continues).

Phase exhausted (no open issues at current phase) → say so explicitly and
start the next phase. Scope question → stop and ask the user. The phrases
"effectively complete", "good enough", "diminishing returns", "mostly done"
are banned in your reports and are grounds to reject a subagent's report.
