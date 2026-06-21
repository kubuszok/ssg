# ssg — sbt 2.0 migration plan (2026-06)

Source: `SGE_SSG_SBT2_HANDOFF.md`. Prepare on `more-improvements`, open a PR, make CI green, then merge.
Env gate **passes**: JDK 25 (≥22 for multiarch panama), Scala 3.8.4. The 11 pre-existing stashes are
left **untouched** (handoff mandate). No campaign (R0610) work is interleaved.

## 0. Dependency / plugin version table

| Artifact | From | To | Where |
|---|---|---|---|
| sbt | 1.12.11 | **2.0.0** | `project/build.properties` |
| sbt-kubuszok (plugin) | 0.2.1 | **0.2.3** | `project/plugins.sbt` |
| sbt-multiarch-scala (plugin) | 0.2.0 | **0.3.0** | `project/plugins.sbt` |
| sbt-scalafix (plugin) | 0.14.6 | verify sbt-2.0 build exists; else drop/pin | `project/plugins.sbt` |
| lls | 0.1.0 | **0.2.0** | build.sbt `versions` |
| hearth | 0.3.0-49-g68e1781-SNAPSHOT | **0.3.1-54-g83c3eb5-SNAPSHOT** (7-char hash) | build.sbt |
| kindlings-yaml | 0.2.0 | **latest master snapshot** (confirm exact on snapshot repo) | build.sbt |
| multiarch (core/resources) | 0.2.0 | **0.3.0** | build.sbt |
| munit | 1.3.2 | **1.3.3** | build.sbt |
| multiarch-resources | — (new dep) | **0.3.0** | build.sbt (ssg-md) |
| scala-sax-parser | n/a (not a direct ssg dep) | 0.1.1 if pulled transitively | — |

## 1. `project/build.properties`
`sbt.version=1.12.11` → `sbt.version=2.0.0`.

## 2. `project/plugins.sbt`
- sbt-kubuszok `0.2.1` → `0.2.3` (this bundles sbt-scalajs 1.22.0 / sbt-scala-native 0.5.12 /
  sbt-projectmatrix-merged-into-sbt2 / sbt-commandmatrix 0.1.0 — so no separate bumps needed unless a
  bundled plugin is missing; **verify what 0.2.3 bundles at execution**).
- sbt-multiarch-scala `0.2.0` → `0.3.0`.
- sbt-scalafix `0.14.6`: confirm an sbt-2.0 artifact exists. If not, drop it (scalafix isn't run in CI per
  ci.yml — only scalafmtCheckAll) or pin to whatever resolves. **Risk point — verify.**
- Keep the `scala-xml` VersionScheme line.

## 3. `build.sbt`
1. **Drop sbtwelcome** (no sbt-2.0 build): remove `import sbtwelcome.UsefulTask` (line 1); remove the
   root `logo := …` (368-382) and `usefulTasks := al.usefulTasks(extra = …)` (383-385). Register the
   `scalafmtAll` convenience via `addCommandAlias` (or the 0.2.3 `Aliases` API — **discover the new
   helper signature at execution**; `usefulTasks()` is gone on sbt2). The `al = new Aliases(published=…,
   compileOnly=…)` object likely still drives the `ci-jvm-3`/`ci-js-3`/`ci-native-3` aliases — keep it;
   just drop the `usefulTasks`/`logo` wiring.
2. **Flatten `val versions = new { … }`** (8-30): the anonymous-refinement object loses its structural
   members under the sbt-2.0 Scala-3 build dialect. Move to top-level `val`s in build.sbt **or** a
   `project/Versions.scala` object (preferred — cleaner). Update all `versions.X` references.
3. **`%%%` → `%%`** (platform-aware on sbt 2.0): lines 72-73, 132, 146-147, 248, 251, 257-258, 296,
   299, 305-306, 344. (The `%%` compilerPlugin at 149 already correct.) Plain `%` Java-dep coordinates
   (multiarch providers at 186, 191, 201, 211) stay `%`.
4. **Version bumps** in the flattened versions: hearth, lls, multiarch, munit, kindlingsYaml per §0.
5. **`Def.uncached` wrapping** for any setting/task capturing non-`HashWriter` types (Classpath,
   UpdateReport, HashedVirtualFileRef, enums). The ssg-md `sourceGenerators` block (274-281) is being
   **removed** (see §4), so the main candidate disappears. Watch the multiarch native-provider settings
   (200) and any `packageBin`/classpath passthrough — wrap if sbt errors on a missing `HashWriter`.
6. **`packageBin`/classpath → `HashedVirtualFileRef`**: if any setting reads a `File` from packageBin/
   classpath, convert via `fileConverter.value.toPath(ref)`. (None obvious in current build.sbt; watch
   the multiarch native packaging path.)
7. **multiarch native host-unfilter** (multiarch 0.3.0): `withCrossNative` now emits a host
   `NativeCrossAxis` row too. ssg-highlight's native axis uses `NativeProviderPlugin.projectSettings`
   (200) — verify it still applies and that we don't double-build the host (don't combine
   `.nativePlatform()` + `.withCrossNative()`). **Risk point — verify provider wiring.**

