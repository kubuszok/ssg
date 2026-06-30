/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * roughjs dashed filler (PatternFiller) — Scala 3 port
 *
 * Original source: roughjs (src/fillers/dashed-filler.ts)
 * Original author: Preet Shihn
 * Original license: MIT
 * upstream-commit: 56a2762
 *
 * Migration notes:
 *   Renames: TS `class DashedFiller implements PatternFiller` -> Scala
 *     `final class DashedFiller extends PatternFiller` (leaf -> `final`). `private
 *     helper` is the constructor parameter. `Point`/`Line`/`lineLength` are the roughjs
 *     geometry types/helper (`rough.Point` / `rough.Line` / `Geometry.lineLength`).
 *   Idiom (`<0` fallbacks): the nested ternaries for `offset`/`gap`
 *     (`o.dashOffset < 0 ? (o.hachureGap < 0 ? strokeWidth*4 : hachureGap) : dashOffset`)
 *     are ported verbatim as nested `if`/`else`.
 *   Idiom (`Math.floor`, critical concern #6): `Math.floor(length / (offset + gap))` is
 *     kept as a whole-valued `Double` `count` (the loop bound).
 *   Idiom (`p1[0] > p2[0]` swap / div-by-zero, critical concern #6): endpoints swapped so
 *     `p1` is the left point; a vertical segment yields `Math.atan(±Infinity) -> ±π/2`.
 *   Idiom (control flow): no `return`; `lines.forEach` -> `lines.foreach`.
 */
package ssg
package graphs
package commons
package rough
package fillers

import scala.collection.mutable.ArrayBuffer

/** Dashed fill-pattern generator. Port of `class DashedFiller`. */
final class DashedFiller(helper: RenderHelper) extends PatternFiller {

  def fillPolygons(polygonList: Vector[Vector[rough.Point]], o: ResolvedOptions): OpSet =
    OpSet(`type` = OpSetType.fillSketch, ops = dashedLine(ScanLineHachure.polygonHachureLines(polygonList, o), o))

  private def dashedLine(lines: Vector[rough.Line], o: ResolvedOptions): Vector[Op] = {
    val offset: Double =
      if (o.dashOffset < 0) (if (o.hachureGap < 0) o.strokeWidth * 4 else o.hachureGap) else o.dashOffset
    val gap: Double =
      if (o.dashGap < 0) (if (o.hachureGap < 0) o.strokeWidth * 4 else o.hachureGap) else o.dashGap
    val ops: ArrayBuffer[Op] = ArrayBuffer.empty
    lines.foreach { line =>
      val length:      Double = Geometry.lineLength(line)
      val count:       Double = Math.floor(length / (offset + gap))
      val startOffset: Double = (length + gap - (count * (offset + gap))) / 2
      var p1: rough.Point = line.p1
      var p2: rough.Point = line.p2
      if (p1.x > p2.x) {
        p1 = line.p2
        p2 = line.p1
      }
      val alpha: Double = Math.atan((p2.y - p1.y) / (p2.x - p1.x))
      var i: Int = 0
      while (i < count) {
        val lstart: Double = i * (offset + gap)
        val lend:   Double = lstart + offset
        val startX: Double = p1.x + (lstart * Math.cos(alpha)) + (startOffset * Math.cos(alpha))
        val startY: Double = p1.y + lstart * Math.sin(alpha) + (startOffset * Math.sin(alpha))
        val endX:   Double = p1.x + (lend * Math.cos(alpha)) + (startOffset * Math.cos(alpha))
        val endY:   Double = p1.y + (lend * Math.sin(alpha)) + (startOffset * Math.sin(alpha))
        ops ++= helper.doubleLineOps(startX, startY, endX, endY, o)
        i += 1
      }
    }
    ops.toVector
  }
}
