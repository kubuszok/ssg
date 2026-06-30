/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Values-asserting tests for the roughjs fillers port (Chip 5 of ISS-1204):
 * PatternFiller / RenderHelper, polygonHachureLines, HachureFiller, ZigZagFiller,
 * ZigZagLineFiller, DotFiller, DashedFiller, HatchFiller and the getFiller factory.
 *
 * Oracle: a faithful JS transcription of the vendored TS sources (roughjs `fillers`
 * pinned 56a2762 + hachure-fill `hachure.ts` pinned 80e47ba) run under Node with the
 * SAME deterministic test-double `RenderHelper` used here (`doubleLineOps` -> a single `lineTo`
 * op carrying the four endpoint coordinates; `ellipse` -> a fixed two-op `move`+`lineTo`
 * OpSet carrying the centre and the width/height). Expected OpSets are transcribed from
 * that oracle.
 *
 * Determinism: cases with `hachureAngle == -90` make `polygonHachureLines`' internal
 * angle (`hachureAngle + 90`) exactly 0, so the hachure lines flow only through
 * `+ - * /`, `Math.min`/`Math.max`, `Math.round` — bit-identical on every platform and
 * asserted EXACTLY. Cases flowing through `Math.cos`/`Math.sin`/`Math.atan` (the zigzag
 * `dgx`/`dgy`, the `angle == 90` swap cases, the second hatch pass) are libm and asserted
 * with a tight 1e-9 tolerance — far tighter than any structural mutation effect. All
 * value cases use `roughness < 1` to avoid the non-deterministic `skipOffset` branch.
 * DotFiller's centre jitter uses bare `Math.random()` (non-deterministic by design), so
 * it is asserted structurally: the ellipse COUNT and that every centre lies within its
 * `[x-ro, x+ro] x [y-ro, y+ro]` bound, with the fixed width/height.
 */
package ssg
package graphs
package commons
package rough
package fillers

import lowlevel.Nullable
import munit.FunSuite

final class FillersIss1204Suite extends FunSuite {

  // A deterministic test-double RenderHelper (the real one is the renderer, Chip 6).
  private final class StubHelper extends RenderHelper {
    def randOffset(x: Double, o: ResolvedOptions): Double                              = x
    def randOffsetWithRange(min: Double, max: Double, o: ResolvedOptions): Double      = min
    def doubleLineOps(x1: Double, y1: Double, x2: Double, y2: Double, o: ResolvedOptions): Vector[Op] =
      Vector(Op(OpType.lineTo, Vector(x1, y1, x2, y2)))
    def ellipse(x: Double, y: Double, width: Double, height: Double, o: ResolvedOptions): OpSet =
      OpSet(`type` = OpSetType.fillSketch, ops = Vector(Op(OpType.move, Vector(x, y)), Op(OpType.lineTo, Vector(width, height))))
  }

  // Base ResolvedOptions matching the roughjs default field set; roughness 0 avoids the
  // random skipOffset branch. Override the relevant fields per test via `.copy`.
  private val base: ResolvedOptions = ResolvedOptions(
    maxRandomnessOffset = 2,
    roughness = 0,
    bowing = 1,
    stroke = "#000",
    strokeWidth = 1,
    curveFitting = 0.95,
    curveTightness = 0,
    curveStepCount = 9,
    fillStyle = "hachure",
    fillWeight = -1,
    hachureAngle = -41,
    hachureGap = -1,
    dashOffset = -1,
    dashGap = -1,
    zigzagOffset = -1,
    seed = 0,
    disableMultiStroke = false,
    disableMultiStrokeFill = false,
    preserveVertices = false,
    fillShapeRoughnessGain = 0.8
  )

  private val rect: Vector[Vector[rough.Point]] =
    Vector(Vector(rough.Point(2, 2), rough.Point(12, 2), rough.Point(12, 12), rough.Point(2, 12)))

  private def lineOp(x1: Double, y1: Double, x2: Double, y2: Double): Op =
    Op(OpType.lineTo, Vector(x1, y1, x2, y2))

