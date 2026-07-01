/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1204 sub-chip 9d: diamond / question / rhombus handDrawn rendering.
 *
 * Proves the DiamondShape hand-drawn branch (reusing the 9b/9c rough-options template).
 * DiamondShape is registered for "diamond"/"question"/"rhombus" — all three route through
 * DiamondShape, so this one branch covers all of them.
 *
 *   - DiamondShape.render look="handDrawn" emits a rough sketch via rough.svg().polygon of SSG's
 *     OWN four rhombus vertices (top/right/bottom/left), a <g> of bezier <path>s, NOT the classic
 *     sharp moveTo/lineTo <path>; the emitted paths match the ported Rough oracle
 *     rough.svg().polygon(Vector(top, right, bottom, left), opts) — pinning the exact vertices,
 *     their order, and the seed/opts threading.
 *   - look="classic"/default still emits the sharp rhombus <path> (regression guard).
 *
 * The rough-sketch expectations use the ported Rough as the oracle (computed directly here),
 * exactly as the brief permits: the shape must route the SAME coords/opts/seed into Rough, so
 * any threading, vertex, or point-order mutation makes the emitted paths diverge from the oracle.
 */
package ssg
package mermaid
package render
package shapes

import munit.FunSuite

import ssg.graphs.commons.rough.{ Options, Rough, Point as RoughPoint }
import ssg.graphs.commons.svg.{ SvgBuilder, SvgElement }
import ssg.mermaid.theme.ThemeVariables

final class DiamondShapeHandDrawnIss1204Suite extends FunSuite {

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

  private def renderDiamond(config: ShapeConfig): SvgElement =
    DiamondShape.render(SvgBuilder.create("g"), config).shapeGroup.build()

  /** The four SSG rhombus vertices, in top/right/bottom/left order, for a config. */
  private def vertices(config: ShapeConfig): Vector[RoughPoint] = {
    val halfW = config.width / 2.0
    val halfH = config.height / 2.0
    val cx    = config.x
    val cy    = config.y
    Vector(
      RoughPoint(cx, cy - halfH),           // top
      RoughPoint(cx + halfW, cy),           // right
      RoughPoint(cx, cy + halfH),           // bottom
      RoughPoint(cx - halfW, cy)            // left
    )
  }

  // ──────────────────────────────────────────────────────────────────────────
  // DiamondShape hand-drawn
  // ──────────────────────────────────────────────────────────────────────────

