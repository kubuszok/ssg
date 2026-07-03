# R0610 campaign — live handoff state

Purpose: an Opus-tier orchestrator must be able to resume the campaign from this
file + `/goal` alone. Updated by the orchestrator at every iteration boundary.
Constitution: `docs/plans/remediation-2026-06.md`. Ledger:
`docs/plans/remediation-progress.md`.

## Iteration 3 result (2026-07-02): ISS-1381 + ISS-1376 RESOLVED; protocols updated

- Both facades cherry-picked onto the LINEARIZED `more-improvements-2`
  (dfda4e58, f0c62e33 + fmt-fixpoint c737a0d8), audited PASS on Fable 5,
  resolved with full evidence chains. 8 wiring issues remain (1373/1374/1375/
  1377/1378/1379/1380/1382) — the error-contracts.md §2.x doc-driven pattern is
  proven executable.
- **Audit model protocol restored** (user-approved 2026-07-02, commit 89d0e72a):
  auditor = Fable 5 (`model: "fable"`) while available (~2026-07-06), Opus 4.8
  fallback after, NEVER the implementer's model. The first Fable dispatch
  VOIDed itself under the older clause — that was the anti-cheat working;
  escalation to the user produced the restoration. verify-issue SKILL.md +
  remediation-2026-06.md §2/C13 both updated.
- **Branch history was linearized + pushed 2026-07-01/02** (repo rules forbid
  merge commits): PR #45 to master is open and MERGEABLE/CLEAN; local master
  force-pushed to its tree-identical twin (de0b5f56). Old shas resolve via
  `archive/more-improvements-2-pre-linearize`. See
  `../MERGE-COMMIT-AUDIT-2026-07-01.md`.
- **Three worktree gotchas for every dispatcher**: (1) Agent worktree isolation
  can spawn at MASTER — first action must be `git checkout --detach <pinned
  sha>`; (2) sbt-git/JGit in agent worktrees can't resolve HEAD without an
  objects alternates/symlink shim; (3) Edit/Write pin to the cwd repo's
  worktree — only dispatch worktree-isolated agents for the repo matching cwd.

## Earlier state (2026-07-01, iteration 1 COMPLETE)

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

## Model routing — TWO-TIER POLICY (user decision 2026-07-03)

- Fable-per-review exhausts session limits. Per-issue audits + reproducers +
  gates run **Opus 4.8** (`model: "opus"`); **Fable 5** is reserved for
  MILESTONE reviews — a whole milestone landed and already Opus-approved.
  Same-model-void (C13) is SUSPENDED for per-issue audits when the
  implementer is also Opus 4.8; the Fable milestone review is the diversity
  backstop. verify-issue SKILL.md + remediation-2026-06.md §2/C13 updated
  2026-07-03. The 2026-07-02 Fable-auditor restoration below is superseded.

## Model routing — earlier deviation record (historical)

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

## PR #45 status (2026-07-03 late)

- ISS-1383 fix landed on the branch; **all 18 CI checks green including both
  Windows JVM legs** (previously 26 failures each). The branch is a signed,
  linear fast-forward of master (`git merge-base --is-ancestor` true; all
  commits %G?=U). The ff-push of `more-improvements-2` → `master` was
  **permission-denied by the session classifier** — merging needs the user's
  explicit go-ahead (or the user merges themselves; GitHub's rebase-merge
  button will be refused by the signed-commits rule, so the ff-push
  `git push origin origin/more-improvements-2:master` is the way).
- Once #45 lands: the per-repo queue is UNBLOCKED — wiring ISS-1373..1382,
  ISS-1384 (JS/Native drive-lift parity), P2 mediums (ISS-1049/1101/1108).