  private def assertOpsApprox(
    obtained:     OpSet,
    expectedType: OpSetType,
    expected:     Vector[(OpType, Vector[Double])],
    delta:        Double
  ): Unit = {
    assertEquals(obtained.`type`, expectedType, "OpSet type")
    assertEquals(obtained.ops.length, expected.length, "op count")
    obtained.ops.zip(expected).zipWithIndex.foreach { case ((op, (et, ed)), i) =>
      assertEquals(op.op, et, s"op $i kind")
      assertEquals(op.data.length, ed.length, s"op $i data length")
      op.data.zip(ed).zipWithIndex.foreach { case ((g, e), j) =>
        assertEqualsDouble(g, e, delta, s"op $i data $j")
      }
    }
  }

  // ---- polygonHachureLines: exact lines at a known angle/gap (roughness < 1) ----

  test("polygonHachureLines rect hachureAngle -90 gap 4 -> exact lines (internal angle 0)") {
    val o: ResolvedOptions = base.copy(hachureAngle = -90, hachureGap = 4)
    assertEquals(
      ScanLineHachure.polygonHachureLines(rect, o),
      Vector(
        rough.Line(rough.Point(2, 2), rough.Point(12, 2)),
        rough.Line(rough.Point(2, 6), rough.Point(12, 6)),
        rough.Line(rough.Point(2, 10), rough.Point(12, 10))
      )
    )
  }

  // ---- HachureFiller.fillPolygons: exact fillSketch ops on a rectangle ----

  test("HachureFiller rect hachureAngle -90 gap 4 -> exact fillSketch ops") {
    val o:   ResolvedOptions = base.copy(hachureAngle = -90, hachureGap = 4)
    val out: OpSet           = HachureFiller(StubHelper()).fillPolygons(rect, o)
    assertEquals(
      out,
      OpSet(
        `type` = OpSetType.fillSketch,
        ops = Vector(
          lineOp(2, 2, 12, 2),
          lineOp(2, 6, 12, 6),
          lineOp(2, 10, 12, 10)
        )
      )
    )
  }

  // ---- ZigZagFiller: cos/sin-derived dgx/dgy offsets (tolerance) ----

  test("ZigZagFiller rect hachureAngle -90 gap 4 -> zigzag offset lines (dgx/dgy)") {
    val o:   ResolvedOptions = base.copy(hachureAngle = -90, hachureGap = 4)
    val out: OpSet           = ZigZagFiller(StubHelper()).fillPolygons(rect, o)
    assertOpsApprox(
      out,
      OpSetType.fillSketch,
      Vector(
        OpType.lineTo -> Vector(2, 0, 12, 2),
        OpType.lineTo -> Vector(2, 4, 12, 2),
        OpType.lineTo -> Vector(2, 4, 12, 6),
        OpType.lineTo -> Vector(2, 8, 12, 6),
        OpType.lineTo -> Vector(2, 8, 12, 10),
        OpType.lineTo -> Vector(2, 12, 12, 10)
      ),
      1e-9
    )
  }

  // ---- ZigZagLineFiller: exact atan/cos/sin geometry (internal angle 0) ----

  test("ZigZagLineFiller rect hachureAngle -90 hGap 4 zigzagOffset 2 -> exact zigzag-line ops") {
    val o:   ResolvedOptions = base.copy(hachureAngle = -90, hachureGap = 4, zigzagOffset = 2)
    val out: OpSet           = ZigZagLineFiller(StubHelper()).fillPolygons(rect, o)
    assertEquals(
      out,
      OpSet(
        `type` = OpSetType.fillSketch,
        ops = Vector(
          lineOp(2, 2, 4, 4),
          lineOp(4, 4, 6, 2),
          lineOp(6, 2, 8, 4),
          lineOp(8, 4, 10, 2),
          lineOp(10, 2, 12, 4),
          lineOp(12, 4, 14, 2),
          lineOp(2, 8, 4, 10),
          lineOp(4, 10, 6, 8),
          lineOp(6, 8, 8, 10),
          lineOp(8, 10, 10, 8),
          lineOp(10, 8, 12, 10),
          lineOp(12, 10, 14, 8)
        )
      )
    )
  }

  // ---- ZigZagLineFiller: the p1[0] > p2[0] swap (lines come right-to-left at angle 90) ----

