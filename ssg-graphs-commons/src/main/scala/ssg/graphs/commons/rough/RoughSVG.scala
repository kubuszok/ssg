/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * roughjs SVG output (builds an SVG `<g>` of `<path>` elements from a Drawable's op-sets:
 * draw/fillSketch/opsToPath/getDefaultOptions/generator + the shape methods
 * line/rectangle/ellipse/circle/linearPath/polygon/arc/curve/path) — Scala 3 port
 *
 * Original source: roughjs (src/svg.ts)
 * Original author: Preet Shihn
 * Original license: MIT
 * upstream-commit: 56a2762
 *
 * Migration notes:
 *   DOM -> SSG SVG-markup ADAPTATION (the heart of this chip): roughjs `RoughSVG` builds
 *     browser DOM (`SVGGElement`/`SVGPathElement` via `document.createElementNS`); SSG is
 *     server-side and emits SVG through the immutable `ssg.graphs.commons.svg.SvgElement`
 *     (a `final case class` with insertion-ordered attributes for deterministic output).
 *     The faithful element-by-element mapping is:
 *       - `doc.createElementNS(SVGNS, 'g')`    -> `SvgElement.g()`
 *       - `doc.createElementNS(SVGNS, 'path')` -> `SvgElement("path")`
 *       - `el.setAttribute(name, value)`       -> `el.withAttr(name, value)` (chained; the
 *         attribute INSERTION ORDER is preserved exactly so `.toMarkup` matches the order
 *         upstream sets attributes: d, stroke, stroke-width, fill, then the optional dash
 *         attrs)
 *       - `g.appendChild(path)`                -> `g.withChild(path)`
 *     `draw(drawable)` therefore returns an `SvgElement` (the `<g>`), and every shape
 *     method returns an `SvgElement` (the `<g>` from `draw`).
 *   Dropped DOM `svg` ctor param: the TS ctor is `constructor(svg: SVGSVGElement,
 *     config?: Config)` and `draw` reads `this.svg.ownerDocument || window.document` to get
 *     a `Document` for `createElementNS`. Both `SVGSVGElement` and `Document` are browser-DOM
 *     types with NO SSG analog (SSG never owns or mutates a live document; `SvgElement` is a
 *     pure value), and the only use of `this.svg` is to obtain that document. With the
 *     document gone, the param is dead. So the SSG ctor takes just `config?` and the
 *     `ownerDocument || window.document` lookup is dropped. (`canvas.ts`'s `RoughCanvas`, the
 *     only consumer of a real rendering surface, is platform-inapplicable; see `Rough.scala`.)
 *   Idiom (`draw`'s `sets || []` / `options || this.getDefaultOptions()`): `Drawable.sets`
 *     and `Drawable.options` are REQUIRED (non-`Option`) fields in `Core.scala`, so both
 *     JS null-guards are dead; ported as direct reads `drawable.sets` / `drawable.options`.
 *     `precision = drawable.options.fixedDecimalPlaceDigits` (an `Option[Double]`) is read
 *     separately to mirror upstream (it equals `o.fixedDecimalPlaceDigits`, but the two reads
 *     are kept distinct as in svg.ts: `path`/`fillPath` pass `precision`, `fillSketch` reads
 *     `o.fixedDecimalPlaceDigits`).
 *   Idiom (number -> string attrs): `o.strokeWidth + ''`, `fweight + ''`,
 *     `` `${o.strokeLineDashOffset}` ``, and each `strokeLineDash`/`fillLineDash` array
 *     element under `.join(' ')` are all ECMA-262 Number::toString coercions — ported via
 *     `RoughGenerator.numToString` (the same ECMA serializer Chip 7 uses), so an integral
 *     `1` renders `"1"` (not `"1.0"`), matching JS `String(n)`.
 *   Idiom (JS truthiness on the optional dash attrs): `if (o.strokeLineDash)` /
 *     `if (o.fillLineDash)` test an ARRAY reference — truthy iff the array is present (even an
 *     empty array is truthy: `[].join(' ').trim()` is `""`, still set) -> `Option.isDefined`
 *     (mapped to the joined value). `if (o.strokeLineDashOffset)` / `if (o.fillLineDashOffset)`
 *     test a NUMBER — falsy iff `0` or `NaN` (or absent) -> `.filter(v => v != 0 && !v.isNaN)`.
 *   Idiom (`o.fill || ''`): JS `||` yields `''` when `fill` is absent/null/empty-string ->
 *     `o.fill.filter(_.nonEmpty).getOrElse("")`. NOTE this differs from the generator's
 *     `toPaths`/`fillSketch` (which use `o.fill || NOS` -> `'none'`): svg.ts deliberately
 *     emits the EMPTY string for `fill`/`stroke` when no fill is set, so `draw` has its OWN
 *     attribute logic and does NOT delegate to `toPaths`.
 *   Idiom (`fillSketch`'s dropped `doc` param): the TS private helper is
 *     `fillSketch(doc: Document, drawing, o)`; `doc` is only used for `createElementNS`, so
 *     it is dropped along with the DOM document (see above) -> `fillSketch(drawing, o)`.
 *   Idiom (control flow): no `return` (the per-op-set element is built into an
 *     `Option[SvgElement]` and the `if (path) g.appendChild(path)` guard becomes
 *     `path.foreach(...)` accumulating into a `var g`); no `null` (the JS `let path = null`
 *     sentinel -> `Option[SvgElement]`); no blanket catch. `final` class; braces throughout.
 *     Original comments preserved.
 */
package ssg
package graphs
package commons
package rough

import ssg.graphs.commons.svg.SvgElement

/** roughjs SVG output: turns a `Drawable`'s op-sets into an SVG `<g>` of `<path>` elements (port of `svg.ts`'s `RoughSVG`).
  *
  * @param config
  *   the generator configuration (forwarded to the backing [[RoughGenerator]]). Port of `constructor(svg, config?)` with the DOM `svg: SVGSVGElement` param dropped (see the migration notes).
  */
final class RoughSVG(config: Config = Config()) {

  // this.gen = new RoughGenerator(config); (the `this.svg = svg` assignment is dropped — see notes)
  private val gen: RoughGenerator = new RoughGenerator(config)

  /** Port of `draw(drawable): SVGGElement` — build the `<g>` of `<path>` elements for the drawable's op-sets. Returns the `<g>` as an [[SvgElement]]. */
  def draw(drawable: Drawable): SvgElement = {
    val sets:      Vector[OpSet]   = drawable.sets
    val o:         ResolvedOptions = drawable.options
    // const doc = this.svg.ownerDocument || window.document; -> dropped (no DOM document; see notes)
    var g:         SvgElement      = SvgElement.g()
    val precision: Option[Double]  = drawable.options.fixedDecimalPlaceDigits
    for (drawing <- sets) {
      val path: Option[SvgElement] = drawing.`type` match {
        case OpSetType.path =>
          var p: SvgElement = SvgElement("path")
            .withAttr("d", opsToPath(drawing, precision))
            .withAttr("stroke", o.stroke)
            .withAttr("stroke-width", numStr(o.strokeWidth))
            .withAttr("fill", "none")
          o.strokeLineDash.foreach { dash =>
            p = p.withAttr("stroke-dasharray", dash.map(numStr).mkString(" ").trim)
          }
          o.strokeLineDashOffset.filter(numTruthy).foreach { off =>
            p = p.withAttr("stroke-dashoffset", numStr(off))
          }
          Some(p)
        case OpSetType.fillPath =>
          var p: SvgElement = SvgElement("path")
            .withAttr("d", opsToPath(drawing, precision))
            .withAttr("stroke", "none")
            .withAttr("stroke-width", "0")
            .withAttr("fill", o.fill.filter(_.nonEmpty).getOrElse(""))
          if (drawable.shape == "curve" || drawable.shape == "polygon") {
            p = p.withAttr("fill-rule", "evenodd")
          }
          Some(p)
        case OpSetType.fillSketch =>
          Some(fillSketch(drawing, o))
      }
      path.foreach { p =>
        g = g.withChild(p)
      }
    }
    g
  }

  /** Port of `private fillSketch(doc, drawing, o): SVGPathElement` (the DOM `doc` param dropped). */
  private def fillSketch(drawing: OpSet, o: ResolvedOptions): SvgElement = {
    var fweight: Double = o.fillWeight
    if (fweight < 0) {
      fweight = o.strokeWidth / 2
    }
    var p: SvgElement = SvgElement("path")
      .withAttr("d", opsToPath(drawing, o.fixedDecimalPlaceDigits))
      .withAttr("stroke", o.fill.filter(_.nonEmpty).getOrElse(""))
      .withAttr("stroke-width", numStr(fweight))
      .withAttr("fill", "none")
    o.fillLineDash.foreach { dash =>
      p = p.withAttr("stroke-dasharray", dash.map(numStr).mkString(" ").trim)
    }
    o.fillLineDashOffset.filter(numTruthy).foreach { off =>
      p = p.withAttr("stroke-dashoffset", numStr(off))
    }
    p
  }

  /** Port of `get generator(): RoughGenerator`. */
  def generator: RoughGenerator = gen

  /** Port of `getDefaultOptions(): ResolvedOptions`. */
  def getDefaultOptions(): ResolvedOptions = gen.defaultOptions

  /** Port of `opsToPath(drawing, fixedDecimalPlaceDigits?)` — delegates to the generator. */
  def opsToPath(drawing: OpSet, fixedDecimalPlaceDigits: Option[Double] = None): String =
    gen.opsToPath(drawing, fixedDecimalPlaceDigits)

  /** Port of `line(x1, y1, x2, y2, options?)`. */
  def line(x1: Double, y1: Double, x2: Double, y2: Double, options: Option[Options] = None): SvgElement = {
    val d: Drawable = gen.line(x1, y1, x2, y2, options)
    draw(d)
  }

  /** Port of `rectangle(x, y, width, height, options?)`. */
  def rectangle(x: Double, y: Double, width: Double, height: Double, options: Option[Options] = None): SvgElement = {
    val d: Drawable = gen.rectangle(x, y, width, height, options)
    draw(d)
  }

  /** Port of `ellipse(x, y, width, height, options?)`. */
  def ellipse(x: Double, y: Double, width: Double, height: Double, options: Option[Options] = None): SvgElement = {
    val d: Drawable = gen.ellipse(x, y, width, height, options)
    draw(d)
  }

  /** Port of `circle(x, y, diameter, options?)`. */
  def circle(x: Double, y: Double, diameter: Double, options: Option[Options] = None): SvgElement = {
    val d: Drawable = gen.circle(x, y, diameter, options)
    draw(d)
  }

  /** Port of `linearPath(points, options?)`. */
  def linearPath(points: Vector[Point], options: Option[Options] = None): SvgElement = {
    val d: Drawable = gen.linearPath(points, options)
    draw(d)
  }

  /** Port of `polygon(points, options?)`. */
  def polygon(points: Vector[Point], options: Option[Options] = None): SvgElement = {
    val d: Drawable = gen.polygon(points, options)
    draw(d)
  }

  /** Port of `arc(x, y, width, height, start, stop, closed = false, options?)`. */
  def arc(
    x:       Double,
    y:       Double,
    width:   Double,
    height:  Double,
    start:   Double,
    stop:    Double,
    closed:  Boolean = false,
    options: Option[Options] = None
  ): SvgElement = {
    val d: Drawable = gen.arc(x, y, width, height, start, stop, closed, options)
    draw(d)
  }

  /** Port of `curve(points: Point[] | Point[][], options?)`. */
  def curve(points: Vector[Point] | Vector[Vector[Point]], options: Option[Options] = None): SvgElement = {
    val d: Drawable = gen.curve(points, options)
    draw(d)
  }

  /** Port of `path(d, options?)`. */
  def path(d: String, options: Option[Options] = None): SvgElement = {
    val drawing: Drawable = gen.path(d, options)
    draw(drawing)
  }

  // ---- private helpers ----

  /** ECMA-262 Number::toString coercion (`number + ''` / `` `${number}` `` / array `.join`), reusing Chip 7's serializer. */
  private def numStr(d: Double): String =
    RoughGenerator.numToString(d)

  /** JS number truthiness (`if (n)` on a number): falsy iff `0` or `NaN`. */
  private def numTruthy(d: Double): Boolean =
    d != 0.0 && !d.isNaN
}
