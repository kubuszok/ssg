# SSG Remediation Campaign — June 2026 (R0610)

**Source:** `docs/reviews/codebase-review-2026-06-10.md` (and 2026-06-09).
**Tracking:** every work item is an issue in `re-scale db issues` whose description
starts with a campaign tag `[R0610-P<phase>]`.
**Orchestration:** main session runs `/loop /goal` (or invokes `/goal` per
iteration); implementer and auditor subagents do the work; the orchestrator
never trusts a subagent's claim it can re-check mechanically.

---

## 1. Goal and success criteria

Make SSG's components individually honest and jointly usable:

1. Every public API parameter/config field either works (proven by a
   differential test) or does not exist.
2. All 3 platforms (JVM, JS, Native) actually run the features they compile —
   no `UnsupportedOperationException` on the Jekyll-critical path.
3. An end-to-end pipeline exists (markdown + front matter → liquid layout →
   sass → minify → page) with an integration test that builds a real fixture
   site.
4. The databases (migration, issues, audit) agree with the source tree, in
   both directions.
5. The metric ratchets (§5) reach their targets and never regress on the way.

**Non-goals (this campaign):** new diagram types, new flavors, performance
work, releasing to Maven Central.

## 2. Roles and authority (two-key rule)

| Role | Who | Model | May do | May NOT do |
|------|-----|-------|--------|------------|
| **Orchestrator** | main session, follows `/goal` | session model | pick issues, dispatch agents, run gates, commit after auditor PASS, resolve issues *after* auditor PASS | write product code |
| **Reproducer** | general agent, one per critical/bug issue | **Opus 4.8** — dispatch with `model: "opus"` | write the red test from the issue text + original source; commit it as the branch's FIRST commit (red-sha) | touch main-source code; see the fix |
| **Implementer** | `re-scale:port-implementer` (porting) or `issue-implementer` following `/fix-issue` | **Opus 4.6** — pinned via the agent definition's frontmatter (`model: claude-opus-4-6`, the full model ID); dispatch with **NO** `model:` override (the Agent-tool `model` enum cannot express 4.6 — only `opus`=4.8 — so an override would un-pin 4.6 and collide with the Opus 4.8 auditor) | edit code/tests, compile, run tests, report | modify the red test, resolve/close ANY issue, edit any TSV/DB, update ratchet baseline, stamp covenants, edit `docs/reviews/*`, make changes outside the issue's scope |
| **Auditor** | `re-scale:port-auditor` (porting) or general agent following `/verify-issue` | **Opus 4.8** (`model: "opus"`) for per-issue audits; **Fable 5** (`model: "fable"`) reserved for MILESTONE reviews only — a whole milestone landed and already Opus-approved (user decision 2026-07-03: Fable-per-review exhausts session limits) | adversarial verification, proof-of-red, verdict PASS/FAIL, file NEW issues for discovered problems | edit product code, lower a finding's severity to make it pass |

The **Reproducer split** (imported from the SGE campaign) exists because an
implementer writing its own reproduction test can — consciously or not —
tailor a weak test to the fix it already has in mind. For `critical`/`bug`
issues the red test is authored by a separate agent *before* the implementer
is dispatched; for low-risk categories (`docs`, `db-hygiene`) the implementer
may write its own tests and the stash-based proof-of-red (§6) plus mutation
spot-check carry the burden alone.

**Parallelism (imported from SGE):** independent issues in different modules
may run as parallel pipelines using `Agent` worktree isolation, one branch
`fix/ISS-NNN-<slug>` per issue, squash-merged on PASS so master never carries
a failing test. Two issues touching the same file are strictly sequential.

The model split is deliberate and part of the anti-cheat design (C13): the
auditor must run on a **different** model than the implementer so they do not
share blind spots — an implementer's plausible-but-wrong rationalization is
less likely to survive a different model's review. **Fable 5 was the original
reproducer/auditor model; Anthropic blocked Fable worldwide (2026-06-13), so
the campaign moved the implementer to Opus 4.6 and the reproducer + auditor to
Opus 4.8** — still two distinct models, so C13 holds. The implementer's 4.6 is
pinned in its agent-definition frontmatter as the full model ID
(`claude-opus-4-6`) and dispatched with NO `model:` override; the reproducer
and auditor take the `model: "opus"` (4.8) override on every dispatch (it takes
precedence over any agent-definition default). An audit performed on the
implementer's model does not count as an audit.

