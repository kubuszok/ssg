/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * roughjs default-export entry (svg/generator/newSeed factory; canvas is platform-
 * inapplicable) — Scala 3 port
 *
 * Original source: roughjs (src/rough.ts)
 * Original author: Preet Shihn
 * Original license: MIT
 * upstream-commit: 56a2762
 *
 * Migration notes:
 *   Renames: the TS `export default { ... }` object literal (the `rough` namespace) ->
 *     `object Rough`. `rough.svg(...)` -> `Rough.svg(...)`, `rough.generator(...)` ->
 *     `Rough.generator(...)`, `rough.newSeed()` -> `Rough.newSeed()`.
 *   Dropped DOM `svg` param: `svg(svg: SVGSVGElement, config?)` -> `svg(config?)` — the DOM
 *     `SVGSVGElement` argument is dropped exactly as in `RoughSVG`'s ctor (see RoughSVG.scala;
 *     SSG has no live document to own).
 *   canvas.ts is PLATFORM-INAPPLICABLE (NOT a silent drop): the TS `canvas(canvas:
 *     HTMLCanvasElement, config?)` constructs a `RoughCanvas` (`canvas.ts`, 153 LOC) that
 *     renders into a `CanvasRenderingContext2D` — a browser-DOM imperative drawing surface.
 *     SSG is server-side and has NO canvas target: every SSG render path emits declarative
 *     SVG markup (`SvgElement`), and Mermaid's `look=handDrawn` (the sole ISS-1204 consumer)
 *     routes EXCLUSIVELY through `rough.svg()` — it never touches `rough.canvas`. So
 *     `canvas.ts` has no port and `RoughCanvas` does not exist in SSG. `Rough.canvas` is kept
 *     here as an explicit unsupported-operation signal that throws `UnsupportedOperationException` rather
 *     than being silently omitted, so a caller that mistakenly reaches for the canvas path
 *     gets a clear diagnostic instead of a missing-method compile error. The skip of
 *     `canvas.ts` is proposed for the skip-policy allow list (the orchestrator files it).
 *   Idiom: no `return`; the `throw` in `canvas` is an explicit unsupported-operation signal
 *     (not a swallowed/blanket catch). Braces throughout; original structure preserved.
 */
package ssg
package graphs
package commons
package rough

/** Signals that `Rough.canvas` was called: `canvas.ts` (DOM `CanvasRenderingContext2D`) is platform-inapplicable on SSG (see the migration notes). A dedicated exception type so the loud-failure
  * semantics read as a deliberate platform decision rather than an unfinished port.
  */
final class RoughCanvasUnsupported(message: String) extends RuntimeException(message)

/** roughjs entry point — the default-export `rough` namespace (port of `rough.ts`). */
object Rough {

  /** Port of `canvas(canvas: HTMLCanvasElement, config?)`. `canvas.ts`'s `RoughCanvas` renders to a DOM `CanvasRenderingContext2D`, which has no SSG analog (see the migration notes);
    * calling it is unsupported. Kept as an explicit platform-inapplicable signal (not a silent drop) so misuse fails loudly.
    */
  def canvas(): Nothing =
    throw new RoughCanvasUnsupported("rough.canvas is not supported on SSG (no DOM canvas; use rough.svg)")

  /** Port of `svg(svg, config?): RoughSVG` (the DOM `svg` param dropped). */
  def svg(config: Config = Config()): RoughSVG =
    new RoughSVG(config)

  /** Port of `generator(config?): RoughGenerator`. */
  def generator(config: Config = Config()): RoughGenerator =
    new RoughGenerator(config)

  /** Port of `newSeed(): number` -> `RoughGenerator.newSeed`. */
  def newSeed(): Int =
    RoughGenerator.newSeed()
}
