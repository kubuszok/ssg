# R0610 remediation campaign progress log

Plan: docs/plans/remediation-2026-06.md · Issues tagged `[R0610-P<phase>]` in re-scale db issues
One line per `/goal` iteration: `date | phase | attempted | resolved | bounced | notes`

- 2026-06-10 | intake | — | — | — | plan + skills created; cross-pollinated with SGE campaign (red-commit protocol, reproducer role, structured resolve notes, hook hardening §7a, exit criteria §10); no fixes merged
- 2026-06-10 | P0 | ISS-979 | ISS-979 | 0 | JS runtime resources embedded at build time (generator in project/EmbeddedResourcesGen.scala, embedded-first lookup, fs fallback intact for test resources); red ce1282b9 → fix c6ed0fe1, audit PASS first try (byte-fidelity 16/16, dual mutation check, emoji/admonition clauses verified by ad-hoc suite); merged c515ca4b; ratchet unchanged; new: ISS-1118 (highlight cwd-relative query dir, same defect class); next per protocol: ISS-1054 design epic — flagged for user review when draft lands
- 2026-06-10 | P0 | ISS-977 | ISS-977 | 1 | Native FileOps implemented via java.nio.file; bounce: v1 red-test comments tripped shortcut scanner (audit FAIL, correct), rebuilt as v2 red 5f6758b0 + fix 4b661334, delta audit PASS; merged c64c9f26; ratchet shortcut_hits 173→172; new: ISS-1116 (stale JVM-only docs), ISS-1117 (baseline exception_swallows mis-measured 4→11, NEEDS USER SIGN-OFF on the raise); auditor verified liquid Native includes now compile but relative-root resolution still broken (ISS-980)
