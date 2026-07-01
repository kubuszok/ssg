/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1204 sub-chip 9e: hexagon / trapezoid handDrawn rendering.
 *
 * Proves the HexagonShape / TrapezoidShape hand-drawn branches (reusing the 9d rough-options
 * template).
 *
 *   - <Shape>.render look="handDrawn" emits a rough sketch via rough.svg().polygon of SSG's OWN
 *     classic vertices (hexagon 6, trapezoid 4), a <g> of bezier <path>s, NOT the classic sharp
 *     moveTo/lineTo <path>; the emitted paths match the ported Rough oracle
 *     rough.svg().polygon(Vector(vertices), opts) — pinning the exact vertices, their order, and
 *     the seed/opts threading.
 *   - look="classic"/default still emits the sharp <path> (regression guard).
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

import ssg.graphs.commons.rough.{ Options, Point as RoughPoint, Rough }
import ssg.graphs.commons.svg.{ PathData, SvgBuilder, SvgElement }
import ssg.mermaid.theme.ThemeVariables

final class HexagonTrapezoidHandDrawnIss1204Suite extends FunSuite {

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

  private def renderHexagon(config: ShapeConfig): SvgElement =
    HexagonShape.render(SvgBuilder.create("g"), config).shapeGroup.build()

  private def renderTrapezoid(config: ShapeConfig): SvgElement =
    TrapezoidShape.render(SvgBuilder.create("g"), config).shapeGroup.build()

  /** The six SSG hexagon vertices, in classic order, for a config. */
  private def hexagonVertices(config: ShapeConfig): Vector[RoughPoint] = {
    val halfW = config.width / 2.0
    val halfH = config.height / 2.0
    val cx    = config.x
    val cy    = config.y
    val inset = config.height * 0.25
    Vector(
      RoughPoint(cx - halfW + inset, cy - halfH), // top-left
      RoughPoint(cx + halfW - inset, cy - halfH), // top-right
      RoughPoint(cx + halfW, cy), // right point
      RoughPoint(cx + halfW - inset, cy + halfH), // bottom-right
      RoughPoint(cx - halfW + inset, cy + halfH), // bottom-left
      RoughPoint(cx - halfW, cy) // left point
    )
  }

  /** The four SSG trapezoid vertices, in classic order, for a config. */
  private def trapezoidVertices(config: ShapeConfig): Vector[RoughPoint] = {
    val halfW = config.width / 2.0
    val halfH = config.height / 2.0
    val cx    = config.x
    val cy    = config.y
    val inset = config.height * 0.25
    Vector(
      RoughPoint(cx - halfW + inset, cy - halfH), // top-left (inset)
      RoughPoint(cx + halfW - inset, cy - halfH), // top-right (inset)
      RoughPoint(cx + halfW, cy + halfH), // bottom-right
      RoughPoint(cx - halfW, cy + halfH) // bottom-left
    )
  }

  // ──────────────────────────────────────────────────────────────────────────
  // HexagonShape hand-drawn
  // ──────────────────────────────────────────────────────────────────────────

