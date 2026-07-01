/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * roughjs scan-line hachure wrapper (polygonHachureLines) — Scala 3 port
 *
 * Original source: roughjs (src/fillers/scan-line-hachure.ts)
 * Original author: Preet Shihn
 * Original license: MIT
 * upstream-commit: 56a2762
 *
 * Migration notes:
 *   Renames: TS module function `polygonHachureLines` -> `object ScanLineHachure`
 *     method. `Point`/`Line` here are the roughjs geometry types (`rough.Point` /
 *     `rough.Line` from `Geometry.scala`), referenced qualified to avoid the mutable
 *     hachure-fill `Point`/`Line` that share this `fillers` package.
 *   Idiom (type bridge, critical): the TS `Point = [number, number]` of roughjs and of
 *     hachure-fill are the SAME structural tuple type, so `hachureLines(polygonList, …)`
 *     accepts the roughjs points directly. In the statically-typed port they are DISTINCT
 *     nominal types: roughjs `rough.Point` is immutable; hachure-fill `Point` (Chip 3)
 *     is mutable. So this wrapper converts the roughjs `Vector[Vector[rough.Point]]` into
 *     hachure-fill polygons (`Vector[HachureFill.Polygon]` = `Vector[Vector[Point]]`),
 *     calls `HachureFill.hachureLines`, then converts the resulting hachure-fill `Line`s
 *     back to roughjs `rough.Line`s. The conversion copies coordinates by value (the
 *     mutable hachure points are scratch state internal to `hachureLines`; the roughjs
 *     output points are fresh immutable copies).
 *   Idiom (`Math.round`, critical concern #6): JS `Math.round` rounds half toward +Inf;
 *     Java `Math.round(Double): Long` is `floor(x + 0.5)`, matching on all signs. Used as
 *     `Math.round(Math.max(gap, 0.1)).toDouble`.
 *   Idiom (`randomizer?.next() || Math.random()`, critical concern #5): `o.randomizer`
 *     is `Nullable[Random]`. The TS optional-chain `?.` yields `undefined` when the
 *     randomizer is absent, and `||` treats a JS-falsy left operand (`undefined`, `0`,
 *     `NaN`) as "use the right operand". So the value is `randomizer.next()` ONLY when a
 *     randomizer is present AND its result is truthy (non-zero, non-NaN); otherwise it
 *     falls back to `Math.random()` (`RoughMath.unseededRandom`, NON-deterministic by
 *     design — documented). The `next() == 0.0` falsy-fallback is reproduced exactly.
 *   Idiom (`skipOffset || 1`): the final argument passed to `hachureLines` is
 *     `skipOffset || 1` — `1` whenever `skipOffset` is JS-falsy (it can be `0` because
 *     `Math.round(0.1) == 0`). Reproduced via a truthiness check.
 *   Idiom (control flow): no `return`; the body is a single expression chain.
 */
package ssg
package graphs
package commons
package rough
package fillers

/** roughjs scan-line hachure wrapper (port of `scan-line-hachure.ts`). */
object ScanLineHachure {

  // JS truthiness of a number: non-zero and not NaN (0, -0 and NaN are falsy).
  private def truthy(d: Double): Boolean =
    d != 0.0 && !d.isNaN

  /** Port of `polygonHachureLines(polygonList, o)`: compute the scan-line hachure fill lines for the polygon list, rotated by `o.hachureAngle + 90`.
    */
  def polygonHachureLines(polygonList: Vector[Vector[rough.Point]], o: ResolvedOptions): Vector[rough.Line] = {
    val angle: Double = o.hachureAngle + 90
    var gap:   Double = o.hachureGap
    if (gap < 0) {
      gap = o.strokeWidth * 4
    }
    gap = Math.round(Math.max(gap, 0.1)).toDouble
    var skipOffset: Double = 1
    if (o.roughness >= 1) {
      // `(o.randomizer?.next() || Math.random())`: use the randomizer's value only when
      // present AND truthy; otherwise fall back to the non-deterministic Math.random().
      val nextValue: Double = o.randomizer.fold(Double.NaN)(rnd => rnd.next())
      val random:    Double = if (truthy(nextValue)) nextValue else RoughMath.unseededRandom()
      if (random > 0.7) {
        skipOffset = gap
      }
    }

    // Bridge the roughjs immutable points to hachure-fill mutable polygons (Chip 3).
    val hachurePolygons: Vector[HachureFill.Polygon] =
      polygonList.map(polygon => polygon.map(point => Point(point.x, point.y)))
    val hachureStepOffset: Double       = if (truthy(skipOffset)) skipOffset else 1
    val lines:             Vector[Line] = HachureFill.hachureLines(hachurePolygons, gap, angle, hachureStepOffset)
    lines.map(line => rough.Line(rough.Point(line.p1.x, line.p1.y), rough.Point(line.p2.x, line.p2.y)))
  }
}
