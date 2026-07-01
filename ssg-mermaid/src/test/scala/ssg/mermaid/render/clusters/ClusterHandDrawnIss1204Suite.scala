/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1204 sub-chip 9i: clusters/subgraphs handDrawn rendering.
 *
 * Proves the cluster hand-drawn branch (the LAST handDrawn wiring before the differential
 * test):
 *   - ClusterRenderer.renderCluster / renderRoundedCluster with look="handDrawn" emit a
 *     rough sketch <g> of <path>s (bezier `C` segments), NOT the classic rounded <rect>;
 *     the emitted paths match the ported Rough oracle for the same seed/opts/coords.
 *   - Geometry decision (RADIUS 0): the cluster hand-drawn path uses
 *     createRoundedRectPathD(x, y, w, h, 0) — a SHARP-cornered rect — faithful to upstream
 *     clusters.js:77 (`createRoundedRectPathD(x, y, width, height, 0)`). This is pinned by
 *     matching the radius-0 oracle AND rejecting the radius-5 oracle, and by routing through
 *     renderRoundedCluster (which forces rx=ry=5 on the classic path) yet still producing the
 *     radius-0 sketch — so the classic rx=5 override cannot leak into the hand-drawn radius.
 *   - The cluster passes a BASE options object (roughness 0.7, fill = clusterBkg = borderColor
 *     backgroundColor, stroke = clusterBorder, fillWeight 3, seed) THROUGH userNodeOverrides;
 *     the emitted paths carry those exact opts (roughness/fillWeight/fill/stroke/seed pinned by
 *     the oracle + stroke-colour presence assertions).
 *   - look="classic" (and the default) still emit the classic rounded <rect> (rx=5) with the
 *     cluster background/border colours (regression guard).
 *   - The cluster keeps its cluster-bg class, inline style, and title/label.
 *   - Seed threads: distinct seeds → distinct sketches.
 *
 * The rough-sketch expectations use the ported Rough as the oracle (computed directly in the
 * test): ClusterRenderer must route the SAME coords/opts/seed/radius into Rough, so any
 * threading, branch, radius, or opts mutation makes the emitted paths diverge from the
 * independently-built oracle.
 */
package ssg
package mermaid
package render
package clusters

import munit.FunSuite

import ssg.graphs.commons.rough.{ Options, Rough }
import ssg.graphs.commons.svg.{ SvgBuilder, SvgElement }
import ssg.mermaid.render.shapes.{ HandDrawnNode, HandDrawnShapeStyles, RoundedRectPath }
import ssg.mermaid.theme.ThemeVariables

final class ClusterHandDrawnIss1204Suite extends FunSuite {

  // ──────────────────────────────────────────────────────────────────────────
  // helpers
  // ──────────────────────────────────────────────────────────────────────────

  private val ClusterBkg    = "#ececff"
  private val ClusterBorder = "#9370db"

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

  /** The base options the cluster hand-drawn branch feeds through userNodeOverrides. */
  private def clusterOptions(bkg: String, border: String, seed: Int, tv: ThemeVariables): Options =
    HandDrawnShapeStyles.userNodeOverrides(
      HandDrawnNode(),
      Options(roughness = Some(0.7), fill = Some(bkg), stroke = Some(border), fillWeight = Some(3), seed = Some(seed)),
      tv,
      seed
    )

  private def renderRoundedBuilt(config: ClusterConfig): SvgElement = {
    val parent = SvgBuilder.create("g")
    ClusterRenderer.renderRoundedCluster(parent, config).build()
  }

  private def baseConfig(seed: Int, tv: ThemeVariables): ClusterConfig =
    ClusterConfig(
      id = "cluster-A",
      title = "Sub A",
      x = 100,
      y = 80,
      width = 200,
      height = 120,
      backgroundColor = ClusterBkg,
      borderColor = ClusterBorder,
      look = "handDrawn",
      handDrawnSeed = seed,
      themeVariables = tv
    )

  // ──────────────────────────────────────────────────────────────────────────
  // handDrawn: rough sketch <path>s, NOT the classic <rect>
  // ──────────────────────────────────────────────────────────────────────────

