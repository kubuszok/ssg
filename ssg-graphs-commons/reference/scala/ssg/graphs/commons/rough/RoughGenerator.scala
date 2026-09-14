/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * roughjs generator (assembles renderer primitives into Drawables + serializes ops to
 * SVG path strings: line/rectangle/ellipse/circle/linearPath/arc/curve/polygon/path ->
 * Drawable; opsToPath/toPaths/fillSketch/_mergedShape/_o/_d) — Scala 3 port
 *
 * Original source: roughjs (src/generator.ts)
 * Original author: Preet Shihn
 * Original license: MIT
 * upstream-commit: 56a2762
 *
 * Migration notes:
 *   Renames: TS `class RoughGenerator` -> `class RoughGenerator`; the `static newSeed()`
 *     -> `object RoughGenerator.newSeed`. The renderer functions (`line`,
 *     `solidFillPolygon`, `patternFillPolygons`, `rectangle`, `ellipseWithParams`,
 *     `generateEllipseParams`, `linearPath`, `arc`, `patternFillArc`, `curve`, `svgPath`)
 *     are members of `object RoughRenderer` (Chip 6); `randomSeed` is `RoughMath.randomSeed`
 *     (Chip 4); `curveToBezier`/`pointsOnBezierCurves` are `CurveToBezier`/`PointsOnCurve`
 *     and `pointsOnPath` is `PointsOnPath` (Chip 2, package `rough.curve`); the const
 *     `NOS = 'none'` is preserved verbatim. `Op`/`OpSet`/`OpType`/`OpSetType`/`Drawable`/
 *     `PathInfo`/`ResolvedOptions`/`Options`/`Config` come from `Core.scala`; `Point` is the
 *     roughjs geometry `Point` (`Geometry.scala`).
 *   Convention (`Point` bridging in `curve`'s pattern-fill): roughjs's `curve` feeds
 *     points through points-on-curve (`curveToBezier`/`pointsOnBezierCurves`), whose `Point`
 *     is the SEPARATE Chip 2 `rough.curve.Point`. The geometry `Point` and the curve `Point`
 *     are both `[number, number]` tuples in TS (interchangeable); here they are distinct
 *     `case class`es, so the points are converted at the boundary (`toCurvePoint`/
 *     `toGeomPoint`).
 *   Convention (`Point[]`/`OpSet[]` accumulators): `const paths = []` then `paths.push(...)`
 *     -> a mutable `ArrayBuffer[OpSet]` frozen with `.toVector`, matching the original push
 *     order. `[[x, y], ...]` point literals -> `Vector(Point(x, y), ...)`.
 *   Idiom (`options?: Options` parameter + `_o`): TS `_o(options) = options ?
 *     Object.assign({}, this.defaultOptions, options) : this.defaultOptions`. The optional
 *     parameter -> `Option[Options]`; `_o(None)` returns the SHARED `defaultOptions`
 *     instance (the `: this.defaultOptions` arm — a genuine roughjs aliasing: a no-options
 *     render therefore mutates `defaultOptions.randomizer` in place, exactly as JS does),
 *     while `_o(Some(o))` builds a FRESH merged `ResolvedOptions` via `mergeOptions`.
 *   Idiom (`Object.assign({}, defaultOptions, options)` merge semantics): each `Options`
 *     field that is `Some` overrides the corresponding `defaultOptions` field; `None`
 *     (= a TS optional key left unset, i.e. not own-enumerable) keeps the default. The
 *     required `ResolvedOptions` fields use `getOrElse`; the seven that stay `Option`
 *     (`fill`/`simplification`/`strokeLineDash`/`strokeLineDashOffset`/`fillLineDash`/
 *     `fillLineDashOffset`/`fixedDecimalPlaceDigits`) use `orElse`; `randomizer` is carried
 *     by `copy` (JS copies it from `defaultOptions` too — so a merge inherits any live
 *     randomizer the default already holds). The TS distinction between "key absent" and
 *     "key explicitly === undefined" is not representable with `Option` (both -> `None`);
 *     roughjs never passes an explicit `undefined`, so this is faithful for real callers.
 *   Idiom (`_d`'s `sets || []` / `options || this.defaultOptions`): both arguments are
 *     always supplied non-null at every call site, so the JS null-guards never fire; ported
 *     as a direct `Drawable(shape, options, sets)` (`Drawable`'s field order is
 *     shape/options/sets).
 *   Idiom (JS truthiness on the fill/stroke guards): `if (o.fill)` (a string) is truthy iff
 *     defined and non-empty -> `fillTruthy(o)`. `o.stroke !== NOS` is `o.stroke != NOS`
 *     (stroke is a required `String`). `o.fill && o.fill !== NOS` (curve) and
 *     `o.fill && o.fill !== 'transparent' && o.fill !== NOS` (path) add the string-equality
 *     conjuncts. `o.roughness ? ... : 0` and `o.simplification && o.simplification < 1` use
 *     `numTruthy` (a number is JS-falsy iff `0` or `NaN`).
 *   Idiom (`circle`/`arc`/`curve`/`path` mutation of returned objects): JS mutates the
 *     returned object in place (`ret.shape = 'circle'`, `shape.type = 'fillPath'`,
 *     `fillOptions.disableMultiStroke = true`). `Drawable`/`OpSet`/`ResolvedOptions` are
 *     immutable `case class`es, so these become `.copy(...)`. `circle` =
 *     `ellipse(...).copy(shape = "circle")`.
 *   Idiom (`ellipse` solid fill calls `ellipseWithParams` TWICE): the response (stroke) and
 *     the solid shape are each a separate `ellipseWithParams` call sharing the SAME
 *     `ellipseParams` — the second call ADVANCES the RNG past the first, so the fillPath and
 *     stroke op-sets differ. Both calls are preserved verbatim (dropping one would shift the
 *     RNG sequence). The shared-randomizer threading (`fillOptions = {...o}` /
 *     `o.copy(...)`) keeps the same advancing `Random` instance.
 *   Idiom (`curve`'s `Point[] | Point[][]` overload): same discriminator as
 *     `RoughRenderer.curve` — `typeof p1[0] === 'number'` (single point list) vs object
 *     (list of lists), reproduced by matching the runtime type of `inputPoints.head`.
 *   Idiom (`opsToPath` number formatting): the template literals embed `${number}`, which
 *     is ECMA-262 Number::toString (6.1.6.1.20); ported as `numToString` (the same
 *     algorithm ssg-js's `JsNumber` implements — replicated here because
 *     `ssg-graphs-commons` cannot depend on `ssg-js`). `+d.toFixed(fixedDecimals)` rounds
 *     via the project's ECMA-262-correct `FormatUtil.toFixed` (round-half-away-from-zero)
 *     and re-parses to `Double`. `fixedDecimals?: number` -> `Option[Double]`; the guard
 *     `(typeof fixedDecimals === 'number') && fixedDecimals >= 0` -> `Some(fd) if fd >= 0`;
 *     `d.toFixed(fixedDecimals)` coerces the digit count to an integer -> `fd.toInt`.
 *   Idiom (`path`'s regex chain — UPSTREAM BUG REPLICATED): `d.replace(/\n/g, ' ')`
 *     .replace(/(-\s)/g, '-').replace('/(\s\s)/g', ' ')`. The first two are real global
 *     regex replaces (`\n`->space; `-`+whitespace->`-`). The THIRD argument is a STRING
 *     literal, NOT a regex: the JS string `'/(\s\s)/g'` evaluates to `"/(ss)/g"` (the
 *     unrecognized `\s` escapes collapse to `s`), so `.replace("/(ss)/g", " ")` is a
 *     literal-substring replace that never matches an SVG path -> a NO-OP. This is a genuine
 *     roughjs bug (the author meant a regex); it is ported faithfully (the literal-string
 *     replace is preserved; it changes nothing). `String.replace` here is a replace-ALL
 *     while JS `String.prototype.replace(string)` is replace-FIRST, but since `/(ss)/g`
 *     never occurs the distinction is unobservable. The `\n` replace uses `String.replace`
 *     (literal newline, global); the `(-\s)` replace uses a `Regex` (`\s`/capture-group are
 *     re2/JS-safe — see cross-platform-regex.md).
 *   Idiom (`path`'s `sets: Point[][]`): `pointsOnPath` returns `rough.curve.Point` sets;
 *     they are converted to geometry `Point` before feeding the renderer fills/linearPath.
 *   Idiom (`toPaths`'s `path: PathInfo | null`): -> `Nullable[PathInfo]`; the `switch` over
 *     `OpSetType` is exhaustive (3 cases) so the value is always assigned, but the
 *     `if (path) paths.push(path)` guard is preserved via `Nullable.foreach`. `o.fill || NOS`
 *     -> `o.fill.filter(_.nonEmpty).getOrElse(NOS)`.
 *   Idiom (control flow): no `return` — `path`'s early `if (!d) return _d(...)` is an
 *     if/else expression; no `null`/`orNull`; no blanket catch. `final` on the helper field;
 *     braces throughout. Original comments preserved.
 */
package ssg
package graphs
package commons
package rough

import lowlevel.Nullable

import scala.collection.mutable.ArrayBuffer

import ssg.graphs.commons.rough.curve.{ CurveToBezier, Point as CurvePoint, PointsOnCurve, PointsOnPath }
import ssg.graphs.commons.util.FormatUtil

/** The `none` paint constant. Port of the module-level `const NOS = 'none'`. */
final val NOS: String = "none"

/** roughjs generator: assembles renderer primitives into `Drawable`s and serializes ops to SVG path strings (port of `generator.ts`).
  *
  * @param config
  *   the generator configuration; `config.options`, if present, seeds `defaultOptions`. Port of `constructor(config?: Config)` (`this.config = config || {}` -> the `Config()` default). The TS
  *   `private config` field is never read after construction, so it is not stored.
  */
final class RoughGenerator(config: Config = Config()) {

  /** Port of the `defaultOptions: ResolvedOptions` field. Every value is pinned exactly to upstream. A `var` because the constructor reassigns it when `config.options` is present (and because
    * `_o(None)` returns this shared instance, whose `randomizer` a render mutates in place).
    */
  var defaultOptions: ResolvedOptions = ResolvedOptions(
    maxRandomnessOffset = 2,
    roughness = 1,
    bowing = 1,
    stroke = "#000",
    strokeWidth = 1,
    curveTightness = 0,
    curveFitting = 0.95,
    curveStepCount = 9,
    fillStyle = "hachure",
    fillWeight = -1,
    hachureAngle = -41,
    hachureGap = -1,
    dashOffset = -1,
    dashGap = -1,
    zigzagOffset = -1,
    seed = 0,
    disableMultiStroke = false,
    disableMultiStrokeFill = false,
    preserveVertices = false,
    fillShapeRoughnessGain = 0.8
  )

  // constructor body: `if (this.config.options) this.defaultOptions = this._o(this.config.options)`
  if (config.options.isDefined) {
    defaultOptions = _o(config.options)
  }

  /** Port of `_o(options?)`: merge user `Options` over `defaultOptions`, or return the shared `defaultOptions` when no options are supplied.
    */
  private def _o(options: Option[Options]): ResolvedOptions =
    options match {
      case Some(o) => mergeOptions(defaultOptions, o)
      case None    => defaultOptions
    }

  /** Port of `Object.assign({}, defaultOptions, options)`: each set (`Some`) `Options` field overrides the corresponding `base` field. */
  private def mergeOptions(base: ResolvedOptions, o: Options): ResolvedOptions =
    base.copy(
      maxRandomnessOffset = o.maxRandomnessOffset.getOrElse(base.maxRandomnessOffset),
      roughness = o.roughness.getOrElse(base.roughness),
      bowing = o.bowing.getOrElse(base.bowing),
      stroke = o.stroke.getOrElse(base.stroke),
      strokeWidth = o.strokeWidth.getOrElse(base.strokeWidth),
      curveFitting = o.curveFitting.getOrElse(base.curveFitting),
      curveTightness = o.curveTightness.getOrElse(base.curveTightness),
      curveStepCount = o.curveStepCount.getOrElse(base.curveStepCount),
      fillStyle = o.fillStyle.getOrElse(base.fillStyle),
      fillWeight = o.fillWeight.getOrElse(base.fillWeight),
      hachureAngle = o.hachureAngle.getOrElse(base.hachureAngle),
      hachureGap = o.hachureGap.getOrElse(base.hachureGap),
      dashOffset = o.dashOffset.getOrElse(base.dashOffset),
      dashGap = o.dashGap.getOrElse(base.dashGap),
      zigzagOffset = o.zigzagOffset.getOrElse(base.zigzagOffset),
      seed = o.seed.getOrElse(base.seed),
      disableMultiStroke = o.disableMultiStroke.getOrElse(base.disableMultiStroke),
      disableMultiStrokeFill = o.disableMultiStrokeFill.getOrElse(base.disableMultiStrokeFill),
      preserveVertices = o.preserveVertices.getOrElse(base.preserveVertices),
      fillShapeRoughnessGain = o.fillShapeRoughnessGain.getOrElse(base.fillShapeRoughnessGain),
      fill = o.fill.orElse(base.fill),
      simplification = o.simplification.orElse(base.simplification),
      strokeLineDash = o.strokeLineDash.orElse(base.strokeLineDash),
      strokeLineDashOffset = o.strokeLineDashOffset.orElse(base.strokeLineDashOffset),
      fillLineDash = o.fillLineDash.orElse(base.fillLineDash),
      fillLineDashOffset = o.fillLineDashOffset.orElse(base.fillLineDashOffset),
      fixedDecimalPlaceDigits = o.fixedDecimalPlaceDigits.orElse(base.fixedDecimalPlaceDigits)
    )

  /** Port of `_d(shape, sets, options)`. */
  private def _d(shape: String, sets: Vector[OpSet], options: ResolvedOptions): Drawable =
    Drawable(shape = shape, sets = sets, options = options)

  /** Port of `line(x1, y1, x2, y2, options?)`. */
  def line(x1: Double, y1: Double, x2: Double, y2: Double, options: Option[Options] = None): Drawable = {
    val o: ResolvedOptions = _o(options)
    _d("line", Vector(RoughRenderer.line(x1, y1, x2, y2, o)), o)
  }

  /** Port of `rectangle(x, y, width, height, options?)`. */
  def rectangle(x: Double, y: Double, width: Double, height: Double, options: Option[Options] = None): Drawable = {
    val o:       ResolvedOptions    = _o(options)
    val paths:   ArrayBuffer[OpSet] = ArrayBuffer.empty
    val outline: OpSet              = RoughRenderer.rectangle(x, y, width, height, o)
    if (fillTruthy(o)) {
      val points: Vector[Point] = Vector(Point(x, y), Point(x + width, y), Point(x + width, y + height), Point(x, y + height))
      if (o.fillStyle == "solid") {
        paths += RoughRenderer.solidFillPolygon(Vector(points), o)
      } else {
        paths += RoughRenderer.patternFillPolygons(Vector(points), o)
      }
    }
    if (o.stroke != NOS) {
      paths += outline
    }
    _d("rectangle", paths.toVector, o)
  }

  /** Port of `ellipse(x, y, width, height, options?)`. */
  def ellipse(x: Double, y: Double, width: Double, height: Double, options: Option[Options] = None): Drawable = {
    val o:               ResolvedOptions    = _o(options)
    val paths:           ArrayBuffer[OpSet] = ArrayBuffer.empty
    val ellipseParams:   EllipseParams      = RoughRenderer.generateEllipseParams(width, height, o)
    val ellipseResponse: EllipseResult      = RoughRenderer.ellipseWithParams(x, y, o, ellipseParams)
    if (fillTruthy(o)) {
      if (o.fillStyle == "solid") {
        // a SECOND ellipseWithParams call (advances the RNG past the response); type -> fillPath
        val shape: OpSet = RoughRenderer.ellipseWithParams(x, y, o, ellipseParams).opset.copy(`type` = OpSetType.fillPath)
        paths += shape
      } else {
        paths += RoughRenderer.patternFillPolygons(Vector(ellipseResponse.estimatedPoints), o)
      }
    }
    if (o.stroke != NOS) {
      paths += ellipseResponse.opset
    }
    _d("ellipse", paths.toVector, o)
  }

  /** Port of `circle(x, y, diameter, options?)`. */
  def circle(x: Double, y: Double, diameter: Double, options: Option[Options] = None): Drawable = {
    val ret: Drawable = this.ellipse(x, y, diameter, diameter, options)
    ret.copy(shape = "circle")
  }

  /** Port of `linearPath(points, options?)`. */
  def linearPath(points: Vector[Point], options: Option[Options] = None): Drawable = {
    val o: ResolvedOptions = _o(options)
    _d("linearPath", Vector(RoughRenderer.linearPath(points, false, o)), o)
  }

  /** Port of `arc(x, y, width, height, start, stop, closed = false, options?)`. */
  def arc(x: Double, y: Double, width: Double, height: Double, start: Double, stop: Double, closed: Boolean = false, options: Option[Options] = None): Drawable = {
    val o:       ResolvedOptions    = _o(options)
    val paths:   ArrayBuffer[OpSet] = ArrayBuffer.empty
    val outline: OpSet              = RoughRenderer.arc(x, y, width, height, start, stop, closed, true, o)
    if (closed && fillTruthy(o)) {
      if (o.fillStyle == "solid") {
        val fillOptions: ResolvedOptions = o.copy(disableMultiStroke = true)
        val shape:       OpSet           = RoughRenderer.arc(x, y, width, height, start, stop, true, false, fillOptions).copy(`type` = OpSetType.fillPath)
        paths += shape
      } else {
        paths += RoughRenderer.patternFillArc(x, y, width, height, start, stop, o)
      }
    }
    if (o.stroke != NOS) {
      paths += outline
    }
    _d("arc", paths.toVector, o)
  }

  /** Port of `curve(points, options?)` (the `Point[] | Point[][]` overload). */
  def curve(points: Vector[Point] | Vector[Vector[Point]], options: Option[Options] = None): Drawable = {
    val o:       ResolvedOptions    = _o(options)
    val paths:   ArrayBuffer[OpSet] = ArrayBuffer.empty
    val outline: OpSet              = RoughRenderer.curve(points, o)
    if (fillTruthy(o) && o.fill.exists(_ != NOS)) {
      if (o.fillStyle == "solid") {
        val fillShape: OpSet = RoughRenderer.curve(
          points,
          o.copy(
            disableMultiStroke = true,
            roughness = if (numTruthy(o.roughness)) o.roughness + o.fillShapeRoughnessGain else 0
          )
        )
        paths += OpSet(`type` = OpSetType.fillPath, ops = _mergedShape(fillShape.ops))
      } else {
        val polyPoints:  ArrayBuffer[Point]                    = ArrayBuffer.empty
        val inputPoints: Vector[Point] | Vector[Vector[Point]] = points
        val ip:          Vector[Any]                           = inputPoints
        if (ip.nonEmpty) {
          val p1:         Any                   = ip.head
          val pointsList: Vector[Vector[Point]] = p1 match {
            case _: Point => Vector(inputPoints.asInstanceOf[Vector[Point]])
            case _ => inputPoints.asInstanceOf[Vector[Vector[Point]]]
          }
          for (pts <- pointsList)
            if (pts.length < 3) {
              polyPoints ++= pts
            } else if (pts.length == 3) {
              polyPoints ++= bezierPolyPoints(Vector(pts(0), pts(0), pts(1), pts(2)), o.roughness)
            } else {
              polyPoints ++= bezierPolyPoints(pts, o.roughness)
            }
        }
        if (polyPoints.nonEmpty) {
          paths += RoughRenderer.patternFillPolygons(Vector(polyPoints.toVector), o)
        }
      }
    }
    if (o.stroke != NOS) {
      paths += outline
    }
    _d("curve", paths.toVector, o)
  }

  /** Port of `polygon(points, options?)`. */
  def polygon(points: Vector[Point], options: Option[Options] = None): Drawable = {
    val o:       ResolvedOptions    = _o(options)
    val paths:   ArrayBuffer[OpSet] = ArrayBuffer.empty
    val outline: OpSet              = RoughRenderer.linearPath(points, true, o)
    if (fillTruthy(o)) {
      if (o.fillStyle == "solid") {
        paths += RoughRenderer.solidFillPolygon(Vector(points), o)
      } else {
        paths += RoughRenderer.patternFillPolygons(Vector(points), o)
      }
    }
    if (o.stroke != NOS) {
      paths += outline
    }
    _d("polygon", paths.toVector, o)
  }

  /** Port of `path(d, options?)`. */
  def path(d: String, options: Option[Options] = None): Drawable = {
    val o:     ResolvedOptions    = _o(options)
    val paths: ArrayBuffer[OpSet] = ArrayBuffer.empty
    if (d.isEmpty) {
      _d("path", paths.toVector, o)
    } else {
      // d.replace(/\n/g, ' ').replace(/(-\s)/g, '-').replace('/(\s\s)/g', ' ')
      // The third replace takes the STRING "/(ss)/g" (JS '/(\s\s)/g'), NOT a regex -> a
      // literal-substring replace that never matches a path: an upstream NO-OP bug.
      val cleaned: String = MinusSpacePattern.replaceAllIn(d.replace("\n", " "), "-").replace("/(ss)/g", " ")

      val hasFill:    Boolean = o.fill.exists(s => s.nonEmpty && s != "transparent" && s != NOS)
      val hasStroke:  Boolean = o.stroke != NOS
      val simplified: Boolean = o.simplification.exists(s => numTruthy(s) && (s < 1))
      val distance:   Double  =
        if (simplified) 4 - 4 * o.simplification.filter(numTruthy).getOrElse(1.0)
        else (1 + o.roughness) / 2
      val sets:  Vector[Vector[Point]] = PointsOnPath.pointsOnPath(cleaned, Some(1.0), Some(distance)).map(_.map(toGeomPoint))
      val shape: OpSet                 = RoughRenderer.svgPath(cleaned, o)

      if (hasFill) {
        if (o.fillStyle == "solid") {
          if (sets.length == 1) {
            val fillShape: OpSet = RoughRenderer.svgPath(
              cleaned,
              o.copy(
                disableMultiStroke = true,
                roughness = if (numTruthy(o.roughness)) o.roughness + o.fillShapeRoughnessGain else 0
              )
            )
            paths += OpSet(`type` = OpSetType.fillPath, ops = _mergedShape(fillShape.ops))
          } else {
            paths += RoughRenderer.solidFillPolygon(sets, o)
          }
        } else {
          paths += RoughRenderer.patternFillPolygons(sets, o)
        }
      }
      if (hasStroke) {
        if (simplified) {
          for (set <- sets)
            paths += RoughRenderer.linearPath(set, false, o)
        } else {
          paths += shape
        }
      }

      _d("path", paths.toVector, o)
    }
  }

  /** Port of `opsToPath(drawing, fixedDecimals?)`. */
  def opsToPath(drawing: OpSet, fixedDecimals: Option[Double] = None): String = {
    val sb: StringBuilder = new StringBuilder
    for (item <- drawing.ops) {
      val data: Vector[Double] = fixedDecimals match {
        case Some(fd) if fd >= 0 => item.data.map(d => FormatUtil.toFixed(d, fd.toInt).toDouble)
        case _                   => item.data
      }
      item.op match {
        case OpType.move     => sb ++= s"M${RoughGenerator.numToString(data(0))} ${RoughGenerator.numToString(data(1))} "
        case OpType.bcurveTo =>
          sb ++= s"C${RoughGenerator.numToString(data(0))} ${RoughGenerator.numToString(data(1))}, ${RoughGenerator.numToString(data(2))} ${RoughGenerator.numToString(data(3))}, ${RoughGenerator.numToString(data(4))} ${RoughGenerator.numToString(data(5))} "
        case OpType.lineTo => sb ++= s"L${RoughGenerator.numToString(data(0))} ${RoughGenerator.numToString(data(1))} "
      }
    }
    sb.toString.trim
  }

  /** Port of `toPaths(drawable)`. */
  def toPaths(drawable: Drawable): Vector[PathInfo] = {
    val sets:  Vector[OpSet]         = drawable.sets
    val o:     ResolvedOptions       = drawable.options
    val paths: ArrayBuffer[PathInfo] = ArrayBuffer.empty
    for (drawing <- sets) {
      val path: Nullable[PathInfo] = drawing.`type` match {
        case OpSetType.path =>
          Nullable(PathInfo(d = opsToPath(drawing), stroke = o.stroke, strokeWidth = o.strokeWidth, fill = Some(NOS)))
        case OpSetType.fillPath =>
          Nullable(PathInfo(d = opsToPath(drawing), stroke = NOS, strokeWidth = 0, fill = Some(o.fill.filter(_.nonEmpty).getOrElse(NOS))))
        case OpSetType.fillSketch =>
          Nullable(fillSketch(drawing, o))
      }
      path.foreach(paths += _)
    }
    paths.toVector
  }

  /** Port of `fillSketch(drawing, o)`. */
  private def fillSketch(drawing: OpSet, o: ResolvedOptions): PathInfo = {
    var fweight: Double = o.fillWeight
    if (fweight < 0) {
      fweight = o.strokeWidth / 2
    }
    PathInfo(
      d = opsToPath(drawing),
      stroke = o.fill.filter(_.nonEmpty).getOrElse(NOS),
      strokeWidth = fweight,
      fill = Some(NOS)
    )
  }

  /** Port of `_mergedShape(input)`: keep index 0; drop later `move` ops; keep everything else. */
  private def _mergedShape(input: Vector[Op]): Vector[Op] =
    input.zipWithIndex.collect {
      case (d, i) if (i == 0) || (d.op != OpType.move) => d
    }

  // ---- private helpers ----

  /** The `(-\s)` global regex (re2/JS-safe: a literal `-` followed by `\s`, capture group). */
  private val MinusSpacePattern = "(-\\s)".r

  /** JS truthiness of `if (o.fill)` (a string): defined and non-empty. */
  private def fillTruthy(o: ResolvedOptions): Boolean =
    o.fill.exists(_.nonEmpty)

  /** JS number truthiness (`x ? : `): a number is falsy iff `0` or `NaN`. */
  private def numTruthy(d: Double): Boolean =
    d != 0.0 && !d.isNaN

  /** Bridge the roughjs geometry `Point` to the Chip 2 curve `Point`. */
  private def toCurvePoint(p: Point): CurvePoint =
    CurvePoint(p.x, p.y)

  /** Bridge the Chip 2 curve `Point` back to the roughjs geometry `Point`. */
  private def toGeomPoint(p: CurvePoint): Point =
    Point(p.x, p.y)

  /** Port of `pointsOnBezierCurves(curveToBezier(pts), 10, (1 + roughness) / 2)` with the `Point`-type bridging (geometry -> curve -> geometry). */
  private def bezierPolyPoints(pts: Vector[Point], roughness: Double): Vector[Point] =
    PointsOnCurve.pointsOnBezierCurves(CurveToBezier.curveToBezier(pts.map(toCurvePoint)), 10, Some((1 + roughness) / 2)).map(toGeomPoint)
}

/** Companion of [[RoughGenerator]]: the `static newSeed()` plus the ECMA-262 number serializer backing `opsToPath`. */
object RoughGenerator {

  /** Port of `static newSeed()`. */
  def newSeed(): Int =
    RoughMath.randomSeed()

  /** Format a `Double` exactly as the JS template literal `${number}` would (ECMA-262 Number::toString, 6.1.6.1.20). Replicated from ssg-js's `JsNumber.toJsString` because `ssg-graphs-commons` cannot
    * depend on `ssg-js`; the platform's shortest round-tripping decimal (`Double.toString`) supplies the digits, reformatted by the ECMA cases.
    */
  private[rough] def numToString(num: Double): String =
    if (num.isNaN) {
      "NaN"
    } else if (num == 0.0) {
      // +0 / -0
      "0"
    } else if (num < 0) {
      "-" + numToString(-num)
    } else if (num.isInfinite) {
      "Infinity"
    } else {
      // Parse the platform's shortest decimal representation to extract (s, n).
      val raw:                 String           = num.toString
      val lower:               String           = raw.replace('E', 'e')
      val eIdx:                Int              = lower.indexOf('e')
      val (mantissa, expPart): (String, String) =
        if (eIdx >= 0) (lower.substring(0, eIdx), lower.substring(eIdx + 1))
        else (lower, "")
      val dotIdx:                Int           = mantissa.indexOf('.')
      val (rawDigits, pointPos): (String, Int) =
        if (dotIdx >= 0) (mantissa.substring(0, dotIdx) + mantissa.substring(dotIdx + 1), dotIdx)
        else (mantissa, mantissa.length)
      val parsedExp: Int =
        if (expPart.isEmpty) 0
        else if (expPart.charAt(0) == '+') expPart.substring(1).toInt
        else expPart.toInt
      var start: Int = 0
      while (start < rawDigits.length && rawDigits.charAt(start) == '0')
        start += 1
      var end: Int = rawDigits.length
      while (end > start && rawDigits.charAt(end - 1) == '0')
        end -= 1
      val s: String = rawDigits.substring(start, end)
      val k: Int    = s.length
      val n: Int    = (pointPos - start) + parsedExp
      ecmaFormat(s, k, n)
    }

  /** Apply ECMA-262 NumberToString formatting given significant digits `s` (length `k`) and exponent `n` (value = `s * 10^(n-k)`). */
  private def ecmaFormat(s: String, k: Int, n: Int): String =
    if (k <= n && n <= 21) {
      s + ("0" * (n - k))
    } else if (0 < n && n <= 21) {
      s.substring(0, n) + "." + s.substring(n)
    } else if (-6 < n && n <= 0) {
      "0." + ("0" * (-n)) + s
    } else if (k == 1) {
      s + "e" + (if (n - 1 >= 0) "+" else "") + (n - 1)
    } else {
      s.charAt(0).toString + "." + s.substring(1) + "e" + (if (n - 1 >= 0) "+" else "") + (n - 1)
    }
}
