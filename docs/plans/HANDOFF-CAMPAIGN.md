# R0610 campaign — live handoff state

Purpose: an Opus-tier orchestrator must be able to resume the campaign from this
file + `/goal` alone. Updated by the orchestrator at every iteration boundary.
Constitution: `docs/plans/remediation-2026-06.md`. Ledger:
`docs/plans/remediation-progress.md`.

## Current state (2026-07-01, iteration 1 COMPLETE)

- Branch: `more-improvements-2` at d8617aa1. P0/P1 exhausted; queue = P2
  (33 open + 10 new wiring = 43) then P3 (58).
- ISS-1102 RESOLVED (audit PASS, 0 bounces): `ssg.commons.Diagnostics` envelope
  + `docs/architecture/error-contracts.md` adoption plan. Full evidence in the
  ledger line and resolve notes.
- Ratchet: CLEAN; baseline updated (`returns_ssg-sass` 62→61 folded post-PASS).
  `covenant_fail_total` 96 still carried.
- **Unblocked work**: ISS-1373..1382 — per-module error-contract wiring, one
  issue per module, each executable from `docs/architecture/error-contracts.md`
  §2.1–2.10 alone. These are INDEPENDENT across modules → good candidates for
  parallel pipelines (worktree isolation, branch `fix/ISS-NNNN-<slug>` each,
  strictly sequential only if touching the same file). ISS-1374 (liquid) and
  ISS-1380 (graphviz) are medium (typed-exception prerequisites); rest low.
- Next wake: SGE iteration (alternation), then back to SSG P2 mediums:
  ISS-1049 (.fail citation umbrella — biggest ratchet target), ISS-1101
  (Nullable sweep), ISS-1108 (codecov path — needs CI-verified canary DoD).

## Model routing — DEVIATION IN EFFECT (record in resolve notes)

- The plan pins implementer = Opus 4.6 via the project agent
  `.claude/agents/issue-implementer.md` frontmatter. This session's Agent
  registry (rooted at the workspace, not the repo) does NOT expose that agent,
  so ISS-1102's implementer runs as general-purpose with explicit
  `model: "fable"` (Fable 5, available through ~2026-07-06). C13 holds:
  implementer (Fable) ≠ auditor (Opus 4.8).
- A resuming orchestrator whose session is rooted IN the ssg repo should go back
  to dispatching `issue-implementer` with NO model override (restores the 4.6 pin).
  After the Fable window closes, never dispatch implementers with `model: "fable"`
  (it will silently fall back to the inherited model — Claude Code does not error
  on unavailable models; verify with
  `claude --model <id> -p "Reply OK" --output-format json` → `modelUsage` key).
- Never use Sonnet/Haiku for implementer/auditor just to differ — capability
  floor beats nominal diversity; single-model + full compensations (fresh
  context, proof-of-red, mutation spot-check, orchestrator-re-run gates) is the
  approved degraded mode.

## Iteration recipe deltas learned so far

- `re-scale db issues list` output is a wide space-padded table; compact with
  `sed -E 's/  +/\t/g'` before awk.
- Phase counts: `re-scale db issues list --status open | grep -o '\[R0610-P[0-9]*\]' | sort | uniq -c`.
- Metric greps run verbatim from `.rescale/data/remediation-baseline.tsv` col 4.

## Standing context

- 5-day takeover window (through 2026-07-06) alternates /sge:goal and /ssg:goal
  per wake, preferring the repo with more unblocked open issues; SGE counterpart
  handoff lives at `../sge/docs/plans/HANDOFF-CAMPAIGN.md` (create on first SGE
  iteration).
- Cross-repo planning roadmap: `../FABLE5_PLANNING_ROADMAP.md`. ISS-1102's
  design doc doubles as roadmap Topic 2 groundwork — keep them consistent.
