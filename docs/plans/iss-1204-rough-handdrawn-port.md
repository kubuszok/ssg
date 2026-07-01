# ISS-1204 — rough.js `look=handDrawn` port plan

**Status:** vendoring DONE; porting NOT STARTED.
**Umbrella issue:** ISS-1204 (`[R0610-P1]`, incomplete-port, high). Resolved only
when the final chip (mermaid wiring + differential test) passes audit.

## Goal

Port rough.js (sketch/hand-drawn SVG rendering) so Mermaid's `look=handDrawn`
produces sketch-style SVG, matching upstream. Upstream wiring: `flowDb.ts:882/918`
copy `config.look` onto nodes; `clusters.js:66`, `edges.js:513`, `shapes/*.ts`
branch `if (node.look === "handDrawn")` into `rough.svg(...)` calls. SSG today has
no rough path — `FlowchartStyles.scala:193` `.rough-node` CSS is dead static text.

## Vendored source (committed: 07462c84 roughjs, 43ff6c7f transitive)

All MIT-licensed, pinned to the versions Mermaid resolves (`pnpm-lock.yaml`):

| submodule | version | pin | src LOC |
|-----------|---------|-----|---------|
| `original-src/roughjs` | 4.6.6 | `56a2762` (default HEAD = 4.6.6; repo stopped tagging at v3.1.0) | 1572 |
| `original-src/path-data-parser` | 0.1.0 | `93d3fa8` | 466 |
| `original-src/points-on-curve` | 0.2.0 | `4824147` (pinned back from HEAD 1.0.1; the 0.2.0 API roughjs imports) | 177 |
| `original-src/points-on-path` | 0.2.1 | `7693ef0` | 69 |
| `original-src/hachure-fill` | 0.5.2 | `80e47ba` | 173 |

Target package root: `ssg-graphs-commons/src/main/scala/ssg/graphs/commons/rough/`
(shared SVG infra; reused by ssg-mermaid and, later, ssg-graphviz). Mappings are
registered in CLAUDE.md "Source Reference".

## Dependency-ordered chips (leaves first)

Each chip is one §7 iteration: port-implementer (Opus 4.6) → port-auditor (Opus 4.8)
→ resolve. Port the chip's files 100% (every method/branch/edge case). A chip
PASSes only when it compiles on **all 3 platforms** (JVM/JS/Native) and its ported
unit tests pass. The orchestrator advances one chip per `/loop /goal` firing and
checks it off here.

- [x] **Chip 1 — path-data-parser** (466 LOC: `parser.ts` 112, `absolutize.ts` 111,
  `normalize.ts` 241, `index.ts` 2). SVG path `d` tokenizer + `parsePath` /
  `absolutize` / `normalize`. No deps. → `rough/pathdata/`. Test: round-trip known
  path strings from the parser's own behavior.
  **DONE 2026-06-30** (commit `9d3469f5`): `Parser`/`Absolutize`/`Normalize`/
  `PathDataParser`.scala + `PathDataParserIss1204Suite` (22 tests, JVM+JS+Native).
  Arc-to-bezier verified digit-exact vs Node oracle (1e-9 libm cushion). Audit PASS
  after 1 bounce (ISS-1355: added relative-c/q/s parity-distinguishing tests).
- [x] **Chip 2 — points-on-curve + points-on-path** (177 + 69 LOC:
  `curve-to-bezier.ts`, points-on-curve `index.ts` (`pointsOnBezierCurves`,
  `simplify`), points-on-path `index.ts` (`pointsOnPath`)). points-on-path depends
  on Chip 1 + points-on-curve. → `rough/curve/`. Test: bezier sampling tolerance.
  **DONE 2026-06-30** (commit `f3042901`): `PointsOnCurve`/`CurveToBezier`/
  `PointsOnPath`.scala + `PointsOnCurveIss1204Suite` (18 tests, JVM+JS+Native).
  Audit PASS after 1 bounce (ISS-1356): added flatness-max + dedup-band tests; the
  `d>1` dedup `>1→>0` flip adjudicated an **equivalent mutant** (de Casteljau `red`
  threaded by-ref → boundary `d≡0`; ~100M checks confirm), dedup pinned via
  guard-removal instead.
