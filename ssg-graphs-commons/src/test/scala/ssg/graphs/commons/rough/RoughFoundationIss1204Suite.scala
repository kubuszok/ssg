/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Values-asserting tests for the roughjs foundation port (Chip 4 of ISS-1204):
 * the Lehmer/MINSTD `Random` PRNG + `randomSeed` (math.ts), the geometry primitives
 * (geometry.ts), and the core types (core.ts).
 *
 * Oracle: the vendored roughjs TS (original-src/roughjs/src/{math,geometry,core}.ts,
 * pinned 56a2762) run under Node. The pinned `Random(seed)` sequences below are the
 * byte-exactness contract for the PRNG: the generator uses only Int multiply (32-bit
 * wrap), Int masking, and division by the power-of-two `2147483648.0`, all bit-identical
 * between JS and Scala on every platform — so every value is asserted EXACTLY (delta 0).
 *
 * Node derivation (run in original-src/roughjs):
 *   node -e 'function R(seed){let s=seed;return{next(){if(s){return ((2**31-1)&
 *     (s=Math.imul(48271,s)))/2**31;}else{return Math.random();}}};}
 *     const r=R(1);for(let i=0;i<8;i++)console.log(r.next());'
 */
package ssg
package graphs
package commons
package rough

import lowlevel.Nullable

import munit.FunSuite

final class RoughFoundationIss1204Suite extends FunSuite {

  private def assertSequence(seed: Int, expected: Vector[Double]): Unit = {
    val r: Random = new Random(seed)
    expected.zipWithIndex.foreach { case (exp, i) =>
      assertEqualsDouble(r.next(), exp, 0.0, s"seed=$seed next($i)")
    }
  }

  // ---- Random sequence pinning (the byte-exactness contract) ----

  test("Random(1) first 8 next() values match the vendored TS exactly") {
    assertSequence(
      1,
      Vector(
        0.000022477935999631882,
        0.08503244863823056,
        0.6013282160274684,
        0.714315861929208,
        0.7409711848013103,
        0.4200615440495312,
        0.7907928149215877,
        0.3599690799601376
      )
    )
  }

  test("Random(42) first 8 next() values match the vendored TS exactly") {
    assertSequence(
      42,
      Vector(
        0.000944073311984539,
        0.5713628428056836,
        0.2557850731536746,
        0.00126620102673769,
        0.12078976165503263,
        0.6425848500803113,
        0.21329822670668364,
        0.11870135832577944
      )
    )
  }

  // ---- Int-wrap edge: seed driving 48271 * seed past 2^31 (negative imul result) ----
  // Math.imul(48271, 123456789) == -2031945029 (the signed 32-bit product). The stored
  // seed keeps the sign bit; only the returned value is masked with 0x7FFFFFFF. Proves
  // the Int multiply reproduces Math.imul through overflow/negative.

  test("Random(123456789) next() values match TS through Int-wrap overflow") {
    assertSequence(
      123456789,
      Vector(
        0.05380186205729842,
        0.06968336785212159,
        0.6858495897613466,
        0.6455473699606955
      )
    )
  }

  test("Random(1000000) first next() matches TS (overflow path)") {
    assertSequence(1000000, Vector(0.4779359996318817))
  }

  test("Random(-5) next() values match TS (negative seed)") {
    assertSequence(
      -5,
      Vector(
        0.9998876103200018,
        0.5748377568088472,
        0.9933589198626578,
        0.4284206903539598
      )
    )
  }

  // ---- Fixed-point seed: Int.MinValue (0x80000000) -> deterministic 0.0 forever ----
  // 48271 is odd, so 48271 * 2^31 ≡ 2^31 (mod 2^32): Int.MinValue is a FIXED POINT of
  // the multiply. The FAITHFUL form (store the full signed product, mask only the
  // result) keeps the seed at 0x80000000, so every next() yields
  // (0x7FFFFFFF & 0x80000000)/2^31 == 0.0 deterministically (verified vs the Node
  // oracle: faithful Random(-2147483648).next() x5 == [0,0,0,0,0]). This pins the
  // mask-only-the-result contract: masking the STORED seed instead would collapse it to
  // 0 after one step, flipping `if (seed)` false so calls 2+ fall into the
  // non-deterministic Math.random() path — observable divergence at this sole seed.
  test("Random(Int.MinValue) returns 0.0 deterministically over successive calls") {
    assertEquals(48271 * Int.MinValue, Int.MinValue) // fixed point of the multiply
    val r: Random = new Random(Int.MinValue)
    var i: Int    = 0
    while (i < 6) {
      assertEqualsDouble(r.next(), 0.0, 0.0, s"Random(Int.MinValue) next($i)")
      i += 1
    }
  }

  // ---- 48271 * seed (Int) == Math.imul(48271, seed) for the overflow witnesses ----

