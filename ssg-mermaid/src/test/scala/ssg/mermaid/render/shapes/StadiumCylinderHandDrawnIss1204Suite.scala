/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1204 sub-chip 9f: stadium + cylinder handDrawn rendering.
 *
 * Unlike the polygon shapes (9b–9e), these route through `rc.path(<d-string>)`:
 *   - StadiumShape.render with look="handDrawn" emits a rough sketch of a fully-rounded
 *     rectangle path (radius = height/2 → pill ends) via rough.svg().path, NOT the classic
 *     <rect>; the emitted paths match the ported Rough oracle built from
 *     createRoundedRectPathD(rectX, rectY, w, h, height/2). A negative radius guard pins the
 *     radius at h/2 (a radius = h → different sketch).
 *   - CylinderShape.render with look="handDrawn" emits TWO rough paths — the body (filled,
 *     normal opts) and the top cap (fill: "none", an unfilled line) — reusing SSG's OWN
 *     bodyPath/capPath outlines; both match the ported Rough oracle, and the cap's opts carry
 *     fill "none" (distinct from the filled body).
 *   - look="classic" (and the default) still emit the classic <rect> / <path>s (regression
 *     guard).
 *   - The node style + theme thread through userNodeOverrides into the rough stroke/fill; the
 *     seed threads through so distinct seeds produce distinct sketches.
 *
 * The rough-sketch expectations use the ported Rough as the oracle (computed directly in the
 * test): each shape must route the SAME coords/opts/seed into Rough, so any threading or branch
 * mutation makes the emitted paths diverge from the independently-built oracle.
 */
package ssg
package mermaid
package render
package shapes

import munit.FunSuite

import ssg.graphs.commons.rough.{ Options, Rough }
import ssg.graphs.commons.svg.{ PathData, SvgBuilder, SvgElement }
import ssg.mermaid.theme.ThemeVariables

final class StadiumCylinderHandDrawnIss1204Suite extends FunSuite {

  // ──────────────────────────────────────────────────────────────────────────
  // helpers
  // ──────────────────────────────────────────────────────────────────────────

  private def theme(nodeBorder: String, mainBkg: String): ThemeVariables = {
    val tv = new ThemeVariables
    tv.nodeBorder = nodeBorder
    tv.mainBkg = mainBkg
    tv
  }

  /** (d, stroke, fill, stroke-width) for every <path> in a subtree, in document order. */
  private def pathDescriptors(el: SvgElement): Vector[(String, String, String, String)] =
    el.findAllByTag("path").toVector.map { p =>
      (
        p.attr("d").getOrElse(""),
        p.attr("stroke").getOrElse(""),
        p.attr("fill").getOrElse(""),
        p.attr("stroke-width").getOrElse("")
      )
    }

  private def renderStadium(config: ShapeConfig): SvgElement = {
    val parent = SvgBuilder.create("g")
    StadiumShape.render(parent, config).shapeGroup.build()
  }

  private def renderCylinder(config: ShapeConfig): SvgElement = {
    val parent = SvgBuilder.create("g")
    CylinderShape.render(parent, config).shapeGroup.build()
  }

  /** The fraction CylinderShape uses for the elliptical cap height (mirrors the private constant). */
  private val CapFraction: Double = 0.15

  /** Rebuild the exact cylinder body outline CylinderShape traces (for the oracle). */
  private def cylinderBodyPath(x: Double, y: Double, width: Double, height: Double): PathData = {
    val halfW  = width / 2.0
    val halfH  = height / 2.0
    val capH   = height * CapFraction
    val left   = x - halfW
    val right  = x + halfW
    val top    = y - halfH
    val bottom = y + halfH
    val p      = PathData()
    p.moveTo(left, top + capH)
    p.arcTo(halfW, capH, 0, largeArc = false, sweep = true, right, top + capH)
    p.lineTo(right, bottom - capH)
    p.arcTo(halfW, capH, 0, largeArc = false, sweep = true, left, bottom - capH)
    p.close()
    p
  }

