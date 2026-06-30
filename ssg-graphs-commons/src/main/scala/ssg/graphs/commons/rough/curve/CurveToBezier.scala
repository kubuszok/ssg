/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Catmull-Rom polyline -> cubic-bezier control point conversion — Scala 3 port
 *
 * Original source: points-on-curve (src/curve-to-bezier.ts)
 * Original author: pshihn
 * Original license: MIT
 * upstream-commit: 4824147
 *
 * Migration notes:
 *   Renames: module-private `clone` + exported `curveToBezier` -> members of
 *     `object CurveToBezier`. Uses the shared `Point` from `PointsOnCurve.scala`.
 *   Convention: `Point[]` -> `Vector[Point]` (results) / `ArrayBuffer[Point]`
 *     (the mutable `out`/`points` accumulators). `clone(p) = [...p]` -> a fresh
 *     `Point(p.x, p.y)`; with an immutable `Point` value this is effectively identity,
 *     but the method is kept to mirror the original structure 1:1.
 *   Convention: `throw new Error('A curve must have at least three points.')` ->
 *     `throw new CurveError(...)` (a `RuntimeException`); the message is preserved
 *     verbatim and the error is never swallowed.
 *   Idiom: the general branch reuses one TS array `b` across iterations, assigning
 *     `b[0..3]` and pushing `b[1]`, `b[2]`, `b[3]` (by value). `b[0]` is assigned but
 *     never read or pushed — a dead store; it is dropped here so the port satisfies
 *     `-Wunused`. The pushed control points are built as fresh `Point` values each
 *     iteration, which matches the TS push-by-value semantics exactly.
 *   Note (enforce compare): the only symbol a bare-identifier `enforce compare`
 *     reports "missing" is `Error` — the JS built-in error, renamed to `CurveError`
 *     here (see the throw above); no logic is dropped.
 */
package ssg
package graphs
package commons
package rough
package curve

import scala.collection.mutable.ArrayBuffer

/** Error raised by [[CurveToBezier.curveToBezier]] when given fewer than three
  * points. Mirrors the `throw new Error(...)` in the original `curveToBezier`.
  */
final class CurveError(message: String) extends RuntimeException(message)

/** Catmull-Rom -> cubic-bezier conversion (port of `curve-to-bezier.ts`). */
object CurveToBezier {

  private def clone(p: Point): Point =
    Point(p.x, p.y)

  def curveToBezier(pointsIn: Vector[Point], curveTightness: Double = 0): Vector[Point] = {
    val len: Int = pointsIn.length
    if (len < 3) {
      throw new CurveError("A curve must have at least three points.")
    }
    val out: ArrayBuffer[Point] = ArrayBuffer.empty
    if (len == 3) {
      out += clone(pointsIn(0))
      out += clone(pointsIn(1))
      out += clone(pointsIn(2))
      out += clone(pointsIn(2))
    } else {
      val points: ArrayBuffer[Point] = ArrayBuffer.empty
      points += pointsIn(0)
      points += pointsIn(0)
      var i: Int = 1
      while (i < pointsIn.length) {
        points += pointsIn(i)
        if (i == (pointsIn.length - 1)) {
          points += pointsIn(i)
        }
        i += 1
      }
      val s: Double = 1 - curveTightness
      out += clone(points(0))
      var j: Int = 1
      while ((j + 2) < points.length) {
        val cachedVertArray: Point = points(j)
        val b1: Point = Point(
          cachedVertArray.x + (s * points(j + 1).x - s * points(j - 1).x) / 6,
          cachedVertArray.y + (s * points(j + 1).y - s * points(j - 1).y) / 6
        )
        val b2: Point = Point(
          points(j + 1).x + (s * points(j).x - s * points(j + 2).x) / 6,
          points(j + 1).y + (s * points(j).y - s * points(j + 2).y) / 6
        )
        val b3: Point = Point(points(j + 1).x, points(j + 1).y)
        out += b1
        out += b2
        out += b3
        j += 1
      }
    }
    out.toVector
  }
}