Hard rule: **an issue is resolved only by the orchestrator, only after an
auditor PASS verdict, only with evidence the orchestrator re-ran itself.**
If an implementer resolves an issue or touches `.rescale/data/`, the
orchestrator reopens it and rejects the entire delivery unreviewed.

## 3. Anti-cheat doctrine

Each counter below maps to a cheat actually observed in this codebase
(review 2026-06-10). These are not hypothetical.

| # | Observed cheat | Counter |
|---|----------------|---------|
| C1 | Migration DB rows marked `ported` with no Scala file (28 found in ssg-md) | DB status changes only by orchestrator post-audit; closure of port issues requires `Glob` existence + `re-scale enforce compare --port --source` output re-run by orchestrator |
| C2 | `Covenant: full-port` stamps on files whose own headers document 23% gaps (Terser.scala) | `/verify-issue` greps changed-file headers for `Gap:`, `not yet`, `TODO`, `for now`; any hit = FAIL. `re-scale enforce stale-stubs` runs in every gate |
| C3 | 1507 tests `.fail`-pinned so CI reads "0 failed" (ssg-js) | **Ratchet**: `.fail` count per module may only decrease (§5). Any NEW `.fail` must carry `// ISS-NNN` on the same line referencing an OPEN issue; `/ratchet-check` validates |
| C4 | `assume(false)` skips citing issues marked *resolved* (6 ssg-js suites) | Ratchet on `assume(` count; every cited issue must be open; stale citation = gate failure |
| C5 | False "X is not yet implemented" comments justifying shortcuts (DropUnused — the API existed) | `re-scale enforce stale-stubs` in every gate; auditor must verify any "not yet" claim against the actual API before accepting it |
| C6 | Premature "done" claims; "effective 100%"; "diminishing returns" | Two-key rule (§2). Banned phrases in reports: *effectively complete, good enough, diminishing returns, mostly done, low priority*. Orchestrator re-runs every gate command itself — pasted output is never evidence |
| C7 | Option parameters accepted and silently dropped, surviving because only happy-path tests exist (5 in sass, ~12 in mermaid) | DoD for `api-noop` issues requires a **differential test per option**: assert output *changes* when the option is toggled (§4) |
| C8 | Smoke tests asserting only `contains("<span")` (highlight, mermaid) | Auditor mutation spot-check: introduce one plausible bug into the fixed code locally, confirm the new test fails, revert. A test no mutation can break is not a test |
| C9 | Simplified rewrite shipped as a "port" (dagre Position.scala vs bk.js) | Port-fidelity closures require `re-scale enforce compare --strict`; if the original is not in `original-src/`, vendoring it is a prerequisite issue — "cannot compare" never means "assume ok" |
| C10 | Test files in a never-compiled directory (`src/test/scala-jvm/`) | Gate requires the test runner to report the new suite by name with N>0 tests executed; "the file exists" is not evidence the tests ran |
| C11 | Fixing the test instead of the code (changing expectations to match wrong behavior) | Expected values in tests may only change with a citation of the upstream source/fixture line that justifies the new value; auditor diffs test expectations against the original |
| C12 | Burying failures: `catch { case _: Exception => input }` | No new blanket catches; gate greps the diff for `case _: Exception`/`case _: Throwable` without rethrow/log |
| C13 | Implementer and auditor sharing one model's blind spots — the same reasoning that produced a shortcut also approves it | Model diversity preferred: implementer **Opus 4.6** via frontmatter pin when the project agent is in the session registry, else Opus 4.8 (documented deviation, see docs/plans/HANDOFF-CAMPAIGN.md). Per-issue auditor: **Opus 4.8** (user decision 2026-07-03 — Fable-per-review exhausts session limits). When implementer and auditor both run Opus 4.8, the same-model-void rule is SUSPENDED for per-issue audits (compensations: fresh auditor context, full adversarial checklist, orchestrator re-runs every gate). Backstop restoring real diversity: a **Fable 5 MILESTONE review** over each whole landed, Opus-approved milestone — its findings reopen issues like any audit FAIL |
| C14 | *(observed in the sibling SGE campaign)* Issue marked resolved on the easy half (accessors added, behavior unwired; init implemented, never called) | **Structured resolve notes**: every resolution carries `red:<sha> fix:<sha> test:<name> audit:PASS`; resolve-note evidence must be re-executable. Plus the sentence check: every clause of the issue description must now be false |
| C15 | *(observed in SGE)* Green CI that gates nothing — `continue-on-error` gates, assume-skipped IT jobs, validation env-skipped on release | **Canary DoD for infra issues**: a gate counts as fixed only when a deliberately-broken branch (stubbed covenant file, injected test regression) is shown to turn CI red; link the red run in the resolve notes, then revert the canary |
| C16 | *(observed in SGE)* Implementer fix-commit quietly rewording the reproduction test | **Red-commit protocol** (§6): the red test is a separate commit BEFORE the fix; `git diff red..fix -- <red-test-file>` must be empty — durable, re-checkable evidence rather than a one-time stash demo |