  test("handDrawn cluster: emits a rough <g> of sketch <path>s, NOT the classic <rect>") {
    val tv    = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val built = renderRoundedBuilt(baseConfig(seed = 42, tv))

    assert(built.findAllByTag("rect").isEmpty, "hand-drawn cluster must not emit a classic <rect>")
    val paths = built.findAllByTag("path").toVector
    assert(paths.nonEmpty, "hand-drawn cluster must emit rough <path> elements")
    assert(paths.exists(_.attr("d").getOrElse("").contains("C")), "rough sketch path must contain bezier (C) segments")
    assert(built.findAllByClass("cluster-bg").nonEmpty, "the rough cluster group must carry the cluster-bg class")
  }

  test("handDrawn cluster: emitted paths match the ported Rough radius-0 path oracle (coords+seed+opts)") {
    val tv    = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed  = 42
    val built = renderRoundedBuilt(baseConfig(seed, tv))

    // Oracle: same top-left coords (x - w/2, y - h/2), same cluster opts, RADIUS 0, same seed.
    val x      = 100 - 200 / 2.0
    val y      = 80 - 120 / 2.0
    val opts   = clusterOptions(ClusterBkg, ClusterBorder, seed, tv)
    val oracle = Rough.svg().path(RoundedRectPath.createRoundedRectPathD(x, y, 200, 120, 0), Some(opts))

    val expected = pathDescriptors(oracle)
    val actual   = pathDescriptors(built.findAllByClass("cluster-bg").head)
    assertEquals(actual, expected)
    assert(expected.nonEmpty, "sanity: the oracle must produce at least one path")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Geometry decision — RADIUS 0 (sharp), NOT 5 (rounded)
  // ──────────────────────────────────────────────────────────────────────────

  test("handDrawn cluster geometry: radius 0 matches; radius 5 does NOT (upstream sharp-cornered)") {
    val tv    = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed  = 7
    val built = renderRoundedBuilt(baseConfig(seed, tv))

    val x    = 100 - 200 / 2.0
    val y    = 80 - 120 / 2.0
    val opts = clusterOptions(ClusterBkg, ClusterBorder, seed, tv)

    val oracleR0 = Rough.svg().path(RoundedRectPath.createRoundedRectPathD(x, y, 200, 120, 0), Some(opts))
    val oracleR5 = Rough.svg().path(RoundedRectPath.createRoundedRectPathD(x, y, 200, 120, 5), Some(opts))

    val actual = pathDescriptors(built.findAllByClass("cluster-bg").head)
    assertEquals(actual, pathDescriptors(oracleR0), "hand-drawn cluster must use radius 0 (upstream faithful)")
    assertNotEquals(pathDescriptors(oracleR0), pathDescriptors(oracleR5), "sanity: radius 0 and radius 5 sketches must differ")
    assertNotEquals(
      actual,
      pathDescriptors(oracleR5),
      "hand-drawn cluster must NOT use radius 5 (rx=5 must not leak from renderRoundedCluster)"
    )
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Cluster-specific opts: roughness 0.7, fillWeight 3, fill=clusterBkg, stroke=clusterBorder
  // ──────────────────────────────────────────────────────────────────────────

  test("handDrawn cluster: border/background colours reach the rough paths (clusterBorder stroke + clusterBkg fill-sketch)") {
    val tv      = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val built   = renderRoundedBuilt(baseConfig(seed = 42, tv))
    val strokes = pathDescriptors(built).map(_._2).toSet
    // The outline stroke is clusterBorder; the fillWeight=3 hachure fill is stroked with clusterBkg.
    assert(strokes.contains(ClusterBorder), s"expected the cluster border colour $ClusterBorder as a path stroke, got $strokes")
    assert(
      strokes.contains(ClusterBkg),
      s"expected the cluster background colour $ClusterBkg as the fill-sketch stroke, got $strokes"
    )
  }

  test("handDrawn cluster: exact opts (roughness 0.7 / fillWeight 3 / fill / stroke) pinned via a full-opts oracle") {
    val tv    = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val seed  = 13
    val built = renderRoundedBuilt(baseConfig(seed, tv))

    val x      = 100 - 200 / 2.0
    val y      = 80 - 120 / 2.0
    val opts   = clusterOptions(ClusterBkg, ClusterBorder, seed, tv)
    val oracle = Rough.svg().path(RoundedRectPath.createRoundedRectPathD(x, y, 200, 120, 0), Some(opts))
    val actual = pathDescriptors(built.findAllByClass("cluster-bg").head)
    assertEquals(actual, pathDescriptors(oracle))

    // Wrong-opts guards: changing roughness / fillWeight / fill / stroke must diverge.
    val wrongRough  = clusterOptions(ClusterBkg, ClusterBorder, seed, tv).copy(roughness = Some(1.5))
    val wrongWeight = clusterOptions(ClusterBkg, ClusterBorder, seed, tv).copy(fillWeight = Some(1))
    val wrongFill   = clusterOptions("#000000", ClusterBorder, seed, tv)
    val wrongStroke = clusterOptions(ClusterBkg, "#000000", seed, tv)
    for (wo <- Vector(wrongRough, wrongWeight, wrongFill, wrongStroke)) {
      val wrong = Rough.svg().path(RoundedRectPath.createRoundedRectPathD(x, y, 200, 120, 0), Some(wo))
      assertNotEquals(actual, pathDescriptors(wrong), "a mutated opt must make the sketch diverge")
    }
  }

  // ──────────────────────────────────────────────────────────────────────────
  // seed threading
  // ──────────────────────────────────────────────────────────────────────────

  test("handDrawn cluster: distinct seeds produce distinct sketches (seed threads to opts)") {
    val tv = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val d1 = pathDescriptors(renderRoundedBuilt(baseConfig(seed = 1, tv)))
    val d2 = pathDescriptors(renderRoundedBuilt(baseConfig(seed = 2, tv)))
    assertNotEquals(d1, d2, "different seeds must yield different rough sketches")
  }

  test("handDrawn cluster: same seed is deterministic") {
    val tv = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val d1 = pathDescriptors(renderRoundedBuilt(baseConfig(seed = 99, tv)))
    val d2 = pathDescriptors(renderRoundedBuilt(baseConfig(seed = 99, tv)))
    assertEquals(d1, d2, "the same seed must yield the same rough sketch")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // cluster structure preserved: cluster-bg class, inline style, title
  // ──────────────────────────────────────────────────────────────────────────

  test("handDrawn cluster: keeps its title label") {
    val tv    = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val built = renderRoundedBuilt(baseConfig(seed = 42, tv))
    assert(built.findAllByClass("cluster-label").nonEmpty, "hand-drawn cluster must keep its cluster-label")
  }

  test("handDrawn cluster: config.style is applied to the rough cluster-bg group") {
    val tv     = theme(nodeBorder = "#333333", mainBkg = "#ECECFF")
    val config = baseConfig(seed = 42, tv).copy(style = "opacity: 0.5")
    val built  = renderRoundedBuilt(config)
    val bg     = built.findAllByClass("cluster-bg").head
    assertEquals(bg.tagName, "g")
    assertEquals(bg.attr("style").getOrElse(""), "opacity: 0.5")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // classic path regression guard
  // ──────────────────────────────────────────────────────────────────────────

  test("classic cluster: still emits the rounded <rect> (rx=5) with border/background, no rough path") {
    val config = ClusterConfig(
      id = "cluster-A",
      title = "Sub A",
      x = 100,
      y = 80,
      width = 200,
      height = 120,
      backgroundColor = ClusterBkg,
      borderColor = ClusterBorder,
      look = "classic"
    )
    val built = renderRoundedBuilt(config)
    val rects = built.findAllByTag("rect").toVector
    assertEquals(rects.size, 1, "classic cluster must emit exactly one <rect>")
    assert(built.findAllByTag("path").isEmpty, "classic cluster must not emit any rough <path>")
    val rect = rects.head
    assertEquals(rect.attr("rx").getOrElse(""), "5", "classic cluster stays rounded (rx=5)")
    assertEquals(rect.attr("fill").getOrElse(""), ClusterBkg)
    assertEquals(rect.attr("stroke").getOrElse(""), ClusterBorder)
    assert(rect.hasClass("cluster-bg"))
  }

  test("default look (classic): rounded <rect>, no rough sketch") {
    val config = ClusterConfig(
      title = "Sub A",
      x = 100,
      y = 80,
      width = 200,
      height = 120,
      backgroundColor = ClusterBkg,
      borderColor = ClusterBorder
    )
    val built = renderRoundedBuilt(config)
    assertEquals(built.findAllByTag("rect").size, 1)
    assert(built.findAllByTag("path").isEmpty)
  }
}
