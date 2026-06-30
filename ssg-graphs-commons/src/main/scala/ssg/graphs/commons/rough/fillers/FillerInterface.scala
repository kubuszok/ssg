/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * roughjs filler interfaces (PatternFiller + RenderHelper) — Scala 3 port
 *
 * Original source: roughjs (src/fillers/filler-interface.ts)
 * Original author: Preet Shihn
 * Original license: MIT
 * upstream-commit: 56a2762
 *
 * Migration notes:
 *   Renames: the TS `interface PatternFiller` / `interface RenderHelper` -> Scala
 *     `trait`s. The `Point` referenced by `PatternFiller.fillPolygons` is the roughjs
 *     geometry `Point` (`rough.Point` from `Geometry.scala`), NOT the mutable
 *     hachure-fill `Point` defined in this same `fillers` package — so it is referenced
 *     qualified as `rough.Point` throughout the fillers. `Op`/`OpSet`/`ResolvedOptions`
 *     come from `rough.Core`.
 *   Convention: `Point[][]` -> `Vector[Vector[rough.Point]]`; `Op[]` -> `Vector[Op]`;
 *     `number` -> `Double`.
 *   Idiom (chip boundary): `RenderHelper` is IMPLEMENTED by the renderer (Chip 6, NOT
 *     yet ported). This file defines only the trait; the fillers depend solely on the
 *     trait. The full trait (including `randOffset`/`randOffsetWithRange`, which the
 *     fillers here do not call) is ported for structural completeness.
 */
package ssg
package graphs
package commons
package rough
package fillers

/** A fill-pattern generator. Port of `interface PatternFiller`. */
trait PatternFiller {

  /** Port of `fillPolygons(polygonList, o)`: produce the fill `OpSet` for the given
    * polygon list under the resolved options.
    */
  def fillPolygons(polygonList: Vector[Vector[rough.Point]], o: ResolvedOptions): OpSet
}

/** The rendering callbacks a filler needs from the renderer. Port of
  * `interface RenderHelper`. Implemented by the renderer (Chip 6).
  */
trait RenderHelper {

  /** Port of `randOffset(x, o)`. */
  def randOffset(x: Double, o: ResolvedOptions): Double

  /** Port of `randOffsetWithRange(min, max, o)`. */
  def randOffsetWithRange(min: Double, max: Double, o: ResolvedOptions): Double

  /** Port of `ellipse(x, y, width, height, o)`. */
  def ellipse(x: Double, y: Double, width: Double, height: Double, o: ResolvedOptions): OpSet

  /** Port of `doubleLineOps(x1, y1, x2, y2, o)`. */
  def doubleLineOps(x1: Double, y1: Double, x2: Double, y2: Double, o: ResolvedOptions): Vector[Op]
}
