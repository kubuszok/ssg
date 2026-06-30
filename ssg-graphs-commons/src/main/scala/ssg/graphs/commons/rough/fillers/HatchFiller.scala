/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * roughjs hatch (cross-hatch) filler (extends HachureFiller) — Scala 3 port
 *
 * Original source: roughjs (src/fillers/hatch-filler.ts)
 * Original author: Preet Shihn
 * Original license: MIT
 * upstream-commit: 56a2762
 *
 * Migration notes:
 *   Renames: TS `class HatchFiller extends HachureFiller` -> Scala
 *     `final class HatchFiller extends HachureFiller(helper)` (leaf -> `final`).
 *   Convention (inheritance, critical concern #2): calls the inherited `protected`
 *     `_fillPolygons` TWICE — once at the configured angle, once at `angle + 90` — and
 *     concatenates the two op lists (cross-hatch).
 *   Idiom (`Object.assign`): `Object.assign({}, o, { hachureAngle: o.hachureAngle + 90 })`
 *     -> `o.copy(hachureAngle = o.hachureAngle + 90)`.
 *   Idiom (`set.ops = set.ops.concat(set2.ops)`): the TS mutates `set.ops` then returns
 *     `set`; `OpSet` is an immutable case class here, so the equivalent is
 *     `set.copy(ops = set.ops ++ set2.ops)` (same `type`, concatenated ops).
 *   Idiom (control flow): no `return`; the body is a single expression chain.
 */
package ssg
package graphs
package commons
package rough
package fillers

/** Cross-hatch fill-pattern generator. Port of `class HatchFiller`. */
final class HatchFiller(helper: RenderHelper) extends HachureFiller(helper) {

  override def fillPolygons(polygonList: Vector[Vector[rough.Point]], o: ResolvedOptions): OpSet = {
    val set:  OpSet           = _fillPolygons(polygonList, o)
    val o2:   ResolvedOptions = o.copy(hachureAngle = o.hachureAngle + 90)
    val set2: OpSet           = _fillPolygons(polygonList, o2)
    set.copy(ops = set.ops ++ set2.ops)
  }
}