- [x] **Chip 3 — hachure-fill** (173 LOC: `hachure.ts` `hachureLines`). Polygon
  scan-line hachure. No deps. → `rough/fillers/`.
  **DONE 2026-06-30** (commit `3a33f2ba`): `HachureFill.scala` (AET scan-line fill,
  mutable `Point(var x,var y)` for in-place rotate) + `HachureFillIss1204Suite`
  (12 tests, JVM+JS+Native). **Audit PASS, NO bounce** (first chip clean — implementer
  ran its own 6-mutation battery; auditor added 2 more, all 8 caught). 2 equivalent
  mutants adjudicated (dropped JS falsy-x single-vs-list artifact; `edges.sort` x/ymax
  tiebreak unobservable — 20k-case refutation failed). Self-mutation-battery brief
  requirement is what avoided the bounce.
- [x] **Chip 4 — roughjs foundation** (`math.ts` Random PRNG + `randomSeed`,
  `geometry.ts` Point/Line/Rectangle/lineLength, `core.ts` Options/ResolvedOptions/
  Op/OpSet/Drawable/PathInfo/Config types + SVGNS). No deps. → `rough/`.
  **RNG faithfulness (critical):** `Random.next` is Lehmer/MINSTD —
  `(2**31-1) & Math.imul(48271, seed)) / 2**31`. Scala Int multiply already wraps to
  32-bit on all 3 platforms, so `48271 * seed` as `Int` == `Math.imul`; mask with
  `0x7FFFFFFF`, divide by `2147483648.0`. This must reproduce byte-for-byte or the
  differential test (Chip 9) can't pin output.
  **DONE 2026-06-30** (commit `f3a09843`): `RoughMath`/`Geometry`/`Core`.scala +
  `RoughFoundationIss1204Suite` (24 tests, JVM+JS+Native; RNG sequences byte-exact).
  Audit PASS after 1 bounce (ISS-1357): the "mask stored seed" optimization is NOT
  equivalent — `seed=Int.MinValue` is a fixed point where the faithful mask-only-return
  form gives deterministic 0 but the masked-stored mutant diverges to Math.random();
  added the Int.MinValue det-0 test + a randomSeed 2^31-scale test. Faithful form was
  always correct; only test coverage + a comment were off. canvas-only `SVGAnimatedLength`
  → Double; Math.random fallback (seed==0 path) non-deterministic, documented.
- [x] **Chip 5 — roughjs fillers** (`fillers/filler-interface.ts`,
  `scan-line-hachure.ts` (wraps Chip 3 `hachureLines`), `hachure-filler.ts`,
  `zigzag-filler.ts`, `zigzag-line-filler.ts`, `dot-filler.ts`, `dashed-filler.ts`,
  `hatch-filler.ts`, `filler.ts` factory `getFiller`). Deps: Chip 3 + Chip 4.
  → `rough/fillers/`.
  **DONE 2026-06-30** (commit `3c36637b`): 9 filler files (PatternFiller/RenderHelper
  traits, HachureFiller base + ZigZag/Hatch extends, getFiller stateful cache quirk) +
  `FillersIss1204Suite` (14 tests, JVM+JS+Native; deterministic stub RenderHelper since
  renderer=Chip 6). Audit PASS after 1 bounce (ISS-1358): the `roughness>=1` skipOffset
  branch had zero coverage (all tests roughness=0) — added seeded tests (Random(82303)
  next=0.85>0.7) pinning skipOffset=gap + the `skipOffset||1` guard (M10 = non-term at
  gap→0). Math.random non-determinism (dot jitter, scan-line randomizer||rand falsy-0)
  + getFiller first-helper-wins cache documented + pinned. RenderHelper trait defined
  here; renderer impl is Chip 6.
