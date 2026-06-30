/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Values-asserting tests for the roughjs renderer port (Chip 6 of ISS-1204): the core
 * sketch algorithm (line/linearPath/polygon/rectangle/curve/ellipse/arc/svgPath + the
 * solid/pattern fills + randOffset/randOffsetWithRange/doubleLineFillOps).
 *
 * Oracle: the vendored roughjs TS (original-src/roughjs/src/renderer.ts, pinned 56a2762)
 * run under Node/tsx against a seeded ResolvedOptions object built from the roughjs
 * generator defaults. The expected OpSets live in `RoughRendererIss1204Data` (generated;
 * see its header). The renderer is deterministic for a non-zero seed: `random()` lazily
 * builds `Random(seed)` and the SAME instance threads/advances across the whole render.
 *
 * Comparison tolerance: the RNG itself is exact Int arithmetic, but the geometry mixes in
 * libm transcendentals (cos/sin/sqrt/pow), which agree across JVM/JS/Native only to a few
 * ULP. A 1e-9 absolute delta is therefore used — far tighter than any mutation's effect
 * (mutations shift values by >= 1e-3), yet loose enough to absorb cross-platform libm
 * drift, so the seeded jitter asserts byte-stably on all three platforms.
 *
 * Cache note (fills): `Filler.getFiller` keeps a process-global cache keyed by fill-style
 * name; FillersIss1204Suite pollutes "hachure" with a test-double helper. So the EXACT
 * fill values are asserted through a FRESH `HachureFiller(rendererHelper)` (== what
 * getFiller builds with a clean cache, cache-pollution-independent), while the public
 * `patternFillPolygons`/`patternFillArc` entry points are checked for wiring (type +
 * non-empty) and, for the arc, point-construction is pinned cache-independently by
 * comparing the public arc against `patternFillPolygons` of the reconstructed points
 * (both route through the same cache, so they are equal iff the points match).
 */
package ssg
package graphs
package commons
package rough

import scala.collection.mutable.ArrayBuffer

import fillers.{ HachureFiller, RenderHelper }

import munit.FunSuite

final class RoughRendererIss1204Suite extends FunSuite {

  import RoughRendererIss1204Data.*

  // The roughjs generator default ResolvedOptions, with a seed (and a few overridable
  // fields). Mirrors the oracle's `opts(...)`. randomizer starts empty so `random()`
  // lazily builds Random(seed).
  private def ro(
    seed:                   Int,
    roughness:              Double = 1,
    disableMultiStroke:     Boolean = false,
    disableMultiStrokeFill: Boolean = false,
    preserveVertices:       Boolean = false
  ): ResolvedOptions =
    ResolvedOptions(
      maxRandomnessOffset = 2,
      roughness = roughness,
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
      seed = seed,
      disableMultiStroke = disableMultiStroke,
      disableMultiStrokeFill = disableMultiStrokeFill,
      preserveVertices = preserveVertices,
      fillShapeRoughnessGain = 0.8
    )

  // A RenderHelper identical in behavior to the renderer's private `helper` — delegates to
  // the public RoughRenderer members. Used to drive a FRESH HachureFiller for exact fill
  // assertions, bypassing the global getFiller cache.
  private val rendererHelper: RenderHelper = new RenderHelper {
    def randOffset(x: Double, o: ResolvedOptions): Double = RoughRenderer.randOffset(x, o)
    def randOffsetWithRange(min: Double, max: Double, o: ResolvedOptions): Double = RoughRenderer.randOffsetWithRange(min, max, o)
    def ellipse(x: Double, y: Double, width: Double, height: Double, o: ResolvedOptions): OpSet = RoughRenderer.ellipse(x, y, width, height, o)
    def doubleLineOps(x1: Double, y1: Double, x2: Double, y2: Double, o: ResolvedOptions): Vector[Op] = RoughRenderer.doubleLineFillOps(x1, y1, x2, y2, o)
  }

  private val Delta: Double = 1e-9

