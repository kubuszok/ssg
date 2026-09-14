/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * roughjs dot filler (PatternFiller) — Scala 3 port
 *
 * Original source: roughjs (src/fillers/dot-filler.ts)
 * Original author: Preet Shihn
 * Original license: MIT
 * upstream-commit: 56a2762
 *
 * Migration notes:
 *   Renames: TS `class DotFiller implements PatternFiller` -> Scala
 *     `final class DotFiller extends PatternFiller` (leaf -> `final`). `private helper`
 *     is the constructor parameter. `Point`/`Line`/`lineLength` are the roughjs geometry
 *     types/helper (`rough.Point` / `rough.Line` / `Geometry.lineLength`).
 *   Idiom (`Object.assign`): `Object.assign({}, o, { hachureAngle: 0 })` ->
 *     `o.copy(hachureAngle = 0)`.
 *   Idiom (`Math.ceil`/`Math.min`, critical concern #6): `Math.ceil(dl) - 1` is kept as a
 *     whole-valued `Double` `count` (used in `count * gap` and as the loop bound);
 *     `Math.min(line[0][1], line[1][1])` -> `Math.min(...)`.
 *   Idiom (`Math.random` for cx/cy, critical concern #5, DOCUMENTED NON-DETERMINISM):
 *     the dot centre jitter `cx`/`cy` uses BARE `Math.random()` (NOT `o.randomizer`), so
 *     it is inherently non-deterministic even with a seeded randomizer — faithful to
 *     upstream. Mapped to `RoughMath.unseededRandom()`. The dot COUNT, `offset`, `x` and
 *     `minY` are deterministic; only the centre offset within `[x-ro, x+ro] ×
 *     [y-ro, y+ro]` is random.
 *   Idiom (control flow): no `return`; `for…of` -> `for`.
 */
package ssg
package graphs
package commons
package rough
package fillers

import scala.collection.mutable.ArrayBuffer

/** Dot fill-pattern generator. Port of `class DotFiller`. */
final class DotFiller(helper: RenderHelper) extends PatternFiller {

  def fillPolygons(polygonList: Vector[Vector[rough.Point]], o: ResolvedOptions): OpSet = {
    val o2:    ResolvedOptions    = o.copy(hachureAngle = 0)
    val lines: Vector[rough.Line] = ScanLineHachure.polygonHachureLines(polygonList, o2)
    dotsOnLines(lines, o2)
  }

  private def dotsOnLines(lines: Vector[rough.Line], o: ResolvedOptions): OpSet = {
    val ops: ArrayBuffer[Op] = ArrayBuffer.empty
    var gap: Double          = o.hachureGap
    if (gap < 0) {
      gap = o.strokeWidth * 4
    }
    gap = Math.max(gap, 0.1)
    var fweight: Double = o.fillWeight
    if (fweight < 0) {
      fweight = o.strokeWidth / 2
    }
    val ro: Double = gap / 4
    for (line <- lines) {
      val length: Double = Geometry.lineLength(line)
      val dl:     Double = length / gap
      val count:  Double = Math.ceil(dl) - 1
      val offset: Double = length - (count * gap)
      val x:      Double = ((line.p1.x + line.p2.x) / 2) - (gap / 4)
      val minY:   Double = Math.min(line.p1.y, line.p2.y)

      var i: Int = 0
      while (i < count) {
        val y:  Double = minY + offset + (i * gap)
        val cx: Double = (x - ro) + RoughMath.unseededRandom() * 2 * ro
        val cy: Double = (y - ro) + RoughMath.unseededRandom() * 2 * ro
        val el: OpSet  = helper.ellipse(cx, cy, fweight, fweight, o)
        ops ++= el.ops
        i += 1
      }
    }
    OpSet(`type` = OpSetType.fillSketch, ops = ops.toVector)
  }
}
