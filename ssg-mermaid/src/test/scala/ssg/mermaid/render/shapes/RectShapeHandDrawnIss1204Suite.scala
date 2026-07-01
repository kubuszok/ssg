/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1204 sub-chip 9b: rect + roundedRect handDrawn rendering.
 *
 * Proves the FIRST shape hand-drawn branch (the rough-options threading template for
 * 9c–9g):
 *   - RoundedRectPath.createRoundedRectPathD builds the exact SVG path `d` string the
 *     upstream `roundedRectPath.ts` array-join produces (a known case, hand-derived).
 *   - RectShape.render with look="handDrawn" and NO rx/ry emits a rough sketch via
 *     rough.svg().rectangle (a <g> of <path>s), NOT a plain <rect>; the emitted paths
 *     match the ported Rough oracle for the same seed/opts/coords.
 *   - RectShape.render with look="handDrawn" WITH rx/ry routes through
 *     createRoundedRectPathD + rough.svg().path (matches the path oracle, distinct from
 *     the rectangle oracle → the rx/ry branch is not inverted).
 *   - look="classic" (and the default) still emits the plain <rect> (regression guard).
 *   - The node style + theme thread through userNodeOverrides into the rough path's
 *     stroke/fill; the seed threads through so distinct seeds produce distinct sketches.
 *
 * The rough-sketch expectations use the ported Rough as the oracle (computed directly in
 * the test), exactly as the brief permits: RectShape must route the SAME coords/opts/seed
 * into Rough, so any threading or branch mutation makes the emitted paths diverge from the
 * independently-built oracle.
 */
package ssg
package mermaid
package render
package shapes

import lowlevel.Nullable

import munit.FunSuite

import ssg.graphs.commons.rough.{ Options, Rough }
import ssg.graphs.commons.svg.{ SvgBuilder, SvgElement }
import ssg.mermaid.theme.ThemeVariables

final class RectShapeHandDrawnIss1204Suite extends FunSuite {

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

  private def renderBuilt(config: ShapeConfig): SvgElement = {
    val parent = SvgBuilder.create("g")
    val res    = RectShape.render(parent, config)
    res.shapeGroup.build()
  }

  // ──────────────────────────────────────────────────────────────────────────
  // createRoundedRectPathD — exact `d` string (upstream roundedRectPath.ts join)
  // ──────────────────────────────────────────────────────────────────────────

  test("createRoundedRectPathD builds the exact M/H/A/V/A/H/A/V/A/Z join (origin case)") {
    // x=0, y=0, w=100, h=50, r=5 — every coordinate integral, so formatNumber == String(n).
    val d = RoundedRectPath.createRoundedRectPathD(0, 0, 100, 50, 5)
    assertEquals(
      d,
      "M 5 0 H 95 A 5 5 0 0 1 100 5 V 45 A 5 5 0 0 1 95 50 H 5 A 5 5 0 0 1 0 45 V 5 A 5 5 0 0 1 5 0 Z"
    )
  }

  test("createRoundedRectPathD builds the exact join (offset case pins every coord formula)") {
    // x=10, y=20, w=100, h=60, r=8.
    val d = RoundedRectPath.createRoundedRectPathD(10, 20, 100, 60, 8)
    assertEquals(
      d,
      "M 18 20 H 102 A 8 8 0 0 1 110 28 V 72 A 8 8 0 0 1 102 80 H 18 A 8 8 0 0 1 10 72 V 28 A 8 8 0 0 1 18 20 Z"
    )
  }

  // ──────────────────────────────────────────────────────────────────────────
  // RectShape.render handDrawn, NO rx/ry → rough.svg().rectangle
  // ──────────────────────────────────────────────────────────────────────────