## 4. multiarch-resources adoption (ssg-md) — the source refactor
**Goal:** replace ssg-md's bespoke build-time resource embedding with the shared `multiarch-resources`.
- **Drop** `project/EmbeddedResourcesGen.scala` and the ssg-md JS `sourceGenerators` block (build.sbt
  273-281).
- **Drop** the 3 platform impls `ssg-md/src/main/{scalajvm,scalajs,scalanative}/.../PlatformResourcesImpl.scala`.
- **Add** dep `"com.kubuszok" %%% "multiarch-resources" % versions.multiarch` to ssg-md.
- **Add** `MultiArchResourcesPlugin.embeddedResourcesSettings(resourceDir, objectName)` on the ssg-md JS
  axis (and reference the generated object once from a JS entrypoint so Scala.js DCE keeps its
  self-registration). **Discover exact signature + the resourceDir/objectName values at execution.**
- **Boundary-wrap (chosen approach):** keep `ssg.md.util.misc.PlatformResources` as a thin shim whose
  `getResourceAsStream(cls, path): Nullable[InputStream]` delegates to `multiarch.resources.PlatformResources`
  (Option-based: `getResourceAsStream/getResourceBytes/getResourceAsString/resourceExists`) and converts
  `Option → Nullable` at the boundary. This keeps the ~6 call sites (Utils, Html5Entities, EmojiReference,
  AdmonitionExtension, PlatformFiles, the ISS-979 JS test) **unchanged** — lowest churn, the handoff's
  sanctioned "wrap at the boundary" option.
- **Verify** the shared API still resolves the **in-repo TEST resources** the spec suites load (ssg-md's
  old JS impl had an fs fallback for `src/test/resources`). If multiarch-resources doesn't cover that
  dev/test path, retain a minimal fs fallback in the shim for tests. **Risk point — the ISS-979
  PlatformResourcesIss979JsSuite must stay green.**

## 5. CI (`.github/workflows/`)
- `ci.yml`: commands are the `ci-jvm-3` / `ci-js-3` / `ci-native-3` aliases (from sbt-kubuszok). The
  `test`→`testFull` change lives **inside** those aliases in 0.2.3 — so likely no ci.yml edit needed;
  **confirm** the 0.2.3 aliases use `testFull` (bare `test` on sbt 2.0 is cached → silent false-green).
  If the aliases aren't updated, define them with `testFull` in build.sbt.
- Native packaging: with multiarch 0.3.0's host-unfilter, unify any current/non-current-arch packaging
  command split into **one** command name (ssg's ci.yml native job — verify whether it has a split).
- `release.yml`: triggers on **both** `push:[master]` and `tags:["*"]`. Per recipe, **release only on
  tags**; keep a snapshot publish on master-push but ensure the tag run waits for the master-push run
  (avoid same-version concurrent Central deploy). Minimal change: gate the `ci-release` job to tags, or
  keep snapshot-on-master + release-on-tag as separate guarded jobs.

## 6. Verification (before PR) — I (orchestrator) re-run these
Use **`sbt --client`** (not bare sbt). All via the root aggregate (covers every projectMatrix variant):
1. `sbt --client compile` (all modules, all 3 platforms via aggregate) — clean.
2. `sbt --client testFull` (NOT `test`) — all suites run (N>0), green on JVM/JS/Native.
3. Resource check: an embedded resource resolves on the **JS** axis (run the ssg-md emoji/entities/
   admonition path + `PlatformResourcesIss979JsSuite`).
4. Native packaging: the unified command produces a binary for host **and** a non-host target.
5. `sbt --client scalafmtCheckAll` clean.
6. Sanity vs R0610: the campaign ratchet is a runtime/test concern, not a build concern — but confirm
   `re-scale enforce shortcuts` still runs and the test counts match pre-migration (no suites silently
   dropped — the `testFull` vs `test` trap).

## 7. PR + merge
- Commit in logical chunks on `more-improvements` (plugins+properties / build.sbt / multiarch-resources /
  CI). `re-scale git` for commits.
- Open a PR (`gh pr create`) from `more-improvements` → base per repo process; let CI verify across the
  6 JVM + JS + 5 native runners.
- **Do NOT merge** until CI is green AND you confirm. The handoff says only the ssg agent merges, per
  repo process — I'll surface the green PR for your go-ahead.

## 8. Risk points (flagged for execution)
- New `Aliases` API in sbt-kubuszok 0.2.3 (usefulTasks gone) — discover signature.
- `MultiArchResourcesPlugin.embeddedResourcesSettings` signature + whether it covers in-repo test
  resources (ISS-979 suite).
- sbt-scalafix sbt-2.0 availability.
- multiarch native host-unfilter double-build / provider wiring on ssg-highlight.
- `Def.uncached` / `HashWriter` errors surfacing only at first sbt-2.0 load — iterate compile-fix.
- Snapshot resolution (hearth/kindlings) — confirm exact published snapshot strings.

## Execution method (after approval)
Per your pick: a heavily-briefed implementer agent does the iterative compile-fix grind to green
`compile`+`testFull` on all 3 platforms (no commit), I review the diff + re-run §6 gates + commit +
open the PR — OR I drive it directly. (To be chosen on approval.)
