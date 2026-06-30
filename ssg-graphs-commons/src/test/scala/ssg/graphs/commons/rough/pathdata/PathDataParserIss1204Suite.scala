/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Structural + numeric tests for the path-data-parser port (Chip 1 of ISS-1204).
 *
 * Oracle: the upstream path-data-parser TS (original-src/path-data-parser,
 * pinned 93d3fa8). Expected literals below were produced by a faithful JS
 * transcription of parser.ts / absolutize.ts / normalize.ts run under Node and
 * are asserted exactly for the rational (no-transcendental) cases (parsePath,
 * serialize, absolutize, H/V->L, Q->C, S/T reflection, zero-radius arc) and to a
 * tight tolerance for the irrational A->C arc math (sin/cos/asin/tan/sqrt).
 */
package ssg
package graphs
package commons
package rough
package pathdata

import munit.FunSuite

final class PathDataParserIss1204Suite extends FunSuite {

  private def seg(key: String, data: Double*): Segment = Segment(key, data.toVector)

  /** Element-wise tolerance comparison for the irrational arc curves. */
  private def assertSegmentsClose(actual: Vector[Segment], expected: Vector[Segment], delta: Double): Unit = {
    assertEquals(actual.length, expected.length, s"segment count: $actual vs $expected")
    actual.zip(expected).zipWithIndex.foreach { case ((a, e), i) =>
      assertEquals(a.key, e.key, s"key at $i")
      assertEquals(a.data.length, e.data.length, s"data length at $i: ${a.data} vs ${e.data}")
      a.data.zip(e.data).zipWithIndex.foreach { case ((av, ev), j) =>
        assertEqualsDouble(av, ev, delta, s"data($i)($j): $av vs $ev")
      }
    }
  }

  // ---- parsePath ----

  test("parsePath: M with implicit L after first move-to") {
    assertEquals(
      Parser.parsePath("M10 20 L30 40 50 60"),
      Vector(seg("M", 10, 20), seg("L", 30, 40), seg("L", 50, 60))
    )
  }

  test("parsePath: cubic bezier with comma separators") {
    assertEquals(
      Parser.parsePath("M0,0 C10,10 20,20 30,30"),
      Vector(seg("M", 0, 0), seg("C", 10, 10, 20, 20, 30, 30))
    )
  }

  test("parsePath: path not beginning with M/m recurses with prepended M0,0") {
    assertEquals(
      Parser.parsePath("L5 5"),
      Vector(seg("M", 0, 0), seg("L", 5, 5))
    )
  }

  test("parsePath: scientific notation and mixed separators") {
    assertEquals(
      Parser.parsePath("M1e2,2.5e1 l-1.5e1 .5"),
      Vector(seg("M", 100, 25), seg("l", -15, 0.5))
    )
  }

  test("parsePath: every command type H V Q T S A Z") {
    assertEquals(
      Parser.parsePath("M0 0 H10 V10 Q5 5 0 10 T-5 -5 S1 1 2 2 A3 3 0 1 0 4 4 Z"),
      Vector(
        seg("M", 0, 0),
        seg("H", 10),
        seg("V", 10),
        seg("Q", 5, 5, 0, 10),
        seg("T", -5, -5),
        seg("S", 1, 1, 2, 2),
        seg("A", 3, 3, 0, 1, 0, 4, 4),
        seg("Z")
      )
    )
  }

  test("parsePath: empty string yields no segments") {
    assertEquals(Parser.parsePath(""), Vector.empty[Segment])
  }

  test("parsePath: data ended short throws PathDataParseError") {
    interceptMessage[PathDataParseError]("Path data ended short") {
      Parser.parsePath("M0 0 L5")
    }
  }

  // ---- serialize ----

  test("serialize: implicit-L path round-trips to JS String() formatting") {
    assertEquals(
      Parser.serialize(Parser.parsePath("M10 20 L30 40 50 60")),
      "M 10 20 L 30 40 L 50 60"
    )
  }

  test("serialize: cubic uses the C/c trailing-comma grouping") {
    assertEquals(
      Parser.serialize(Parser.parsePath("M0,0 C10,10 20,20 30,30")),
      "M 0 0 C 10 10, 20 20, 30 30"
    )
  }

  // ---- absolutize ----

  test("absolutize: relative m/l/c/h/v/z become absolute") {
    assertEquals(
      Absolutize.absolutize(Parser.parsePath("m10 10 l5 5 c1 1 2 2 3 3 h4 v4 z")),
      Vector(
        seg("M", 10, 10),
        seg("L", 15, 15),
        seg("C", 16, 16, 17, 17, 18, 18),
        seg("H", 22),
        seg("V", 22),
        seg("Z")
      )
    )
  }

  test("absolutize: relative arc folds endpoint coordinates only") {
    assertEquals(
      Absolutize.absolutize(Parser.parsePath("M10 10 a5 5 0 0 1 10 10")),
      Vector(seg("M", 10, 10), seg("A", 5, 5, 0, 0, 1, 20, 20))
    )
  }

