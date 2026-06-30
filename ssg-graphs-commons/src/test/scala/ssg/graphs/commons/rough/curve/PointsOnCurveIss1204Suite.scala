/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Values-asserting tests for the points-on-curve / curve-to-bezier / points-on-path
 * port (Chip 2 of ISS-1204).
 *
 * Oracle: a faithful JS transcription of the vendored TS sources
 * (original-src/points-on-curve, pinned 4824147; original-src/points-on-path, pinned
 * 7693ef0) run under Node. All expected literals below are asserted EXACTLY: every
 * coordinate flows through `+ - * /` and IEEE-754 `lerp`/`tan`-free arithmetic that is
 * bit-identical between JS and Scala, and the `distance`/`flatness` decisions (which
 * use `Math.pow`/`Math.sqrt`) are exercised far from their thresholds, so the produced
 * point lists match to the last bit. Each branch is pinned by distinguishing values:
 * `curveTightness` 0 vs 0.5 (the `s = 1 - curveTightness` factor); the `d > 1` dedup
 * across two bezier segments sharing an endpoint; the RDP split-vs-collapse; and the
 * pointsOnPath M/L/C/Z state machine incl. implicit-lastPoint seed and multi-subpath
 * split.
 */
package ssg
package graphs
package commons
package rough
package curve

import munit.FunSuite

final class PointsOnCurveIss1204Suite extends FunSuite {

  private def p(x: Double, y: Double): Point = Point(x, y)

  // ---- curveToBezier ----

  test("curveToBezier: len === 3 passthrough (clones p0,p1,p2,p2)") {
    assertEquals(
      CurveToBezier.curveToBezier(Vector(p(0.0, 0.0), p(10.0, 5.0), p(20.0, 0.0))),
      Vector(p(0.0, 0.0), p(10.0, 5.0), p(20.0, 0.0), p(20.0, 0.0))
    )
  }

  test("curveToBezier: len > 3, curveTightness 0 (s = 1)") {
    assertEquals(
      CurveToBezier.curveToBezier(Vector(p(0.0, 0.0), p(10.0, 20.0), p(30.0, 10.0), p(40.0, 30.0))),
      Vector(
        p(0.0, 0.0),
        p(1.6666666666666667, 3.3333333333333335),
        p(5.0, 18.333333333333332),
        p(10.0, 20.0),
        p(15.0, 21.666666666666668),
        p(25.0, 8.333333333333334),
        p(30.0, 10.0),
        p(35.0, 11.666666666666666),
        p(38.333333333333336, 26.666666666666668),
        p(40.0, 30.0)
      )
    )
  }

  test("curveToBezier: len > 3, curveTightness 0.5 (s = 0.5) differs from tightness 0") {
    val pts      = Vector(p(0.0, 0.0), p(10.0, 20.0), p(30.0, 10.0), p(40.0, 30.0))
    val tight    = CurveToBezier.curveToBezier(pts, 0.5)
    val loose    = CurveToBezier.curveToBezier(pts, 0)
    assertEquals(
      tight,
      Vector(
        p(0.0, 0.0),
        p(0.8333333333333334, 1.6666666666666667),
        p(7.5, 19.166666666666668),
        p(10.0, 20.0),
        p(12.5, 20.833333333333332),
        p(27.5, 9.166666666666666),
        p(30.0, 10.0),
        p(32.5, 10.833333333333334),
        p(39.166666666666664, 28.333333333333332),
        p(40.0, 30.0)
      )
    )
    assertNotEquals(tight, loose)
  }

  test("curveToBezier: fewer than three points throws CurveError with the original message") {
    val ex = intercept[CurveError] {
      CurveToBezier.curveToBezier(Vector(p(0.0, 0.0), p(1.0, 1.0)))
    }
    assertEquals(ex.getMessage, "A curve must have at least three points.")
  }

  // ---- pointsOnBezierCurves ----