## 4. Definition of Done — per issue category

Common to ALL categories (the "floor"):
- [ ] `re-scale build compile --module <M> --all --errors-only` clean (all 3 platforms)
- [ ] `re-scale test unit --module <M> --all` — suites touching the change pass on all platforms they run on; the specific suite is named in the report with its executed-test count
- [ ] `re-scale enforce shortcuts --file <each changed file>` clean (or skip-policy entry justified)
- [ ] `re-scale enforce stale-stubs --src <module dirs>` clean for changed files
- [ ] `/ratchet-check` shows no regression
- [ ] No new `.fail`, `assume(`, blanket `catch`, `null`, `return`, `orNull` without an open-issue citation / interop justification
- [ ] Original comments preserved; no header gap-notes removed unless the gap is actually closed

Per category, in addition:

**`incomplete-port` (unported/partial code):**
- [ ] Original source file:lines identified in the issue; conversion follows `/guide-conversion`
- [ ] `re-scale enforce compare --port <scala> --source <orig>` clean, or every deviation documented in the header Migration notes AND accepted by auditor
- [ ] Tests ported from the original (or written if original had none) — proof-of-red applies
- [ ] Covenant stamped via `re-scale enforce verify --file` only after the above
- [ ] Migration DB row updated by orchestrator, not implementer

**`bug`:**
- [ ] A test reproducing the bug exists and **failed before the fix** (proof-of-red, §6)
- [ ] Fix is a faithful port of the original's logic, not an ad-hoc patch (no monkey-patching rule)

**`api-noop` (accepted-but-ignored options/params):**
- [ ] For each parameter: a differential test asserting output differs between option-on and option-off
- [ ] Wiring semantics match the original library's handling of that option (cite original file:line)
- [ ] If the decision is to REMOVE the parameter instead: deprecation/removal applied consistently and documented

**`missing-test`:**
- [ ] New tests assert structure/values (golden output, upstream fixture, or reference comparison), not just non-emptiness
- [ ] Auditor performs mutation spot-check (C8)

**`pit-of-success` (confusing API):**
- [ ] The footgun is impossible or loud: type-level fix, validation error, or documented + logged
- [ ] A test demonstrates the previously-confusing call now does the right/loud thing

**`infra` / `db-hygiene` / `docs`:**
- [ ] The specific drift is gone; the command/document named in the issue now succeeds/matches; for DB rows, each status change carries a one-line evidence note

## 5. Metric ratchets

Baseline lives in `.rescale/data/remediation-baseline.tsv` (created 2026-06-10,
exact measurement commands recorded in the file). `/ratchet-check` recomputes
and compares. Rules:

- Any metric moving the wrong way = **hard stop**: the orchestrator rejects
  the current delivery and files an issue if the regression came from elsewhere.
- Baseline updates are downward-only, by the orchestrator, after an auditor PASS.
- Targets: `covenant_fail_total` 99 → 0; `fail_marks_ssg-js` 1523 → <100
  (each remaining one citing an open issue); `fail_marks_ssg-liquid` 47 → 0;
  `assumes_uncited` → 0; `shortcut_hits` 173 → only skip-policy-approved.

## 6. Proof-of-red (the core premature-done guard)

