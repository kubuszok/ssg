/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * roughjs zig-zag filler (extends HachureFiller) — Scala 3 port
 *
 * Original source: roughjs (src/fillers/zigzag-filler.ts)
 * Original author: Preet Shihn
 * Original license: MIT
 * upstream-commit: 56a2762
 *
 * Migration notes:
 *   Renames: TS `class ZigZagFiller extends HachureFiller` -> Scala
 *     `final class ZigZagFiller extends HachureFiller(helper)` (leaf class -> `final`).
 *     `Point`/`Line`/`lineLength` are the roughjs geometry types/helper
 *     (`rough.Point` / `rough.Line` / `Geometry.lineLength`).
 *   Idiom (`Object.assign`, critical concern #3): `Object.assign({}, o, { hachureGap:
 *     gap })` -> `o.copy(hachureGap = gap)` on the `ResolvedOptions` case class.
 *   Idiom (`if (lineLength([p1, p2]))`): JS-truthiness of a length (non-zero, non-NaN);
 *     a zero-length segment is skipped.
 *   Idiom (`[...p2]` spread): copies `p2` so the pushed line does not alias the source;
 *     the immutable `rough.Point(p2.x, p2.y)` is a faithful by-value copy.
 *   Idiom (control flow): no `return`; `for…of` destructuring `[p1, p2]` -> field access
 *     `line.p1` / `line.p2`.
 */
package ssg
package graphs
package commons
package rough
package fillers

import scala.collection.mutable.ArrayBuffer

/** Zig-zag fill-pattern generator. Port of `class ZigZagFiller`. */
final class ZigZagFiller(helper: RenderHelper) extends HachureFiller(helper) {

  override def fillPolygons(polygonList: Vector[Vector[rough.Point]], o: ResolvedOptions): OpSet = {
    var gap: Double = o.hachureGap
    if (gap < 0) {
      gap = o.strokeWidth * 4
    }
    gap = Math.max(gap, 0.1)
    val o2:          ResolvedOptions         = o.copy(hachureGap = gap)
    val lines:       Vector[rough.Line]      = ScanLineHachure.polygonHachureLines(polygonList, o2)
    val zigZagAngle: Double                  = (Math.PI / 180) * o.hachureAngle
    val zigzagLines: ArrayBuffer[rough.Line] = ArrayBuffer.empty
    val dgx:         Double                  = gap * 0.5 * Math.cos(zigZagAngle)
    val dgy:         Double                  = gap * 0.5 * Math.sin(zigZagAngle)
    for (line <- lines) {
      val p1: rough.Point = line.p1
      val p2: rough.Point = line.p2
      // `if (lineLength([p1, p2]))`: skip zero-length segments.
      if (truthy(Geometry.lineLength(rough.Line(p1, p2)))) {
        zigzagLines += rough.Line(rough.Point(p1.x - dgx, p1.y + dgy), rough.Point(p2.x, p2.y))
        zigzagLines += rough.Line(rough.Point(p1.x + dgx, p1.y - dgy), rough.Point(p2.x, p2.y))
      }
    }
    val ops: Vector[Op] = renderLines(zigzagLines.toVector, o)
    OpSet(`type` = OpSetType.fillSketch, ops = ops)
  }

  // JS truthiness of a number: non-zero and not NaN (0, -0 and NaN are falsy).
  private def truthy(d: Double): Boolean =
    d != 0.0 && !d.isNaN
}
