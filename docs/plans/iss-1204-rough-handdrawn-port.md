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
- [ ] **Chip 2 — points-on-curve + points-on-path** (177 + 69 LOC:
  `curve-to-bezier.ts`, points-on-curve `index.ts` (`pointsOnBezierCurves`,
  `simplify`), points-on-path `index.ts` (`pointsOnPath`)). points-on-path depends
  on Chip 1 + points-on-curve. → `rough/curve/`. Test: bezier sampling tolerance.
- [ ] **Chip 3 — hachure-fill** (173 LOC: `hachure.ts` `hachureLines`). Polygon
  scan-line hachure. No deps. → `rough/fillers/`.
- [ ] **Chip 4 — roughjs foundation** (`math.ts` Random PRNG + `randomSeed`,
  `geometry.ts` Point/Line/Rectangle/lineLength, `core.ts` Options/ResolvedOptions/
  Op/OpSet/Drawable/PathInfo/Config types + SVGNS). No deps. → `rough/`.
  **RNG faithfulness (critical):** `Random.next` is Lehmer/MINSTD —
  `(2**31-1) & Math.imul(48271, seed)) / 2**31`. Scala Int multiply already wraps to
  32-bit on all 3 platforms, so `48271 * seed` as `Int` == `Math.imul`; mask with
  `0x7FFFFFFF`, divide by `2147483648.0`. This must reproduce byte-for-byte or the
  differential test (Chip 9) can't pin output.
- [ ] **Chip 5 — roughjs fillers** (`fillers/filler-interface.ts`,
  `scan-line-hachure.ts` (wraps Chip 3 `hachureLines`), `hachure-filler.ts`,
  `zigzag-filler.ts`, `zigzag-line-filler.ts`, `dot-filler.ts`, `dashed-filler.ts`,
  `hatch-filler.ts`, `filler.ts` factory `getFiller`). Deps: Chip 3 + Chip 4.
  → `rough/fillers/`.
- [ ] **Chip 6 — roughjs renderer** (`renderer.ts` 533: `line`, `linearPath`,
  `polygon`, `rectangle`, `curve`, `ellipse`, `generateEllipseParams`,
  `ellipseWithParams`, `arc`, `svgPath`, `solidFillPolygon`, `patternFillPolygons`,
  `patternFillArc`, `randOffset`, `randOffsetWithRange`, `doubleLineFillOps`, and
  the private `_*` helpers). Deps: Chip 1 (`parsePath`/`normalize`/`absolutize`),
  Chip 4, Chip 5 (`getFiller`). The core sketch algorithm. → `rough/`.
- [ ] **Chip 7 — roughjs generator** (`generator.ts` 311: `RoughGenerator` —
  `line`/`rectangle`/`ellipse`/`circle`/`linearPath`/`polygon`/`arc`/`curve`/`path`
  → `Drawable`; `opsToPath`, `toPaths`, `fillSketch`, `_d`). Deps: Chip 2
  (`curveToBezier`/`pointsOnBezierCurves`/`pointsOnPath`), Chip 4, Chip 6. → `rough/`.
- [ ] **Chip 8 — roughjs SVG output + entry** (`svg.ts` `RoughSVG` (`draw`,
  `opsToPath`, `path`/`rectangle`/etc, `fillSketch`), `rough.ts` `rough.svg`/
  `generator`/`newSeed`). Emit SSG SVG markup (`commons/svg/SvgBuilder` etc.), NOT
  DOM `SVGElement`. Deps: Chip 7. → `rough/`.
  **canvas.ts (153 LOC) is platform-inapplicable** — it renders to
  `CanvasRenderingContext2D`; SSG has no DOM-canvas target and Mermaid `handDrawn`
  uses only `rough.svg()`. Propose a skip-policy entry (NOT a silent drop); the
  auditor/user confirm.
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