  test("Int multiply wraps to 32-bit exactly like Math.imul") {
    assertEquals(48271 * 1, 48271)
    assertEquals(48271 * 1000000, 1026359744)
    assertEquals(48271 * 123456789, -2031945029)
    assertEquals(48271 * -5, -241355)
    assertEquals(48271 * 2000000000, -274879488)
  }

  // ---- randomSeed() range ----

  test("randomSeed() returns a value in [0, 2147483648)") {
    var i: Int = 0
    while (i < 1000) {
      val s: Int = RoughMath.randomSeed()
      assert(s >= 0, s"randomSeed() $s should be >= 0")
      assert(s < 2147483648.0, s"randomSeed() $s should be < 2147483648")
      i += 1
    }
  }

  // ---- randomSeed() scale (not just the clamped range) ----
  // A widened scale (`* 2^31` -> `* 2^32`) is INVISIBLE to a plain [0, 2^31) range
  // check: floor(rand * 2^32) for rand >= 0.5 lands in [2^31, 2^32), and Double#toInt
  // clamps any value >= 2^31 to Int.MaxValue (2147483647) — still inside the range. So
  // pin the SCALE distributionally: with the correct `* 2^31` scale, hitting exactly
  // Int.MaxValue requires rand in [~0.9999999995, 1.0) (probability ~4.7e-10), so over
  // 100_000 draws we expect ≈ 0 such values; a widened `* 2^32` scale would clamp ~50%
  // of draws to exactly Int.MaxValue. Assert the clamp fraction is tiny.
  test("randomSeed() pins the 2^31 scale (no mass clamp at Int.MaxValue)") {
    val samples: Int = 100000
    var clamped: Int = 0
    var i: Int       = 0
    while (i < samples) {
      if (RoughMath.randomSeed() == Int.MaxValue) {
        clamped += 1
      }
      i += 1
    }
    // Correct scale: expected ≈ 0.0000466 clamps; widened scale: ≈ 50000. 100 cleanly
    // separates the two with astronomically low flake probability.
    assert(clamped < 100, s"randomSeed() clamped $clamped/$samples to Int.MaxValue (scale widened?)")
  }

  // ---- lineLength ----

  test("lineLength of a 3-4-5 triangle is exactly 5.0") {
    assertEqualsDouble(
      Geometry.lineLength(Line(Point(0, 0), Point(3, 4))),
      5.0,
      0.0
    )
  }

  test("lineLength of a non-integer case") {
    // sqrt((1-4)^2 + (5-1)^2) = sqrt(9 + 16) = sqrt(25) = 5
    assertEqualsDouble(
      Geometry.lineLength(Line(Point(1, 5), Point(4, 1))),
      5.0,
      0.0
    )
    // sqrt((0-1)^2 + (0-1)^2) = sqrt(2)
    assertEqualsDouble(
      Geometry.lineLength(Line(Point(0, 0), Point(1, 1))),
      Math.sqrt(2.0),
      0.0
    )
  }

  test("lineLength is zero for coincident endpoints") {
    assertEqualsDouble(
      Geometry.lineLength(Line(Point(7, 7), Point(7, 7))),
      0.0,
      0.0
    )
  }

  // ---- OpType / OpSetType string values ----

  test("OpType cases serialize to their exact upstream string literals") {
    assertEquals(OpType.move.value, "move")
    assertEquals(OpType.bcurveTo.value, "bcurveTo")
    assertEquals(OpType.lineTo.value, "lineTo")
  }

  test("OpSetType cases serialize to their exact upstream string literals") {
    assertEquals(OpSetType.path.value, "path")
    assertEquals(OpSetType.fillPath.value, "fillPath")
    assertEquals(OpSetType.fillSketch.value, "fillSketch")
  }

  // ---- SVGNS ----

  test("SVGNS is the SVG XML namespace") {
    assertEquals(SVGNS, "http://www.w3.org/2000/svg")
  }

  // ---- core type construction + field round-trips ----

  test("Op round-trips its fields") {
    val op: Op = Op(OpType.bcurveTo, Vector(1.0, 2.0, 3.0, 4.0, 5.0, 6.0))
    assertEquals(op.op, OpType.bcurveTo)
    assertEquals(op.op.value, "bcurveTo")
    assertEquals(op.data, Vector(1.0, 2.0, 3.0, 4.0, 5.0, 6.0))
  }

  test("OpSet round-trips its fields including the `type` keyword field") {
    val ops: Vector[Op] = Vector(Op(OpType.move, Vector(0.0, 0.0)), Op(OpType.lineTo, Vector(10.0, 10.0)))
    val opSet: OpSet    = OpSet(OpSetType.fillSketch, ops, Some(Point(100, 200)), Some("M0 0L10 10"))
    assertEquals(opSet.`type`, OpSetType.fillSketch)
    assertEquals(opSet.`type`.value, "fillSketch")
    assertEquals(opSet.ops, ops)
    assertEquals(opSet.size, Some(Point(100, 200)))
    assertEquals(opSet.path, Some("M0 0L10 10"))
  }

