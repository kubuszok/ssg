/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * roughjs geometry primitives (Point/Line/Rectangle + lineLength) — Scala 3 port
 *
 * Original source: roughjs (src/geometry.ts)
 * Original author: Preet Shihn
 * Original license: MIT
 * upstream-commit: 56a2762
 *
 * Migration notes:
 *   Renames: the TS tuple aliases `Point = [number, number]` and `Line = [Point, Point]`
 *     and the `Rectangle` interface -> this library's OWN `final case class`es
 *     `Point`/`Line`/`Rectangle`, local to `ssg/graphs/commons/rough`. This roughjs
 *     `Point` is intentionally NOT shared with the Chip 2 `rough/curve` `Point` nor the
 *     Chip 3 `rough/fillers` (hachure) `Point`; each is a separate vendored library with
 *     its own semantics. Later chips/adapters bridge between them where roughjs hands
 *     points to points-on-curve / hachure-fill.
 *   Convention: index access `p[0]`/`p[1]` -> `p.x`/`p.y`; `line[0]`/`line[1]` ->
 *     `line.p1`/`line.p2`. `Math.pow(d, 2)` is preserved verbatim (not rewritten to
 *     `d * d`) to keep the libm path bit-identical to upstream.
 *   Idiom: `lineLength` is a pure expression; no `return`/control flow.
 */
package ssg
package graphs
package commons
package rough

/** A 2D point. Port of the roughjs `Point = [number, number]` tuple type. */
final case class Point(x: Double, y: Double)

/** A line segment. Port of the roughjs `Line = [Point, Point]` tuple type. */
final case class Line(p1: Point, p2: Point)

/** An axis-aligned rectangle. Port of the roughjs `Rectangle` interface. */
final case class Rectangle(x: Double, y: Double, width: Double, height: Double)

/** roughjs geometry helpers (port of the module-level functions of `geometry.ts`). */
object Geometry {

  /** Port of `lineLength(line)`: the Euclidean distance between the line's endpoints,
    * `Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2))`.
    */
  def lineLength(line: Line): Double = {
    val p1: Point = line.p1
    val p2: Point = line.p2
    Math.sqrt(Math.pow(p1.x - p2.x, 2) + Math.pow(p1.y - p2.y, 2))
  }
}
