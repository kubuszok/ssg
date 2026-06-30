/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scan-line polygon hachure fill — Scala 3 port
 *
 * Original source: hachure-fill (src/hachure.ts)
 * Original author: pshihn
 * Original license: MIT
 * upstream-commit: 80e47ba
 *
 * Migration notes:
 *   Renames: the TS tuple aliases `Point = [number, number]`, `Line = [Point, Point]`,
 *     `Polygon = Point[]` -> this library's OWN `Point`/`Line`/`Polygon` defined here.
 *     These are intentionally NOT shared with the `rough/curve` `Point` nor with the
 *     (later) roughjs `geometry.ts` `Point`; each is a separate library with separate
 *     semantics. Chip 5's `scan-line-hachure` will adapt roughjs points to these.
 *     The `EdgeEntry`/`ActiveEdgeEntry` interfaces -> `final case class`es; the
 *     `ActiveEdgeEntry.s` field is set-but-never-read in the original (a dead field on
 *     the interface) and is preserved here verbatim for structural fidelity.
 *   Convention: index access `p[0]`/`p[1]` -> `p.x`/`p.y`. `Point[]`/`Line[]` ->
 *     `Vector[...]` for read-only inputs and public results; the mutable scan-state
 *     accumulators (`vertexArray`, `edges`, `activeEdges`, `lines`) -> `ArrayBuffer`.
 *   Idiom (in-place mutation, critical concern #1): the original MUTATES point
 *     coordinates in place — `rotatePoints` writes `p[0]`/`p[1]`, and `hachureLines`
 *     rotates the caller's polygons FORWARD by `angle`, scans, then rotates them BACK
 *     by `-angle` (restoring them, modulo floating-point residue) and rotates the
 *     output lines by `-angle`. To mirror this exactly we model `Point` as a MUTABLE
 *     value (`var x`, `var y`) and share point references between polygons, the flat
 *     point list built by `rotateLines`, and the emitted lines — so a single in-place
 *     rotate mutates every alias just as the JS object-reference aliasing does. This
 *     makes the rotate/restore 1:1 and reproduces the same caller-side residue the
 *     original leaves; a functional rotate-and-replace would instead leave the caller's
 *     input pristine, which differs from the original's observable post-call state.
 *   Idiom (float modulo, critical concern #2): `iteration % gap === 0` where `gap` is a
 *     Double and `iteration` an int -> `iteration.toDouble % gap == 0.0`. Scala/Java `%`
 *     on Double is the same truncated (fmod-like) remainder as JS `%`, so fractional
 *     gaps (e.g. `gap == 0.1`) reproduce the JS FP result bit-for-bit. The
 *     `hachureStepOffset !== 1 ||` short-circuit is preserved: when stepOffset != 1 the
 *     gap test is never evaluated and every iteration fills.
 *   Idiom (Math.round, critical concern #3): JS `Math.round` rounds half toward +Inf
 *     (`round(-0.5)==0`, `round(0.5)==1`). Java `Math.round(Double): Long` is
 *     `floor(x + 0.5)`, which matches on all signs; used as `Math.round(x).toDouble`
 *     for the emitted line x-coords.
 *   Idiom (sort comparators, critical concern #4): both `edges.sort` and
 *     `activeEdges.sort` return a -1/0/+1 (the `(a-b)/abs(a-b)` sign with a `=== 0`
 *     tie). Ported as Int-returning comparators (each branch preserved literally) fed
 *     to `sortInPlaceWith((a, b) => cmp(a, b) < 0)`. Scala's sort is stable, matching
 *     modern V8's stable `Array.sort` tie behavior. Note: only the `ymin` branch of
 *     `edges.sort` is observable — the algorithm relies on ymin-ascending order for the
 *     contiguous-prefix splice, but every equal-ymin edge activates together and
 *     `activeEdges.sort` re-sorts by `x` each scanline, so the `edges.sort` `x`/`ymax`
 *     sub-branches do not affect output (an equivalent-mutant situation).
 *   Idiom (splice, critical concern #5): `edges.splice(0, ix + 1)` -> capture
 *     `edges.take(ix + 1)` then `edges.remove(0, ix + 1)`; the preceding `ix` scan
 *     (`if (ymin > y) break; ix = i`) uses `boundary`/`break`.
 *   Idiom (single-vs-list detection, critical concern re entry): the original
 *     `(polygons[0] && polygons[0][0] && typeof polygons[0][0] === 'number')` picks
 *     "single Polygon" when the first element's first member is a number (i.e. the
 *     first element is a `Point`, not a `Polygon`). Ported via a Scala 3 union
 *     `Polygon | Vector[Polygon]` and a runtime element-type check: single iff the
 *     input is non-empty and its head is a `Point`. DEVIATION (documented): the
 *     original additionally requires `polygons[0][0]` to be JS-truthy, so a single
 *     Polygon whose first vertex has `x` in {0, -0, NaN} is MISCLASSIFIED as a
 *     Polygon-list — which, downstream, either throws (angle != 0, rotating a numeric
 *     primitive) or yields no edges (angle == 0). That sub-condition is a
 *     dynamic-typing artifact: a latent bug reachable only through JS primitive
 *     spreading, which cannot occur in the statically-typed port where the caller's
 *     type unambiguously selects single vs list. The port therefore drops the falsy-x
 *     sub-condition and treats every first-element-is-a-Point input as a single
 *     Polygon (the `typeof === 'number'` intent), filling x==0-origin polygons
 *     correctly rather than reproducing the throw/empty. No other branch is dropped.
 *   Idiom (control flow): `if (angle)` / `if (points && points.length)` JS-truthiness
 *     -> explicit predicates (`truthy(angle)` = `!= 0.0 && !isNaN`; `points.nonEmpty`).
 *     The early `if (!edges.length) return lines` -> an `if/else` returning the
 *     accumulated lines (no `return`). Loop `break`s -> `boundary`/`break`.
 *   Note (tests): for `angle != 0` the output flows through `Math.cos`/`Math.sin`,
 *     which are libm functions not guaranteed bit-identical across JVM/JS/Native;
 *     rotated-output assertions therefore use a tight tolerance (1e-9). Pure scan-line
 *     (angle 0) output is integer/exact and asserted exactly on all platforms.
 */
package ssg
package graphs
package commons
package rough
package fillers

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

/** A 2D point. Port of the hachure-fill `Point = [number, number]` tuple type. Mutable
  * (`var x`/`var y`) so the in-place `rotatePoints` and the shared-reference aliasing of
  * the original are reproduced exactly. This `Point` is local to `rough/fillers`.
  */
final case class Point(var x: Double, var y: Double)

/** A line segment. Port of the hachure-fill `Line = [Point, Point]` tuple type. */
final case class Line(p1: Point, p2: Point)

/** An edge of the active-edge table. Port of the `EdgeEntry` interface; `x` is mutated
  * each scanline (`x += stepOffset * islope`), so it is a `var`.
  */
final case class EdgeEntry(ymin: Double, ymax: Double, var x: Double, islope: Double)

/** An entry of the active-edge list. Port of the `ActiveEdgeEntry` interface. `s` (the
  * scanline `y` at insertion) is set-but-never-read in the original and is preserved.
  */
final case class ActiveEdgeEntry(s: Double, edge: EdgeEntry)

/** Scan-line polygon hachure fill (port of hachure-fill `hachure.ts`). */
object HachureFill {

  /** A `Polygon` is a list of vertices. Port of `Polygon = Point[]`. */
  type Polygon = Vector[Point]

  // JS truthiness of a number: non-zero and not NaN (0, -0 and NaN are falsy).
  private def truthy(d: Double): Boolean =
    d != 0.0 && !d.isNaN

  private def rotatePoints(points: Vector[Point], center: Point, degrees: Double): Unit = {
    if (points.nonEmpty) {
      val cx: Double    = center.x
      val cy: Double    = center.y
      val angle: Double = (Math.PI / 180) * degrees
      val cos: Double   = Math.cos(angle)
      val sin: Double   = Math.sin(angle)
      for (p <- points) {
        val x: Double = p.x
        val y: Double = p.y
        p.x = ((x - cx) * cos) - ((y - cy) * sin) + cx
        p.y = ((x - cx) * sin) + ((y - cy) * cos) + cy
      }
    }
  }

  private def rotateLines(lines: Vector[Line], center: Point, degrees: Double): Unit = {
    val points: ArrayBuffer[Point] = ArrayBuffer.empty
    lines.foreach { line =>
      points += line.p1
      points += line.p2
    }
    rotatePoints(points.toVector, center, degrees)
  }

  private def areSamePoints(p1: Point, p2: Point): Boolean =
    p1.x == p2.x && p1.y == p2.y

  def hachureLines(
      polygons: Polygon | Vector[Polygon],
      hachureGap: Double,
      hachureAngle: Double,
      hachureStepOffset: Double = 1
  ): Vector[Line] = {
    val angle: Double = hachureAngle
    val gap: Double   = Math.max(hachureGap, 0.1)
    // Detect single Polygon vs Polygon list: single iff the first element is a Point
    // (the original's `typeof polygons[0][0] === 'number'`). See the header note on the
    // dropped falsy-x sub-condition.
    val pv: Vector[Any] = polygons.asInstanceOf[Vector[Any]]
    val polygonList: Vector[Polygon] =
      if (pv.nonEmpty && pv.head.isInstanceOf[Point]) {
        Vector(polygons.asInstanceOf[Polygon])
      } else {
        polygons.asInstanceOf[Vector[Polygon]]
      }

    val rotationCenter: Point = Point(0, 0)
    if (truthy(angle)) {
      for (polygon <- polygonList) {
        rotatePoints(polygon, rotationCenter, angle)
      }
    }
    val lines: Vector[Line] = straightHachureLines(polygonList, gap, hachureStepOffset)
    if (truthy(angle)) {
      for (polygon <- polygonList) {
        rotatePoints(polygon, rotationCenter, -angle)
      }
      rotateLines(lines, rotationCenter, -angle)
    }
    lines
  }

  private def straightHachureLines(
      polygons: Vector[Polygon],
      gapIn: Double,
      hachureStepOffset: Double
  ): Vector[Line] = {
    val vertexArray: ArrayBuffer[Vector[Point]] = ArrayBuffer.empty
    for (polygon <- polygons) {
      val vertices: ArrayBuffer[Point] = ArrayBuffer.from(polygon)
      if (!areSamePoints(vertices(0), vertices(vertices.length - 1))) {
        vertices += Point(vertices(0).x, vertices(0).y)
      }
      if (vertices.length > 2) {
        vertexArray += vertices.toVector
      }
    }

    val lines: ArrayBuffer[Line] = ArrayBuffer.empty
    val gap: Double              = Math.max(gapIn, 0.1)

    // Create sorted edges table
    val edges: ArrayBuffer[EdgeEntry] = ArrayBuffer.empty

    for (vertices <- vertexArray) {
      var i: Int = 0
      while (i < vertices.length - 1) {
        val p1: Point = vertices(i)
        val p2: Point = vertices(i + 1)
        if (p1.y != p2.y) {
          val ymin: Double = Math.min(p1.y, p2.y)
          edges += EdgeEntry(
            ymin = ymin,
            ymax = Math.max(p1.y, p2.y),
            x = if (ymin == p1.y) p1.x else p2.x,
            islope = (p2.x - p1.x) / (p2.y - p1.y)
          )
        }
        i += 1
      }
    }

    edges.sortInPlaceWith((e1, e2) => edgeCompare(e1, e2) < 0)
    if (edges.isEmpty) {
      lines.toVector
    } else {
      // Start scanning
      var activeEdges: ArrayBuffer[ActiveEdgeEntry] = ArrayBuffer.empty
      var y: Double                                 = edges(0).ymin
      var iteration: Int                            = 0
      while (activeEdges.nonEmpty || edges.nonEmpty) {
        if (edges.nonEmpty) {
          var ix: Int = -1
          boundary {
            var i: Int = 0
            while (i < edges.length) {
              if (edges(i).ymin > y) {
                break()
              }
              ix = i
              i += 1
            }
          }
          val removed: Vector[EdgeEntry] = edges.take(ix + 1).toVector
          edges.remove(0, ix + 1)
          removed.foreach { edge =>
            activeEdges += ActiveEdgeEntry(y, edge)
          }
        }
        activeEdges = activeEdges.filter { ae =>
          if (ae.edge.ymax <= y) {
            false
          } else {
            true
          }
        }
        activeEdges.sortInPlaceWith((ae1, ae2) => activeCompare(ae1, ae2) < 0)

        // fill between the edges
        if ((hachureStepOffset != 1) || (iteration.toDouble % gap == 0)) {
          if (activeEdges.length > 1) {
            boundary {
              var i: Int = 0
              while (i < activeEdges.length) {
                val nexti: Int = i + 1
                if (nexti >= activeEdges.length) {
                  break()
                }
                val ce: EdgeEntry = activeEdges(i).edge
                val ne: EdgeEntry = activeEdges(nexti).edge
                lines += Line(
                  Point(Math.round(ce.x).toDouble, y),
                  Point(Math.round(ne.x).toDouble, y)
                )
                i = i + 2
              }
            }
          }
        }
        y += hachureStepOffset
        activeEdges.foreach { ae =>
          ae.edge.x = ae.edge.x + (hachureStepOffset * ae.edge.islope)
        }
        iteration += 1
      }
      lines.toVector
    }
  }

  // edges.sort comparator: lexicographic (ymin, x, ymax) ascending; the final case
  // returns the (a-b)/abs(a-b) sign (+/-1) of the ymax difference.
  private def edgeCompare(e1: EdgeEntry, e2: EdgeEntry): Int = {
    if (e1.ymin < e2.ymin) {
      -1
    } else if (e1.ymin > e2.ymin) {
      1
    } else if (e1.x < e2.x) {
      -1
    } else if (e1.x > e2.x) {
      1
    } else if (e1.ymax == e2.ymax) {
      0
    } else {
      ((e1.ymax - e2.ymax) / Math.abs(e1.ymax - e2.ymax)).toInt
    }
  }

  // activeEdges.sort comparator: the (a-b)/abs(a-b) sign (+/-1) of the x difference,
  // with a 0 tie when the x's are equal.
  private def activeCompare(ae1: ActiveEdgeEntry, ae2: ActiveEdgeEntry): Int = {
    if (ae1.edge.x == ae2.edge.x) {
      0
    } else {
      ((ae1.edge.x - ae2.edge.x) / Math.abs(ae1.edge.x - ae2.edge.x)).toInt
    }
  }
}