  private def assertOpEquals(actual: Op, expected: Op, ctx: String): Unit = {
    assertEquals(actual.op, expected.op, s"$ctx op")
    assertEquals(actual.data.length, expected.data.length, s"$ctx data length")
    actual.data.zip(expected.data).zipWithIndex.foreach { case ((av, ev), j) =>
      assertEqualsDouble(av, ev, Delta, s"$ctx data[$j]")
    }
  }

  private def assertOps(actual: OpSet, expType: OpSetType, expected: Vector[Op]): Unit = {
    assertEquals(actual.`type`, expType, "OpSet type")
    assertEquals(actual.ops.length, expected.length, "ops length")
    actual.ops.zip(expected).zipWithIndex.foreach { case ((a, e), i) =>
      assertOpEquals(a, e, s"op[$i]")
    }
  }

  // ---- line ----

  test("line seed42 roughness1 emits the two jittered bcurveTo strokes exactly") {
    assertOps(RoughRenderer.line(10, 10, 110, 30, ro(42)), OpSetType.path, expLine42)
  }

  test("line seed42 disableMultiStroke emits a single stroke") {
    assertOps(RoughRenderer.line(10, 10, 110, 30, ro(42, disableMultiStroke = true)), OpSetType.path, expLine42Single)
  }

  test("line seed42 roughness0 is deterministic core geometry (no jitter)") {
    assertOps(RoughRenderer.line(10, 10, 110, 30, ro(42, roughness = 0)), OpSetType.path, expLine42R0)
  }

  // ---- rectangle / polygon / linearPath ----

  test("rectangle seed7 = the closed 4-segment double-line polygon") {
    assertOps(RoughRenderer.rectangle(0, 0, 100, 50, ro(7)), OpSetType.path, expRect7)
  }

  test("polygon seed7 closes the triangle") {
    assertOps(RoughRenderer.polygon(Vector(Point(0, 0), Point(50, 0), Point(25, 40)), ro(7)), OpSetType.path, expPoly7)
  }

  test("linearPath open (close=false) skips the closing segment") {
    assertOps(RoughRenderer.linearPath(Vector(Point(0, 0), Point(50, 0), Point(25, 40)), false, ro(7)), OpSetType.path, expLinPathOpen7)
  }

  test("linearPath 2 points delegates to line") {
    assertOps(RoughRenderer.linearPath(Vector(Point(0, 0), Point(50, 0)), false, ro(7)), OpSetType.path, expLinPath2pt7)
  }

  test("linearPath 1 point is an empty path") {
    val out: OpSet = RoughRenderer.linearPath(Vector(Point(0, 0)), false, ro(7))
    assertEquals(out.`type`, OpSetType.path)
    assertEquals(out.ops, Vector.empty[Op])
  }

  test("linearPath 0 points is an empty path") {
    assertEquals(RoughRenderer.linearPath(Vector.empty, false, ro(7)).ops, Vector.empty[Op])
  }

  // ---- curve (single list + multi list) ----

  test("curve single point list seed3") {
    assertOps(RoughRenderer.curve(Vector(Point(0, 0), Point(20, 30), Point(40, 10), Point(60, 50), Point(80, 0)), ro(3)), OpSetType.path, expCurveSingle3)
  }

  test("curve multi list seed3 (concats underlay/overlay, move-skips later segments)") {
    val lists: Vector[Vector[Point]] = Vector(
      Vector(Point(0, 0), Point(20, 30), Point(40, 10)),
      Vector(Point(60, 50), Point(80, 0), Point(100, 20))
    )
    assertOps(RoughRenderer.curve(lists, ro(3)), OpSetType.path, expCurveMulti3)
  }

  test("curve empty input is an empty path") {
    assertEquals(RoughRenderer.curve(Vector.empty[Point], ro(3)).ops, Vector.empty[Op])
  }

  // curveTightness != 0 so the `s = 1 - o.curveTightness` sign is observable (with tightness 0
  // the sign is a no-op). single-stroke to isolate the underlay curve.
  test("curve with curveTightness=0.5 pins s = 1 - curveTightness") {
    val o: ResolvedOptions = ro(3, disableMultiStroke = true).copy(curveTightness = 0.5)
    assertOps(RoughRenderer.curve(Vector(Point(0, 0), Point(20, 30), Point(40, 10), Point(60, 50), Point(80, 0)), o), OpSetType.path, expCurveTight3)
  }