- [x] **Chip 6 — roughjs renderer** (`renderer.ts` 533: `line`, `linearPath`,
  `polygon`, `rectangle`, `curve`, `ellipse`, `generateEllipseParams`,
  `ellipseWithParams`, `arc`, `svgPath`, `solidFillPolygon`, `patternFillPolygons`,
  `patternFillArc`, `randOffset`, `randOffsetWithRange`, `doubleLineFillOps`, and
  the private `_*` helpers). Deps: Chip 1 (`parsePath`/`normalize`/`absolutize`),
  Chip 4, Chip 5 (`getFiller`). The core sketch algorithm. → `rough/`.
  **DONE 2026-06-30** (commit `62dbd710`): `RoughRenderer.scala` (object, all 27
  fns + `helper` RenderHelper) + `RoughRendererIss1204Suite` (36 tests, JVM+JS+Native;
  seeded jitter BYTE-IDENTICAL cross-platform — the RNG byte-exactness proof). Required
  `Core.scala` `randomizer` val→var (RNG must persist+advance across a render). RNG draw
  ORDER/COUNT preserved via left-to-right eval; 4 OpSets independently Node-re-derived by
  auditor. Audit PASS after 1 bounce (ISS-1359: arc/patternFillArc `>2π` clamp untested —
  added span>2π tests). Impl's own battery self-caught an equivalent-under-curveTightness=0
  mutant. canvas.ts still pending (Chip 8 skip-policy).
- [x] **Chip 7 — roughjs generator** (`generator.ts` 311: `RoughGenerator` —
  `line`/`rectangle`/`ellipse`/`circle`/`linearPath`/`polygon`/`arc`/`curve`/`path`
  → `Drawable`; `opsToPath`, `toPaths`, `fillSketch`, `_d`). Deps: Chip 2
  (`curveToBezier`/`pointsOnBezierCurves`/`pointsOnPath`), Chip 4, Chip 6. → `rough/`.
  **DONE 2026-07-01** (commit `81a0c65d`+cold-norm `d7b9c43d`): `RoughGenerator.scala`
  (18 members + `NOS`) + `RoughGeneratorIss1204Suite` (35 tests, JVM+JS+Native).
  **Audit PASS, NO bounce** (2nd clean chip): all 20 defaultOptions byte-exact; `numToString`
  (ECMA-262, replicated locally) verified vs 250k random doubles (0 mismatch); the `path()`
  3rd-replace BUG (`'/(\s\s)/g'`→literal `"/(ss)/g"` no-op) replicated faithfully; hachure
  fill cases pinned cache-independently (161/161 full Iss1204 set); 13 mutations all caught.
- [x] **Chip 8 — roughjs SVG output + entry** (`svg.ts` `RoughSVG` (`draw`,
  `opsToPath`, `path`/`rectangle`/etc, `fillSketch`), `rough.ts` `rough.svg`/
  `generator`/`newSeed`). Emit SSG SVG markup (`commons/svg/SvgBuilder` etc.), NOT
  DOM `SVGElement`. Deps: Chip 7. → `rough/`.
  **canvas.ts (153 LOC) is platform-inapplicable** — it renders to
  `CanvasRenderingContext2D`; SSG has no DOM-canvas target and Mermaid `handDrawn`
  uses only `rough.svg()`. Propose a skip-policy entry (NOT a silent drop); the
  auditor/user confirm.
  **DONE 2026-07-01** (commit `df5422af`+cold-norm): `RoughSVG.scala` (DOM→SSG
  `SvgElement` adaptation: createElementNS→`SvgElement.g()`, setAttribute→`withAttr`,
  appendChild→`withChild`; returns `SvgElement`) + `Rough.scala` (entry: svg/generator/
  newSeed; `canvas` = loud `RoughCanvasUnsupported` throw) + `RoughSvgIss1204Suite`
  (41 tests, JVM+JS+Native). Audit PASS after 1 bounce (ISS-1360: fixedDecimalPlaceDigits
  precision threading untested). **canvas.ts skip ADJUDICATED + skip-policy filed**
  (auditor grep-proved mermaid uses only `rough.svg(...)`, zero `rough.canvas`). Pre-audit
  gate also caught+fixed a ratchet regression (UnsupportedOperationException→custom exc).