  test("ZigZagLineFiller rect hachureAngle 90 -> right-to-left lines exercise the swap") {
    val o:   ResolvedOptions = base.copy(hachureAngle = 90, hachureGap = 4, zigzagOffset = 2)
    val out: OpSet           = ZigZagLineFiller(StubHelper()).fillPolygons(rect, o)
    assertOpsApprox(
      out,
      OpSetType.fillSketch,
      Vector(
        OpType.lineTo -> Vector(2, 6, 4, 8),
        OpType.lineTo -> Vector(4, 8, 6, 6),
        OpType.lineTo -> Vector(6, 6, 8, 8),
        OpType.lineTo -> Vector(8, 8, 10, 6),
        OpType.lineTo -> Vector(10, 6, 12, 8),
        OpType.lineTo -> Vector(12, 8, 14, 6)
      ),
      1e-9
    )
  }

  // ---- DashedFiller: exact Math.floor / startOffset geometry (internal angle 0) ----

  test("DashedFiller rect hachureAngle -90 dashOffset 2 dashGap 1 -> exact dash ops") {
    val o:   ResolvedOptions = base.copy(hachureAngle = -90, hachureGap = 4, dashOffset = 2, dashGap = 1)
    val out: OpSet           = DashedFiller(StubHelper()).fillPolygons(rect, o)
    assertEquals(
      out,
      OpSet(
        `type` = OpSetType.fillSketch,
        ops = Vector(
          lineOp(3, 2, 5, 2),
          lineOp(6, 2, 8, 2),
          lineOp(9, 2, 11, 2),
          lineOp(3, 6, 5, 6),
          lineOp(6, 6, 8, 6),
          lineOp(9, 6, 11, 6),
          lineOp(3, 10, 5, 10),
          lineOp(6, 10, 8, 10),
          lineOp(9, 10, 11, 10)
        )
      )
    )
  }

  // ---- DashedFiller: the p1[0] > p2[0] swap + Math.floor at angle 90 ----

  test("DashedFiller rect hachureAngle 90 -> right-to-left lines exercise the swap") {
    val o:   ResolvedOptions = base.copy(hachureAngle = 90, hachureGap = 4, dashOffset = 2, dashGap = 1)
    val out: OpSet           = DashedFiller(StubHelper()).fillPolygons(rect, o)
    assertOpsApprox(
      out,
      OpSetType.fillSketch,
      Vector(
        OpType.lineTo -> Vector(3, 8, 5, 8),
        OpType.lineTo -> Vector(6, 8, 8, 8),
        OpType.lineTo -> Vector(9, 8, 11, 8),
        OpType.lineTo -> Vector(3, 4, 5, 4),
        OpType.lineTo -> Vector(6, 4, 8, 4),
        OpType.lineTo -> Vector(9, 4, 11, 4)
      ),
      1e-9
    )
  }

  // ---- HatchFiller: ops = hachure(angle) ++ hachure(angle+90), doubled structure ----

  test("HatchFiller rect hachureAngle -90 gap 4 -> hachure(angle) ++ hachure(angle+90)") {
    val o:    ResolvedOptions = base.copy(hachureAngle = -90, hachureGap = 4)
    val out:  OpSet           = HatchFiller(StubHelper()).fillPolygons(rect, o)
    val first: OpSet          = HachureFiller(StubHelper()).fillPolygons(rect, o)
    val second: OpSet         = HachureFiller(StubHelper()).fillPolygons(rect, o.copy(hachureAngle = o.hachureAngle + 90))
    // structural: out is exactly the concatenation of the two passes
    assertEquals(out.`type`, OpSetType.fillSketch)
    assertEquals(out.ops.length, first.ops.length + second.ops.length)
    assertEquals(out.ops.length, 6)
    assertEquals(out.ops.take(3), first.ops)
    assertEquals(out.ops.drop(3), second.ops)
    // and the second pass (angle+90) differs from the first (catches +90 -> +0)
    assert(second.ops != first.ops, "angle+90 pass must differ from angle pass")
    // exact first half (internal angle 0); tolerance second half (libm rotation)
    assertEquals(
      out.ops.take(3),
      Vector(lineOp(2, 2, 12, 2), lineOp(2, 6, 12, 6), lineOp(2, 10, 12, 10))
    )
    assertOpsApprox(
      OpSet(`type` = OpSetType.fillSketch, ops = out.ops.drop(3)),
      OpSetType.fillSketch,
      Vector(
        OpType.lineTo -> Vector(2, 2, 2, 2),
        OpType.lineTo -> Vector(6, 12, 6, 2),
        OpType.lineTo -> Vector(10, 12, 10, 2)
      ),
      1e-9
    )
  }

