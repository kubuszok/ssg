/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Values-asserting tests for the hachure-fill scan-line polygon fill port
 * (Chip 3 of ISS-1204).
 *
 * Oracle: a faithful JS transcription of the vendored TS source
 * (original-src/hachure-fill/src/hachure.ts, pinned 80e47ba) run under Node. Every
 * expected `Line` below is asserted EXACTLY for the pure scan-line (hachureAngle == 0)
 * cases — those coordinates flow only through `+ - * /`, `Math.min`/`Math.max` and
 * `Math.round`, all bit-identical between JS and Scala on every platform. The single
 * rotated case (hachureAngle == 30) flows through `Math.cos`/`Math.sin` (libm, not
 * guaranteed bit-identical across JVM/JS/Native) and is asserted with a tight 1e-9
 * tolerance — far tighter than any structural mutation effect.
 *
 * Each branch is pinned by distinguishing values: gap-skipping (`iteration % gap == 0`)
 * via gap 4 vs gap 1; the `hachureStepOffset != 1` short-circuit via stepOffset 0.5;
 * `Math.round` half-up via a slanted edge landing on x.5; the `ymax <= y` active-edge
 * eviction via a gap-1 rectangle (no line at the top edge); the active-edge pairing
 * `i += 2` via a two-rectangle list (4 active edges per row); the `edges.sort` ymin
 * branch via a triangle with edges of differing ymin; horizontal-edge skipping and
 * open-polygon auto-closing via a triangle; and the single-vs-list detection via the
 * same geometry passed both shapes (plus the documented x==0-origin deviation).
 */
package ssg
package graphs
package commons
package rough
package fillers

import munit.FunSuite

final class HachureFillIss1204Suite extends FunSuite {

  private def p(x: Double, y: Double):                           Point = Point(x, y)
  private def l(x1: Double, y1: Double, x2: Double, y2: Double): Line  =
    Line(Point(x1, y1), Point(x2, y2))

  private def assertLinesApprox(
    obtained: Vector[Line],
    expected: Vector[(Double, Double, Double, Double)],
    delta:    Double
  ): Unit = {
    assertEquals(obtained.length, expected.length, "line count")
    obtained.zip(expected).zipWithIndex.foreach { case ((line, (x1, y1, x2, y2)), i) =>
      assertEqualsDouble(line.p1.x, x1, delta, s"line $i p1.x")
      assertEqualsDouble(line.p1.y, y1, delta, s"line $i p1.y")
      assertEqualsDouble(line.p2.x, x2, delta, s"line $i p2.x")
      assertEqualsDouble(line.p2.y, y2, delta, s"line $i p2.y")
    }
  }

  // ---- A: simple axis-aligned rectangle, angle 0, gap 4 (gap-skipping) ----

  test("rectangle angle 0 gap 4 -> horizontal fill every 4th iteration") {
    val rect: Vector[Point] = Vector(p(2, 2), p(12, 2), p(12, 12), p(2, 12))
    assertEquals(
      HachureFill.hachureLines(rect, 4, 0, 1),
      Vector(
        l(2, 2, 12, 2),
        l(2, 6, 12, 6),
        l(2, 10, 12, 10)
      )
    )
  }

  // ---- B: two-rectangle Polygon LIST, angle 0, gap 1 (pairing i += 2, list shape) ----

  test("two-rectangle list angle 0 gap 1 -> 4 active edges paired (i += 2)") {
    val twoRects: Vector[Vector[Point]] = Vector(
      Vector(p(0, 0), p(10, 0), p(10, 10), p(0, 10)),
      Vector(p(20, 0), p(30, 0), p(30, 10), p(20, 10))
    )
    val expected: Vector[Line] =
      (0 to 9).flatMap(y => Vector(l(0, y, 10, y), l(20, y, 30, y))).toVector
    assertEquals(HachureFill.hachureLines(twoRects, 1, 0, 1), expected)
  }

  // ---- C: rectangle, angle 0, gap 1 (ymax <= y eviction at the top edge) ----

  test("rectangle angle 0 gap 1 -> fills every row, no line at the top (ymax) edge") {
    val rect: Vector[Point] = Vector(p(1, 1), p(6, 1), p(6, 6), p(1, 6))
    assertEquals(
      HachureFill.hachureLines(rect, 1, 0, 1),
      Vector(
        l(1, 1, 6, 1),
        l(1, 2, 6, 2),
        l(1, 3, 6, 3),
        l(1, 4, 6, 4),
        l(1, 5, 6, 5)
      )
    )
  }

  // ---- D: single polygon, angle 30 (rotate forward, scan, rotate back) ----

  test("rectangle angle 30 gap 5 -> rotated fill lines") {
    val rect:     Vector[Point]                            = Vector(p(1, 1), p(21, 1), p(21, 21), p(1, 21))
    val expected: Vector[(Double, Double, Double, Double)] = Vector(
      (0.6830127018922192, 1.1830127018922194, 0.6830127018922192, 1.1830127018922194),
      (0.5849364905389027, 7.013139720814412, 10.977241335952167, 1.0131397208144133),
      (1.3528856829700242, 12.343266739736606, 21.271469970012113, 0.8432667397366078),
      (1.2548094716167073, 18.173393758658797, 21.173393758658797, 6.6733937586588),
      (5.486860279185585, 21.50352077758099, 21.07531754730548, 12.503520777580992),
      (14.915063509461094, 21.833647796503186, 20.977241335952165, 18.333647796503186)
    )
    assertLinesApprox(HachureFill.hachureLines(rect, 5, 30, 1), expected, 1e-9)
  }

