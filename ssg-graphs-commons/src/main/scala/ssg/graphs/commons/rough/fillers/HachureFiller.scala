/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * roughjs hachure filler (base PatternFiller) — Scala 3 port
 *
 * Original source: roughjs (src/fillers/hachure-filler.ts)
 * Original author: Preet Shihn
 * Original license: MIT
 * upstream-commit: 56a2762
 *
 * Migration notes:
 *   Renames: TS `class HachureFiller implements PatternFiller` -> Scala
 *     `class HachureFiller extends PatternFiller`. The `private helper` field is the
 *     constructor parameter (Scala makes it a private field because the methods reference
 *     it). `Point`/`Line` are the roughjs geometry types (`rough.Point` / `rough.Line`).
 *   Convention (inheritance, critical concern #2): this is the BASE class, extended by
 *     `ZigZagFiller` and `HatchFiller`; therefore NOT `final`. `_fillPolygons` and
 *     `renderLines` are `protected` (the TS `protected` members), so subclasses can call
 *     them. The public `fillPolygons` delegates to `_fillPolygons`.
 *   Convention: `ops.push(...this.helper.doubleLineOps(...))` spread-append ->
 *     `ops ++= helper.doubleLineOps(...)` on an `ArrayBuffer[Op]`.
 *   Idiom: `{ type: 'fillSketch', ops }` -> `OpSet(`type` = OpSetType.fillSketch, ops)`.
 *   Idiom (control flow): no `return`; the `for…of` loop -> a `for` over the lines.
 */
package ssg
package graphs
package commons
package rough
package fillers

import scala.collection.mutable.ArrayBuffer

/** Hachure fill-pattern generator (base class). Port of `class HachureFiller`. */
class HachureFiller(helper: RenderHelper) extends PatternFiller {

  /** Port of `fillPolygons(polygonList, o)`: delegates to `_fillPolygons`. */
  def fillPolygons(polygonList: Vector[Vector[rough.Point]], o: ResolvedOptions): OpSet =
    _fillPolygons(polygonList, o)

  /** Port of the protected `_fillPolygons(polygonList, o)`. */
  protected def _fillPolygons(polygonList: Vector[Vector[rough.Point]], o: ResolvedOptions): OpSet = {
    val lines: Vector[rough.Line] = ScanLineHachure.polygonHachureLines(polygonList, o)
    val ops:   Vector[Op]         = renderLines(lines, o)
    OpSet(`type` = OpSetType.fillSketch, ops = ops)
  }

  /** Port of the protected `renderLines(lines, o)`. */
  protected def renderLines(lines: Vector[rough.Line], o: ResolvedOptions): Vector[Op] = {
    val ops: ArrayBuffer[Op] = ArrayBuffer.empty
    for (line <- lines)
      ops ++= helper.doubleLineOps(line.p1.x, line.p1.y, line.p2.x, line.p2.y, o)
    ops.toVector
  }
}