- [ ] **Chip 9 — mermaid wiring + differential test (ISS-1204 acceptance).** Wire
  `look=handDrawn` through `FlowchartRenderer` so nodes/edges/clusters route to
  `rough.svg(...)` (upstream `clusters.js:66`, `edges.js:513`, `shapes/*.ts`,
  `flowDb.ts:882/918`); replace the dead `.rough-node` CSS path with real rough
  output. Deps: Chip 8. **Differential test:** pin `seed` in `MermaidConfig`/options
  so rough output is deterministic, render a small flowchart with `look: handDrawn`,
  assert the emitted sketch-path `d`/`OpSet` matches values computed from the
  upstream renderer for the same seed. Resolve ISS-1204 on this chip's PASS.

## Notes

- No `original-src/roughjs` test fixtures exist; differential expectations for
  Chips 1–8 come from each module's own deterministic behavior (parser round-trips,
  seed-pinned RNG sequences, bezier tolerances), and for Chip 9 from the seeded
  upstream render.
- Cross-platform: no DOM, no `Math.random` in the hot path (seed-pinned), watch
  32-bit Int semantics in the PRNG, and re2/JS regex limits in the path tokenizer
  (`parser.ts`) — see `docs/contributing/cross-platform-regex.md`.

## Chip 9 decomposition (user: "full decomp, grind all" — 2026-07-01)

Chip 9 (the ISS-1204 acceptance) is a sub-campaign in ssg-mermaid. Ordered sub-chips,
one per /loop iteration (impl Opus 4.6 → auditor Opus 4.8; each faithful to upstream
`shapes/*.ts` / `edges.js` / `clusters.js`). ssg-mermaid depends on ssg-graphs-commons
(Chip 8 `Rough`/`RoughSVG` usable). handDrawnSeed default 0 (=random; tests pin non-zero).

- [x] **9a — config + look/seed plumbing + handDrawnShapeStyles**: add
  `MermaidConfig.handDrawnSeed: Int = 0`; add `look: String` + `handDrawnSeed: Int` to
  `ShapeConfig`; thread `config.look`/`config.handDrawnSeed` through `FlowchartRenderer`
  → every `ShapeConfig` (+ make available to edges/clusters). Port `handDrawnShapeStyles.ts`
  (`userNodeOverrides`/`solidStateFill` → the rough `Options` each shape passes). NO render
  change yet (shapes stay classic; look/seed just becomes available). Foundation for 9b+.
- [x] **9b — rect + roundedRect handDrawn** (`drawRect.ts`: `rc.path(createRoundedRectPathD)`
  / `rc.rectangle`). The `createRoundedRectPathD` helper + the look-branch in RectShape/
  RoundedRectShape → `Rough.svg().path/.rectangle`.
- [x] **9c — circle + ellipse + doublecircle handDrawn** (`circle.ts`/`ellipse.ts`/
  `doublecircle.ts`: `rc.circle`/`rc.ellipse`).
- [x] **9d — diamond/rhombus/question handDrawn** (`question.ts`/`diamond`: `rc.polygon`).
- [x] **9e — hexagon + trapezoid handDrawn** (`hexagon.ts`/`trapezoid.ts`: `rc.polygon`).
- [x] **9f — stadium + cylinder handDrawn** (`stadium.ts`/`cylinder.ts`: `rc.path`).
- [x] **9g — note + subroutine handDrawn** (`note.ts`/`subroutine.ts`: `rc.rectangle`+lines).
- [x] **9h — edges handDrawn** (`edges.js` ~513: `rc.path(lineFunction(points))` sketchy edge).
- [x] **9i — clusters/subgraphs handDrawn** (`clusters.js:66/224`: `rc.path(createRoundedRectPathD)`).
- [ ] **9j — differential test + activate `.rough-node` CSS (ISS-1204 ACCEPTANCE)**: seeded
  end-to-end flowchart with `look: handDrawn` → assert the emitted sketch SVG matches the
  upstream (mermaid) render for the same seed; wire the (currently dead) `.rough-node` CSS.
  **RESOLVES ISS-1204 on this sub-chip's PASS.**