  // ---- DotFiller: ellipse count + per-centre bounds (cx/cy use bare Math.random) ----

  test("DotFiller rect hGap 4 fillWeight 2 -> 4 ellipses, centres within [x-ro,x+ro] bounds") {
    val o:   ResolvedOptions = base.copy(hachureGap = 4, fillWeight = 2)
    val out: OpSet           = DotFiller(StubHelper()).fillPolygons(rect, o)
    assertEquals(out.`type`, OpSetType.fillSketch)
    // each ellipse contributes a (move, lineTo) pair from the test-double helper -> 2 ops per ellipse.
    assertEquals(out.ops.length % 2, 0, "even op count (pairs)")
    val ellipses: Int = out.ops.length / 2
    assertEquals(ellipses, 4, "ellipse count (Math.ceil(dl) - 1 summed over lines)")
    val eps: Double = 1e-6
    // deterministic centres are x in {5,9}, y in {4,8}; ro = gap/4 = 1; fweight = 2.
    out.ops.grouped(2).foreach { pair =>
      val move: Op = pair(0)
      val line: Op = pair(1)
      assertEquals(move.op, OpType.move)
      assertEquals(line.op, OpType.lineTo)
      // width == height == fweight == 2
      assertEqualsDouble(line.data(0), 2.0, eps, "ellipse width")
      assertEqualsDouble(line.data(1), 2.0, eps, "ellipse height")
      val cx: Double = move.data(0)
      val cy: Double = move.data(1)
      val cxOk: Boolean = (cx >= 4 - eps && cx <= 6 + eps) || (cx >= 8 - eps && cx <= 10 + eps)
      val cyOk: Boolean = (cy >= 3 - eps && cy <= 5 + eps) || (cy >= 7 - eps && cy <= 9 + eps)
      assert(cxOk, s"cx $cx within [4,6] or [8,10]")
      assert(cyOk, s"cy $cy within [3,5] or [7,9]")
    }
  }

  // ---- getFiller: each fillStyle returns the correct filler type ----

  test("getFiller returns the correct filler type for each fillStyle") {
    val h: RenderHelper = StubHelper()
    assert(Filler.getFiller(base.copy(fillStyle = "hachure"), h).isInstanceOf[HachureFiller])
    assert(Filler.getFiller(base.copy(fillStyle = "zigzag"), h).isInstanceOf[ZigZagFiller])
    assert(Filler.getFiller(base.copy(fillStyle = "cross-hatch"), h).isInstanceOf[HatchFiller])
    assert(Filler.getFiller(base.copy(fillStyle = "dots"), h).isInstanceOf[DotFiller])
    assert(Filler.getFiller(base.copy(fillStyle = "dashed"), h).isInstanceOf[DashedFiller])
    assert(Filler.getFiller(base.copy(fillStyle = "zigzag-line"), h).isInstanceOf[ZigZagLineFiller])
    // unknown style falls through to hachure; empty string is JS-falsy -> hachure
    assert(Filler.getFiller(base.copy(fillStyle = "no-such-style"), h).isInstanceOf[HachureFiller])
    assert(Filler.getFiller(base.copy(fillStyle = ""), h).isInstanceOf[HachureFiller])
  }

  test("getFiller distinguishes ZigZag vs ZigZagLine (no aliasing of distinct styles)") {
    val h: RenderHelper = StubHelper()
    assert(!Filler.getFiller(base.copy(fillStyle = "zigzag"), h).isInstanceOf[ZigZagLineFiller])
    assert(!Filler.getFiller(base.copy(fillStyle = "dots"), h).isInstanceOf[DashedFiller])
  }

  // ---- getFiller cache quirk: first helper wins; same style returns the cached instance ----

  test("getFiller caches per style: same style returns the same instance (first helper retained)") {
    val helperA: RenderHelper = StubHelper()
    val helperB: RenderHelper = StubHelper()
    assert(helperA ne helperB, "test setup: distinct helper instances")
    // use a dedicated style so the assertion is independent of other tests' caching order
    val first:  PatternFiller = Filler.getFiller(base.copy(fillStyle = "zigzag-line"), helperA)
    val second: PatternFiller = Filler.getFiller(base.copy(fillStyle = "zigzag-line"), helperB)
    assert(first eq second, "same style must return the identical cached filler instance")
  }