  // ---- ellipse / generateEllipseParams / ellipseWithParams ----

  test("generateEllipseParams seed99 jittered rx/ry/increment") {
    val p: EllipseParams = RoughRenderer.generateEllipseParams(80, 60, ro(99))
    assertEqualsDouble(p.rx, epRx99, Delta, "rx")
    assertEqualsDouble(p.ry, epRy99, Delta, "ry")
    assertEqualsDouble(p.increment, epInc99, Delta, "increment")
  }

  test("generateEllipseParams seed99 roughness0 has no jitter on rx/ry") {
    val p: EllipseParams = RoughRenderer.generateEllipseParams(80, 60, ro(99, roughness = 0))
    assertEqualsDouble(p.rx, epRx99r0, Delta, "rx")
    assertEqualsDouble(p.ry, epRy99r0, Delta, "ry")
    assertEqualsDouble(p.increment, epInc99, Delta, "increment")
  }

  test("ellipse seed99 roughness0 takes the coreOnly path (single stroke)") {
    assertOps(RoughRenderer.ellipse(50, 50, 80, 60, ro(99, roughness = 0)), OpSetType.path, expEllipseCore99)
  }

  test("ellipse seed99 roughness1 is jittered with the second (1.5) stroke") {
    assertOps(RoughRenderer.ellipse(50, 50, 80, 60, ro(99)), OpSetType.path, expEllipseJit99)
  }

  test("ellipseWithParams returns estimatedPoints (corePoints) + the opset") {
    val params: EllipseParams = EllipseParams(rx = 40, ry = 30, increment = Math.PI / 4)
    val res:    EllipseResult = RoughRenderer.ellipseWithParams(50, 50, ro(99, roughness = 0), params)
    assertEquals(res.estimatedPoints.length, ewrEstCount)
    assertEqualsDouble(res.estimatedPoints(0).x, ewrEst0x, Delta, "est0.x")
    assertEqualsDouble(res.estimatedPoints(0).y, ewrEst0y, Delta, "est0.y")
    assertOps(res.opset, OpSetType.path, expEwr)
  }

  // ---- arc ----

  test("arc closed roughClosure seed5 (multi-stroke + two double-line closes)") {
    assertOps(RoughRenderer.arc(50, 50, 80, 60, 0, Math.PI, true, true, ro(5)), OpSetType.path, expArcClosedRough5)
  }

  test("arc closed sharp (roughClosure=false) appends two lineTo closes") {
    assertOps(RoughRenderer.arc(50, 50, 80, 60, 0, Math.PI, true, false, ro(5)), OpSetType.path, expArcClosedSharp5)
  }

  test("arc open seed5 (no closure ops)") {
    assertOps(RoughRenderer.arc(50, 50, 80, 60, 0, Math.PI, false, false, ro(5)), OpSetType.path, expArcOpen5)
  }

  test("arc open seed5 roughness0 (deterministic, both strokes identical)") {
    assertOps(RoughRenderer.arc(50, 50, 80, 60, 0, Math.PI, false, false, ro(5, roughness = 0)), OpSetType.path, expArcOpen5R0)
  }

  test("arc with negative start wraps via the while(strt<0) normalization") {
    assertOps(RoughRenderer.arc(50, 50, 80, 60, -1, 1, false, false, ro(5)), OpSetType.path, expArcNeg5)
  }

  // span > 2π (0..7, 7 > 2π≈6.2832) triggers the `(stp-strt) > 2π` clamp -> strt/stp reset to
  // 0..2π (a full sweep). Previously uncovered (ISS-1359): the clamp branch never executed.
  test("arc span > 2π triggers the clamp to a full 0..2π sweep (open)") {
    assertOps(RoughRenderer.arc(50, 50, 80, 60, 0, 7, false, false, ro(5)), OpSetType.path, RoughRendererClampIss1204Data.expArcClampOpen5)
  }

