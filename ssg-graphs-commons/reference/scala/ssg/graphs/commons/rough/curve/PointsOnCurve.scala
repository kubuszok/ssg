/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Bezier-curve point sampling + Ramer–Douglas–Peucker simplification — Scala 3 port
 *
 * Original source: points-on-curve (src/index.ts)
 * Original author: pshihn
 * Original license: MIT
 * upstream-commit: 4824147
 *
 * Migration notes:
 *   Renames: the TS `Point = [number, number]` tuple type -> a shared
 *     `final case class Point(x: Double, y: Double)` defined here at package level
 *     (points-on-path re-exports it; same package, so the re-export is implicit).
 *     Index access `p[0]`/`p[1]` -> `p.x`/`p.y`. This is the ONE shared `Point` for
 *     the `rough/curve` package; roughjs `geometry.ts` has its own unrelated `Point`
 *     (a later chip) which is intentionally NOT shared here.
 *   Convention: `Point[]` -> `Vector[Point]` for read-only inputs and public results;
 *     the shared mutable accumulator threaded through
 *     `getPointsOnBezierCurveWithSplitting`/`simplifyPoints` (the TS `newPoints?`
 *     out-parameter) -> `Option[ArrayBuffer[Point]]`, ported faithfully so the
 *     `d > 1` dedup and RDP in-place accumulation order match the original exactly.
 *     `newPoints || []` -> `newPoints.getOrElse(ArrayBuffer.empty)`. The TS
 *     `newPoints?` is optional; `simplifyPoints` keeps the `= None` default (its
 *     public callers `simplify`/`pointsOnBezierCurves` omit it), but
 *     `getPointsOnBezierCurveWithSplitting` drops the default because every call site
 *     (the `pointsOnBezierCurves` loop and the two recursive sub-curve calls) passes
 *     an explicit `Some(buffer)` — an unused default getter trips `-Wunused`. The
 *     `Option`/`getOrElse` is retained so the `|| []` fallback semantics are preserved.
 *   Idiom: `simplify(points, distance)` and `pointsOnBezierCurves(points, tolerance,
 *     distance?)` both have a param named `distance` that shadows the module-private
 *     `distance` function. The param is renamed `distanceTolerance` in both so the
 *     `distance` function stays callable.
 *   Idiom: `pointsOnBezierCurves`' `distance?` (`if (distance && distance > 0)`) ->
 *     `Option[Double]` matched as `case Some(d) if d > 0` (undefined and 0 both fall
 *     through to returning the raw points — preserving JS falsiness for `0`).
 *   Idiom: `const numSegments = (points.length - 1) / 3` is a JS float; the
 *     `for (i = 0; i < numSegments; i++)` loop is ported as a `Double` bound with a
 *     `while (i < numSegments)` so non-`3n+1` inputs iterate identically to the
 *     original (and overrun their bounds identically) rather than via integer division.
 *   Idiom: every `return outPoints`/`return newPoints` is an expression-final value
 *     (no `return` keyword); the early `return simplifyPoints(...)` in
 *     `pointsOnBezierCurves` becomes a `match` arm.
 */
package ssg
package graphs
package commons
package rough
package curve

import scala.collection.mutable.ArrayBuffer

/** A 2D point. Port of the points-on-curve `Point = [number, number]` tuple type; shared across the `rough/curve` package (points-on-path re-exports it).
  */
final case class Point(x: Double, y: Double)

/** Bezier sampling + RDP simplification (port of points-on-curve `index.ts`). */
object PointsOnCurve {

  // distance between 2 points
  private def distance(p1: Point, p2: Point): Double =
    Math.sqrt(distanceSq(p1, p2))