  test("pointsOnBezierCurves: subdivision (flat >= tolerance) samples the curve") {
    assertEquals(
      PointsOnCurve.pointsOnBezierCurves(Vector(p(0.0, 0.0), p(0.0, 100.0), p(100.0, 100.0), p(100.0, 0.0))),
      Vector(
        p(0.0, 0.0), p(0.072479248046875, 4.6142578125), p(0.286865234375, 9.08203125),
        p(0.638580322265625, 13.4033203125), p(1.123046875, 17.578125), p(2.471923828125, 25.48828125),
        p(4.296875, 32.8125), p(6.561279296875, 39.55078125), p(9.228515625, 45.703125),
        p(12.261962890625, 51.26953125), p(15.625, 56.25), p(19.281005859375, 60.64453125),
        p(23.193359375, 64.453125), p(27.325439453125, 67.67578125), p(31.640625, 70.3125),
        p(36.102294921875, 72.36328125), p(40.673828125, 73.828125), p(45.318603515625, 74.70703125),
        p(50.0, 75.0), p(54.681396484375, 74.70703125), p(59.326171875, 73.828125),
        p(63.897705078125, 72.36328125), p(68.359375, 70.3125), p(72.674560546875, 67.67578125),
        p(76.806640625, 64.453125), p(80.718994140625, 60.64453125), p(84.375, 56.25),
        p(87.738037109375, 51.26953125), p(90.771484375, 45.703125), p(93.438720703125, 39.55078125),
        p(95.703125, 32.8125), p(97.528076171875, 25.48828125), p(98.876953125, 17.578125),
        p(99.36141967773438, 13.4033203125), p(99.713134765625, 9.08203125), p(99.92752075195312, 4.6142578125),
        p(100.0, 0.0)
      )
    )
  }

  test("pointsOnBezierCurves: two segments sharing an endpoint exercise the d > 1 dedup") {
    assertEquals(
      PointsOnCurve.pointsOnBezierCurves(
        Vector(
          p(0.0, 0.0), p(0.0, 50.0), p(50.0, 50.0), p(50.0, 0.0),
          p(50.0, -50.0), p(100.0, -50.0), p(100.0, 0.0)
        )
      ),
      Vector(
        p(0.0, 0.0), p(0.1434326171875, 4.541015625), p(0.5615234375, 8.7890625),
        p(1.2359619140625, 12.744140625), p(2.1484375, 16.40625), p(3.2806396484375, 19.775390625),
        p(4.6142578125, 22.8515625), p(6.1309814453125, 25.634765625), p(7.8125, 28.125),
        p(9.6405029296875, 30.322265625), p(11.5966796875, 32.2265625), p(13.6627197265625, 33.837890625),
        p(15.8203125, 35.15625), p(18.0511474609375, 36.181640625), p(20.3369140625, 36.9140625),
        p(22.6593017578125, 37.353515625), p(25.0, 37.5), p(27.3406982421875, 37.353515625),
        p(29.6630859375, 36.9140625), p(31.9488525390625, 36.181640625), p(34.1796875, 35.15625),
        p(36.3372802734375, 33.837890625), p(38.4033203125, 32.2265625), p(40.3594970703125, 30.322265625),
        p(42.1875, 28.125), p(43.8690185546875, 25.634765625), p(45.3857421875, 22.8515625),
        p(46.7193603515625, 19.775390625), p(47.8515625, 16.40625), p(48.7640380859375, 12.744140625),
        p(49.4384765625, 8.7890625), p(49.8565673828125, 4.541015625), p(50.0, 0.0),
        p(50.1434326171875, -4.541015625), p(50.5615234375, -8.7890625), p(51.2359619140625, -12.744140625),
        p(52.1484375, -16.40625), p(53.2806396484375, -19.775390625), p(54.6142578125, -22.8515625),
        p(56.1309814453125, -25.634765625), p(57.8125, -28.125), p(59.6405029296875, -30.322265625),
        p(61.5966796875, -32.2265625), p(63.6627197265625, -33.837890625), p(65.8203125, -35.15625),
        p(68.0511474609375, -36.181640625), p(70.3369140625, -36.9140625), p(72.6593017578125, -37.353515625),
        p(75.0, -37.5), p(77.3406982421875, -37.353515625), p(79.6630859375, -36.9140625),
        p(81.9488525390625, -36.181640625), p(84.1796875, -35.15625), p(86.3372802734375, -33.837890625),
        p(88.4033203125, -32.2265625), p(90.3594970703125, -30.322265625), p(92.1875, -28.125),
        p(93.8690185546875, -25.634765625), p(95.3857421875, -22.8515625), p(96.7193603515625, -19.775390625),
        p(97.8515625, -16.40625), p(98.7640380859375, -12.744140625), p(99.4384765625, -8.7890625),
        p(99.8565673828125, -4.541015625), p(100.0, 0.0)
      )
    )
  }