  // negative start that ALSO spans > 2π: while(strt<0) shifts (-1..7 -> 5.283..13.283), then
  // (stp-strt)=8 > 2π fires the clamp -> 0..2π. closed + roughClosure exercises the closure too.
  test("arc negative start spanning > 2π: normalize then clamp (closed, roughClosure)") {
    assertOps(RoughRenderer.arc(50, 50, 80, 60, -1, 7, true, true, ro(5)), OpSetType.path, RoughRendererClampIss1204Data.expArcClampNegClosed5)
  }

  // ---- svgPath ----

  test("svgPath M/L/C/Z routes through _doubleLine + _bezierTo") {
    assertOps(RoughRenderer.svgPath("M10 10 L50 10 C60 20 70 30 80 40 Z", ro(11)), OpSetType.path, expSvgPath11)
  }

  // ---- solidFillPolygon ----

  test("solidFillPolygon seed13 emits a fillPath (move + lineTo per vertex)") {
    val polys: Vector[Vector[Point]] = Vector(Vector(Point(0, 0), Point(40, 0), Point(40, 30), Point(0, 30)))
    assertOps(RoughRenderer.solidFillPolygon(polys, ro(13)), OpSetType.fillPath, expSolidFill13)
  }

  // ---- patternFillPolygons (wires getFiller + helper -> hachure fillSketch) ----

  test("patternFillPolygons via the renderer helper = exact hachure fillSketch") {
    val polys: Vector[Vector[Point]] = Vector(Vector(Point(0, 0), Point(40, 0), Point(40, 30), Point(0, 30)))
    // cache-independent exact value (fresh HachureFiller == what getFiller builds clean):
    val viaFiller: OpSet = HachureFiller(rendererHelper).fillPolygons(polys, ro(13, roughness = 0.5))
    assertOps(viaFiller, OpSetType.fillSketch, expPatFillPoly13)
  }

  test("patternFillPolygons public entry wires to a non-empty fillSketch") {
    val polys: Vector[Vector[Point]] = Vector(Vector(Point(0, 0), Point(40, 0), Point(40, 30), Point(0, 30)))
    val out:   OpSet                 = RoughRenderer.patternFillPolygons(polys, ro(13, roughness = 0.5))
    assertEquals(out.`type`, OpSetType.fillSketch)
    assert(out.ops.nonEmpty, "patternFillPolygons should produce a non-empty fill")
  }

  // ---- patternFillArc ----

  // Reconstruct the arc's polygon exactly as patternFillArc does (rx/ry jitter, the
  // while(strt<0) normalize, the (stp-strt)>2π clamp, then the angle sweep), advancing the
  // SAME options' RNG, so the hachure fill matches the oracle for any start/stop span.
  private def reconstructArcPolygon(start: Double, stop: Double, o: ResolvedOptions): Vector[Point] = {
    val cx:  Double = 50
    val cy:  Double = 50
    var rx:  Double = Math.abs(80.0 / 2)
    var ry:  Double = Math.abs(60.0 / 2)
    rx += RoughRenderer.randOffset(rx * 0.01, o)
    ry += RoughRenderer.randOffset(ry * 0.01, o)
    var strt: Double = start
    var stp:  Double = stop
    while (strt < 0) {
      strt += Math.PI * 2
      stp += Math.PI * 2
    }
    if ((stp - strt) > (Math.PI * 2)) {
      strt = 0
      stp = Math.PI * 2
    }
    val increment: Double             = (stp - strt) / o.curveStepCount
    val pts:       ArrayBuffer[Point] = ArrayBuffer.empty
    var angle:     Double             = strt
    while (angle <= stp) {
      pts += Point(cx + rx * Math.cos(angle), cy + ry * Math.sin(angle))
      angle = angle + increment
    }
    pts += Point(cx + rx * Math.cos(stp), cy + ry * Math.sin(stp))
    pts += Point(cx, cy)
    pts.toVector
  }

  test("patternFillArc via the renderer helper = exact hachure fillSketch") {
    val o:   ResolvedOptions = ro(13, roughness = 0.5)
    val pts: Vector[Point]   = reconstructArcPolygon(0, Math.PI, o) // advances o's RNG by the 2 rx/ry draws
    val viaFiller: OpSet = HachureFiller(rendererHelper).fillPolygons(Vector(pts), o)
    assertOps(viaFiller, OpSetType.fillSketch, expPatFillArc13)
  }