  // ---- roughness >= 1 -> skipOffset = gap branch (seeded, deterministic) ----
  //
  // The `roughness >= 1` region of polygonHachureLines (the `skipOffset` logic and the
  // `(o.randomizer?.next() || Math.random())` value) is exercised here with a SEEDED
  // randomizer so the random branch is deterministic. `Random(82303).next()` ==
  // 0.8500015665777028 (Lehmer/MINSTD first step; verified byte-identical to Node's
  // `(0x7fffffff & Math.imul(48271, 82303)) / 2**31`), which is > 0.7, so the branch sets
  // `skipOffset = gap`. This also touches the falsy-0 fallback path: the randomizer is
  // PRESENT and returns a truthy value, so `o.randomizer.fold(NaN)(_.next())` + the truthy
  // check select 0.85 (not the Math.random() fallback).
  //
  // Geometry: a "house" pentagon whose slanted-roof edges have `ymin` OFF the gap grid
  // (ymin == 6, gap == 4) — the only configuration where skipOffset == gap (coarse y-step)
  // observably diverges from skipOffset == 1 (fine y-step, fill every gap-th iteration):
  // a late-inserted edge's x is NOT advanced to the fill y. First vertex x == 1 (not 0) to
  // avoid the documented hachure-fill JS falsy-x single-vs-list misclassification.
  test("polygonHachureLines roughness>=1 fires skipOffset=gap (seeded) -> differs from baseline") {
    val house: Vector[Vector[rough.Point]] =
      Vector(Vector(rough.Point(1, 0), rough.Point(11, 0), rough.Point(11, 6), rough.Point(6, 9), rough.Point(1, 6)))
    // baseline: roughness 0 -> skipOffset stays 1 (fine y-step).
    val baseline: Vector[rough.Line] =
      ScanLineHachure.polygonHachureLines(house, base.copy(hachureAngle = -90, hachureGap = 4, roughness = 0))
    assertEquals(
      baseline,
      Vector(
        rough.Line(rough.Point(1, 0), rough.Point(11, 0)),
        rough.Line(rough.Point(1, 4), rough.Point(11, 4)),
        rough.Line(rough.Point(4, 8), rough.Point(8, 8))
      )
    )
    // branch: roughness 1 + seeded next() 0.85 > 0.7 -> skipOffset = gap = 4 (coarse y-step).
    val branch: Vector[rough.Line] =
      ScanLineHachure.polygonHachureLines(
        house,
        base.copy(hachureAngle = -90, hachureGap = 4, roughness = 1, randomizer = Nullable(Random(82303)))
      )
    assertEquals(
      branch,
      Vector(
        rough.Line(rough.Point(1, 0), rough.Point(11, 0)),
        rough.Line(rough.Point(1, 4), rough.Point(11, 4)),
        rough.Line(rough.Point(1, 8), rough.Point(11, 8))
      )
    )
    assert(branch != baseline, "skipOffset = gap branch must change the fill vs skipOffset = 1")
  }

  // ---- the `skipOffset || 1` guard: gap rounds to 0 AND the random branch fires (M10) ----
  //
  // hachureGap 0.1 -> `Math.round(Math.max(0.1, 0.1))` == 0; roughness 1 + seeded
  // next() 0.85 > 0.7 -> skipOffset = gap = 0. The faithful `skipOffset || 1` guard
  // (ported as `if (truthy(skipOffset)) skipOffset else 1`) must yield stepOffset 1 so the
  // scan TERMINATES and returns a finite result. The M10 mutant (bare `skipOffset` == 0)
  // passes stepOffset 0 to HachureFill.hachureLines, which never advances `y` (and never
  // evicts edges) -> infinite loop; this test then hangs (the synchronous non-termination
  // is the catch — note: not a munitTimeout failure, since munitTimeout only governs
  // Future-returning tests). On the faithful code the assertion passes instantly.
  test("polygonHachureLines gap rounds to 0 + skipOffset fires -> ||1 guard terminates (M10)") {
    val o: ResolvedOptions =
      base.copy(hachureAngle = -90, hachureGap = 0.1, roughness = 1, randomizer = Nullable(Random(82303)))
    assertEquals(
      ScanLineHachure.polygonHachureLines(rect, o),
      Vector(rough.Line(rough.Point(2, 2), rough.Point(12, 2)))
    )
  }
}