  // distance between 2 points squared
  private def distanceSq(p1: Point, p2: Point): Double =
    Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2)

  // Sistance squared from a point p to the line segment vw
  private def distanceToSegmentSq(p: Point, v: Point, w: Point): Double = {
    val l2: Double = distanceSq(v, w)
    if (l2 == 0) {
      distanceSq(p, v)
    } else {
      var t: Double = ((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2
      t = Math.max(0, Math.min(1, t))
      distanceSq(p, lerp(v, w, t))
    }
  }

  private def lerp(a: Point, b: Point, t: Double): Point =
    Point(
      a.x + (b.x - a.x) * t,
      a.y + (b.y - a.y) * t
    )

  // Adapted from https://seant23.wordpress.com/2010/11/12/offset-bezier-curves/
  private def flatness(points: Vector[Point], offset: Int): Double = {
    val p1: Point = points(offset + 0)
    val p2: Point = points(offset + 1)
    val p3: Point = points(offset + 2)
    val p4: Point = points(offset + 3)

    var ux: Double = 3 * p2.x - 2 * p1.x - p4.x; ux *= ux
    var uy: Double = 3 * p2.y - 2 * p1.y - p4.y; uy *= uy
    var vx: Double = 3 * p3.x - 2 * p4.x - p1.x; vx *= vx
    var vy: Double = 3 * p3.y - 2 * p4.y - p1.y; vy *= vy

    if (ux < vx) {
      ux = vx
    }

    if (uy < vy) {
      uy = vy
    }

    ux + uy
  }

  private def getPointsOnBezierCurveWithSplitting(
    points:    Vector[Point],
    offset:    Int,
    tolerance: Double,
    newPoints: Option[ArrayBuffer[Point]]
  ): ArrayBuffer[Point] = {
    val outPoints: ArrayBuffer[Point] = newPoints.getOrElse(ArrayBuffer.empty)
    if (flatness(points, offset) < tolerance) {
      val p0: Point = points(offset + 0)
      if (outPoints.nonEmpty) {
        val d: Double = distance(outPoints(outPoints.length - 1), p0)
        if (d > 1) {
          outPoints += p0
        }
      } else {
        outPoints += p0
      }
      outPoints += points(offset + 3)
    } else {
      // subdivide
      val t:  Double = .5
      val p1: Point  = points(offset + 0)
      val p2: Point  = points(offset + 1)
      val p3: Point  = points(offset + 2)
      val p4: Point  = points(offset + 3)

      val q1: Point = lerp(p1, p2, t)
      val q2: Point = lerp(p2, p3, t)
      val q3: Point = lerp(p3, p4, t)

      val r1: Point = lerp(q1, q2, t)
      val r2: Point = lerp(q2, q3, t)

      val red: Point = lerp(r1, r2, t)

      getPointsOnBezierCurveWithSplitting(Vector(p1, q1, r1, red), 0, tolerance, Some(outPoints))
      getPointsOnBezierCurveWithSplitting(Vector(red, r2, q3, p4), 0, tolerance, Some(outPoints))
    }
    outPoints
  }

  def simplify(points: Vector[Point], distanceTolerance: Double): Vector[Point] =
    simplifyPoints(points, 0, points.length, distanceTolerance).toVector

  // Ramer–Douglas–Peucker algorithm
  // https://en.wikipedia.org/wiki/Ramer%E2%80%93Douglas%E2%80%93Peucker_algorithm
  private def simplifyPoints(
    points:    Vector[Point],
    start:     Int,
    end:       Int,
    epsilon:   Double,
    newPoints: Option[ArrayBuffer[Point]] = None
  ): ArrayBuffer[Point] = {
    val outPoints: ArrayBuffer[Point] = newPoints.getOrElse(ArrayBuffer.empty)

    // find the most distance point from the endpoints
    val s:         Point  = points(start)
    val e:         Point  = points(end - 1)
    var maxDistSq: Double = 0
    var maxNdx:    Int    = 1
    var i:         Int    = start + 1
    while (i < end - 1) {
      val distSq: Double = distanceToSegmentSq(points(i), s, e)
      if (distSq > maxDistSq) {
        maxDistSq = distSq
        maxNdx = i
      }
      i += 1
    }

    // if that point is too far, split
    if (Math.sqrt(maxDistSq) > epsilon) {
      simplifyPoints(points, start, maxNdx + 1, epsilon, Some(outPoints))
      simplifyPoints(points, maxNdx, end, epsilon, Some(outPoints))
    } else {
      if (outPoints.isEmpty) {
        outPoints += s
      }
      outPoints += e
    }

    outPoints
  }

  def pointsOnBezierCurves(
    points:            Vector[Point],
    tolerance:         Double = 0.15,
    distanceTolerance: Option[Double] = None
  ): Vector[Point] = {
    val newPoints:   ArrayBuffer[Point] = ArrayBuffer.empty
    val numSegments: Double             = (points.length - 1).toDouble / 3
    var i:           Int                = 0
    while (i < numSegments) {
      val offset: Int = i * 3
      getPointsOnBezierCurveWithSplitting(points, offset, tolerance, Some(newPoints))
      i += 1
    }
    distanceTolerance match {
      case Some(d) if d > 0 =>
        simplifyPoints(newPoints.toVector, 0, newPoints.length, d).toVector
      case _ =>
        newPoints.toVector
    }
  }
}
