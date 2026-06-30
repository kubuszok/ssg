/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * roughjs zig-zag-line filler (PatternFiller) — Scala 3 port
 *
 * Original source: roughjs (src/fillers/zigzag-line-filler.ts)
 * Original author: Preet Shihn
 * Original license: MIT
 * upstream-commit: 56a2762
 *
 * Migration notes:
 *   Renames: TS `class ZigZagLineFiller implements PatternFiller` -> Scala
 *     `final class ZigZagLineFiller extends PatternFiller` (leaf -> `final`). `private
 *     helper` is the constructor parameter. `Point`/`Line`/`lineLength` are the roughjs
 *     geometry types/helper (`rough.Point` / `rough.Line` / `Geometry.lineLength`).
 *   Idiom (`Object.assign`): `o = Object.assign({}, o, { hachureGap: gap + zo })` ->
 *     `val o2 = o.copy(hachureGap = gap + zo)`; `o2` is what is passed onward (the TS
 *     reassigns the `o` parameter, then uses that reassigned value).
 *   Idiom (`Math.round`, critical concern #6): `Math.round(length / (2 * zo))` returns a
 *     whole-valued count; Java `Math.round(Double): Long` matches JS half-up-toward-+Inf.
 *   Idiom (`p1[0] > p2[0]` swap, critical concern #6/#7): the endpoints are swapped so
 *     `p1` is the left point; preserved verbatim (mutation-pinned).
 *   Idiom (div-by-zero, critical concern #6): a vertical segment makes
 *     `(p2.y - p1.y) / (p2.x - p1.x)` a `±Infinity` and `Math.atan(±Infinity)` -> `±π/2`;
 *     preserved (no guard added).
 *   Idiom (control flow): no `return`; `lines.forEach` -> `lines.foreach`.
 */
package ssg
package graphs
package commons
package rough
package fillers

import scala.collection.mutable.ArrayBuffer

/** Zig-zag-line fill-pattern generator. Port of `class ZigZagLineFiller`. */
final class ZigZagLineFiller(helper: RenderHelper) extends PatternFiller {

  def fillPolygons(polygonList: Vector[Vector[rough.Point]], o: ResolvedOptions): OpSet = {
    val gap: Double = if (o.hachureGap < 0) o.strokeWidth * 4 else o.hachureGap
    val zo:  Double = if (o.zigzagOffset < 0) gap else o.zigzagOffset
    val o2:  ResolvedOptions = o.copy(hachureGap = gap + zo)
    val lines: Vector[rough.Line] = ScanLineHachure.polygonHachureLines(polygonList, o2)
    OpSet(`type` = OpSetType.fillSketch, ops = zigzagLines(lines, zo, o2))
  }

  private def zigzagLines(lines: Vector[rough.Line], zo: Double, o: ResolvedOptions): Vector[Op] = {
    val ops: ArrayBuffer[Op] = ArrayBuffer.empty
    lines.foreach { line =>
      val length: Double = Geometry.lineLength(line)
      val count:  Long   = Math.round(length / (2 * zo))
      var p1: rough.Point = line.p1
      var p2: rough.Point = line.p2
      if (p1.x > p2.x) {
        p1 = line.p2
        p2 = line.p1
      }
      val alpha: Double = Math.atan((p2.y - p1.y) / (p2.x - p1.x))
      var i: Int = 0
      while (i < count) {
        val lstart: Double = i * 2 * zo
        val lend:   Double = (i + 1) * 2 * zo
        val dz:     Double = Math.sqrt(2 * Math.pow(zo, 2))
        val startX: Double = p1.x + (lstart * Math.cos(alpha))
        val startY: Double = p1.y + lstart * Math.sin(alpha)
        val endX:   Double = p1.x + (lend * Math.cos(alpha))
        val endY:   Double = p1.y + (lend * Math.sin(alpha))
        val middleX: Double = startX + dz * Math.cos(alpha + Math.PI / 4)
        val middleY: Double = startY + dz * Math.sin(alpha + Math.PI / 4)
        ops ++= helper.doubleLineOps(startX, startY, middleX, middleY, o)
        ops ++= helper.doubleLineOps(middleX, middleY, endX, endY, o)
        i += 1
      }
    }
    ops.toVector
  }
}
