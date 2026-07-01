/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * SVG path -> sampled point sets (M/L/C/Z state machine) — Scala 3 port
 *
 * Original source: points-on-path (src/index.ts)
 * Original author: pshihn
 * Original license: MIT
 * upstream-commit: 7693ef0
 *
 * Migration notes:
 *   Renames: module-level `pointsOnPath` -> `object PointsOnPath.pointsOnPath`. The
 *     TS `export { Point } from 'points-on-curve'` re-export is implicit: `Point`
 *     lives in `PointsOnCurve.scala` in this same `rough/curve` package, so it is
 *     already visible to downstream callers without an explicit re-export.
 *   Convention: `parsePath`/`absolutize`/`normalize` come from the Chip 1 port
 *     (`ssg.graphs.commons.rough.pathdata.PathDataParser`); its `Segment.data` is a
 *     `Vector[Double]`, indexed `data(0)`, `data(1)`, … where the TS does `data[0]`.
 *     `Point[][]` result -> `Vector[Vector[Point]]`.
 *   Idiom: the TS closures `appendPendingCurve`/`appendPendingPoints` capture and
 *     mutate `sets`/`currentPoints`/`start`/`pendingCurve`; ported to local `var`s
 *     plus nested `def`s (which close over and reassign the outer `var`s). The TS
 *     `sets.push(currentPoints)` then `currentPoints = []` pushes the old reference
 *     and rebinds; the port snapshots via `sets += currentPoints.toVector` then
 *     rebinds `currentPoints` to a fresh buffer — equivalent.
 *   Idiom: optional params `tolerance?`/`distance?` -> `Option[Double]`. The
 *     `pointsOnBezierCurves(pendingCurve, tolerance)` call passes a possibly-undefined
 *     `tolerance` that the callee defaults to 0.15; here `tolerance.getOrElse(0.15)`
 *     reproduces that default-at-the-call-boundary (the canonical 0.15 default lives
 *     on `pointsOnBezierCurves`). The trailing `if (!distance) return sets` is JS
 *     falsiness (undefined, 0, -0, and NaN all falsy); ported as a `match` where only
 *     `Some(d)` with `d != 0 && !d.isNaN` runs the `simplify` pass, otherwise `sets`
 *     is returned. The early `return sets` and final `return out` are expression-final
 *     match arms (no `return` keyword).
 *   Note (enforce compare): `parsePath`/`normalize` (Chip 1) and `simplify`
 *     (PointsOnCurve) are the TS imported functions; they are invoked here via
 *     qualified calls (`PathDataParser.*`, `PointsOnCurve.simplify`) rather than as
 *     bare locals, so a bare-identifier `enforce compare` reports them as "missing" —
 *     this is the faithful cross-module delegation, not dropped logic.
 */
package ssg
package graphs
package commons
package rough
package curve

import scala.collection.mutable.ArrayBuffer

import ssg.graphs.commons.rough.pathdata.PathDataParser

/** SVG path -> sampled `Point` sets (port of points-on-path `index.ts`). */
object PointsOnPath {

  def pointsOnPath(
    path:      String,
    tolerance: Option[Double] = None,
    distance:  Option[Double] = None
  ): Vector[Vector[Point]] = {
    val segments   = PathDataParser.parsePath(path)
    val normalized = PathDataParser.normalize(PathDataParser.absolutize(segments))

    val sets:          ArrayBuffer[Vector[Point]] = ArrayBuffer.empty
    var currentPoints: ArrayBuffer[Point]         = ArrayBuffer.empty
    var start:         Point                      = Point(0, 0)
    var pendingCurve:  ArrayBuffer[Point]         = ArrayBuffer.empty

    def appendPendingCurve(): Unit = {
      if (pendingCurve.length >= 4) {
        currentPoints ++= PointsOnCurve.pointsOnBezierCurves(pendingCurve.toVector, tolerance.getOrElse(0.15))
      }
      pendingCurve = ArrayBuffer.empty
    }

    def appendPendingPoints(): Unit = {
      appendPendingCurve()
      if (currentPoints.nonEmpty) {
        sets += currentPoints.toVector
        currentPoints = ArrayBuffer.empty
      }
    }

    for (segment <- normalized) {
      val key:  String         = segment.key
      val data: Vector[Double] = segment.data
      key match {
        case "M" =>
          appendPendingPoints()
          start = Point(data(0), data(1))
          currentPoints += start
        case "L" =>
          appendPendingCurve()
          currentPoints += Point(data(0), data(1))
        case "C" =>
          if (pendingCurve.isEmpty) {
            val lastPoint: Point = if (currentPoints.nonEmpty) currentPoints(currentPoints.length - 1) else start
            pendingCurve += Point(lastPoint.x, lastPoint.y)
          }
          pendingCurve += Point(data(0), data(1))
          pendingCurve += Point(data(2), data(3))
          pendingCurve += Point(data(4), data(5))
        case "Z" =>
          appendPendingCurve()
          currentPoints += Point(start.x, start.y)
        case _ =>
          ()
      }
    }
    appendPendingPoints()

    distance match {
      case Some(d) if d != 0 && !d.isNaN =>
        val out: ArrayBuffer[Vector[Point]] = ArrayBuffer.empty
        for (set <- sets) {
          val simplifiedSet: Vector[Point] = PointsOnCurve.simplify(set, d)
          if (simplifiedSet.nonEmpty) {
            out += simplifiedSet
          }
        }
        out.toVector
      case _ =>
        sets.toVector
    }
  }
}