**Preferred form — red-commit protocol (imported from the SGE campaign).**
Work happens on a worktree branch `fix/ISS-NNN-<slug>`:

1. The red test is the branch's **first commit** (red-sha), authored by the
   Reproducer (or, for low-risk categories, the implementer) — named so it
   contains the ISS id, with the expected values cited from the original
   source/fixture in a comment.
2. The fix lands in separate commit(s) (fix-sha). The auditor verifies
   mechanically: red test FAILS at red-sha (record the assertion line),
   PASSES at fix-sha, and `git diff <red-sha>..<fix-sha> -- <red-test-file>`
   is **empty** — the fix may not touch the red test (C16).
3. On PASS the branch is squash-merged (master never carries a failing test);
   red-sha/fix-sha go into the resolve notes so the evidence can be re-run
   later (Phase-final random re-audit, §10).

**Fallback form — stash-revert** (when the change predates the branch
protocol or checking out red-sha is impractical), the **auditor** must:

1. List the main-source files the implementer changed (from `re-scale git diff`).
2. `git stash push -u -- <those main-source paths>` (tests stay in place).
3. Run the specific suite(s): they **must fail**, and the failing assertion must
   be the one the issue is about. Record the failure line.
4. `git stash pop`, recompile, rerun: must pass.
5. If step 3 passes (green without the fix): the tests prove nothing → verdict
   FAIL with reason "test does not cover the fix".

If stash is impractical (huge change), use a worktree at HEAD, copy only the
test files in, and run there. "Too hard to demonstrate red" is a FAIL, not an
exemption — in either form.

## 7. Execution protocol (one `/goal` iteration)

```
1. /ratchet-check                       — abort iteration on any regression
2. Pick next open [R0610] issue         — lowest phase first; within a phase,
                                          critical > high > medium > low;
                                          prefer issues unblocking others
3. Dispatch IMPLEMENTER subagent        — /fix-issue <ID>; porting issues use
                                          re-scale:port-implementer
4. On return: orchestrator runs the     — floor gates from §4, itself, fresh;
   mechanical gates                       implementer output is not evidence
   + verify issue is still open         — if implementer resolved it: reopen,
                                          reject delivery (C6)
5. Dispatch AUDITOR subagent            — /verify-issue <ID>; porting issues use
                                          re-scale:port-auditor; includes
                                          proof-of-red (§6)
6. FAIL verdict → send findings back    — same implementer via SendMessage;
   to implementer                          goto 4. After 3 bounces, escalate to
                                          the user instead of lowering the bar
7. PASS verdict → orchestrator:         — re-run /ratchet-check; squash-merge
                                          the issue branch; resolve with
                                          structured notes (C14):
                                          red:<sha> fix:<sha> test:<name>
                                          audit:PASS — notes missing any of
                                          these are an invalid resolution;
                                          update baseline downward if improved;
                                          commit via re-scale git stage/commit
                                          citing ISS id
8. Log + end of iteration               — append one line to
                                          docs/plans/remediation-progress.md
                                          (date | phase | attempted | resolved
                                          | bounced | notes); /loop schedules
                                          the next one
```

Stop conditions: no open `[R0610]` issues in the current phase → announce and
move to next phase; any situation requiring a scope decision → stop and ask
the user; never silently re-scope.

## 7a. Hook hardening (imported from SGE — apply early in P0)

Add project overrides in `.rescale/claude-hooks.yaml` (schema in the re-scale
repo docs):

1. **Deny** `re-scale db issues resolve` unless `--notes` matches
   `red:[0-9a-f]{7,}` AND `fix:[0-9a-f]{7,}` AND `audit:PASS` — mechanical
   enforcement of C14. Infra/canary issues use the same `audit:PASS` token
   from their variant DoD.
2. **Deny** `re-scale enforce skip-policy add` and `re-scale db audit set`
   for the duration of the campaign; lifting the deny is an orchestrator
   edit to the override file, visible in the diff.
3. If the schema cannot express (1), deny `db issues resolve` outright and
   route resolutions through a single documented orchestrator path, backed by
   the §10 random re-audit.

Hooks stop accidents; the audit step stops adversaries — keep both.

## 8. Phases / workstreams

