/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Values-asserting tests for the roughjs SVG-output port (Chip 8 of ISS-1204): RoughSVG
 * (draw/fillSketch/opsToPath/getDefaultOptions/generator + shape methods) and the Rough
 * entry object (svg/generator/newSeed/canvas).
 *
 * Oracle: the vendored roughjs svg.ts (src/svg.ts, pinned 56a2762). Because draw()/fillSketch()
 * are a pure DOM->SVG-markup ADAPTATION (no RNG of their own), their attribute logic is pinned
 * with LITERAL expected (name,value) sequences transcribed directly from svg.ts lines 14-77
 * (the per-OpSet-type setAttribute calls). Hand-built Drawables with trivial op-sets give known
 * `opsToPath` strings ("M1 2 L3 4"), so the expected attrs are exact and independent of the
 * generator. The shape methods (line/rectangle/...) are pinned by ROUTING-EQUIVALENCE: each
 * `svg.<shape>(args, seeded)` markup is asserted equal to `draw(gen.<shape>(args, seeded))` for
 * an explicitly-named generator call (so calling the wrong gen.<shape> diverges), seeded for
 * determinism.
 */
package ssg
package graphs
package commons
package rough

import ssg.graphs.commons.svg.SvgElement

import munit.FunSuite

final class RoughSvgIss1204Suite extends FunSuite {