  test("OpSet optional fields default to None") {
    val opSet: OpSet = OpSet(OpSetType.path, Vector.empty)
    assertEquals(opSet.size, None)
    assertEquals(opSet.path, None)
  }

  test("Options defaults are all None; set fields round-trip") {
    val empty: Options = Options()
    assertEquals(empty.roughness, None)
    assertEquals(empty.seed, None)
    assertEquals(empty.strokeLineDash, None)
    assertEquals(empty.disableMultiStroke, None)
    val o: Options = Options(
      roughness = Some(1.5),
      stroke = Some("#000"),
      seed = Some(42),
      strokeLineDash = Some(Vector(4.0, 2.0)),
      disableMultiStroke = Some(true)
    )
    assertEquals(o.roughness, Some(1.5))
    assertEquals(o.stroke, Some("#000"))
    assertEquals(o.seed, Some(42))
    assertEquals(o.strokeLineDash, Some(Vector(4.0, 2.0)))
    assertEquals(o.disableMultiStroke, Some(true))
  }

  test("ResolvedOptions round-trips required + optional + Nullable randomizer") {
    val rng: Random = new Random(7)
    val ro: ResolvedOptions = ResolvedOptions(
      maxRandomnessOffset = 2.0,
      roughness = 1.0,
      bowing = 1.0,
      stroke = "#000",
      strokeWidth = 1.0,
      curveFitting = 0.95,
      curveTightness = 0.0,
      curveStepCount = 9.0,
      fillStyle = "hachure",
      fillWeight = 1.0,
      hachureAngle = -41.0,
      hachureGap = 4.0,
      dashOffset = -1.0,
      dashGap = -1.0,
      zigzagOffset = -1.0,
      seed = 0,
      disableMultiStroke = false,
      disableMultiStrokeFill = false,
      preserveVertices = false,
      fillShapeRoughnessGain = 0.8,
      randomizer = Nullable(rng),
      fill = Some("red")
    )
    assertEquals(ro.maxRandomnessOffset, 2.0)
    assertEquals(ro.fillStyle, "hachure")
    assertEquals(ro.hachureAngle, -41.0)
    assertEquals(ro.seed, 0)
    assertEquals(ro.fill, Some("red"))
    assertEquals(ro.simplification, None)
    assert(ro.randomizer.isDefined, "randomizer should be defined")
    assert(ro.randomizer.getOrElse(new Random(0)) eq rng, "randomizer holds the same instance")

    // Default Nullable.empty randomizer
    val ro2: ResolvedOptions = ro.copy(randomizer = Nullable.empty)
    assert(ro2.randomizer.isEmpty, "randomizer should be empty")
  }

  test("Drawable round-trips its fields") {
    val ro: ResolvedOptions = ResolvedOptions(
      maxRandomnessOffset = 2.0,
      roughness = 1.0,
      bowing = 1.0,
      stroke = "#000",
      strokeWidth = 1.0,
      curveFitting = 0.95,
      curveTightness = 0.0,
      curveStepCount = 9.0,
      fillStyle = "hachure",
      fillWeight = 1.0,
      hachureAngle = -41.0,
      hachureGap = 4.0,
      dashOffset = -1.0,
      dashGap = -1.0,
      zigzagOffset = -1.0,
      seed = 1,
      disableMultiStroke = false,
      disableMultiStrokeFill = false,
      preserveVertices = false,
      fillShapeRoughnessGain = 0.8
    )
    val sets: Vector[OpSet] = Vector(OpSet(OpSetType.path, Vector(Op(OpType.move, Vector(0.0, 0.0)))))
    val d: Drawable         = Drawable("line", ro, sets)
    assertEquals(d.shape, "line")
    assertEquals(d.options, ro)
    assertEquals(d.sets, sets)
  }

  test("PathInfo round-trips its fields; fill defaults to None") {
    val pi: PathInfo = PathInfo("M0 0L10 10", "#000", 1.5)
    assertEquals(pi.d, "M0 0L10 10")
    assertEquals(pi.stroke, "#000")
    assertEquals(pi.strokeWidth, 1.5)
    assertEquals(pi.fill, None)
    assertEquals(PathInfo("M0 0", "#000", 1.0, Some("red")).fill, Some("red"))
  }

  test("Config and DrawingSurface round-trip") {
    assertEquals(Config().options, None)
    assertEquals(Config(Some(Options(roughness = Some(2.0)))).options.flatMap(_.roughness), Some(2.0))
    val ds: DrawingSurface = DrawingSurface(640.0, 480.0)
    assertEquals(ds.width, 640.0)
    assertEquals(ds.height, 480.0)
  }

  test("Rectangle round-trips its fields") {
    val r: Rectangle = Rectangle(1.0, 2.0, 30.0, 40.0)
    assertEquals(r.x, 1.0)
    assertEquals(r.y, 2.0)
    assertEquals(r.width, 30.0)
    assertEquals(r.height, 40.0)
  }
}