  test("diamond handDrawn: emits a rough <g> of sketch <path>s, NOT the sharp classic <path>") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv)
    val built  = renderDiamond(config)

    val paths = built.findAllByTag("path").toVector
    assert(paths.nonEmpty, "hand-drawn diamond must emit rough <path> elements")
    assert(paths.exists(_.attr("d").getOrElse("").contains("C")), "rough sketch path must contain bezier (C) segments")
    // The classic sharp rhombus path has no bezier curves; the hand-drawn one does.
    assert(!paths.exists(_.attr("d").getOrElse("") == classicD(config)), "hand-drawn must NOT emit the classic sharp <path>")
    assert(built.findAllByClass("node-shape").nonEmpty, "rough shape group must carry the node-shape class")
  }

  test("diamond handDrawn: emitted paths match rough.polygon(top, right, bottom, left, opts) oracle") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed   = 42
    // Non-square box so a halfW<->halfH confusion is observable.
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val built  = renderDiamond(config)

    val opts     = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(), tv, seed)
    val oracle   = Rough.svg().polygon(vertices(config), Some(opts))
    val expected = pathDescriptors(oracle)
    val actual   = pathDescriptors(built.findAllByClass("node-shape").head)

    assertEquals(actual, expected)
    assert(expected.nonEmpty, "sanity: the oracle must produce at least one path")

    // vertex-coordinate mutant guard: top with +halfH (instead of -halfH) yields a different sketch.
    val badTop = Vector(
      RoughPoint(config.x, config.y + config.height / 2.0), // WRONG: +halfH
      RoughPoint(config.x + config.width / 2.0, config.y),
      RoughPoint(config.x, config.y + config.height / 2.0),
      RoughPoint(config.x - config.width / 2.0, config.y)
    )
    assertNotEquals(pathDescriptors(oracle), pathDescriptors(Rough.svg().polygon(badTop, Some(opts))))

    // point-order mutant guard: reversing the vertex order yields a different sketch.
    assertNotEquals(pathDescriptors(oracle), pathDescriptors(Rough.svg().polygon(vertices(config).reverse, Some(opts))))
  }

  test("diamond handDrawn: the exact SSG vertices (top/right/bottom/left) are used") {
    // Distinct box so every vertex has a unique coordinate; assert the oracle vertices are the
    // SSG geometry (not upstream's s = w + h decision box).
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed   = 7
    val config = ShapeConfig(x = 20, y = 30, width = 80, height = 40, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val vs     = vertices(config)
    assertEquals(vs, Vector(RoughPoint(20, 10), RoughPoint(60, 30), RoughPoint(20, 50), RoughPoint(-20, 30)))

    val opts   = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(), tv, seed)
    val oracle = Rough.svg().polygon(vs, Some(opts))
    val built  = renderDiamond(config)
    assertEquals(pathDescriptors(built.findAllByClass("node-shape").head), pathDescriptors(oracle))
  }

  test("diamond classic look: still emits the sharp rhombus <path>, no rough bezier") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "classic")
    val built  = renderDiamond(config)
    val paths  = built.findAllByTag("path").toVector
    assertEquals(paths.size, 1, "classic look must emit exactly one <path>")
    assertEquals(paths.head.attr("d").getOrElse(""), classicD(config))
    assert(!paths.head.attr("d").getOrElse("").contains("C"), "classic path must have no bezier (C) segments")
    assert(paths.head.hasClass("node-shape"))
  }

  test("diamond default look (classic): sharp rhombus <path>, no rough sketch") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60)
    val built  = renderDiamond(config)
    val paths  = built.findAllByTag("path").toVector
    assertEquals(paths.size, 1)
    assertEquals(paths.head.attr("d").getOrElse(""), classicD(config))
    assert(!paths.head.attr("d").getOrElse("").contains("C"))
  }

  test("diamond handDrawn: seed threads to opts — distinct seeds produce distinct sketches") {
    val tv   = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val base = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", themeVariables = tv)
    val d1   = pathDescriptors(renderDiamond(base.copy(handDrawnSeed = 1)))
    val d2   = pathDescriptors(renderDiamond(base.copy(handDrawnSeed = 2)))
    assertNotEquals(d1, d2, "different seeds must yield different rough sketches (seed must thread to opts)")
  }

  test("diamond handDrawn: node style threads through userNodeOverrides into stroke/fill") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val config = ShapeConfig(
      x = 50,
      y = 40,
      width = 100,
      height = 60,
      look = "handDrawn",
      handDrawnSeed = 42,
      themeVariables = tv,
      cssStyles = Vector("stroke: #ff0000", "fill: #00ff00")
    )
    val strokes = pathDescriptors(renderDiamond(config)).map(_._2).toSet
    assert(strokes.contains("#ff0000"), s"expected a path with stroke #ff0000, got $strokes")
    assert(strokes.contains("#00ff00"), s"expected the fill-sketch path stroke #00ff00, got $strokes")
  }

  test("diamond handDrawn: config.style is applied to the rough shape group") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv, style = "opacity: 0.5")
    val shape  = renderDiamond(config).findAllByClass("node-shape").head
    assertEquals(shape.tagName, "g")
    assertEquals(shape.attr("style").getOrElse(""), "opacity: 0.5")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // oracle for the classic sharp rhombus `d`
  // ──────────────────────────────────────────────────────────────────────────

  private def classicD(config: ShapeConfig): String = {
    import ssg.graphs.commons.svg.PathData
    val halfW = config.width / 2.0
    val halfH = config.height / 2.0
    val cx    = config.x
    val cy    = config.y
    val path  = PathData()
    path.moveTo(cx, cy - halfH)
    path.lineTo(cx + halfW, cy)
    path.lineTo(cx, cy + halfH)
    path.lineTo(cx - halfW, cy)
    path.close()
    path.toString
  }
}
