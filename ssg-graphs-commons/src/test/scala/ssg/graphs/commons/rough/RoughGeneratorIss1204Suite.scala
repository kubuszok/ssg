/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Values-asserting tests for the roughjs generator port (Chip 7 of ISS-1204): RoughGenerator
 * (line/rectangle/ellipse/circle/linearPath/arc/curve/polygon/path -> Drawable) plus
 * opsToPath/toPaths/fillSketch/_mergedShape/_o (merge) and the path() regex-cleaning bug.
 *
 * Oracle: the vendored roughjs generator.ts (src/generator.ts, pinned 56a2762) run under
 * Node/tsx against a default `new RoughGenerator()` with seeded partial Options; the
 * expected `sets` for the non-hachure cases (solid fill + stroke-only) live in
 * `RoughGeneratorIss1204Data` (generated; see its header) and are compared with a 1e-9
 * delta (cross-platform libm cushion, far tighter than any mutation's >= 1e-3 effect).
 *
 * Cache note (hachure pattern fills): roughjs's pattern fill routes through the process-
 * global `Filler.getFiller` cache, which FillersIss1204Suite pollutes with a stub helper in
 * a full test run. So the EXACT hachure values cannot be pinned through the public generator
 * against an inlined oracle. Instead the pattern cases are asserted CACHE-INDEPENDENTLY by
 * reconstruction: the drawable's `sets` are compared (exact, same-JVM) against the same
 * RoughRenderer primitives (`rectangle`/`ellipseWithParams`/`arc`/`patternFillArc`/
 * `linearPath`/`curve`/`svgPath`/`patternFillPolygons`) called on a parallel-threaded
 * ResolvedOptions in the SAME source order — both route through the SAME cache, so they are
 * equal iff the points + RNG threading match. The polygon construction for `curve`'s
 * pattern fill (the <3/==3/else branches + the curve<->geometry Point bridging) is
 * reconstructed via CurveToBezier/PointsOnCurve.
 */
package ssg
package graphs
package commons
package rough

import ssg.graphs.commons.rough.curve.{ CurveToBezier, Point as CurvePoint, PointsOnCurve, PointsOnPath }

import munit.FunSuite

final class RoughGeneratorIss1204Suite extends FunSuite {

  import RoughGeneratorIss1204Data.*

  private val Delta: Double = 1e-9

  // ---- ops/sets comparison (with delta, for inline-oracle solid/stroke cases) ----

  private def assertOpApprox(actual: Op, expected: Op, ctx: String): Unit = {
    assertEquals(actual.op, expected.op, s"$ctx op")
    assertEquals(actual.data.length, expected.data.length, s"$ctx data length")
    actual.data.zip(expected.data).zipWithIndex.foreach { case ((av, ev), j) =>
      assertEqualsDouble(av, ev, Delta, s"$ctx data[$j]")
    }
  }

  private def assertSetsApprox(actual: Vector[OpSet], expected: Vector[OpSet], ctx: String): Unit = {
    assertEquals(actual.length, expected.length, s"$ctx sets length")
    actual.zip(expected).zipWithIndex.foreach { case ((a, e), i) =>
      assertEquals(a.`type`, e.`type`, s"$ctx set[$i] type")
      assertEquals(a.ops.length, e.ops.length, s"$ctx set[$i] ops length")
      a.ops.zip(e.ops).zipWithIndex.foreach { case ((ao, eo), j) =>
        assertOpApprox(ao, eo, s"$ctx set[$i] op[$j]")
      }
    }
  }

  // ---- ResolvedOptions field-wise comparison (ignoring the mutable `randomizer`) ----

  private def assertResolvedEq(a: ResolvedOptions, e: ResolvedOptions, ctx: String): Unit = {
    assertEquals(a.maxRandomnessOffset, e.maxRandomnessOffset, s"$ctx maxRandomnessOffset")
    assertEquals(a.roughness, e.roughness, s"$ctx roughness")
    assertEquals(a.bowing, e.bowing, s"$ctx bowing")
    assertEquals(a.stroke, e.stroke, s"$ctx stroke")
    assertEquals(a.strokeWidth, e.strokeWidth, s"$ctx strokeWidth")
    assertEquals(a.curveFitting, e.curveFitting, s"$ctx curveFitting")
    assertEquals(a.curveTightness, e.curveTightness, s"$ctx curveTightness")
    assertEquals(a.curveStepCount, e.curveStepCount, s"$ctx curveStepCount")
    assertEquals(a.fillStyle, e.fillStyle, s"$ctx fillStyle")
    assertEquals(a.fillWeight, e.fillWeight, s"$ctx fillWeight")
    assertEquals(a.hachureAngle, e.hachureAngle, s"$ctx hachureAngle")
    assertEquals(a.hachureGap, e.hachureGap, s"$ctx hachureGap")
    assertEquals(a.dashOffset, e.dashOffset, s"$ctx dashOffset")
    assertEquals(a.dashGap, e.dashGap, s"$ctx dashGap")
    assertEquals(a.zigzagOffset, e.zigzagOffset, s"$ctx zigzagOffset")
    assertEquals(a.seed, e.seed, s"$ctx seed")
    assertEquals(a.disableMultiStroke, e.disableMultiStroke, s"$ctx disableMultiStroke")
    assertEquals(a.disableMultiStrokeFill, e.disableMultiStrokeFill, s"$ctx disableMultiStrokeFill")
    assertEquals(a.preserveVertices, e.preserveVertices, s"$ctx preserveVertices")
    assertEquals(a.fillShapeRoughnessGain, e.fillShapeRoughnessGain, s"$ctx fillShapeRoughnessGain")
    assertEquals(a.fill, e.fill, s"$ctx fill")
    assertEquals(a.simplification, e.simplification, s"$ctx simplification")
    assertEquals(a.strokeLineDash, e.strokeLineDash, s"$ctx strokeLineDash")
    assertEquals(a.strokeLineDashOffset, e.strokeLineDashOffset, s"$ctx strokeLineDashOffset")
    assertEquals(a.fillLineDash, e.fillLineDash, s"$ctx fillLineDash")
    assertEquals(a.fillLineDashOffset, e.fillLineDashOffset, s"$ctx fillLineDashOffset")
    assertEquals(a.fixedDecimalPlaceDigits, e.fixedDecimalPlaceDigits, s"$ctx fixedDecimalPlaceDigits")
  }

  // The roughjs generator default ResolvedOptions (the 21-field default block), threadable.
  private def roDefaults: ResolvedOptions = ResolvedOptions(
    maxRandomnessOffset = 2,
    roughness = 1,
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

  // ---- curve <-> geometry Point bridging (for curve pattern-fill reconstruction) ----

  private def toCurve(p: Point): CurvePoint = CurvePoint(p.x, p.y)
  private def toGeom(p: CurvePoint): Point  = Point(p.x, p.y)
  private def bezierPoly(pts: Vector[Point], roughness: Double): Vector[Point] =
    PointsOnCurve.pointsOnBezierCurves(CurveToBezier.curveToBezier(pts.map(toCurve)), 10, Some((1 + roughness) / 2)).map(toGeom)

  // ================= defaultOptions: pin all 20 fields exactly (mutation (a)) =================

  test("defaultOptions pins every default value exactly") {
    val d: ResolvedOptions = new RoughGenerator().defaultOptions
    assertEquals(d.maxRandomnessOffset, 2.0)
    assertEquals(d.roughness, 1.0)
    assertEquals(d.bowing, 1.0)
    assertEquals(d.stroke, "#000")
    assertEquals(d.strokeWidth, 1.0)
    assertEquals(d.curveTightness, 0.0)
    assertEquals(d.curveFitting, 0.95)
    assertEquals(d.curveStepCount, 9.0)
    assertEquals(d.fillStyle, "hachure")
    assertEquals(d.fillWeight, -1.0)
    assertEquals(d.hachureAngle, -41.0)
    assertEquals(d.hachureGap, -1.0)
    assertEquals(d.dashOffset, -1.0)
    assertEquals(d.dashGap, -1.0)
    assertEquals(d.zigzagOffset, -1.0)
    assertEquals(d.seed, 0)
    assertEquals(d.disableMultiStroke, false)
    assertEquals(d.disableMultiStrokeFill, false)
    assertEquals(d.preserveVertices, false)
    assertEquals(d.fillShapeRoughnessGain, 0.8)
    // the seven inherited-optional fields default to None
    assertEquals(d.fill, None)
    assertEquals(d.simplification, None)
  }

  // ================= _o merge via constructor(config.options) (mutation (b)) =================

  test("_o merge: a partial Options overrides exactly those fields, keeps the rest as defaults") {
    val g: RoughGenerator = new RoughGenerator(
      Config(Some(Options(
        roughness = Some(3),
        stroke = Some("red"),
        fill = Some("blue"),
        seed = Some(5),
        fillStyle = Some("solid"),
        hachureAngle = Some(17)
      )))
    )
    val expected: ResolvedOptions =
      roDefaults.copy(roughness = 3, stroke = "red", fill = Some("blue"), seed = 5, fillStyle = "solid", hachureAngle = 17)
    assertResolvedEq(g.defaultOptions, expected, "merged")
  }

  test("_o merge keeps a field at its default when the override is absent (None does not override)") {
    val g: RoughGenerator = new RoughGenerator(Config(Some(Options(roughness = Some(3)))))
    // only roughness changed; hachureAngle/stroke/etc stay at defaults
    assertEquals(g.defaultOptions.roughness, 3.0)
    assertEquals(g.defaultOptions.hachureAngle, -41.0)
    assertEquals(g.defaultOptions.stroke, "#000")
    assertEquals(g.defaultOptions.fillStyle, "hachure")
  }

  // ================= shape methods: exact solid/stroke Drawables (inline oracle) =================

  test("line seed42 -> exact Drawable (shape/sets/options)") {
    val d: Drawable = new RoughGenerator().line(10, 10, 110, 30, Some(Options(seed = Some(42))))
    assertEquals(d.shape, "line")
    assertSetsApprox(d.sets, expLine42, "line")
    assertResolvedEq(d.options, roDefaults.copy(seed = 42), "line options")
  }

  test("ellipse solid fill calls ellipseWithParams TWICE (mutation (f)) -> exact [fillPath, path]") {
    val d: Drawable =
      new RoughGenerator().ellipse(50, 50, 80, 60, Some(Options(seed = Some(99), fill = Some("red"), fillStyle = Some("solid"))))
    assertEquals(d.shape, "ellipse")
    assertEquals(d.sets.map(_.`type`), Vector(OpSetType.fillPath, OpSetType.path))
    assertSetsApprox(d.sets, expEllipseSolid99, "ellipse solid")
    // the fillPath (2nd ellipseWithParams) must DIFFER from the stroke path (1st call): proves
    // the RNG advanced between the two calls (a single-call mutant makes them equal).
    assert(d.sets(0).ops.head.data != d.sets(1).ops.head.data, "fillPath (2nd call) must differ from stroke (1st call)")
  }

  test("ellipse no fill, stroke=none -> empty sets") {
    val d: Drawable =
      new RoughGenerator().ellipse(50, 50, 80, 60, Some(Options(seed = Some(99), stroke = Some("none"))))
    assertEquals(d.shape, "ellipse")
    assertEquals(d.sets, Vector.empty[OpSet])
  }

  test("circle sets shape to 'circle' (mutation (g)) and reuses ellipse geometry") {
    val d: Drawable = new RoughGenerator().circle(50, 50, 40, Some(Options(seed = Some(99))))
    assertEquals(d.shape, "circle")
    assertSetsApprox(d.sets, expCircle99, "circle")
    // circle(d) == ellipse(d, d) with shape renamed:
    val e: Drawable = new RoughGenerator().ellipse(50, 50, 40, 40, Some(Options(seed = Some(99))))
    assertEquals(d.sets, e.sets)
    assertEquals(e.shape, "ellipse")
  }

  test("linearPath seed7 -> exact Drawable") {
    val d: Drawable = new RoughGenerator().linearPath(Vector(Point(0, 0), Point(50, 0), Point(25, 40)), Some(Options(seed = Some(7))))
    assertEquals(d.shape, "linearPath")
    assertSetsApprox(d.sets, expLinearPath7, "linearPath")
  }

  test("arc closed solid fill -> exact [fillPath, path]; fillOptions.disableMultiStroke=true") {
    val d: Drawable =
      new RoughGenerator().arc(50, 50, 80, 60, 0, Math.PI, true, Some(Options(seed = Some(5), fill = Some("red"), fillStyle = Some("solid"))))
    assertEquals(d.shape, "arc")
    assertEquals(d.sets.map(_.`type`), Vector(OpSetType.fillPath, OpSetType.path))
    assertSetsApprox(d.sets, expArcSolid5, "arc solid")
  }

  test("arc open (closed=false) skips the fill branch even when fill is set -> [path]") {
    val d: Drawable =
      new RoughGenerator().arc(50, 50, 80, 60, 0, Math.PI, false, Some(Options(seed = Some(5), fill = Some("red"))))
    assertEquals(d.shape, "arc")
    assertEquals(d.sets.map(_.`type`), Vector(OpSetType.path))
    assertSetsApprox(d.sets, expArcOpen5, "arc open")
  }

  test("curve solid fill -> _mergedShape fillPath + stroke (mutation (i): index-0 kept, no later moves here)") {
    val d: Drawable =
      new RoughGenerator().curve(Vector(Point(0, 0), Point(20, 30), Point(40, 10), Point(60, 50), Point(80, 0)), Some(Options(seed = Some(3), fill = Some("red"), fillStyle = Some("solid"))))
    assertEquals(d.shape, "curve")
    assertEquals(d.sets.map(_.`type`), Vector(OpSetType.fillPath, OpSetType.path))
    assertSetsApprox(d.sets, expCurveSolid3, "curve solid")
  }

  test("polygon solid fill -> exact [fillPath, path]") {
    val d: Drawable =
      new RoughGenerator().polygon(Vector(Point(0, 0), Point(40, 0), Point(40, 30), Point(0, 30)), Some(Options(seed = Some(13), fill = Some("red"), fillStyle = Some("solid"))))
    assertEquals(d.shape, "polygon")
    assertEquals(d.sets.map(_.`type`), Vector(OpSetType.fillPath, OpSetType.path))
    assertSetsApprox(d.sets, expPolygonSolid13, "polygon solid")
  }

  // ================= path(): regex cleaning + the solid branches =================

  test("path stroke-only: \\n -> space and (-\\s) -> '-' regexes (mutation (e)) via the cleaned d") {
    // input contains a newline and a "- " (minus+space); cleaned -> "M10 10 L50 10  L-30 40"
    val d: Drawable = new RoughGenerator().path("M10 10\nL50 10  L- 30 40", Some(Options(seed = Some(11))))
    assertEquals(d.shape, "path")
    assertSetsApprox(d.sets, expPathStroke11, "path stroke")
  }

  test("path 3rd replace: literal '/(ss)/g' substring is replaced by a space (upstream bug replicated)") {
    // "M0 0 L40 0/(ss)/gL40 30 L0 30 Z" -> the literal '/(ss)/g' becomes ' ', cleaning to the
    // rectangle path; a mutant that drops the 3rd replace would feed garbage to svgPath.
    val d: Drawable = new RoughGenerator().path("M0 0 L40 0/(ss)/gL40 30 L0 30 Z", Some(Options(seed = Some(11))))
    assertSetsApprox(d.sets, expPathLiteralSlashSS11, "path /(ss)/g")
  }

  test("path solid, single subpath -> _mergedShape drops later 'move' ops (mutation (i))") {
    val d: Drawable =
      new RoughGenerator().path("M0 0 L40 0 L40 30 L0 30 Z", Some(Options(seed = Some(11), fill = Some("red"), fillStyle = Some("solid"))))
    assertEquals(d.sets.map(_.`type`), Vector(OpSetType.fillPath, OpSetType.path))
    // mergedShape keeps op[0] (a move) + drops the 3 later moves -> a single leading move:
    assertEquals(d.sets(0).ops.head.op, OpType.move)
    assertEquals(d.sets(0).ops.tail.count(_.op == OpType.move), 0, "no later move ops survive mergedShape")
    assertSetsApprox(d.sets, expPathSolidSingle11, "path solid single")
  }

  test("path solid, two subpaths (sets.length>1) -> solidFillPolygon (not mergedShape)") {
    val d: Drawable =
      new RoughGenerator().path("M0 0 L40 0 L40 30 Z M50 50 L80 50 L80 70 Z", Some(Options(seed = Some(11), fill = Some("red"), fillStyle = Some("solid"))))
    assertEquals(d.sets.map(_.`type`), Vector(OpSetType.fillPath, OpSetType.path))
    assertSetsApprox(d.sets, expPathTwoSubpaths11, "path solid two subpaths")
  }

  test("path with simplification<1 -> simplified linearPath stroke (no shape, no fill)") {
    val d: Drawable =
      new RoughGenerator().path("M0 0 L40 0 L40 30 L0 30 Z", Some(Options(seed = Some(11), simplification = Some(0.5))))
    assertEquals(d.sets.map(_.`type`), Vector(OpSetType.path))
    assertSetsApprox(d.sets, expPathSimplified11, "path simplified")
  }

  test("path empty d -> empty sets") {
    val d: Drawable = new RoughGenerator().path("", Some(Options(seed = Some(11))))
    assertEquals(d.shape, "path")
    assertEquals(d.sets, Vector.empty[OpSet])
  }

  // ================= pattern (hachure) fills: cache-independent reconstruction =================

  test("rectangle pattern (hachure) fill -> [fillSketch, path], reconstructed cache-independently") {
    val d: Drawable =
      new RoughGenerator().rectangle(0, 0, 100, 50, Some(Options(seed = Some(7), fill = Some("red"))))
    assertEquals(d.sets.map(_.`type`), Vector(OpSetType.fillSketch, OpSetType.path))
    val o2:      ResolvedOptions = roDefaults.copy(seed = 7, fill = Some("red"))
    val outline: OpSet           = RoughRenderer.rectangle(0, 0, 100, 50, o2)
    val pts:     Vector[Point]   = Vector(Point(0, 0), Point(100, 0), Point(100, 50), Point(0, 50))
    val fill:    OpSet           = RoughRenderer.patternFillPolygons(Vector(pts), o2)
    assertEquals(d.sets, Vector(fill, outline))
  }

  test("rectangle no fill, stroke=none -> empty sets") {
    val d: Drawable =
      new RoughGenerator().rectangle(0, 0, 100, 50, Some(Options(seed = Some(7), stroke = Some("none"))))
    assertEquals(d.sets, Vector.empty[OpSet])
  }

  test("ellipse pattern (hachure) fill -> [fillSketch, path] (single ellipseWithParams), reconstructed") {
    val d: Drawable =
      new RoughGenerator().ellipse(50, 50, 80, 60, Some(Options(seed = Some(99), fill = Some("red"))))
    assertEquals(d.sets.map(_.`type`), Vector(OpSetType.fillSketch, OpSetType.path))
    val o2:     ResolvedOptions = roDefaults.copy(seed = 99, fill = Some("red"))
    val params: EllipseParams   = RoughRenderer.generateEllipseParams(80, 60, o2)
    val resp:   EllipseResult   = RoughRenderer.ellipseWithParams(50, 50, o2, params)
    val fill:   OpSet           = RoughRenderer.patternFillPolygons(Vector(resp.estimatedPoints), o2)
    assertEquals(d.sets, Vector(fill, resp.opset))
  }

  test("arc closed pattern (hachure) fill -> [fillSketch, path] via patternFillArc, reconstructed") {
    val d: Drawable =
      new RoughGenerator().arc(50, 50, 80, 60, 0, Math.PI, true, Some(Options(seed = Some(5), fill = Some("red"))))
    assertEquals(d.sets.map(_.`type`), Vector(OpSetType.fillSketch, OpSetType.path))
    val o2:      ResolvedOptions = roDefaults.copy(seed = 5, fill = Some("red"))
    val outline: OpSet           = RoughRenderer.arc(50, 50, 80, 60, 0, Math.PI, true, true, o2)
    val fill:    OpSet           = RoughRenderer.patternFillArc(50, 50, 80, 60, 0, Math.PI, o2)
    assertEquals(d.sets, Vector(fill, outline))
  }

  test("arc closed pattern, stroke=none -> [fillSketch] only") {
    val d: Drawable =
      new RoughGenerator().arc(50, 50, 80, 60, 0, Math.PI, true, Some(Options(seed = Some(5), fill = Some("red"), stroke = Some("none"))))
    assertEquals(d.sets.map(_.`type`), Vector(OpSetType.fillSketch))
  }

  test("polygon pattern (hachure) fill -> [fillSketch, path], reconstructed") {
    val pts: Vector[Point] = Vector(Point(0, 0), Point(40, 0), Point(40, 30), Point(0, 30))
    val d:   Drawable      = new RoughGenerator().polygon(pts, Some(Options(seed = Some(13), fill = Some("red"))))
    assertEquals(d.sets.map(_.`type`), Vector(OpSetType.fillSketch, OpSetType.path))
    val o2:      ResolvedOptions = roDefaults.copy(seed = 13, fill = Some("red"))
    val outline: OpSet           = RoughRenderer.linearPath(pts, true, o2)
    val fill:    OpSet           = RoughRenderer.patternFillPolygons(Vector(pts), o2)
    assertEquals(d.sets, Vector(fill, outline))
  }

  test("curve pattern else-branch (5 points, >3): curveToBezier+pointsOnBezierCurves polygon, reconstructed") {
    val pts5: Vector[Point] = Vector(Point(0, 0), Point(20, 30), Point(40, 10), Point(60, 50), Point(80, 0))
    val d:    Drawable      = new RoughGenerator().curve(pts5, Some(Options(seed = Some(3), fill = Some("red"))))
    assertEquals(d.sets.map(_.`type`), Vector(OpSetType.fillSketch, OpSetType.path))
    val o2:      ResolvedOptions = roDefaults.copy(seed = 3, fill = Some("red"))
    val outline: OpSet           = RoughRenderer.curve(pts5, o2)
    val poly:    Vector[Point]   = bezierPoly(pts5, 1) // roughness default 1
    val fill:    OpSet           = RoughRenderer.patternFillPolygons(Vector(poly), o2)
    assertEquals(d.sets, Vector(fill, outline))
  }

  test("curve pattern ===3 branch (3 points): [p0,p0,p1,p2] bezier polygon, reconstructed") {
    val pts3: Vector[Point] = Vector(Point(0, 0), Point(20, 30), Point(40, 10))
    val d:    Drawable      = new RoughGenerator().curve(pts3, Some(Options(seed = Some(3), fill = Some("red"))))
    assertEquals(d.sets.map(_.`type`), Vector(OpSetType.fillSketch, OpSetType.path))
    val o2:      ResolvedOptions = roDefaults.copy(seed = 3, fill = Some("red"))
    val outline: OpSet           = RoughRenderer.curve(pts3, o2)
    val poly:    Vector[Point]   = bezierPoly(Vector(pts3(0), pts3(0), pts3(1), pts3(2)), 1)
    val fill:    OpSet           = RoughRenderer.patternFillPolygons(Vector(poly), o2)
    assertEquals(d.sets, Vector(fill, outline))
  }

  test("curve pattern <3 branch (2 points): the points are used verbatim, reconstructed") {
    val pts2: Vector[Point] = Vector(Point(0, 0), Point(20, 30))
    val d:    Drawable      = new RoughGenerator().curve(pts2, Some(Options(seed = Some(3), fill = Some("red"))))
    assertEquals(d.sets.map(_.`type`), Vector(OpSetType.fillSketch, OpSetType.path))
    val o2:      ResolvedOptions = roDefaults.copy(seed = 3, fill = Some("red"))
    val outline: OpSet           = RoughRenderer.curve(pts2, o2)
    val fill:    OpSet           = RoughRenderer.patternFillPolygons(Vector(pts2), o2)
    assertEquals(d.sets, Vector(fill, outline))
  }

  test("curve pattern multi-list [3pts, 2pts]: concat of the ===3 and <3 polygons, reconstructed") {
    val lists: Vector[Vector[Point]] =
      Vector(Vector(Point(0, 0), Point(20, 30), Point(40, 10)), Vector(Point(60, 50), Point(80, 0)))
    val d: Drawable = new RoughGenerator().curve(lists, Some(Options(seed = Some(3), fill = Some("red"))))
    assertEquals(d.sets.map(_.`type`), Vector(OpSetType.fillSketch, OpSetType.path))
    val o2:      ResolvedOptions = roDefaults.copy(seed = 3, fill = Some("red"))
    val outline: OpSet           = RoughRenderer.curve(lists, o2)
    val l0:      Vector[Point]   = lists(0)
    val l1:      Vector[Point]   = lists(1)
    val poly:    Vector[Point]   = bezierPoly(Vector(l0(0), l0(0), l0(1), l0(2)), 1) ++ l1 // ===3 then <3
    val fill:    OpSet           = RoughRenderer.patternFillPolygons(Vector(poly), o2)
    assertEquals(d.sets, Vector(fill, outline))
  }

  test("path pattern (hachure) fill -> [fillSketch, path], reconstructed (shape computed before fill)") {
    val pathD: String   = "M0 0 L40 0 L40 30 L0 30 Z"
    val d:     Drawable = new RoughGenerator().path(pathD, Some(Options(seed = Some(11), fill = Some("red"))))
    assertEquals(d.sets.map(_.`type`), Vector(OpSetType.fillSketch, OpSetType.path))
    val o2:    ResolvedOptions       = roDefaults.copy(seed = 11, fill = Some("red"))
    val sets:  Vector[Vector[Point]] = PointsOnPath.pointsOnPath(pathD, Some(1.0), Some((1 + 1.0) / 2)).map(_.map(toGeom))
    val shape: OpSet                 = RoughRenderer.svgPath(pathD, o2) // computed BEFORE the fill (advances RNG first)
    val fill:  OpSet                 = RoughRenderer.patternFillPolygons(sets, o2)
    assertEquals(d.sets, Vector(fill, shape))
  }

  // ================= opsToPath: exact SVG strings + fixedDecimals (mutations (c),(d)) =================

  private val sampleOpSet: OpSet = OpSet(
    `type` = OpSetType.path,
    ops = Vector(
      Op(OpType.move, Vector(10, 10)),
      Op(OpType.bcurveTo, Vector(20.5, 30.25, 40, 10.123456789, 60.7, 50)),
      Op(OpType.lineTo, Vector(0.0001, 100000000))
    )
  )

  test("opsToPath formats move/bcurveTo/lineTo with exact spacing + commas + trim (mutation (c))") {
    assertEquals(
      new RoughGenerator().opsToPath(sampleOpSet),
      "M10 10 C20.5 30.25, 40 10.123456789, 60.7 50 L0.0001 100000000"
    )
  }

  test("opsToPath fixedDecimals=2 rounds each datum then re-stringifies (mutation (d))") {
    assertEquals(
      new RoughGenerator().opsToPath(sampleOpSet, Some(2)),
      "M10 10 C20.5 30.25, 40 10.12, 60.7 50 L0 100000000"
    )
  }

  test("opsToPath fixedDecimals=0 rounds half-away-from-zero") {
    assertEquals(
      new RoughGenerator().opsToPath(sampleOpSet, Some(0)),
      "M10 10 C21 30, 40 10, 61 50 L0 100000000"
    )
  }

  // ================= toPaths + fillSketch (mutation (h)) =================

  private def toPathsDrawable(fillWeight: Double): Drawable =
    Drawable(
      shape = "rectangle",
      options = roDefaults.copy(seed = 7, fill = Some("red"), stroke = "blue", strokeWidth = 3, fillWeight = fillWeight),
      sets = Vector(
        OpSet(`type` = OpSetType.fillSketch, ops = Vector(Op(OpType.move, Vector(1, 2)), Op(OpType.lineTo, Vector(3, 4)))),
        OpSet(`type` = OpSetType.fillPath, ops = Vector(Op(OpType.move, Vector(5, 6)), Op(OpType.lineTo, Vector(7, 8)))),
        OpSet(`type` = OpSetType.path, ops = Vector(Op(OpType.move, Vector(9, 10)), Op(OpType.bcurveTo, Vector(11, 12, 13, 14, 15, 16))))
      )
    )

  test("toPaths builds PathInfo per OpSet type; fillSketch fweight<0 falls back to strokeWidth/2 (mutation (h))") {
    val paths: Vector[PathInfo] = new RoughGenerator().toPaths(toPathsDrawable(-1))
    assertEquals(
      paths,
      Vector(
        // fillSketch: stroke = fill||none = red; strokeWidth = fillWeight<0 -> strokeWidth/2 = 1.5; fill = none
        PathInfo(d = "M1 2 L3 4", stroke = "red", strokeWidth = 1.5, fill = Some("none")),
        // fillPath: stroke = none; strokeWidth = 0; fill = fill||none = red
        PathInfo(d = "M5 6 L7 8", stroke = "none", strokeWidth = 0, fill = Some("red")),
        // path: stroke = o.stroke = blue; strokeWidth = 3; fill = none
        PathInfo(d = "M9 10 C11 12, 13 14, 15 16", stroke = "blue", strokeWidth = 3, fill = Some("none"))
      )
    )
  }

  test("toPaths fillSketch with fillWeight>=0 uses fillWeight verbatim (no /2 fallback)") {
    val paths: Vector[PathInfo] = new RoughGenerator().toPaths(toPathsDrawable(2.5))
    assertEquals(paths.head, PathInfo(d = "M1 2 L3 4", stroke = "red", strokeWidth = 2.5, fill = Some("none")))
  }

  // ================= companion: newSeed + numToString (ECMA) =================

  test("newSeed returns an integer seed in [0, 2^31)") {
    val s: Int = RoughGenerator.newSeed()
    assert(s >= 0, s"newSeed >= 0, got $s")
  }
}