  // ---- E: stepOffset != 1 -> fills every iteration regardless of gap ----

  test("rectangle stepOffset 0.5 gap 3 -> fills every 0.5 step (stepOffset != 1 branch)") {
    val rect:     Vector[Point] = Vector(p(2, 0), p(12, 0), p(12, 10), p(2, 10))
    val expected: Vector[Line]  =
      (0 until 20).map(i => l(2, i * 0.5, 12, i * 0.5)).toVector
    assertEquals(HachureFill.hachureLines(rect, 3, 0, 0.5), expected)
  }

  // ---- F: horizontal edge skipped + open polygon auto-closed ----

  test("open triangle gap 1 -> horizontal edge skipped, polygon auto-closed") {
    val openTriangle: Vector[Point] = Vector(p(1, 1), p(9, 1), p(5, 9))
    assertEquals(
      HachureFill.hachureLines(openTriangle, 1, 0, 1),
      Vector(
        l(1, 1, 9, 1),
        l(2, 2, 9, 2),
        l(2, 3, 8, 3),
        l(3, 4, 8, 4),
        l(3, 5, 7, 5),
        l(4, 6, 7, 6),
        l(4, 7, 6, 7),
        l(5, 8, 6, 8)
      )
    )
  }

  test("closed triangle gap 1 == open triangle (auto-close is a no-op when already closed)") {
    val open:   Vector[Point] = Vector(p(1, 1), p(9, 1), p(5, 9))
    val closed: Vector[Point] = Vector(p(1, 1), p(9, 1), p(5, 9), p(1, 1))
    assertEquals(
      HachureFill.hachureLines(closed, 1, 0, 1),
      HachureFill.hachureLines(open, 1, 0, 1)
    )
  }

  // ---- G: single Polygon vs one-element Polygon LIST -> same geometry, same output ----

  test("single Polygon and one-element Polygon list produce identical lines") {
    val verts:    Vector[Point] = Vector(p(2, 2), p(12, 2), p(12, 12), p(2, 12))
    val asSingle: Vector[Line]  = HachureFill.hachureLines(verts, 4, 0, 1)
    val asList:   Vector[Line]  = HachureFill.hachureLines(Vector(verts), 4, 0, 1)
    val expected: Vector[Line]  = Vector(
      l(2, 2, 12, 2),
      l(2, 6, 12, 6),
      l(2, 10, 12, 10)
    )
    assertEquals(asSingle, expected)
    assertEquals(asList, expected)
  }

  // ---- H: slanted edge -> Math.round half-up (x lands on .5) ----

  test("slanted triangle gap 1 -> Math.round rounds half toward +Inf") {
    val triangle: Vector[Point] = Vector(p(1, 0), p(6, 10), p(11, 0))
    assertEquals(
      HachureFill.hachureLines(triangle, 1, 0, 1),
      Vector(
        l(1, 0, 11, 0),
        l(2, 1, 11, 1),
        l(2, 2, 10, 2),
        l(3, 3, 10, 3),
        l(3, 4, 9, 4),
        l(4, 5, 9, 5),
        l(4, 6, 8, 6),
        l(5, 7, 8, 7),
        l(5, 8, 7, 8),
        l(6, 9, 7, 9)
      )
    )
  }

  // ---- I: triangle with edges of differing ymin (edges.sort ymin branch) ----

  test("triangle differing edge ymin gap 1 -> edges sorted ascending by ymin") {
    val triangle: Vector[Point] = Vector(p(1, 1), p(5, 5), p(1, 9))
    assertEquals(
      HachureFill.hachureLines(triangle, 1, 0, 1),
      Vector(
        l(1, 1, 1, 1),
        l(1, 2, 2, 2),
        l(1, 3, 3, 3),
        l(1, 4, 4, 4),
        l(1, 5, 5, 5),
        l(1, 6, 4, 6),
        l(1, 7, 3, 7),
        l(1, 8, 2, 8)
      )
    )
  }

  // ---- Deviation pin: x==0-origin single Polygon is filled (not misclassified) ----

  test("single Polygon whose first vertex x is 0 is filled (documented deviation)") {
    // The original JS misclassifies this single Polygon as a Polygon-list (because
    // `polygons[0][0]` is the falsy number 0); the typed port treats it as single and
    // fills it. Expected == the original's output for the same geometry passed as an
    // explicit one-element list.
    val rect: Vector[Point] = Vector(p(0, 0), p(10, 0), p(10, 10), p(0, 10))
    assertEquals(
      HachureFill.hachureLines(rect, 4, 0, 1),
      Vector(
        l(0, 0, 10, 0),
        l(0, 4, 10, 4),
        l(0, 8, 10, 8)
      )
    )
  }

  // ---- empty input -> empty result (no crash) ----

  test("empty polygon list -> empty result") {
    assertEquals(HachureFill.hachureLines(Vector.empty[Vector[Point]], 4, 0, 1), Vector.empty[Line])
  }
}