  test("hexagon handDrawn: emits a rough <g> of sketch <path>s, NOT the sharp classic <path>") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv)
    val built  = renderHexagon(config)

    val paths = built.findAllByTag("path").toVector
    assert(paths.nonEmpty, "hand-drawn hexagon must emit rough <path> elements")
    assert(paths.exists(_.attr("d").getOrElse("").contains("C")), "rough sketch path must contain bezier (C) segments")
    assert(!paths.exists(_.attr("d").getOrElse("") == classicHexD(config)), "hand-drawn must NOT emit the classic sharp <path>")
    assert(built.findAllByClass("node-shape").nonEmpty, "rough shape group must carry the node-shape class")
  }

  test("hexagon handDrawn: emitted paths match rough.polygon(6 vertices, opts) oracle") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed   = 42
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val built  = renderHexagon(config)

    val opts     = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(), tv, seed)
    val oracle   = Rough.svg().polygon(hexagonVertices(config), Some(opts))
    val expected = pathDescriptors(oracle)
    val actual   = pathDescriptors(built.findAllByClass("node-shape").head)

    assertEquals(actual, expected)
    assert(expected.nonEmpty, "sanity: the oracle must produce at least one path")

    // vertex-coordinate mutant guard: dropping the inset on the top-left vertex diverges.
    val badInset = {
      val halfW = config.width / 2.0
      val halfH = config.height / 2.0
      val cx    = config.x
      val cy    = config.y
      val inset = config.height * 0.25
      Vector(
        RoughPoint(cx - halfW, cy - halfH), // WRONG: inset dropped on top-left
        RoughPoint(cx + halfW - inset, cy - halfH),
        RoughPoint(cx + halfW, cy),
        RoughPoint(cx + halfW - inset, cy + halfH),
        RoughPoint(cx - halfW + inset, cy + halfH),
        RoughPoint(cx - halfW, cy)
      )
    }
    assertNotEquals(pathDescriptors(oracle), pathDescriptors(Rough.svg().polygon(badInset, Some(opts))))

    // point-order mutant guard: reversing the vertex order yields a different sketch.
    assertNotEquals(pathDescriptors(oracle), pathDescriptors(Rough.svg().polygon(hexagonVertices(config).reverse, Some(opts))))
  }

  test("hexagon handDrawn: the exact six SSG vertices (classic order) are used") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed   = 7
    val config = ShapeConfig(x = 20, y = 30, width = 80, height = 40, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val vs     = hexagonVertices(config)
    // halfW=40, halfH=20, inset=10 → left edge at -20+10=-10, right at 60-10=50; points at ±60/-20.
    assertEquals(
      vs,
      Vector(
        RoughPoint(-10, 10), // top-left
        RoughPoint(50, 10), // top-right
        RoughPoint(60, 30), // right point
        RoughPoint(50, 50), // bottom-right
        RoughPoint(-10, 50), // bottom-left
        RoughPoint(-20, 30) // left point
      )
    )
    assertEquals(vs.size, 6)

    val opts   = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(), tv, seed)
    val oracle = Rough.svg().polygon(vs, Some(opts))
    val built  = renderHexagon(config)
    assertEquals(pathDescriptors(built.findAllByClass("node-shape").head), pathDescriptors(oracle))
  }

  test("hexagon classic look: still emits the sharp <path>, no rough bezier") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "classic")
    val built  = renderHexagon(config)
    val paths  = built.findAllByTag("path").toVector
    assertEquals(paths.size, 1, "classic look must emit exactly one <path>")
    assertEquals(paths.head.attr("d").getOrElse(""), classicHexD(config))
    assert(!paths.head.attr("d").getOrElse("").contains("C"), "classic path must have no bezier (C) segments")
    assert(paths.head.hasClass("node-shape"))
  }

  test("hexagon default look (classic): sharp <path>, no rough sketch") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60)
    val built  = renderHexagon(config)
    val paths  = built.findAllByTag("path").toVector
    assertEquals(paths.size, 1)
    assertEquals(paths.head.attr("d").getOrElse(""), classicHexD(config))
    assert(!paths.head.attr("d").getOrElse("").contains("C"))
  }

  test("hexagon handDrawn: seed threads to opts — distinct seeds produce distinct sketches") {
    val tv   = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val base = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", themeVariables = tv)
    val d1   = pathDescriptors(renderHexagon(base.copy(handDrawnSeed = 1)))
    val d2   = pathDescriptors(renderHexagon(base.copy(handDrawnSeed = 2)))
    assertNotEquals(d1, d2, "different seeds must yield different rough sketches (seed must thread to opts)")
  }

  test("hexagon handDrawn: node style threads through userNodeOverrides into stroke/fill") {
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
    val strokes = pathDescriptors(renderHexagon(config)).map(_._2).toSet
    assert(strokes.contains("#ff0000"), s"expected a path with stroke #ff0000, got $strokes")
    assert(strokes.contains("#00ff00"), s"expected the fill-sketch path stroke #00ff00, got $strokes")
  }

  test("hexagon handDrawn: config.style is applied to the rough shape group") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv, style = "opacity: 0.5")
    val shape  = renderHexagon(config).findAllByClass("node-shape").head
    assertEquals(shape.tagName, "g")
    assertEquals(shape.attr("style").getOrElse(""), "opacity: 0.5")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // TrapezoidShape hand-drawn
  // ──────────────────────────────────────────────────────────────────────────

  test("trapezoid handDrawn: emits a rough <g> of sketch <path>s, NOT the sharp classic <path>") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv)
    val built  = renderTrapezoid(config)

    val paths = built.findAllByTag("path").toVector
    assert(paths.nonEmpty, "hand-drawn trapezoid must emit rough <path> elements")
    assert(paths.exists(_.attr("d").getOrElse("").contains("C")), "rough sketch path must contain bezier (C) segments")
    assert(!paths.exists(_.attr("d").getOrElse("") == classicTrapD(config)), "hand-drawn must NOT emit the classic sharp <path>")
    assert(built.findAllByClass("node-shape").nonEmpty, "rough shape group must carry the node-shape class")
  }

  test("trapezoid handDrawn: emitted paths match rough.polygon(4 vertices, opts) oracle") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed   = 42
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val built  = renderTrapezoid(config)

    val opts     = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(), tv, seed)
    val oracle   = Rough.svg().polygon(trapezoidVertices(config), Some(opts))
    val expected = pathDescriptors(oracle)
    val actual   = pathDescriptors(built.findAllByClass("node-shape").head)

    assertEquals(actual, expected)
    assert(expected.nonEmpty, "sanity: the oracle must produce at least one path")

    // vertex-coordinate mutant guard: dropping the top-left inset diverges.
    val badInset = {
      val halfW = config.width / 2.0
      val halfH = config.height / 2.0
      val cx    = config.x
      val cy    = config.y
      val inset = config.height * 0.25
      Vector(
        RoughPoint(cx - halfW, cy - halfH), // WRONG: inset dropped on top-left
        RoughPoint(cx + halfW - inset, cy - halfH),
        RoughPoint(cx + halfW, cy + halfH),
        RoughPoint(cx - halfW, cy + halfH)
      )
    }
    assertNotEquals(pathDescriptors(oracle), pathDescriptors(Rough.svg().polygon(badInset, Some(opts))))

    // point-order mutant guard: reversing the vertex order yields a different sketch.
    assertNotEquals(pathDescriptors(oracle), pathDescriptors(Rough.svg().polygon(trapezoidVertices(config).reverse, Some(opts))))
  }

  test("trapezoid handDrawn: the exact four SSG vertices (classic order) are used") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed   = 7
    val config = ShapeConfig(x = 20, y = 30, width = 80, height = 40, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val vs     = trapezoidVertices(config)
    // halfW=40, halfH=20, inset=10 → top at -20+10=-10 .. 60-10=50; bottom full-width ±40 at y=50.
    assertEquals(
      vs,
      Vector(
        RoughPoint(-10, 10), // top-left (inset)
        RoughPoint(50, 10), // top-right (inset)
        RoughPoint(60, 50), // bottom-right
        RoughPoint(-20, 50) // bottom-left
      )
    )
    assertEquals(vs.size, 4)

    val opts   = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(), tv, seed)
    val oracle = Rough.svg().polygon(vs, Some(opts))
    val built  = renderTrapezoid(config)
    assertEquals(pathDescriptors(built.findAllByClass("node-shape").head), pathDescriptors(oracle))
  }

  test("trapezoid classic look: still emits the sharp <path>, no rough bezier") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "classic")
    val built  = renderTrapezoid(config)
    val paths  = built.findAllByTag("path").toVector
    assertEquals(paths.size, 1, "classic look must emit exactly one <path>")
    assertEquals(paths.head.attr("d").getOrElse(""), classicTrapD(config))
    assert(!paths.head.attr("d").getOrElse("").contains("C"), "classic path must have no bezier (C) segments")
    assert(paths.head.hasClass("node-shape"))
  }

  test("trapezoid default look (classic): sharp <path>, no rough sketch") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60)
    val built  = renderTrapezoid(config)
    val paths  = built.findAllByTag("path").toVector
    assertEquals(paths.size, 1)
    assertEquals(paths.head.attr("d").getOrElse(""), classicTrapD(config))
    assert(!paths.head.attr("d").getOrElse("").contains("C"))
  }

  test("trapezoid handDrawn: seed threads to opts — distinct seeds produce distinct sketches") {
    val tv   = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val base = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", themeVariables = tv)
    val d1   = pathDescriptors(renderTrapezoid(base.copy(handDrawnSeed = 1)))
    val d2   = pathDescriptors(renderTrapezoid(base.copy(handDrawnSeed = 2)))
    assertNotEquals(d1, d2, "different seeds must yield different rough sketches (seed must thread to opts)")
  }

  test("trapezoid handDrawn: node style threads through userNodeOverrides into stroke/fill") {
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
    val strokes = pathDescriptors(renderTrapezoid(config)).map(_._2).toSet
    assert(strokes.contains("#ff0000"), s"expected a path with stroke #ff0000, got $strokes")
    assert(strokes.contains("#00ff00"), s"expected the fill-sketch path stroke #00ff00, got $strokes")
  }

  test("trapezoid handDrawn: config.style is applied to the rough shape group") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv, style = "opacity: 0.5")
    val shape  = renderTrapezoid(config).findAllByClass("node-shape").head
    assertEquals(shape.tagName, "g")
    assertEquals(shape.attr("style").getOrElse(""), "opacity: 0.5")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // oracles for the classic sharp `d`
  // ──────────────────────────────────────────────────────────────────────────

  private def classicHexD(config: ShapeConfig): String = {
    val vs   = hexagonVertices(config)
    val path = PathData()
    path.moveTo(vs.head.x, vs.head.y)
    vs.tail.foreach(p => path.lineTo(p.x, p.y))
    path.close()
    path.toString
  }

  private def classicTrapD(config: ShapeConfig): String = {
    val vs   = trapezoidVertices(config)
    val path = PathData()
    path.moveTo(vs.head.x, vs.head.y)
    vs.tail.foreach(p => path.lineTo(p.x, p.y))
    path.close()
    path.toString
  }
}