**P0 — the product exists and the platforms are real**
- E2E pipeline: design doc first (site config, front-matter→DataView bridge,
  layouts, output), then minimal `Site.build()` + integration test with a
  fixture site. This is an epic: the design-doc issue gates the rest.
- `FileOps` on Native (java.nio.file works on Scala Native) and Node JS;
  consumers (liquid includes, sass CompileFile) un-gated.
- ssg-md Scala.js resource embedding (Html5Entities/emoji/admonition).

**P1 — silent no-ops and high-confidence bugs**
- sass: wire `functions`/`quietDeps`/`charset`/`importers`/`loadPaths`;
  `CompileFile` syntax inference + url; delete-or-implement `Compile.compile`.
- js: `compress=true`/`mangle=true` semantics; `optionBool` on
  inline/pure_getters/sequences; DropUnused Pass 3 transform; `defaults=false`;
  rename `src/test/scala-jvm`; remove stale `assume(false)` skips.
- mermaid: frontmatter must not throw; apply `%%{init:}%%` + frontmatter
  config; wire-or-delete dead config fields; emit click/href.
- liquid: unquoted dotted/slashed include names; `endraw -%}` swallow;
  `where_exp` scoping; leading whitespace strip.
- minify: fnmatch excludes; `.min.*` passthrough; diagnostics channel.
- katex: `Macros.registerAll` guard; highlight: renderer rewrite + offsets.

**P2 — make the dashboards trustworthy**
- Issues/audit/migration DB reconciliation (close stale, file missing,
  fix 28 false `ported` rows, fix sass-spec-baseline TSV header).
- `.fail`/`assume` citation backfill (umbrella issue per suite family).
- CI: blocking enforce gate (per-module staging allowed), codecov path,
  remove `pull_request` from release.yml.
- Nullable consolidation to lls; delete `ssg.commons.Nullable` + root
  `lowlevel/`; error-contract unification design.

**P3 — fidelity and proof debt**
- Vendor dagre-js into `original-src/`; port bk.js positioning + pre-rank
  nesting; graphviz clusters/HTML labels.
- katex `AnyRef` placeholders → real types; unicode-spec port; missing
  spec cases.
- Cross-platform consistency tests (same input → same output, 3 platforms)
  for highlight/sass/md; mermaid reference-comparison harness modeled on
  graphviz's `ReferenceComparisonSuite`.

## 9. Tooling map

| Need | Tool |
|------|------|
| Iterate autonomously | `/loop /goal` (dynamic pacing) |
| Load constitution + pick work | `/goal` |
| Implementer checklist | `/fix-issue <ISS-ID>` |
| Auditor checklist + proof-of-red | `/verify-issue <ISS-ID>` |
| Regression tripwire | `/ratchet-check [--update]` |
| Port conversions | `re-scale:port-implementer`, `/guide-conversion` |
| Port audits | `re-scale:port-auditor`, `re-scale enforce compare` |
| Issue lifecycle | `re-scale db issues add/list/resolve` |
| Commit hygiene | `re-scale git stage/commit` — message cites ISS ids |
| Parallel issue pipelines | `Agent` with `isolation: "worktree"`, branch `fix/ISS-NNN-<slug>` |
| Progress ledger | `docs/plans/remediation-progress.md` — one line per iteration |

## 10. Campaign exit criteria (imported from SGE)

"No open issues" is necessary but not sufficient. The campaign is complete
only when ALL hold:

1. `re-scale db issues list --status open` filtered for `[R0610]` → empty
   (including any `bounce`-category issues filed along the way).
2. All §5 ratchet targets reached, measured by `/ratchet-check` on master,
   with the enforce gate **blocking** in CI.
3. The canary suite still works: a deliberately stubbed covenanted file and a
   deliberately broken test on a scratch branch each turn CI red (C15).
4. A **fresh adversarial re-review** — same multi-agent shape as the
   2026-06-10 review, new session, reviewers told which modules to inspect
   but NOT what was fixed — finds zero critical findings.
5. Random re-audit: for 10 randomly sampled resolved `[R0610]` issues, an
   auditor re-executes the resolve-note evidence (red/fix sha test runs)
   successfully. Any failure reopens the issue and triggers a sweep of that
   implementer's other resolutions.