  // The roughjs generator default ResolvedOptions (the 20-field default block), used to build
  // hand-crafted Drawables for the draw()/fillSketch() attribute tests.
  private def ro: ResolvedOptions = ResolvedOptions(
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

  // A trivial op-set: opsToPath -> "M1 2 L3 4".
  private def opset(t: OpSetType): OpSet =
    OpSet(`type` = t, ops = Vector(Op(OpType.move, Vector(1, 2)), Op(OpType.lineTo, Vector(3, 4))))

  // The ordered (name, value) attribute pairs of an element (insertion order = svg.ts setAttribute order).
  private def attrsOf(el: SvgElement): Vector[(String, String)] =
    el.attributes.toVector

  private def child(g: SvgElement, i: Int): SvgElement =
    g.children(i)

  // ===================== draw(): 'path' OpSet =====================

  test("draw 'path' -> <path> with d/stroke/stroke-width/fill='none' in that order (mutation a: fill='none')") {
    val o: ResolvedOptions = ro.copy(stroke = "blue", strokeWidth = 3)
    val d: Drawable        = Drawable("rectangle", o, Vector(opset(OpSetType.path)))
    val g: SvgElement      = new RoughSVG().draw(d)
    assertEquals(g.tagName, "g")
    assertEquals(g.children.size, 1)
    val p: SvgElement = child(g, 0)
    assertEquals(p.tagName, "path")
    assertEquals(
      attrsOf(p),
      Vector("d" -> "M1 2 L3 4", "stroke" -> "blue", "stroke-width" -> "3", "fill" -> "none")
    )
    // full markup (self-closing path inside a pretty <g>)
    assertEquals(g.toMarkup(), "<g>\n  <path d=\"M1 2 L3 4\" stroke=\"blue\" stroke-width=\"3\" fill=\"none\" />\n</g>")
  }

  test("draw 'path' strokeWidth uses ECMA Number::toString (integral 3 -> '3', not '3.0')") {
    val o: ResolvedOptions = ro.copy(strokeWidth = 3)
    val p: SvgElement      = child(new RoughSVG().draw(Drawable("rectangle", o, Vector(opset(OpSetType.path)))), 0)
    assertEquals(p.attr("stroke-width").toOption, Some("3"))
  }

  test(
    "draw 'path' with strokeLineDash + strokeLineDashOffset -> dasharray (space-joined) + dashoffset (mutation e: separator)"
  ) {
    val o: ResolvedOptions = ro.copy(stroke = "blue", strokeWidth = 3, strokeLineDash = Some(Vector(5, 3)), strokeLineDashOffset = Some(2))
    val p: SvgElement      = child(new RoughSVG().draw(Drawable("rectangle", o, Vector(opset(OpSetType.path)))), 0)
    assertEquals(
      attrsOf(p),
      Vector(
        "d" -> "M1 2 L3 4",
        "stroke" -> "blue",
        "stroke-width" -> "3",
        "fill" -> "none",
        "stroke-dasharray" -> "5 3",
        "stroke-dashoffset" -> "2"
      )
    )
  }

  test(
    "draw 'path' strokeLineDash present-but-EMPTY array is still truthy -> dasharray='' set; offset 0 is falsy -> no dashoffset"
  ) {
    val o: ResolvedOptions = ro.copy(strokeLineDash = Some(Vector.empty), strokeLineDashOffset = Some(0))
    val p: SvgElement      = child(new RoughSVG().draw(Drawable("rectangle", o, Vector(opset(OpSetType.path)))), 0)
    assertEquals(p.attr("stroke-dasharray").toOption, Some(""))
    assertEquals(p.attr("stroke-dashoffset").isDefined, false)
  }

  test("draw 'path' strokeLineDash None -> no dasharray; offset present nonzero -> dashoffset set") {
    val o: ResolvedOptions = ro.copy(strokeLineDash = None, strokeLineDashOffset = Some(7))
    val p: SvgElement      = child(new RoughSVG().draw(Drawable("rectangle", o, Vector(opset(OpSetType.path)))), 0)
    assertEquals(p.attr("stroke-dasharray").isDefined, false)
    assertEquals(p.attr("stroke-dashoffset").toOption, Some("7"))
  }

  // ===================== draw(): 'fillPath' OpSet =====================

  test("draw 'fillPath' (shape rectangle) -> stroke='none'/stroke-width='0'/fill=o.fill; NO fill-rule") {
    val o: ResolvedOptions = ro.copy(fill = Some("red"))
    val p: SvgElement      = child(new RoughSVG().draw(Drawable("rectangle", o, Vector(opset(OpSetType.fillPath)))), 0)
    assertEquals(
      attrsOf(p),
      Vector("d" -> "M1 2 L3 4", "stroke" -> "none", "stroke-width" -> "0", "fill" -> "red")
    )
  }

  test("draw 'fillPath' shape='curve' -> fill-rule='evenodd' appended (mutation b)") {
    val o: ResolvedOptions = ro.copy(fill = Some("red"))
    val p: SvgElement      = child(new RoughSVG().draw(Drawable("curve", o, Vector(opset(OpSetType.fillPath)))), 0)
    assertEquals(p.attr("fill-rule").toOption, Some("evenodd"))
    assertEquals(attrsOf(p).last, ("fill-rule", "evenodd"))
  }

  test("draw 'fillPath' shape='polygon' -> fill-rule='evenodd'") {
    val o: ResolvedOptions = ro.copy(fill = Some("red"))
    val p: SvgElement      = child(new RoughSVG().draw(Drawable("polygon", o, Vector(opset(OpSetType.fillPath)))), 0)
    assertEquals(p.attr("fill-rule").toOption, Some("evenodd"))
  }

  test("draw 'fillPath' shape='ellipse' (NOT curve/polygon) -> NO fill-rule (mutation b: wrong shape)") {
    val o: ResolvedOptions = ro.copy(fill = Some("red"))
    val p: SvgElement      = child(new RoughSVG().draw(Drawable("ellipse", o, Vector(opset(OpSetType.fillPath)))), 0)
    assertEquals(p.attr("fill-rule").isDefined, false)
  }

  test("draw 'fillPath' with o.fill None -> fill='' (the o.fill||'' empty-string, NOT 'none')") {
    val o: ResolvedOptions = ro.copy(fill = None)
    val p: SvgElement      = child(new RoughSVG().draw(Drawable("rectangle", o, Vector(opset(OpSetType.fillPath)))), 0)
    assertEquals(p.attr("fill").toOption, Some(""))
  }

  // ===================== draw(): 'fillSketch' OpSet =====================

  test("draw 'fillSketch' -> stroke=o.fill (mutation c: not o.stroke)/stroke-width=fweight/fill='none'") {
    val o: ResolvedOptions = ro.copy(fill = Some("green"), stroke = "black", fillWeight = 2, strokeWidth = 4)
    val p: SvgElement      = child(new RoughSVG().draw(Drawable("rectangle", o, Vector(opset(OpSetType.fillSketch)))), 0)
    assertEquals(
      attrsOf(p),
      Vector("d" -> "M1 2 L3 4", "stroke" -> "green", "stroke-width" -> "2", "fill" -> "none")
    )
  }

  test("draw 'fillSketch' fillWeight<0 -> strokeWidth/2 (mutation d: fallback)") {
    val o: ResolvedOptions = ro.copy(fill = Some("green"), fillWeight = -1, strokeWidth = 4)
    val p: SvgElement      = child(new RoughSVG().draw(Drawable("rectangle", o, Vector(opset(OpSetType.fillSketch)))), 0)
    assertEquals(p.attr("stroke-width").toOption, Some("2")) // 4 / 2
  }

  test("draw 'fillSketch' fillWeight>=0 used verbatim (no /2 fallback)") {
    val o: ResolvedOptions = ro.copy(fill = Some("green"), fillWeight = 2.5, strokeWidth = 4)
    val p: SvgElement      = child(new RoughSVG().draw(Drawable("rectangle", o, Vector(opset(OpSetType.fillSketch)))), 0)
    assertEquals(p.attr("stroke-width").toOption, Some("2.5"))
  }

  test("draw 'fillSketch' o.fill None -> stroke='' (o.fill||'')") {
    val o: ResolvedOptions = ro.copy(fill = None, fillWeight = 2)
    val p: SvgElement      = child(new RoughSVG().draw(Drawable("rectangle", o, Vector(opset(OpSetType.fillSketch)))), 0)
    assertEquals(p.attr("stroke").toOption, Some(""))
  }

  test("draw 'fillSketch' with fillLineDash + fillLineDashOffset -> dasharray (space-joined) + dashoffset") {
    val o: ResolvedOptions = ro.copy(fill = Some("green"), fillWeight = 2, fillLineDash = Some(Vector(2, 1)), fillLineDashOffset = Some(3))
    val p: SvgElement      = child(new RoughSVG().draw(Drawable("rectangle", o, Vector(opset(OpSetType.fillSketch)))), 0)
    assertEquals(p.attr("stroke-dasharray").toOption, Some("2 1"))
    assertEquals(p.attr("stroke-dashoffset").toOption, Some("3"))
  }

  test("draw 'fillSketch' fillLineDash empty -> dasharray='' set; fillLineDashOffset 0 -> no dashoffset") {
    val o: ResolvedOptions = ro.copy(fill = Some("green"), fillWeight = 2, fillLineDash = Some(Vector.empty), fillLineDashOffset = Some(0))
    val p: SvgElement      = child(new RoughSVG().draw(Drawable("rectangle", o, Vector(opset(OpSetType.fillSketch)))), 0)
    assertEquals(p.attr("stroke-dasharray").toOption, Some(""))
    assertEquals(p.attr("stroke-dashoffset").isDefined, false)
  }

  // ===================== draw(): all three OpSet types, none dropped =====================

  test("draw appends EVERY op-set's <path> in order (mutation g: a fillPath dropped)") {
    val o: ResolvedOptions = ro.copy(fill = Some("red"), stroke = "blue", fillWeight = 2, strokeWidth = 3)
    val d: Drawable        =
      Drawable("rectangle", o, Vector(opset(OpSetType.fillSketch), opset(OpSetType.fillPath), opset(OpSetType.path)))
    val g: SvgElement = new RoughSVG().draw(d)
    assertEquals(g.children.size, 3)
    assertEquals(g.children.toVector.map(_.tagName), Vector("path", "path", "path"))
    // fillSketch: stroke=red, stroke-width=2, fill=none
    assertEquals(child(g, 0).attr("stroke").toOption, Some("red"))
    assertEquals(child(g, 0).attr("stroke-width").toOption, Some("2"))
    assertEquals(child(g, 0).attr("fill").toOption, Some("none"))
    // fillPath: stroke=none, stroke-width=0, fill=red
    assertEquals(child(g, 1).attr("stroke").toOption, Some("none"))
    assertEquals(child(g, 1).attr("stroke-width").toOption, Some("0"))
    assertEquals(child(g, 1).attr("fill").toOption, Some("red"))
    // path: stroke=blue, stroke-width=3, fill=none
    assertEquals(child(g, 2).attr("stroke").toOption, Some("blue"))
    assertEquals(child(g, 2).attr("stroke-width").toOption, Some("3"))
    assertEquals(child(g, 2).attr("fill").toOption, Some("none"))
  }

  test("draw with empty sets -> self-closing <g /> (no children)") {
    val g: SvgElement = new RoughSVG().draw(Drawable("rectangle", ro, Vector.empty))
    assertEquals(g.children.isEmpty, true)
    assertEquals(g.toMarkup(), "<g />")
  }

  // ===================== draw(): fixedDecimalPlaceDigits precision threading =====================
  // An op-set with NON-integral coords: opsToPath full-precision -> "M1.234 2.5 L3 4.567";
  // with fixedDecimals=1 each datum is round-half-away-from-zero'd to 1 place then re-stringified
  // (1.234->1.2, 2.5->2.5, 3.0->3, 4.567->4.6) -> "M1.2 2.5 L3 4.6". The mutation under test drops
  // the precision arg from the opsToPath call, which would emit the full-precision form instead.

  private def precOpset(t: OpSetType): OpSet =
    OpSet(`type` = t, ops = Vector(Op(OpType.move, Vector(1.234, 2.5)), Op(OpType.lineTo, Vector(3.0, 4.567))))

  // Independent oracle: Chip 7's opsToPath with fixedDecimals=1 (the value `draw` threads).
  private val precRounded: String = "M1.2 2.5 L3 4.6"
  private val precFull:    String = "M1.234 2.5 L3 4.567"

  test("draw 'path' threads precision: fixedDecimalPlaceDigits=Some(1) rounds the emitted d (mutation: drop precision arg)") {
    val o: ResolvedOptions = ro.copy(stroke = "blue", fixedDecimalPlaceDigits = Some(1))
    val p: SvgElement      = child(new RoughSVG().draw(Drawable("line", o, Vector(precOpset(OpSetType.path)))), 0)
    assertEquals(p.attr("d").toOption, Some(precRounded))
    // sanity: the oracle equals Chip 7's opsToPath at the same precision, and differs from full precision
    assertEquals(new RoughGenerator().opsToPath(precOpset(OpSetType.path), Some(1)), precRounded)
    assertNotEquals(precRounded, precFull)
  }

  test("draw 'fillPath' threads precision: fixedDecimalPlaceDigits=Some(1) rounds the emitted d") {
    val o: ResolvedOptions = ro.copy(fill = Some("red"), fixedDecimalPlaceDigits = Some(1))
    val p: SvgElement      = child(new RoughSVG().draw(Drawable("rectangle", o, Vector(precOpset(OpSetType.fillPath)))), 0)
    assertEquals(p.attr("d").toOption, Some(precRounded))
  }

  test("draw 'fillSketch' threads precision via o.fixedDecimalPlaceDigits: Some(1) rounds the emitted d") {
    val o: ResolvedOptions = ro.copy(fill = Some("green"), fillWeight = 2, fixedDecimalPlaceDigits = Some(1))
    val p: SvgElement      = child(new RoughSVG().draw(Drawable("rectangle", o, Vector(precOpset(OpSetType.fillSketch)))), 0)
    assertEquals(p.attr("d").toOption, Some(precRounded))
  }

  test("draw with fixedDecimalPlaceDigits UNSET (None) emits full-precision d (no rounding)") {
    val o:  ResolvedOptions = ro.copy(stroke = "blue", fill = Some("green"), fillWeight = 2, fixedDecimalPlaceDigits = None)
    val gp: SvgElement      = child(new RoughSVG().draw(Drawable("line", o, Vector(precOpset(OpSetType.path)))), 0)
    assertEquals(gp.attr("d").toOption, Some(precFull))
    val gf: SvgElement = child(new RoughSVG().draw(Drawable("rectangle", o, Vector(precOpset(OpSetType.fillPath)))), 0)
    assertEquals(gf.attr("d").toOption, Some(precFull))
    val gs: SvgElement = child(new RoughSVG().draw(Drawable("rectangle", o, Vector(precOpset(OpSetType.fillSketch)))), 0)
    assertEquals(gs.attr("d").toOption, Some(precFull))
  }

  // ===================== opsToPath / getDefaultOptions / generator delegation =====================

  test("opsToPath delegates to the generator") {
    assertEquals(new RoughSVG().opsToPath(opset(OpSetType.path)), "M1 2 L3 4")
  }

  test("getDefaultOptions returns the backing generator's defaultOptions") {
    val svg: RoughSVG = new RoughSVG()
    assertEquals(svg.getDefaultOptions().stroke, "#000")
    assertEquals(svg.getDefaultOptions().strokeWidth, 1.0)
    assertEquals(svg.getDefaultOptions().fillStyle, "hachure")
  }

  test("generator getter exposes a RoughGenerator with the same defaultOptions") {
    val svg: RoughSVG = new RoughSVG()
    assertEquals(svg.generator.defaultOptions.stroke, svg.getDefaultOptions().stroke)
  }

  test("ctor forwards config to the generator (config.options seeds defaultOptions)") {
    val svg: RoughSVG = new RoughSVG(Config(Some(Options(stroke = Some("red"), strokeWidth = Some(4)))))
    assertEquals(svg.getDefaultOptions().stroke, "red")
    assertEquals(svg.getDefaultOptions().strokeWidth, 4.0)
  }

  // ===================== shape methods: routing equivalence (mutation f) =====================

  private val seeded: Option[Options] = Some(Options(seed = Some(7)))

  private def refDraw(d: Drawable): String =
    new RoughSVG().draw(d).toMarkup()

  test("line routes to gen.line") {
    assertEquals(
      new RoughSVG().line(10, 10, 110, 30, seeded).toMarkup(),
      refDraw(new RoughGenerator().line(10, 10, 110, 30, seeded))
    )
  }

  test("rectangle routes to gen.rectangle") {
    assertEquals(
      new RoughSVG().rectangle(0, 0, 100, 50, seeded).toMarkup(),
      refDraw(new RoughGenerator().rectangle(0, 0, 100, 50, seeded))
    )
  }

  test("ellipse routes to gen.ellipse") {
    assertEquals(
      new RoughSVG().ellipse(50, 50, 80, 60, seeded).toMarkup(),
      refDraw(new RoughGenerator().ellipse(50, 50, 80, 60, seeded))
    )
  }

  test("circle routes to gen.circle") {
    assertEquals(
      new RoughSVG().circle(50, 50, 40, seeded).toMarkup(),
      refDraw(new RoughGenerator().circle(50, 50, 40, seeded))
    )
  }

  test("linearPath routes to gen.linearPath") {
    val pts: Vector[Point] = Vector(Point(0, 0), Point(50, 0), Point(25, 40))
    assertEquals(
      new RoughSVG().linearPath(pts, seeded).toMarkup(),
      refDraw(new RoughGenerator().linearPath(pts, seeded))
    )
  }

  test("polygon routes to gen.polygon") {
    val pts: Vector[Point] = Vector(Point(0, 0), Point(40, 0), Point(40, 30), Point(0, 30))
    assertEquals(
      new RoughSVG().polygon(pts, seeded).toMarkup(),
      refDraw(new RoughGenerator().polygon(pts, seeded))
    )
  }

  test("arc routes to gen.arc") {
    assertEquals(
      new RoughSVG().arc(50, 50, 80, 60, 0, Math.PI, true, seeded).toMarkup(),
      refDraw(new RoughGenerator().arc(50, 50, 80, 60, 0, Math.PI, true, seeded))
    )
  }

  test("curve routes to gen.curve") {
    val pts: Vector[Point] = Vector(Point(0, 0), Point(20, 30), Point(40, 10), Point(60, 50), Point(80, 0))
    assertEquals(
      new RoughSVG().curve(pts, seeded).toMarkup(),
      refDraw(new RoughGenerator().curve(pts, seeded))
    )
  }

  test("path routes to gen.path") {
    val pathD: String = "M0 0 L40 0 L40 30 L0 30 Z"
    assertEquals(
      new RoughSVG().path(pathD, seeded).toMarkup(),
      refDraw(new RoughGenerator().path(pathD, seeded))
    )
  }

  test("distinct shapes produce distinct markup (so routing checks have teeth)") {
    val rectM: String = refDraw(new RoughGenerator().rectangle(0, 0, 100, 50, seeded))
    val ellM:  String = refDraw(new RoughGenerator().ellipse(0, 0, 100, 50, seeded))
    val lineM: String = refDraw(new RoughGenerator().line(0, 0, 100, 50, seeded))
    assertNotEquals(rectM, ellM)
    assertNotEquals(rectM, lineM)
    assertNotEquals(ellM, lineM)
  }

  test("shape method with fill produces the fillSketch+path <g> (end-to-end seeded)") {
    val g: SvgElement = new RoughSVG().rectangle(0, 0, 100, 50, Some(Options(seed = Some(7), fill = Some("red"))))
    assertEquals(g.tagName, "g")
    // hachure fill -> [fillSketch, path]; both serialize to <path>
    assertEquals(g.children.toVector.map(_.tagName), Vector("path", "path"))
    // fillSketch stroke = o.fill = red; path stroke = default #000
    assertEquals(child(g, 0).attr("stroke").toOption, Some("red"))
    assertEquals(child(g, 1).attr("stroke").toOption, Some("#000"))
  }

  // ===================== Rough entry object =====================

  test("Rough.svg(config) builds a RoughSVG with the forwarded config") {
    val cfg: Config = Config(Some(Options(stroke = Some("purple"))))
    assertEquals(Rough.svg(cfg).getDefaultOptions().stroke, "purple")
    assertEquals(
      Rough.svg(cfg).rectangle(0, 0, 10, 10, seeded).toMarkup(),
      new RoughSVG(cfg).rectangle(0, 0, 10, 10, seeded).toMarkup()
    )
  }

  test("Rough.generator(config) builds a RoughGenerator with the forwarded config") {
    val g: RoughGenerator = Rough.generator(Config(Some(Options(fillStyle = Some("solid")))))
    assertEquals(g.defaultOptions.fillStyle, "solid")
  }

  test("Rough.newSeed returns a non-negative Int seed") {
    val s: Int = Rough.newSeed()
    assert(s >= 0, s"newSeed >= 0, got $s")
  }

  test("Rough.canvas throws RoughCanvasUnsupported (canvas.ts platform-inapplicable)") {
    val ex: RoughCanvasUnsupported = intercept[RoughCanvasUnsupported](Rough.canvas())
    assertEquals(ex.getMessage, "rough.canvas is not supported on SSG (no DOM canvas; use rough.svg)")
  }
}