  /** Rebuild the exact cylinder cap outline CylinderShape traces (for the oracle). */
  private def cylinderCapPath(x: Double, y: Double, width: Double, height: Double): PathData = {
    val halfW = width / 2.0
    val halfH = height / 2.0
    val capH  = height * CapFraction
    val left  = x - halfW
    val right = x + halfW
    val top   = y - halfH
    val p     = PathData()
    p.moveTo(left, top + capH)
    p.arcTo(halfW, capH, 0, largeArc = false, sweep = false, right, top + capH)
    p.arcTo(halfW, capH, 0, largeArc = false, sweep = true, left, top + capH)
    p
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Stadium handDrawn → rough.svg().path(createRoundedRectPathD(..., h/2))
  // ──────────────────────────────────────────────────────────────────────────

  test("stadium handDrawn: emits a rough <g> of sketch <path>s, NOT a plain <rect>") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv)
    val built  = renderStadium(config)

    assert(built.findAllByTag("rect").isEmpty, "hand-drawn stadium must not emit a plain <rect>")
    val paths = built.findAllByTag("path").toVector
    assert(paths.nonEmpty, "hand-drawn stadium must emit rough <path> elements")
    assert(paths.exists(_.attr("d").getOrElse("").contains("C")), "rough sketch path must contain bezier (C) segments")
    assert(built.findAllByClass("node-shape").nonEmpty, "rough shape group must carry the node-shape class")
  }

  test("stadium handDrawn: emitted paths match the Rough path oracle (radius = height/2)") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed   = 42
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val built  = renderStadium(config)

    val rectX  = 50 - 100 / 2.0
    val rectY  = 40 - 60 / 2.0
    val halfH  = 60 / 2.0
    val opts   = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(), tv, seed)
    val oracle = Rough.svg().path(RoundedRectPath.createRoundedRectPathD(rectX, rectY, 100, 60, halfH), Some(opts))

    val actual   = pathDescriptors(built.findAllByClass("node-shape").head)
    val expected = pathDescriptors(oracle)
    assertEquals(actual, expected)
    assert(expected.nonEmpty, "sanity: the oracle must produce at least one path")
  }

  test("stadium handDrawn: radius is height/2, NOT height (radius mutation guard)") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed   = 42
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val built  = renderStadium(config)

    val rectX = 50 - 100 / 2.0
    val rectY = 40 - 60 / 2.0
    val opts  = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(), tv, seed)

    val halfRadiusOracle = Rough.svg().path(RoundedRectPath.createRoundedRectPathD(rectX, rectY, 100, 60, 30), Some(opts))
    val fullRadiusOracle = Rough.svg().path(RoundedRectPath.createRoundedRectPathD(rectX, rectY, 100, 60, 60), Some(opts))
    val actual           = pathDescriptors(built.findAllByClass("node-shape").head)

    assertEquals(actual, pathDescriptors(halfRadiusOracle))
    assertNotEquals(pathDescriptors(halfRadiusOracle), pathDescriptors(fullRadiusOracle), "sanity: radius h/2 vs h must differ")
  }

  test("stadium classic look: still emits the plain <rect> and no rough <path>") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "classic")
    val built  = renderStadium(config)
    val rects  = built.findAllByTag("rect").toVector
    assertEquals(rects.size, 1, "classic look must emit exactly one <rect>")
    assert(built.findAllByTag("path").isEmpty, "classic look must not emit any rough <path>")
    assertEquals(rects.head.attr("rx").getOrElse(""), "30")
    assertEquals(rects.head.attr("ry").getOrElse(""), "30")
    assert(rects.head.hasClass("node-shape"))
  }

  test("stadium default look (classic): plain <rect>, no rough sketch") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60)
    val built  = renderStadium(config)
    assertEquals(built.findAllByTag("rect").size, 1)
    assert(built.findAllByTag("path").isEmpty)
  }

  test("stadium handDrawn: node style + theme thread into the rough stroke/fill; seed threads") {
    val tv      = theme(nodeBorder = "#abcdef", mainBkg = "#123456")
    val config  = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv)
    val built   = renderStadium(config)
    val strokes = pathDescriptors(built).map(_._2).toSet
    assert(strokes.contains("#abcdef"), s"expected theme nodeBorder #abcdef as a stroke, got $strokes")
    assert(strokes.contains("#123456"), s"expected theme mainBkg #123456 as the fill-sketch stroke, got $strokes")

    val base = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", themeVariables = tv)
    val d1   = pathDescriptors(renderStadium(base.copy(handDrawnSeed = 1)))
    val d2   = pathDescriptors(renderStadium(base.copy(handDrawnSeed = 2)))
    assertNotEquals(d1, d2, "different seeds must yield different rough sketches (seed must thread to opts)")
  }

  test("stadium handDrawn: config.style is applied to the rough shape group") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv, style = "opacity: 0.5")
    val built  = renderStadium(config)
    val shape  = built.findAllByClass("node-shape").head
    assertEquals(shape.tagName, "g")
    assertEquals(shape.attr("style").getOrElse(""), "opacity: 0.5")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Cylinder handDrawn → TWO rough.svg().path (body filled + cap fill:none)
  // ──────────────────────────────────────────────────────────────────────────

  private def cylinderBodyGroup(built: SvgElement): SvgElement =
    built.findAllByClass("node-shape").toVector.filterNot(_.hasClass("cylinder-cap")).head

  private def cylinderCapGroup(built: SvgElement): SvgElement =
    built.findAllByClass("cylinder-cap").toVector.head

  test("cylinder handDrawn: emits TWO rough groups (body + cap), NOT plain <path>s") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv)
    val built  = renderCylinder(config)

    // Two grafted rough groups (both node-shape), exactly one of which is the cap.
    assertEquals(built.findAllByClass("node-shape").size, 2, "hand-drawn cylinder must graft body + cap groups")
    assertEquals(built.findAllByClass("cylinder-cap").size, 1, "exactly one grafted group must be the cap")

    // Rough sketch: both groups carry bezier (C) sketch paths.
    val bodyPaths = cylinderBodyGroup(built).findAllByTag("path").toVector
    val capPaths  = cylinderCapGroup(built).findAllByTag("path").toVector
    assert(bodyPaths.nonEmpty && bodyPaths.exists(_.attr("d").getOrElse("").contains("C")), "body must be a rough sketch")
    assert(capPaths.nonEmpty && capPaths.exists(_.attr("d").getOrElse("").contains("C")), "cap must be a rough sketch")

    // Paint order: the body group must be grafted BEFORE the cap (cylinder-cap) group among the
    // cylinder group's direct children, so the unfilled cap arc paints over the filled body (matches
    // upstream's final DOM order [outer, inner] and SSG's classic body-then-cap append). A reversed
    // graft (cap first) must fail here.
    val kids    = built.children.toVector
    val bodyIdx = kids.indexWhere(c => c.hasClass("node-shape") && !c.hasClass("cylinder-cap"))
    val capIdx  = kids.indexWhere(_.hasClass("cylinder-cap"))
    assert(bodyIdx >= 0 && capIdx >= 0, s"both grafted groups must be direct children (body=$bodyIdx cap=$capIdx)")
    assert(bodyIdx < capIdx, s"body must be grafted before cap (paint order): body=$bodyIdx cap=$capIdx")
  }

  test("cylinder handDrawn: body matches the filled Rough oracle; cap matches the fill:none oracle") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed   = 42
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val built  = renderCylinder(config)

    val bodyD = cylinderBodyPath(50, 40, 100, 60).toString
    val capD  = cylinderCapPath(50, 40, 100, 60).toString

    val bodyOpts = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(), tv, seed)
    val capOpts  = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(fill = Some("none")), tv, seed)

    val bodyOracle = Rough.svg().path(bodyD, Some(bodyOpts))
    val capOracle  = Rough.svg().path(capD, Some(capOpts))

    assertEquals(pathDescriptors(cylinderBodyGroup(built)), pathDescriptors(bodyOracle))
    assertEquals(pathDescriptors(cylinderCapGroup(built)), pathDescriptors(capOracle))
    assert(pathDescriptors(bodyOracle).nonEmpty, "sanity: body oracle must produce paths")
    assert(pathDescriptors(capOracle).nonEmpty, "sanity: cap oracle must produce paths")
  }

  test("cylinder handDrawn: cap is fill:none, distinct from a filled cap (fill mutation guard)") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed   = 42
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val built  = renderCylinder(config)

    val capD          = cylinderCapPath(50, 40, 100, 60).toString
    val capNoneOpts   = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(fill = Some("none")), tv, seed)
    val capFilledOpts = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(), tv, seed)

    val capNoneOracle   = Rough.svg().path(capD, Some(capNoneOpts))
    val capFilledOracle = Rough.svg().path(capD, Some(capFilledOpts))

    val actualCap = pathDescriptors(cylinderCapGroup(built))
    assertEquals(actualCap, pathDescriptors(capNoneOracle), "cap must use fill:none opts")
    assertNotEquals(pathDescriptors(capNoneOracle), pathDescriptors(capFilledOracle), "sanity: fill none vs filled must differ")
    // The resolved cap fill is literally "none" (userNodeOverrides keeps the passed fill).
    assertEquals(capNoneOpts.fill, Some("none"))
  }

  test("cylinder classic look: still emits two plain <path>s (body + cap), no rough sketch") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "classic")
    val built  = renderCylinder(config)
    val paths  = built.findAllByTag("path").toVector
    assertEquals(paths.size, 2, "classic cylinder must emit exactly two <path>s (body + cap)")
    assert(paths.forall(!_.attr("d").getOrElse("").contains("C")), "classic paths must not be rough beziers")
    assertEquals(built.findAllByClass("cylinder-cap").size, 1)
    assert(paths.forall(_.hasClass("node-shape")))
  }

  test("cylinder default look (classic): two plain <path>s, no rough sketch groups") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60)
    val built  = renderCylinder(config)
    assertEquals(built.findAllByTag("path").size, 2)
    // No grafted rough <g> groups: node-shape lives on the two <path>s directly, none on a <g>.
    assert(built.findAllByClass("node-shape").forall(_.tagName == "path"))
  }

  test("cylinder handDrawn: node style + theme thread; seed threads to distinct sketches") {
    val tv      = theme(nodeBorder = "#abcdef", mainBkg = "#123456")
    val config  = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv)
    val built   = renderCylinder(config)
    val strokes = pathDescriptors(built).map(_._2).toSet
    // The body is filled (mainBkg reaches its fill-sketch stroke); nodeBorder is the outline stroke.
    assert(strokes.contains("#abcdef"), s"expected theme nodeBorder #abcdef as a stroke, got $strokes")
    assert(strokes.contains("#123456"), s"expected theme mainBkg #123456 as the body fill-sketch stroke, got $strokes")

    val base = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", themeVariables = tv)
    val d1   = pathDescriptors(renderCylinder(base.copy(handDrawnSeed = 1)))
    val d2   = pathDescriptors(renderCylinder(base.copy(handDrawnSeed = 2)))
    assertNotEquals(d1, d2, "different seeds must yield different rough sketches (seed must thread to opts)")
  }

  test("cylinder handDrawn: config.style is applied to both grafted groups") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv, style = "opacity: 0.5")
    val built  = renderCylinder(config)
    assertEquals(cylinderBodyGroup(built).attr("style").getOrElse(""), "opacity: 0.5")
    assertEquals(cylinderCapGroup(built).attr("style").getOrElse(""), "opacity: 0.5")
    assertEquals(cylinderBodyGroup(built).tagName, "g")
    assertEquals(cylinderCapGroup(built).tagName, "g")
  }
}
