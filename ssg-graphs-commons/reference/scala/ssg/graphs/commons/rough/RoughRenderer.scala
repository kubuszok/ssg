/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * roughjs renderer (the core sketch algorithm: line/path/polygon/rectangle/curve/
 * ellipse/arc/svgPath + the solid/pattern fills + the private `_*` jitter helpers) —
 * Scala 3 port
 *
 * Original source: roughjs (src/renderer.ts)
 * Original author: Preet Shihn
 * Original license: MIT
 * upstream-commit: 56a2762
 *
 * Migration notes:
 *   Renames: the module-level exported + private functions of `renderer.ts` ->
 *     members of `object RoughRenderer`. The module-private `interface EllipseParams`
 *     and the exported `interface EllipseResult` -> sibling `final case class`es in this
 *     package. `Point`/`Op`/`OpSet`/`OpType`/`OpSetType`/`ResolvedOptions`/`Random` are
 *     the roughjs types from `Geometry.scala`/`Core.scala`/`RoughMath.scala`;
 *     `getFiller` from `fillers/Filler.scala`; `RenderHelper` from
 *     `fillers/FillerInterface.scala`; `parsePath`/`absolutize`/`normalize` from
 *     `pathdata/PathDataParser.scala`.
 *   Convention: `[x, y]` -> `Point(x, y)`; `p[0]`/`p[1]` -> `p.x`/`p.y`. `Op[]`/`Point[]`
 *     -> immutable `Vector[Op]`/`Vector[Point]`; ops/points accumulated in a mutable
 *     `ArrayBuffer` then frozen with `.toVector` (matching the original's `push`/`concat`
 *     array mutation). `{ op: 'move', data: [...] }` -> `Op(OpType.move, Vector(...))`;
 *     `{ type: 'path', ops }` -> `OpSet(`type` = OpSetType.path, ops = ...)`.
 *   Convention (RNG threading, CRITICAL — the differential-test contract): `random(ops)`
 *     mutates `ops.randomizer` in place — first call lazily creates `new Random(ops.seed)`
 *     and stores it; every later call REUSES and ADVANCES that same instance. This
 *     requires `ResolvedOptions.randomizer` to be a `var` (the single authorized change to
 *     a committed Chip 1–5 file; see Core.scala's migration notes). The RNG call ORDER and
 *     COUNT match the TS exactly: JS array-literal/argument elements evaluate left-to-
 *     right, each `randomHalf()`/`randomFull()`/`_offsetOpt(...)`/`random(o)` advancing the
 *     shared RNG. Scala evaluates `Vector(...)` arguments and `Point(a, b)` arguments
 *     left-to-right too, so the original element order is preserved verbatim (locals are
 *     bound in source order where the draw sequence must be made explicit).
 *   Idiom (`ops.seed || 0` in `random`): the seed is an `Int`; the only JS-falsy Int is
 *     `0` (`0 || 0 == 0`), so `new Random(ops.seed || 0)` == `new Random(ops.seed)`.
 *   Idiom (`x || 0` / `x || 1` on a number = JS truthiness): a number is JS-falsy when it
 *     is `0` or `NaN`. Reproduced by `numTruthy(d) = d != 0.0 && !d.isNaN`, used for
 *     `o.maxRandomnessOffset || 0` (in `_line`/`solidFillPolygon`) and
 *     `o.maxRandomnessOffset || 1` (in `_bezierTo`).
 *   Idiom (`closePoint: Point | null`): `_curve`'s `closePoint` -> `Nullable[Point]`. The
 *     TS guard `closePoint && closePoint.length === 2` is `closePoint.isDefined` — a
 *     `Point` (the `[number, number]` tuple) always has length 2, so the `.length === 2`
 *     conjunct is constant-true and collapses to the non-null check. (In this renderer
 *     `_curve` is always called with `null`/`Nullable.empty`, so this arm is never reached,
 *     but it is ported for completeness.)
 *   Idiom (`curve`'s `Point[] | Point[][]` overload): ported as the Scala 3 union type
 *     `Vector[Point] | Vector[Vector[Point]]`. The TS discriminator
 *     `typeof p1[0] === 'number'` (where `p1 = inputPoints[0]`) distinguishes a single
 *     point list (whose head is a `Point`) from a list of lists (whose head is a
 *     `Vector[Point]`); reproduced by matching the runtime type of `inputPoints.head`
 *     (`case _: Point` => single list, wrapped as `[inputPoints]`; otherwise list of
 *     lists). The two `asInstanceOf` narrowings are unavoidable given JVM erasure and are
 *     guarded by that head-type check.
 *   Idiom (`_curve`'s dead `b[0]`): the TS computes `b[0] = [cachedVertArray[0],
 *     cachedVertArray[1]]` but only emits `b[1]`/`b[2]`/`b[3]`; `b[0]` is a dead store with
 *     no RNG side effect, so it is omitted (an unused local would trip `-Wunused`). The
 *     emitted control points `b[1]`/`b[2]`/`b[3]` are ported verbatim.
 *   Idiom (`_computeEllipsePoints` return `Point[][]`): the 2-element `[allPoints,
 *     corePoints]` array -> a Scala 2-tuple `(Vector[Point], Vector[Point])`; the TS
 *     destructurings `const [ap1, cp1] = ...` / `const [ap2] = ...` map to tuple access.
 *   Idiom (`const helper: RenderHelper = { ... }`): the module-level helper object that
 *     wires the fillers back into the renderer (`{ randOffset, randOffsetWithRange,
 *     ellipse, doubleLineOps: doubleLineFillOps }`) -> a `private val helper: RenderHelper`
 *     anonymous-class instance delegating to the corresponding `RoughRenderer` members.
 *   Idiom (control flow): no `return`; loops use `while`/`for`; the `switch` in `svgPath`
 *     (which `normalize` only ever feeds `M`/`L`/`C`/`Z`) keeps an explicit `case _ => ()`
 *     for the absent-default no-op. Comments ("// Fills", "// Private helpers") preserved.
 */
package ssg
package graphs
package commons
package rough

import lowlevel.Nullable

import scala.collection.mutable.ArrayBuffer

import fillers.{ Filler, RenderHelper }
import pathdata.PathDataParser

/** The ellipse-generation parameters. Port of the (module-private) `interface EllipseParams`. */
final case class EllipseParams(rx: Double, ry: Double, increment: Double)

/** The result of `ellipseWithParams`. Port of `export interface EllipseResult`. */
final case class EllipseResult(opset: OpSet, estimatedPoints: Vector[Point])

/** roughjs renderer: the core sketch algorithm (port of `renderer.ts`). */
object RoughRenderer {

  private val helper: RenderHelper = new RenderHelper {
    def randOffset(x:            Double, o:   ResolvedOptions):                                           Double     = RoughRenderer.randOffset(x, o)
    def randOffsetWithRange(min: Double, max: Double, o:     ResolvedOptions):                            Double     = RoughRenderer.randOffsetWithRange(min, max, o)
    def ellipse(x:               Double, y:   Double, width: Double, height: Double, o: ResolvedOptions): OpSet      = RoughRenderer.ellipse(x, y, width, height, o)
    def doubleLineOps(x1:        Double, y1:  Double, x2:    Double, y2:     Double, o: ResolvedOptions): Vector[Op] = RoughRenderer.doubleLineFillOps(x1, y1, x2, y2, o)
  }

  /** Port of `line(x1, y1, x2, y2, o)`. */
  def line(x1: Double, y1: Double, x2: Double, y2: Double, o: ResolvedOptions): OpSet =
    OpSet(`type` = OpSetType.path, ops = _doubleLine(x1, y1, x2, y2, o))

  /** Port of `linearPath(points, close, o)`. */
  def linearPath(points: Vector[Point], close: Boolean, o: ResolvedOptions): OpSet = {
    val len: Int = points.length
    if (len > 2) {
      val ops: ArrayBuffer[Op] = ArrayBuffer.empty
      var i:   Int             = 0
      while (i < (len - 1)) {
        ops ++= _doubleLine(points(i).x, points(i).y, points(i + 1).x, points(i + 1).y, o)
        i += 1
      }
      if (close) {
        ops ++= _doubleLine(points(len - 1).x, points(len - 1).y, points(0).x, points(0).y, o)
      }
      OpSet(`type` = OpSetType.path, ops = ops.toVector)
    } else if (len == 2) {
      line(points(0).x, points(0).y, points(1).x, points(1).y, o)
    } else {
      OpSet(`type` = OpSetType.path, ops = Vector.empty)
    }
  }

  /** Port of `polygon(points, o)`. */
  def polygon(points: Vector[Point], o: ResolvedOptions): OpSet =
    linearPath(points, true, o)

  /** Port of `rectangle(x, y, width, height, o)`. */
  def rectangle(x: Double, y: Double, width: Double, height: Double, o: ResolvedOptions): OpSet = {
    val points: Vector[Point] = Vector(
      Point(x, y),
      Point(x + width, y),
      Point(x + width, y + height),
      Point(x, y + height)
    )
    polygon(points, o)
  }

  /** Port of `curve(inputPoints, o)` (the `Point[] | Point[][]` overload). */
  def curve(inputPoints: Vector[Point] | Vector[Vector[Point]], o: ResolvedOptions): OpSet = {
    val ip: Vector[Any] = inputPoints
    if (ip.nonEmpty) {
      val pointsList: Vector[Vector[Point]] = ip.head match {
        case _: Point => Vector(inputPoints.asInstanceOf[Vector[Point]])
        case _ => inputPoints.asInstanceOf[Vector[Vector[Point]]]
      }

      val o1: ArrayBuffer[Op] = ArrayBuffer.empty
      o1 ++= _curveWithOffset(pointsList(0), 1 * (1 + o.roughness * 0.2), o)
      val o2: ArrayBuffer[Op] = ArrayBuffer.empty
      if (!o.disableMultiStroke) {
        o2 ++= _curveWithOffset(pointsList(0), 1.5 * (1 + o.roughness * 0.22), cloneOptionsAlterSeed(o))
      }

      var i: Int = 1
      while (i < pointsList.length) {
        val points: Vector[Point] = pointsList(i)
        if (points.nonEmpty) {
          val underlay: Vector[Op] = _curveWithOffset(points, 1 * (1 + o.roughness * 0.2), o)
          val overlay:  Vector[Op] =
            if (o.disableMultiStroke) Vector.empty
            else _curveWithOffset(points, 1.5 * (1 + o.roughness * 0.22), cloneOptionsAlterSeed(o))
          for (item <- underlay)
            if (item.op != OpType.move) {
              o1 += item
            }
          for (item <- overlay)
            if (item.op != OpType.move) {
              o2 += item
            }
        }
        i += 1
      }

      OpSet(`type` = OpSetType.path, ops = (o1 ++ o2).toVector)
    } else {
      OpSet(`type` = OpSetType.path, ops = Vector.empty)
    }
  }

  /** Port of `ellipse(x, y, width, height, o)`. */
  def ellipse(x: Double, y: Double, width: Double, height: Double, o: ResolvedOptions): OpSet = {
    val params: EllipseParams = generateEllipseParams(width, height, o)
    ellipseWithParams(x, y, o, params).opset
  }

  /** Port of `generateEllipseParams(width, height, o)`. */
  def generateEllipseParams(width: Double, height: Double, o: ResolvedOptions): EllipseParams = {
    val psq:                Double = Math.sqrt(Math.PI * 2 * Math.sqrt((Math.pow(width / 2, 2) + Math.pow(height / 2, 2)) / 2))
    val stepCount:          Double = Math.ceil(Math.max(o.curveStepCount, (o.curveStepCount / Math.sqrt(200)) * psq))
    val increment:          Double = (Math.PI * 2) / stepCount
    var rx:                 Double = Math.abs(width / 2)
    var ry:                 Double = Math.abs(height / 2)
    val curveFitRandomness: Double = 1 - o.curveFitting
    rx += _offsetOpt(rx * curveFitRandomness, o)
    ry += _offsetOpt(ry * curveFitRandomness, o)
    EllipseParams(rx = rx, ry = ry, increment = increment)
  }

  /** Port of `ellipseWithParams(x, y, o, ellipseParams)`. */
  def ellipseWithParams(x: Double, y: Double, o: ResolvedOptions, ellipseParams: EllipseParams): EllipseResult = {
    val overlap:    Double                         = ellipseParams.increment * _offset(0.1, _offset(0.4, 1, o), o)
    val (ap1, cp1): (Vector[Point], Vector[Point]) =
      _computeEllipsePoints(ellipseParams.increment, x, y, ellipseParams.rx, ellipseParams.ry, 1, overlap, o)
    val o1: ArrayBuffer[Op] = ArrayBuffer.empty
    o1 ++= _curve(ap1, Nullable.empty, o)
    if ((!o.disableMultiStroke) && (o.roughness != 0)) {
      val (ap2, _): (Vector[Point], Vector[Point]) =
        _computeEllipsePoints(ellipseParams.increment, x, y, ellipseParams.rx, ellipseParams.ry, 1.5, 0, o)
      val o2: Vector[Op] = _curve(ap2, Nullable.empty, o)
      o1 ++= o2
    }
    EllipseResult(
      estimatedPoints = cp1,
      opset = OpSet(`type` = OpSetType.path, ops = o1.toVector)
    )
  }

  /** Port of `arc(x, y, width, height, start, stop, closed, roughClosure, o)`. */
  def arc(x: Double, y: Double, width: Double, height: Double, start: Double, stop: Double, closed: Boolean, roughClosure: Boolean, o: ResolvedOptions): OpSet = {
    val cx: Double = x
    val cy: Double = y
    var rx: Double = Math.abs(width / 2)
    var ry: Double = Math.abs(height / 2)
    rx += _offsetOpt(rx * 0.01, o)
    ry += _offsetOpt(ry * 0.01, o)
    var strt: Double = start
    var stp:  Double = stop
    while (strt < 0) {
      strt += Math.PI * 2
      stp += Math.PI * 2
    }
    if ((stp - strt) > (Math.PI * 2)) {
      strt = 0
      stp = Math.PI * 2
    }
    val ellipseInc: Double          = (Math.PI * 2) / o.curveStepCount
    val arcInc:     Double          = Math.min(ellipseInc / 2, (stp - strt) / 2)
    val ops:        ArrayBuffer[Op] = ArrayBuffer.empty
    ops ++= _arc(arcInc, cx, cy, rx, ry, strt, stp, 1, o)
    if (!o.disableMultiStroke) {
      val o2: Vector[Op] = _arc(arcInc, cx, cy, rx, ry, strt, stp, 1.5, o)
      ops ++= o2
    }
    if (closed) {
      if (roughClosure) {
        ops ++= _doubleLine(cx, cy, cx + rx * Math.cos(strt), cy + ry * Math.sin(strt), o)
        ops ++= _doubleLine(cx, cy, cx + rx * Math.cos(stp), cy + ry * Math.sin(stp), o)
      } else {
        ops += Op(OpType.lineTo, Vector(cx, cy))
        ops += Op(OpType.lineTo, Vector(cx + rx * Math.cos(strt), cy + ry * Math.sin(strt)))
      }
    }
    OpSet(`type` = OpSetType.path, ops = ops.toVector)
  }

  /** Port of `svgPath(path, o)`. */
  def svgPath(path: String, o: ResolvedOptions): OpSet = {
    val segments: Vector[pathdata.Segment] = PathDataParser.normalize(PathDataParser.absolutize(PathDataParser.parsePath(path)))
    val ops:      ArrayBuffer[Op]          = ArrayBuffer.empty
    var first:    Point                    = Point(0, 0)
    var current:  Point                    = Point(0, 0)
    for (segment <- segments) {
      val key:  String         = segment.key
      val data: Vector[Double] = segment.data
      key match {
        case "M" =>
          current = Point(data(0), data(1))
          first = Point(data(0), data(1))
        case "L" =>
          ops ++= _doubleLine(current.x, current.y, data(0), data(1), o)
          current = Point(data(0), data(1))
        case "C" =>
          val x1: Double = data(0)
          val y1: Double = data(1)
          val x2: Double = data(2)
          val y2: Double = data(3)
          val x:  Double = data(4)
          val y:  Double = data(5)
          ops ++= _bezierTo(x1, y1, x2, y2, x, y, current, o)
          current = Point(x, y)
        case "Z" =>
          ops ++= _doubleLine(current.x, current.y, first.x, first.y, o)
          current = Point(first.x, first.y)
        case _ => ()
      }
    }
    OpSet(`type` = OpSetType.path, ops = ops.toVector)
  }

  // Fills

  /** Port of `solidFillPolygon(polygonList, o)`. */
  def solidFillPolygon(polygonList: Vector[Vector[Point]], o: ResolvedOptions): OpSet = {
    val ops: ArrayBuffer[Op] = ArrayBuffer.empty
    for (points <- polygonList)
      if (points.nonEmpty) {
        val offset: Double = if (numTruthy(o.maxRandomnessOffset)) o.maxRandomnessOffset else 0.0
        val len:    Int    = points.length
        if (len > 2) {
          ops += Op(OpType.move, Vector(points(0).x + _offsetOpt(offset, o), points(0).y + _offsetOpt(offset, o)))
          var i: Int = 1
          while (i < len) {
            ops += Op(OpType.lineTo, Vector(points(i).x + _offsetOpt(offset, o), points(i).y + _offsetOpt(offset, o)))
            i += 1
          }
        }
      }
    OpSet(`type` = OpSetType.fillPath, ops = ops.toVector)
  }

  /** Port of `patternFillPolygons(polygonList, o)`. */
  def patternFillPolygons(polygonList: Vector[Vector[Point]], o: ResolvedOptions): OpSet =
    Filler.getFiller(o, helper).fillPolygons(polygonList, o)

  /** Port of `patternFillArc(x, y, width, height, start, stop, o)`. */
  def patternFillArc(x: Double, y: Double, width: Double, height: Double, start: Double, stop: Double, o: ResolvedOptions): OpSet = {
    val cx: Double = x
    val cy: Double = y
    var rx: Double = Math.abs(width / 2)
    var ry: Double = Math.abs(height / 2)
    rx += _offsetOpt(rx * 0.01, o)
    ry += _offsetOpt(ry * 0.01, o)
    var strt: Double = start
    var stp:  Double = stop
    while (strt < 0) {
      strt += Math.PI * 2
      stp += Math.PI * 2
    }
    if ((stp - strt) > (Math.PI * 2)) {
      strt = 0
      stp = Math.PI * 2
    }
    val increment: Double             = (stp - strt) / o.curveStepCount
    val points:    ArrayBuffer[Point] = ArrayBuffer.empty
    var angle:     Double             = strt
    while (angle <= stp) {
      points += Point(cx + rx * Math.cos(angle), cy + ry * Math.sin(angle))
      angle = angle + increment
    }
    points += Point(cx + rx * Math.cos(stp), cy + ry * Math.sin(stp))
    points += Point(cx, cy)
    patternFillPolygons(Vector(points.toVector), o)
  }

  /** Port of `randOffset(x, o)`. */
  def randOffset(x: Double, o: ResolvedOptions): Double =
    _offsetOpt(x, o)

  /** Port of `randOffsetWithRange(min, max, o)`. */
  def randOffsetWithRange(min: Double, max: Double, o: ResolvedOptions): Double =
    _offset(min, max, o)

  /** Port of `doubleLineFillOps(x1, y1, x2, y2, o)`. */
  def doubleLineFillOps(x1: Double, y1: Double, x2: Double, y2: Double, o: ResolvedOptions): Vector[Op] =
    _doubleLine(x1, y1, x2, y2, o, true)

  // Private helpers

  /** Port of `cloneOptionsAlterSeed(ops)`: a shallow copy with a cleared randomizer and a bumped seed (`ops.seed ? ops.seed + 1 : ops.seed`).
    */
  private def cloneOptionsAlterSeed(ops: ResolvedOptions): ResolvedOptions =
    ops.copy(
      randomizer = Nullable.empty,
      seed = if (ops.seed != 0) ops.seed + 1 else ops.seed
    )

  /** Port of `random(ops)`: lazily creates and then reuses/advances `ops.randomizer`. */
  private def random(ops: ResolvedOptions): Double = {
    val r: Random = ops.randomizer.getOrElse {
      // `new Random(ops.seed || 0)` — `|| 0` is identity for an Int seed.
      val created: Random = new Random(ops.seed)
      ops.randomizer = Nullable(created)
      created
    }
    r.next()
  }

  /** Port of `_offset(min, max, ops, roughnessGain = 1)`. */
  private def _offset(min: Double, max: Double, ops: ResolvedOptions, roughnessGain: Double = 1): Double =
    ops.roughness * roughnessGain * ((random(ops) * (max - min)) + min)

  /** Port of `_offsetOpt(x, ops, roughnessGain = 1)`. */
  private def _offsetOpt(x: Double, ops: ResolvedOptions, roughnessGain: Double = 1): Double =
    _offset(-x, x, ops, roughnessGain)

  /** Port of `_doubleLine(x1, y1, x2, y2, o, filling = false)`. */
  private def _doubleLine(x1: Double, y1: Double, x2: Double, y2: Double, o: ResolvedOptions, filling: Boolean = false): Vector[Op] = {
    val singleStroke: Boolean    = if (filling) o.disableMultiStrokeFill else o.disableMultiStroke
    val o1:           Vector[Op] = _line(x1, y1, x2, y2, o, true, false)
    if (singleStroke) {
      o1
    } else {
      val o2: Vector[Op] = _line(x1, y1, x2, y2, o, true, true)
      o1 ++ o2
    }
  }

  /** Port of `_line(x1, y1, x2, y2, o, move, overlay)`. */
  private def _line(x1: Double, y1: Double, x2: Double, y2: Double, o: ResolvedOptions, move: Boolean, overlay: Boolean): Vector[Op] = {
    val lengthSq:      Double = Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2)
    val length:        Double = Math.sqrt(lengthSq)
    var roughnessGain: Double = 1
    if (length < 200) {
      roughnessGain = 1
    } else if (length > 500) {
      roughnessGain = 0.4
    } else {
      roughnessGain = -0.0016668 * length + 1.233334
    }

    var offset: Double = if (numTruthy(o.maxRandomnessOffset)) o.maxRandomnessOffset else 0.0
    if ((offset * offset * 100) > lengthSq) {
      offset = length / 10
    }
    val halfOffset:   Double = offset / 2
    val divergePoint: Double = 0.2 + random(o) * 0.2
    var midDispX:     Double = o.bowing * o.maxRandomnessOffset * (y2 - y1) / 200
    var midDispY:     Double = o.bowing * o.maxRandomnessOffset * (x1 - x2) / 200
    midDispX = _offsetOpt(midDispX, o, roughnessGain)
    midDispY = _offsetOpt(midDispY, o, roughnessGain)
    val ops:              ArrayBuffer[Op] = ArrayBuffer.empty
    val randomHalf:       () => Double    = () => _offsetOpt(halfOffset, o, roughnessGain)
    val randomFull:       () => Double    = () => _offsetOpt(offset, o, roughnessGain)
    val preserveVertices: Boolean         = o.preserveVertices
    if (move) {
      if (overlay) {
        ops += Op(
          OpType.move,
          Vector(
            x1 + (if (preserveVertices) 0.0 else randomHalf()),
            y1 + (if (preserveVertices) 0.0 else randomHalf())
          )
        )
      } else {
        ops += Op(
          OpType.move,
          Vector(
            x1 + (if (preserveVertices) 0.0 else _offsetOpt(offset, o, roughnessGain)),
            y1 + (if (preserveVertices) 0.0 else _offsetOpt(offset, o, roughnessGain))
          )
        )
      }
    }
    if (overlay) {
      ops += Op(
        OpType.bcurveTo,
        Vector(
          midDispX + x1 + (x2 - x1) * divergePoint + randomHalf(),
          midDispY + y1 + (y2 - y1) * divergePoint + randomHalf(),
          midDispX + x1 + 2 * (x2 - x1) * divergePoint + randomHalf(),
          midDispY + y1 + 2 * (y2 - y1) * divergePoint + randomHalf(),
          x2 + (if (preserveVertices) 0.0 else randomHalf()),
          y2 + (if (preserveVertices) 0.0 else randomHalf())
        )
      )
    } else {
      ops += Op(
        OpType.bcurveTo,
        Vector(
          midDispX + x1 + (x2 - x1) * divergePoint + randomFull(),
          midDispY + y1 + (y2 - y1) * divergePoint + randomFull(),
          midDispX + x1 + 2 * (x2 - x1) * divergePoint + randomFull(),
          midDispY + y1 + 2 * (y2 - y1) * divergePoint + randomFull(),
          x2 + (if (preserveVertices) 0.0 else randomFull()),
          y2 + (if (preserveVertices) 0.0 else randomFull())
        )
      )
    }
    ops.toVector
  }

  /** Port of `_curveWithOffset(points, offset, o)`. */
  private def _curveWithOffset(points: Vector[Point], offset: Double, o: ResolvedOptions): Vector[Op] =
    if (points.isEmpty) {
      Vector.empty
    } else {
      val ps: ArrayBuffer[Point] = ArrayBuffer.empty
      ps += Point(
        points(0).x + _offsetOpt(offset, o),
        points(0).y + _offsetOpt(offset, o)
      )
      ps += Point(
        points(0).x + _offsetOpt(offset, o),
        points(0).y + _offsetOpt(offset, o)
      )
      var i: Int = 1
      while (i < points.length) {
        ps += Point(
          points(i).x + _offsetOpt(offset, o),
          points(i).y + _offsetOpt(offset, o)
        )
        if (i == (points.length - 1)) {
          ps += Point(
            points(i).x + _offsetOpt(offset, o),
            points(i).y + _offsetOpt(offset, o)
          )
        }
        i += 1
      }
      _curve(ps.toVector, Nullable.empty, o)
    }

  /** Port of `_curve(points, closePoint, o)`. */
  private def _curve(points: Vector[Point], closePoint: Nullable[Point], o: ResolvedOptions): Vector[Op] = {
    val len: Int             = points.length
    val ops: ArrayBuffer[Op] = ArrayBuffer.empty
    if (len > 3) {
      val s: Double = 1 - o.curveTightness
      ops += Op(OpType.move, Vector(points(1).x, points(1).y))
      var i: Int = 1
      while ((i + 2) < len) {
        val cachedVertArray: Point = points(i)
        // b[0] = [cachedVertArray.x, cachedVertArray.y] is computed upstream but never
        // emitted (only b[1]/b[2]/b[3] below); omitted as a dead store (see migration notes).
        val b1x: Double = cachedVertArray.x + (s * points(i + 1).x - s * points(i - 1).x) / 6
        val b1y: Double = cachedVertArray.y + (s * points(i + 1).y - s * points(i - 1).y) / 6
        val b2x: Double = points(i + 1).x + (s * points(i).x - s * points(i + 2).x) / 6
        val b2y: Double = points(i + 1).y + (s * points(i).y - s * points(i + 2).y) / 6
        val b3x: Double = points(i + 1).x
        val b3y: Double = points(i + 1).y
        ops += Op(OpType.bcurveTo, Vector(b1x, b1y, b2x, b2y, b3x, b3y))
        i += 1
      }
      closePoint.foreach { cp =>
        val ro: Double = o.maxRandomnessOffset
        ops += Op(OpType.lineTo, Vector(cp.x + _offsetOpt(ro, o), cp.y + _offsetOpt(ro, o)))
      }
    } else if (len == 3) {
      ops += Op(OpType.move, Vector(points(1).x, points(1).y))
      ops += Op(
        OpType.bcurveTo,
        Vector(
          points(1).x,
          points(1).y,
          points(2).x,
          points(2).y,
          points(2).x,
          points(2).y
        )
      )
    } else if (len == 2) {
      ops ++= _line(points(0).x, points(0).y, points(1).x, points(1).y, o, true, true)
    }
    ops.toVector
  }

  /** Port of `_computeEllipsePoints(increment, cx, cy, rx, ry, offset, overlap, o)`. Returns `(allPoints, corePoints)`.
    */
  private def _computeEllipsePoints(increment: Double, cx: Double, cy: Double, rx: Double, ry: Double, offset: Double, overlap: Double, o: ResolvedOptions): (Vector[Point], Vector[Point]) = {
    val coreOnly:   Boolean            = o.roughness == 0
    val corePoints: ArrayBuffer[Point] = ArrayBuffer.empty
    val allPoints:  ArrayBuffer[Point] = ArrayBuffer.empty
    var inc:        Double             = increment

    if (coreOnly) {
      inc = inc / 4
      allPoints += Point(
        cx + rx * Math.cos(-inc),
        cy + ry * Math.sin(-inc)
      )
      var angle: Double = 0
      while (angle <= Math.PI * 2) {
        val p: Point = Point(
          cx + rx * Math.cos(angle),
          cy + ry * Math.sin(angle)
        )
        corePoints += p
        allPoints += p
        angle = angle + inc
      }
      allPoints += Point(
        cx + rx * Math.cos(0),
        cy + ry * Math.sin(0)
      )
      allPoints += Point(
        cx + rx * Math.cos(inc),
        cy + ry * Math.sin(inc)
      )
    } else {
      val radOffset: Double = _offsetOpt(0.5, o) - (Math.PI / 2)
      allPoints += Point(
        _offsetOpt(offset, o) + cx + 0.9 * rx * Math.cos(radOffset - inc),
        _offsetOpt(offset, o) + cy + 0.9 * ry * Math.sin(radOffset - inc)
      )
      val endAngle: Double = Math.PI * 2 + radOffset - 0.01
      var angle:    Double = radOffset
      while (angle < endAngle) {
        val p: Point = Point(
          _offsetOpt(offset, o) + cx + rx * Math.cos(angle),
          _offsetOpt(offset, o) + cy + ry * Math.sin(angle)
        )
        corePoints += p
        allPoints += p
        angle = angle + inc
      }
      allPoints += Point(
        _offsetOpt(offset, o) + cx + rx * Math.cos(radOffset + Math.PI * 2 + overlap * 0.5),
        _offsetOpt(offset, o) + cy + ry * Math.sin(radOffset + Math.PI * 2 + overlap * 0.5)
      )
      allPoints += Point(
        _offsetOpt(offset, o) + cx + 0.98 * rx * Math.cos(radOffset + overlap),
        _offsetOpt(offset, o) + cy + 0.98 * ry * Math.sin(radOffset + overlap)
      )
      allPoints += Point(
        _offsetOpt(offset, o) + cx + 0.9 * rx * Math.cos(radOffset + overlap * 0.5),
        _offsetOpt(offset, o) + cy + 0.9 * ry * Math.sin(radOffset + overlap * 0.5)
      )
    }

    (allPoints.toVector, corePoints.toVector)
  }

  /** Port of `_arc(increment, cx, cy, rx, ry, strt, stp, offset, o)`. */
  private def _arc(increment: Double, cx: Double, cy: Double, rx: Double, ry: Double, strt: Double, stp: Double, offset: Double, o: ResolvedOptions): Vector[Op] = {
    val radOffset: Double             = strt + _offsetOpt(0.1, o)
    val points:    ArrayBuffer[Point] = ArrayBuffer.empty
    points += Point(
      _offsetOpt(offset, o) + cx + 0.9 * rx * Math.cos(radOffset - increment),
      _offsetOpt(offset, o) + cy + 0.9 * ry * Math.sin(radOffset - increment)
    )
    var angle: Double = radOffset
    while (angle <= stp) {
      points += Point(
        _offsetOpt(offset, o) + cx + rx * Math.cos(angle),
        _offsetOpt(offset, o) + cy + ry * Math.sin(angle)
      )
      angle = angle + increment
    }
    points += Point(
      cx + rx * Math.cos(stp),
      cy + ry * Math.sin(stp)
    )
    points += Point(
      cx + rx * Math.cos(stp),
      cy + ry * Math.sin(stp)
    )
    _curve(points.toVector, Nullable.empty, o)
  }

  /** Port of `_bezierTo(x1, y1, x2, y2, x, y, current, o)`. */
  private def _bezierTo(x1: Double, y1: Double, x2: Double, y2: Double, x: Double, y: Double, current: Point, o: ResolvedOptions): Vector[Op] = {
    val ops: ArrayBuffer[Op] = ArrayBuffer.empty
    val ros: Vector[Double]  = Vector(
      if (numTruthy(o.maxRandomnessOffset)) o.maxRandomnessOffset else 1.0,
      (if (numTruthy(o.maxRandomnessOffset)) o.maxRandomnessOffset else 1.0) + 0.3
    )
    var f:                Point   = Point(0, 0)
    val iterations:       Int     = if (o.disableMultiStroke) 1 else 2
    val preserveVertices: Boolean = o.preserveVertices
    var i:                Int     = 0
    while (i < iterations) {
      if (i == 0) {
        ops += Op(OpType.move, Vector(current.x, current.y))
      } else {
        ops += Op(
          OpType.move,
          Vector(
            current.x + (if (preserveVertices) 0.0 else _offsetOpt(ros(0), o)),
            current.y + (if (preserveVertices) 0.0 else _offsetOpt(ros(0), o))
          )
        )
      }
      f = if (preserveVertices) Point(x, y) else Point(x + _offsetOpt(ros(i), o), y + _offsetOpt(ros(i), o))
      ops += Op(
        OpType.bcurveTo,
        Vector(
          x1 + _offsetOpt(ros(i), o),
          y1 + _offsetOpt(ros(i), o),
          x2 + _offsetOpt(ros(i), o),
          y2 + _offsetOpt(ros(i), o),
          f.x,
          f.y
        )
      )
      i += 1
    }
    ops.toVector
  }

  /** JS number truthiness for the `x || fallback` idiom: a number is falsy iff it is `0` or `NaN`. */
  private def numTruthy(d: Double): Boolean =
    d != 0.0 && !d.isNaN
}