  test("patternFillArc public entry wires to a non-empty fillSketch and builds the arc polygon") {
    val realArc: OpSet = RoughRenderer.patternFillArc(50, 50, 80, 60, 0, Math.PI, ro(13, roughness = 0.5))
    assertEquals(realArc.`type`, OpSetType.fillSketch)
    assert(realArc.ops.nonEmpty, "patternFillArc should produce a non-empty fill")
    // Cache-independent pin of the point construction: the public arc must equal
    // patternFillPolygons of the reconstructed points (both route through the same cache).
    val o2:   ResolvedOptions = ro(13, roughness = 0.5)
    val pts2: Vector[Point]   = reconstructArcPolygon(0, Math.PI, o2)
    assertEquals(realArc, RoughRenderer.patternFillPolygons(Vector(pts2), o2))
  }

  // patternFillArc with span > 2π exercises the identical (stp-strt) > 2π clamp (ISS-1359):
  // 7 > 2π, so strt/stp reset to 0..2π (a full sweep). Asserted cache-independently.
  test("patternFillArc span > 2π triggers the clamp to a full 0..2π sweep") {
    val o:   ResolvedOptions = ro(13, roughness = 0.5)
    val pts: Vector[Point]   = reconstructArcPolygon(0, 7, o) // clamp -> 0..2π
    val viaFiller: OpSet = HachureFiller(rendererHelper).fillPolygons(Vector(pts), o)
    assertOps(viaFiller, OpSetType.fillSketch, RoughRendererClampIss1204Data.expPatFillArcClamp13)
    // Cache-independent pin that the PUBLIC arc actually applies the clamp: the >2π arc must
    // equal patternFillPolygons of the clamped (0..2π) reconstructed points.
    val o2:   ResolvedOptions = ro(13, roughness = 0.5)
    val pts2: Vector[Point]   = reconstructArcPolygon(0, 7, o2)
    assertEquals(RoughRenderer.patternFillArc(50, 50, 80, 60, 0, 7, ro(13, roughness = 0.5)), RoughRenderer.patternFillPolygons(Vector(pts2), o2))
  }

  // ---- randOffset / randOffsetWithRange / doubleLineFillOps ----

  test("randOffset seed21 = _offsetOpt(x, o)") {
    assertEqualsDouble(RoughRenderer.randOffset(5, ro(21)), expRandOffset21, Delta)
  }

  test("randOffsetWithRange seed21 = _offset(min, max, o)") {
    assertEqualsDouble(RoughRenderer.randOffsetWithRange(2, 8, ro(21)), expRandOffsetRange21, Delta)
  }

  test("doubleLineFillOps seed21 = _doubleLine(..., filling=true)") {
    val out: Vector[Op] = RoughRenderer.doubleLineFillOps(0, 0, 30, 10, ro(21))
    assertEquals(out.length, expDblLineFill21.length)
    out.zip(expDblLineFill21).zipWithIndex.foreach { case ((a, e), i) => assertOpEquals(a, e, s"op[$i]") }
  }

  // ---- RNG threading: the var-randomizer must persist + advance across calls ----

  test("RNG threads across calls on the SAME options (randomizer persists and advances)") {
    val o: ResolvedOptions = ro(55)
    // First call seeds + advances o.randomizer.
    val l: OpSet = RoughRenderer.line(10, 10, 60, 10, o)
    assertOps(l, OpSetType.path, expThreadLine55)
    // Second call on the SAME o continues the advanced RNG (NOT a fresh Random(55)).
    val r: OpSet = RoughRenderer.rectangle(0, 0, 30, 20, o)
    assertOps(r, OpSetType.path, expThreadRect55)
    // Proof it advanced: the threaded rectangle differs from a fresh-seed rectangle.
    val fresh: OpSet = RoughRenderer.rectangle(0, 0, 30, 20, ro(55))
    assertOps(fresh, OpSetType.path, expFreshRect55)
    assert(r.ops.head.data != fresh.ops.head.data, "threaded vs fresh rectangle must differ (RNG advanced)")
  }
}
