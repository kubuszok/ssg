/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1204 sub-chip 9c: circle + ellipse + doublecircle handDrawn rendering.
 *
 * Proves the three shape hand-drawn branches (reusing the 9b rough-options template):
 *   - CircleShape.render look="handDrawn" emits a rough sketch via rough.svg().circle
 *     (a <g> of <path>s), NOT a plain <circle>; the emitted paths match the ported Rough
 *     oracle rough.svg().circle(cx, cy, radius*2, opts) — pinning the radius*2 diameter and
 *     the seed/opts threading.
 *   - EllipseShape.render look="handDrawn" emits rough.svg().ellipse(cx, cy, width, height,
 *     opts) (no upstream ellipse.ts; modeled on circle.ts) — width/height order pinned by a
 *     non-square box.
 *   - DoubleCircleShape.render look="handDrawn" emits TWO rough circles (outer roughness=0.2
 *     strokeWidth=2.5 at outerRadius*2, inner roughness=0.2 strokeWidth=1.5 at innerRadius*2),
 *     matching the concatenation of the two independently-built oracles → the "only one circle"
 *     mutant is caught.
 *   - look="classic"/default still emits the plain <circle>/<ellipse>/two <circle>s (regression).
 *
 * The rough-sketch expectations use the ported Rough as the oracle (computed directly here),
 * exactly as the brief permits: each shape must route the SAME coords/opts/seed into Rough, so
 * any threading or branch mutation makes the emitted paths diverge from the oracle.
 */
package ssg
package mermaid
package render
package shapes

import munit.FunSuite

import ssg.graphs.commons.rough.{ Options, Rough }
import ssg.graphs.commons.svg.{ SvgBuilder, SvgElement }
import ssg.mermaid.theme.ThemeVariables

final class CircleEllipseDoubleCircleHandDrawnIss1204Suite extends FunSuite {

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

  private def renderCircle(config: ShapeConfig): SvgElement =
    CircleShape.render(SvgBuilder.create("g"), config).shapeGroup.build()

  private def renderEllipse(config: ShapeConfig): SvgElement =
    EllipseShape.render(SvgBuilder.create("g"), config).shapeGroup.build()

  private def renderDoubleCircle(config: ShapeConfig): SvgElement =
    DoubleCircleShape.render(SvgBuilder.create("g"), config).shapeGroup.build()

  // ──────────────────────────────────────────────────────────────────────────
  // CircleShape hand-drawn
  // ──────────────────────────────────────────────────────────────────────────