  // Pins the `flatness` max-selection (`if (ux < vx) ux = vx` / the `uy` twin), i.e.
  // the subdivision DEPTH. This asymmetric, near-horizontal control polygon (x-spread
  // dominates) subdivides to 19 points under the correct `max` pick; flipping the pick
  // to `min` (`ux > vx`) under-subdivides to 18 points, so this exact list
  // distinguishes max-vs-min. Literals derived from the vendored TS under Node
  // (original-src/points-on-curve, pinned 4824147).
  test("pointsOnBezierCurves: asymmetric cubic pins the flatness max-selection (subdivision depth)") {
    val result = PointsOnCurve.pointsOnBezierCurves(
      Vector(p(0.0, 0.0), p(40.0, 0.0), p(40.0, 1.0), p(40.0, 2.0)),
      0.15
    )
    assertEquals(result.length, 19)
    assertEquals(
      result,
      Vector(
        p(0.0, 0.0), p(3.634033203125, 0.002899169921875), p(7.041015625, 0.011474609375),
        p(10.228271484375, 0.025543212890625), p(13.203125, 0.044921875), p(15.972900390625, 0.069427490234375),
        p(18.544921875, 0.098876953125), p(23.125, 0.171875), p(27.001953125, 0.262451171875),
        p(30.234375, 0.369140625), p(32.880859375, 0.490478515625), p(35.0, 0.625),
        p(36.650390625, 0.771240234375), p(37.890625, 0.927734375), p(38.779296875, 1.093017578125),
        p(39.375, 1.265625), p(39.736328125, 1.444091796875), p(39.921875, 1.626953125), p(40.0, 2.0)
      )
    )
  }

  // Pins the `d > 1` dedup branch's observable behavior at unit scale: the subdivided
  // curve's coincident leaf-boundary points are deduplicated. The correct skip yields
  // 5 points; dropping the dedup guard (always pushing `p0`) yields 8 (each interior
  // leaf boundary duplicated), so this exact list kills the guard-removal mutation.
  // NOTE: the `d > 1` -> `d > 0` flip specifically is an EQUIVALENT mutant — across
  // 5.4M dedup-checks over random multi-segment polygons, the checked `d` is always
  // exactly 0 (subdivision boundary points coincide bit-for-bit), so `0 > 1` and
  // `0 > 0` are identically false and no input can distinguish them. This assertion
  // pins the dedup's real, killable behavior instead. Literals derived from the
  // vendored TS under Node.
  test("pointsOnBezierCurves: unit-scale curve pins the d > 1 dedup (coincident boundary skip)") {
    val result = PointsOnCurve.pointsOnBezierCurves(
      Vector(p(0.0, 0.0), p(0.0, 1.0), p(1.0, 1.0), p(1.0, 0.0)),
      0.15
    )
    assertEquals(result.length, 5)
    assertEquals(
      result,
      Vector(p(0.0, 0.0), p(0.15625, 0.5625), p(0.5, 0.75), p(0.84375, 0.5625), p(1.0, 0.0))
    )
  }

  test("pointsOnBezierCurves: flat curve below tolerance emits just the segment endpoints") {
    assertEquals(
      PointsOnCurve.pointsOnBezierCurves(Vector(p(0.0, 0.0), p(0.1, 0.0), p(0.2, 0.0), p(0.3, 0.0))),
      Vector(p(0.0, 0.0), p(0.3, 0.0))
    )
  }

  test("pointsOnBezierCurves: positive distance simplifies; distance 0 and None do not") {
    val curve     = Vector(p(0.0, 0.0), p(0.0, 100.0), p(100.0, 100.0), p(100.0, 0.0))
    val simplified = PointsOnCurve.pointsOnBezierCurves(curve, 0.15, Some(30.0))
    assertEquals(simplified, Vector(p(0.0, 0.0), p(50.0, 75.0), p(100.0, 0.0)))
    // distance == 0 is falsy in the original `if (distance && distance > 0)` guard:
    val unsimplifiedZero = PointsOnCurve.pointsOnBezierCurves(curve, 0.15, Some(0.0))
    val unsimplifiedNone = PointsOnCurve.pointsOnBezierCurves(curve, 0.15, None)
    assertEquals(unsimplifiedZero, unsimplifiedNone)
    assertEquals(unsimplifiedZero.length, 37)
    assertNotEquals(simplified, unsimplifiedZero)
  }