  // The relative c/q/s folds use `(i % 2) ? d + cy : d + cx` — even indices take the
  // current x, odd indices the current y. These three cases pin that parity at a point
  // where cx != cy AND the per-axis offsets differ, so swapping cx/cy changes the exact
  // result (catches an `i % 2 == 0` mutation that cx==cy cases miss).
  test("absolutize: relative c folds even->+cx, odd->+cy (parity-distinguishing)") {
    // m 10 20 -> M(10,20), cx=10 cy=20; c 1 2 3 4 5 6 ->
    //   i0:1+10=11 i1:2+20=22 i2:3+10=13 i3:4+20=24 i4:5+10=15 i5:6+20=26
    assertEquals(
      Absolutize.absolutize(Parser.parsePath("m 10 20 c 1 2 3 4 5 6")),
      Vector(seg("M", 10, 20), seg("C", 11, 22, 13, 24, 15, 26))
    )
  }

  test("absolutize: relative q folds even->+cx, odd->+cy (parity-distinguishing)") {
    // m 10 20 -> M(10,20), cx=10 cy=20; q 1 2 3 4 ->
    //   i0:1+10=11 i1:2+20=22 i2:3+10=13 i3:4+20=24
    assertEquals(
      Absolutize.absolutize(Parser.parsePath("m 10 20 q 1 2 3 4")),
      Vector(seg("M", 10, 20), seg("Q", 11, 22, 13, 24))
    )
  }

  test("absolutize: relative s folds even->+cx, odd->+cy (parity-distinguishing)") {
    // m 10 20 -> M(10,20), cx=10 cy=20; s 1 2 3 4 ->
    //   i0:1+10=11 i1:2+20=22 i2:3+10=13 i3:4+20=24
    assertEquals(
      Absolutize.absolutize(Parser.parsePath("m 10 20 s 1 2 3 4")),
      Vector(seg("M", 10, 20), seg("S", 11, 22, 13, 24))
    )
  }

  // ---- normalize ----

  test("normalize: H and V become L") {
    assertEquals(
      Normalize.normalize(Absolutize.absolutize(Parser.parsePath("M0 0 H10 V20"))),
      Vector(seg("M", 0, 0), seg("L", 10, 0), seg("L", 10, 20))
    )
  }

  test("normalize: Q becomes C") {
    assertEquals(
      Normalize.normalize(Absolutize.absolutize(Parser.parsePath("M0 0 Q10 0 10 10"))),
      Vector(
        seg("M", 0, 0),
        seg("C", 6.666666666666667, 0, 10, 3.333333333333333, 10, 10)
      )
    )
  }

  test("normalize: S reflects the previous C control point") {
    assertEquals(
      Normalize.normalize(Absolutize.absolutize(Parser.parsePath("M0 0 C0 0 5 5 10 10 S15 15 20 20"))),
      Vector(
        seg("M", 0, 0),
        seg("C", 0, 0, 5, 5, 10, 10),
        seg("C", 15, 15, 15, 15, 20, 20)
      )
    )
  }

  test("normalize: T reflects the previous Q control point") {
    assertEquals(
      Normalize.normalize(Absolutize.absolutize(Parser.parsePath("M0 0 Q10 0 10 10 T20 20"))),
      Vector(
        seg("M", 0, 0),
        seg("C", 6.666666666666667, 0, 10, 3.333333333333333, 10, 10),
        seg("C", 10, 16.666666666666668, 13.333333333333332, 20, 20, 20)
      )
    )
  }

  test("normalize: zero-radius arc degenerates to a straight C") {
    assertEquals(
      Normalize.normalize(Absolutize.absolutize(Parser.parsePath("M0 0 A0 5 0 0 1 10 10"))),
      Vector(seg("M", 0, 0), seg("C", 0, 0, 10, 10, 10, 10))
    )
  }

  test("normalize: A becomes one or more C (arc-to-bezier, small arc)") {
    assertSegmentsClose(
      Normalize.normalize(Absolutize.absolutize(Parser.parsePath("M10 10 A5 5 0 0 1 20 20"))),
      Vector(
        seg("M", 10, 10),
        seg("C", 13.849001848015174, 6.150998258820165, 20.42129461382163, 7.912038893119127, 21.83012697688632, 13.169873143303436),
        seg("C", 22.483968334474227, 15.610042445373088, 21.786327878334575, 18.213672075250408, 20, 20)
      ),
      1e-9
    )
  }

  test("normalize: large arc splits into multiple C segments (>120deg)") {
    assertSegmentsClose(
      Normalize.normalize(Absolutize.absolutize(Parser.parsePath("M0 0 A50 50 0 1 1 0 100"))),
      Vector(
        seg("M", 0, 0),
        seg("C", 38.49001794597504, 2.3568338638331258e-15, 62.54627916220945, 41.66666666666667, 43.30127018922194, 75),
        seg("C", 34.36963044151785, 90.47005383792515, 17.863279495408182, 100, 0, 100)
      ),
      1e-9
    )
  }

  // ---- facade (index.ts re-exports) ----

  test("PathDataParser facade re-exports parsePath/serialize/absolutize/normalize") {
    val parsed: Vector[Segment]     = PathDataParser.parsePath("m10 10 l5 5")
    val absolute: Vector[Segment]   = PathDataParser.absolutize(parsed)
    val normalized: Vector[Segment] = PathDataParser.normalize(absolute)
    assertEquals(absolute, Vector(seg("M", 10, 10), seg("L", 15, 15)))
    assertEquals(normalized, Vector(seg("M", 10, 10), seg("L", 15, 15)))
    assertEquals(PathDataParser.serialize(absolute), "M 10 10 L 15 15")
  }
}