  test("handDrawn no rx/ry: emits a rough <g> of sketch <path>s, NOT a plain <rect>") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv)
    val built  = renderBuilt(config)

    // No classic <rect> is emitted on the hand-drawn path.
    assert(built.findAllByTag("rect").isEmpty, "hand-drawn rect must not emit a plain <rect>")
    // The shape is a rough sketch: at least one <path> with bezier (C) segments.
    val paths = built.findAllByTag("path").toVector
    assert(paths.nonEmpty, "hand-drawn rect must emit rough <path> elements")
    assert(paths.exists(_.attr("d").getOrElse("").contains("C")), "rough sketch path must contain bezier (C) segments")
    // The rough node carries the node-shape class.
    assert(built.findAllByClass("node-shape").nonEmpty, "rough shape group must carry the node-shape class")
  }

  test("handDrawn no rx/ry: emitted paths match the ported Rough rectangle oracle (coords+seed+opts)") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed   = 42
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val built  = renderBuilt(config)

    // Oracle: same top-left coords SSG computes (x - w/2, y - h/2), same opts, same seed.
    val opts     = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(), tv, seed)
    val oracle   = Rough.svg().rectangle(50 - 100 / 2.0, 40 - 60 / 2.0, 100, 60, Some(opts))
    val expected = pathDescriptors(oracle)
    val actual   = pathDescriptors(built.findAllByClass("node-shape").head)

    assertEquals(actual, expected)
    assert(expected.nonEmpty, "sanity: the oracle must produce at least one path")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // RectShape.render handDrawn, WITH rx/ry → createRoundedRectPathD + rough.svg().path
  // ──────────────────────────────────────────────────────────────────────────

  test("handDrawn with rx/ry: routes through createRoundedRectPathD + rough.svg().path (matches path oracle)") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed   = 7
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, rx = 5, ry = 5, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val built  = renderBuilt(config)

    val rectX = 50 - 100 / 2.0
    val rectY = 40 - 60 / 2.0
    val opts  = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(), tv, seed)

    val pathOracle = Rough.svg().path(RoundedRectPath.createRoundedRectPathD(rectX, rectY, 100, 60, 5), Some(opts))
    val actual     = pathDescriptors(built.findAllByClass("node-shape").head)
    assertEquals(actual, pathDescriptors(pathOracle))

    // Branch-inversion guard: a rectangle sketch of the same box differs from the rounded path.
    val rectOracle = Rough.svg().rectangle(rectX, rectY, 100, 60, Some(opts))
    assertNotEquals(pathDescriptors(pathOracle), pathDescriptors(rectOracle))
  }

  test("handDrawn radius uses rx || 0 — the rx value feeds createRoundedRectPathD") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed   = 7
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, rx = 12, ry = 12, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val built  = renderBuilt(config)

    val rectX  = 50 - 100 / 2.0
    val rectY  = 40 - 60 / 2.0
    val opts   = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(), tv, seed)
    val oracle = Rough.svg().path(RoundedRectPath.createRoundedRectPathD(rectX, rectY, 100, 60, 12), Some(opts))
    assertEquals(pathDescriptors(built.findAllByClass("node-shape").head), pathDescriptors(oracle))
  }

  // ──────────────────────────────────────────────────────────────────────────
  // classic path regression guard
  // ──────────────────────────────────────────────────────────────────────────

  test("classic look: still emits the plain <rect> and no rough <path>") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "classic")
    val built  = renderBuilt(config)
    val rects  = built.findAllByTag("rect").toVector
    assertEquals(rects.size, 1, "classic look must emit exactly one <rect>")
    assert(built.findAllByTag("path").isEmpty, "classic look must not emit any rough <path>")
    assertEquals(rects.head.attr("x").getOrElse(""), "0")
    assertEquals(rects.head.attr("y").getOrElse(""), "10")
    assert(rects.head.hasClass("node-shape"))
  }

  test("default look (classic): plain <rect>, no rough sketch") {
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60)
    val built  = renderBuilt(config)
    assertEquals(built.findAllByTag("rect").size, 1)
    assert(built.findAllByTag("path").isEmpty)
  }

  // ──────────────────────────────────────────────────────────────────────────
  // opts threading: node style + theme reach the rough path's stroke/fill
  // ──────────────────────────────────────────────────────────────────────────

  test("node style threads through userNodeOverrides into the rough path stroke/fill") {
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
    val built   = renderBuilt(config)
    val strokes = pathDescriptors(built).map(_._2).toSet
    // The style-supplied stroke (#ff0000) and fill (#00ff00, emitted as the fill-sketch stroke) reach the paths.
    assert(strokes.contains("#ff0000"), s"expected a path with stroke #ff0000, got $strokes")
    assert(strokes.contains("#00ff00"), s"expected the fill-sketch path stroke #00ff00, got $strokes")
  }

  test("theme nodeBorder/mainBkg default the rough stroke/fill when no node style is set") {
    val tv      = theme(nodeBorder = "#abcdef", mainBkg = "#123456")
    val config  = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv)
    val built   = renderBuilt(config)
    val strokes = pathDescriptors(built).map(_._2).toSet
    assert(strokes.contains("#abcdef"), s"expected theme nodeBorder #abcdef as a stroke, got $strokes")
    assert(strokes.contains("#123456"), s"expected theme mainBkg #123456 as the fill-sketch stroke, got $strokes")
  }

  test("seed threads to opts: distinct seeds produce distinct sketches") {
    val tv   = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val base = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", themeVariables = tv)
    val d1   = pathDescriptors(renderBuilt(base.copy(handDrawnSeed = 1)))
    val d2   = pathDescriptors(renderBuilt(base.copy(handDrawnSeed = 2)))
    assertNotEquals(d1, d2, "different seeds must yield different rough sketches (seed must thread to opts)")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // inline style + class on the grafted rough group
  // ──────────────────────────────────────────────────────────────────────────

  test("handDrawn: config.style is applied to the rough shape group") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = 42, themeVariables = tv, style = "opacity: 0.5")
    val built  = renderBuilt(config)
    val shape  = built.findAllByClass("node-shape").head
    assertEquals(shape.tagName, "g")
    assertEquals(shape.attr("style").getOrElse(""), "opacity: 0.5")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // RoundedRectShape delegates → rounded hand-drawn works via the rx/ry branch
  // ──────────────────────────────────────────────────────────────────────────

  test("RoundedRectShape handDrawn: default rx=ry=5 routes through the rounded path") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed   = 7
    val config = ShapeConfig(x = 50, y = 40, width = 100, height = 60, look = "handDrawn", handDrawnSeed = seed, themeVariables = tv)
    val parent = SvgBuilder.create("g")
    val built  = RoundedRectShape.render(parent, config).shapeGroup.build()

    val rectX  = 50 - 100 / 2.0
    val rectY  = 40 - 60 / 2.0
    val opts   = HandDrawnShapeStyles.userNodeOverrides(HandDrawnNode(), Options(), tv, seed)
    val oracle = Rough.svg().path(RoundedRectPath.createRoundedRectPathD(rectX, rectY, 100, 60, 5), Some(opts))
    assertEquals(pathDescriptors(built.findAllByClass("node-shape").head), pathDescriptors(oracle))
    assert(built.findAllByTag("rect").isEmpty)
  }
}