  // ---- simplify (Ramer–Douglas–Peucker) ----

  test("simplify: far midpoint keeps the split point") {
    assertEquals(
      PointsOnCurve.simplify(Vector(p(0.0, 0.0), p(5.0, 10.0), p(10.0, 0.0)), 1),
      Vector(p(0.0, 0.0), p(5.0, 10.0), p(10.0, 0.0))
    )
  }

  test("simplify: collinear midpoint collapses to the endpoints") {
    assertEquals(
      PointsOnCurve.simplify(Vector(p(0.0, 0.0), p(5.0, 0.0), p(10.0, 0.0)), 1),
      Vector(p(0.0, 0.0), p(10.0, 0.0))
    )
  }

  test("simplify: large epsilon collapses even a far midpoint") {
    assertEquals(
      PointsOnCurve.simplify(Vector(p(0.0, 0.0), p(5.0, 10.0), p(10.0, 0.0)), 100),
      Vector(p(0.0, 0.0), p(10.0, 0.0))
    )
  }

  // ---- pointsOnPath ----

  test("pointsOnPath: M/L/C/Z produces one set with the implicit-lastPoint curve seed") {
    assertEquals(
      PointsOnPath.pointsOnPath("M0,0 L10,0 C20,0 20,10 10,10 Z"),
      Vector(
        Vector(
          p(0.0, 0.0), p(10.0, 0.0), p(10.0, 0.0), p(11.7578125, 0.1123046875), p(13.28125, 0.4296875),
          p(14.5703125, 0.9228515625), p(15.625, 1.5625), p(16.4453125, 2.3193359375), p(17.03125, 3.1640625),
          p(17.3828125, 4.0673828125), p(17.5, 5.0), p(17.3828125, 5.9326171875), p(17.03125, 6.8359375),
          p(16.4453125, 7.6806640625), p(15.625, 8.4375), p(14.5703125, 9.0771484375), p(13.28125, 9.5703125),
          p(11.7578125, 9.8876953125), p(10.0, 10.0), p(0.0, 0.0)
        )
      )
    )
  }

  test("pointsOnPath: two move-to subpaths split into two sets") {
    assertEquals(
      PointsOnPath.pointsOnPath("M0,0 L10,0 M20,20 L30,20"),
      Vector(
        Vector(p(0.0, 0.0), p(10.0, 0.0)),
        Vector(p(20.0, 20.0), p(30.0, 20.0))
      )
    )
  }

  test("pointsOnPath: leading C seeds the pending curve from the move-to start point") {
    assertEquals(
      PointsOnPath.pointsOnPath("M0,0 C10,0 10,10 0,10"),
      Vector(
        Vector(
          p(0.0, 0.0), p(0.0, 0.0), p(1.7578125, 0.1123046875), p(3.28125, 0.4296875),
          p(4.5703125, 0.9228515625), p(5.625, 1.5625), p(6.4453125, 2.3193359375), p(7.03125, 3.1640625),
          p(7.3828125, 4.0673828125), p(7.5, 5.0), p(7.3828125, 5.9326171875), p(7.03125, 6.8359375),
          p(6.4453125, 7.6806640625), p(5.625, 8.4375), p(4.5703125, 9.0771484375), p(3.28125, 9.5703125),
          p(1.7578125, 9.8876953125), p(0.0, 10.0)
        )
      )
    )
  }

  test("pointsOnPath: positive distance simplifies each set") {
    assertEquals(
      PointsOnPath.pointsOnPath("M0,0 L10,0 C20,0 20,10 10,10 Z", None, Some(5.0)),
      Vector(
        Vector(p(0.0, 0.0), p(17.3828125, 5.9326171875), p(10.0, 10.0), p(0.0, 0.0))
      )
    )
  }

  test("pointsOnPath: distance None returns the unsimplified sets") {
    val unsimplified = PointsOnPath.pointsOnPath("M0,0 L10,0 C20,0 20,10 10,10 Z")
    val simplified   = PointsOnPath.pointsOnPath("M0,0 L10,0 C20,0 20,10 10,10 Z", None, Some(5.0))
    assertNotEquals(unsimplified, simplified)
    assertEquals(unsimplified.head.length, 20)
  }
}