  test("circle handDrawn: emits a rough <g> of sketch <path>s, NOT a plain <circle>") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv)
    val built  = renderCircle(config)

    assert(built.findAllByTag("circle").isEmpty, "hand-drawn circle must not emit a plain <circle>")
    val paths = built.findAllByTag("path").toVector
    assert(paths.nonEmpty, "hand-drawn circle must emit rough <path> elements")
    assert(paths.exists(_.attr("d").getOrElse("").contains("C")), "rough sketch path must contain bezier (C) segments")
    assert(built.findAllByClass("node-shape").nonEmpty, "rough shape group must carry the node-shape class")
  }

  test("circle handDrawn: emitted paths match rough.circle(cx, cy, radius*2, opts) oracle") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed   = 42
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val built  = renderCircle(config)

    // radius = max(100, 60) / 2 = 50 ; diameter = radius * 2 = 100 ; centered at (50, 40).
    val opts     = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(), tv, seed)
    val oracle   = Rough.svg().circle(50, 40, 100, Some(opts))
    val expected = pathDescriptors(oracle)
    val actual   = pathDescriptors(built.findAllByClass("node-shape").head)

    assertEquals(actual, expected)
    assert(expected.nonEmpty, "sanity: the oracle must produce at least one path")

    // radius*2 mutant guard: a diameter of `radius` (=50) would differ from `radius*2` (=100).
    val halfOracle = Rough.svg().circle(50, 40, 50, Some(opts))
    assertNotEquals(pathDescriptors(oracle), pathDescriptors(halfOracle))
  }

  test("circle classic look: still emits the plain <circle>, no rough <path>") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "classic")
    val built  = renderCircle(config)
    val circs  = built.findAllByTag("circle").toVector
    assertEquals(circs.size, 1, "classic look must emit exactly one <circle>")
    assert(built.findAllByTag("path").isEmpty, "classic look must not emit any rough <path>")
    assertEquals(circs.head.attr("r").getOrElse(""), "50")
    assert(circs.head.hasClass("node-shape"))
  }

  test("circle default look (classic): plain <circle>, no rough sketch") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60)
    val built  = renderCircle(config)
    assertEquals(built.findAllByTag("circle").size, 1)
    assert(built.findAllByTag("path").isEmpty)
  }

  test("circle handDrawn: seed threads to opts — distinct seeds produce distinct sketches") {
    val tv   = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val base = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", themeVariables = tv)
    val d1   = pathDescriptors(renderCircle(base.copy(handDrawnSeed = 1)))
    val d2   = pathDescriptors(renderCircle(base.copy(handDrawnSeed = 2)))
    assertNotEquals(d1, d2, "different seeds must yield different rough sketches (seed must thread to opts)")
  }

  test("circle handDrawn: node style threads through userNodeOverrides into stroke/fill") {
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
    val strokes = pathDescriptors(renderCircle(config)).map(_._2).toSet
    assert(strokes.contains("#ff0000"), s"expected a path with stroke #ff0000, got $strokes")
    assert(strokes.contains("#00ff00"), s"expected the fill-sketch path stroke #00ff00, got $strokes")
  }

  test("circle handDrawn: config.style is applied to the rough shape group") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv, style = "opacity: 0.5")
    val shape  = renderCircle(config).findAllByClass("node-shape").head
    assertEquals(shape.tagName, "g")
    assertEquals(shape.attr("style").getOrElse(""), "opacity: 0.5")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // EllipseShape hand-drawn
  // ──────────────────────────────────────────────────────────────────────────

  test("ellipse handDrawn: emits a rough <g> of sketch <path>s, NOT a plain <ellipse>") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv)
    val built  = renderEllipse(config)

    assert(built.findAllByTag("ellipse").isEmpty, "hand-drawn ellipse must not emit a plain <ellipse>")
    val paths = built.findAllByTag("path").toVector
    assert(paths.nonEmpty, "hand-drawn ellipse must emit rough <path> elements")
    assert(paths.exists(_.attr("d").getOrElse("").contains("C")), "rough sketch path must contain bezier (C) segments")
    assert(built.findAllByClass("node-shape").nonEmpty, "rough shape group must carry the node-shape class")
  }

  test("ellipse handDrawn: emitted paths match rough.ellipse(cx, cy, width, height, opts) oracle") {
    val tv   = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed = 13
    // Non-square box so a width<->height swap is observable.
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val built  = renderEllipse(config)

    val opts   = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(), tv, seed)
    val oracle = Rough.svg().ellipse(50, 40, 100, 60, Some(opts))
    assertEquals(pathDescriptors(built.findAllByClass("node-shape").head), pathDescriptors(oracle))

    // width/height swap guard: ellipse(50, 40, 60, 100) differs from ellipse(50, 40, 100, 60).
    val swapped = Rough.svg().ellipse(50, 40, 60, 100, Some(opts))
    assertNotEquals(pathDescriptors(oracle), pathDescriptors(swapped))
  }

  test("ellipse classic look: still emits the plain <ellipse>, no rough <path>") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "classic")
    val built  = renderEllipse(config)
    val ells   = built.findAllByTag("ellipse").toVector
    assertEquals(ells.size, 1, "classic look must emit exactly one <ellipse>")
    assert(built.findAllByTag("path").isEmpty, "classic look must not emit any rough <path>")
    assertEquals(ells.head.attr("rx").getOrElse(""), "50")
    assertEquals(ells.head.attr("ry").getOrElse(""), "30")
    assert(ells.head.hasClass("node-shape"))
  }

  test("ellipse default look (classic): plain <ellipse>, no rough sketch") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60)
    val built  = renderEllipse(config)
    assertEquals(built.findAllByTag("ellipse").size, 1)
    assert(built.findAllByTag("path").isEmpty)
  }

  test("ellipse handDrawn: seed threads to opts — distinct seeds produce distinct sketches") {
    val tv   = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val base = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", themeVariables = tv)
    val d1   = pathDescriptors(renderEllipse(base.copy(handDrawnSeed = 1)))
    val d2   = pathDescriptors(renderEllipse(base.copy(handDrawnSeed = 2)))
    assertNotEquals(d1, d2)
  }

  // ──────────────────────────────────────────────────────────────────────────
  // DoubleCircleShape hand-drawn
  // ──────────────────────────────────────────────────────────────────────────

  test("doublecircle handDrawn: emits BOTH rough circles (outer + inner), NOT plain <circle>s") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed   = 99
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val built  = renderDoubleCircle(config)

    assert(built.findAllByTag("circle").isEmpty, "hand-drawn doublecircle must not emit plain <circle>s")

    // outerRadius = max(100,60)/2 = 50 ; innerRadius = 50 - 5 (CircleGap) = 45.
    val outerOpts = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(roughness = Some(0.2), strokeWidth = Some(2.5)), tv, seed)
    val innerOpts = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(roughness = Some(0.2), strokeWidth = Some(1.5)), tv, seed)
    val outer     = pathDescriptors(Rough.svg().circle(50, 40, 100, Some(outerOpts)))
    val inner     = pathDescriptors(Rough.svg().circle(50, 40, 90, Some(innerOpts)))
    assert(outer.nonEmpty && inner.nonEmpty, "sanity: both oracle circles must produce paths")

    val actual = pathDescriptors(built.findAllByClass("node-shape").head)
    // Both rings present, in order: the concatenation of the two independently-built oracles.
    assertEquals(actual, outer ++ inner)

    // "only one circle" mutant guard: emitting just outer (or just inner) would not equal outer++inner.
    assertNotEquals(actual, outer)
    assertNotEquals(actual, inner)
  }

  test("doublecircle handDrawn: outer/inner use distinct base options (strokeWidth 2.5 vs 1.5)") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed   = 99
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val built  = renderDoubleCircle(config)

    // The rough path stroke-width reflects options.strokeWidth (userNodeOverrides preserves it).
    val strokeWidths = pathDescriptors(built.findAllByClass("node-shape").head).map(_._4).toSet
    assert(strokeWidths.contains("2.5"), s"expected outer strokeWidth 2.5, got $strokeWidths")
    assert(strokeWidths.contains("1.5"), s"expected inner strokeWidth 1.5, got $strokeWidths")
  }

  test("doublecircle classic look: still emits two plain <circle>s, no rough <path>") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "classic")
    val built  = renderDoubleCircle(config)
    val circs  = built.findAllByTag("circle").toVector
    assertEquals(circs.size, 2, "classic look must emit exactly two <circle>s")
    assert(built.findAllByTag("path").isEmpty, "classic look must not emit any rough <path>")
    assert(built.findAllByClass("outer-circle").nonEmpty)
    assert(built.findAllByClass("inner-circle").nonEmpty)
  }

  test("doublecircle default look (classic): two <circle>s, no rough sketch") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60)
    val built  = renderDoubleCircle(config)
    assertEquals(built.findAllByTag("circle").size, 2)
    assert(built.findAllByTag("path").isEmpty)
  }

  test("doublecircle handDrawn: seed threads to opts — distinct seeds produce distinct sketches") {
    val tv   = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val base = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", themeVariables = tv)
    val d1   = pathDescriptors(renderDoubleCircle(base.copy(handDrawnSeed = 1)))
    val d2   = pathDescriptors(renderDoubleCircle(base.copy(handDrawnSeed = 2)))
    assertNotEquals(d1, d2)
  }

  test("doublecircle handDrawn: config.style is applied to the wrapping rough group") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv, style = "opacity: 0.5")
    val shape  = renderDoubleCircle(config).findAllByClass("node-shape").head
    assertEquals(shape.tagName, "g")
    assertEquals(shape.attr("style").getOrElse(""), "opacity: 0.5")
  }
}
