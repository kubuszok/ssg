/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1204 sub-chip 9h: edges handDrawn rendering (edges.js:513 `edge.look === 'handDrawn'`).
 *
 *   - EdgeRenderer.renderEdge with look="handDrawn" grafts a rough sketch <g>/<path> (the
 *     rough.js sketch of the edge's OWN interpolated path) in place of the plain interpolated
 *     <path>. The rough path reuses SSG's OWN pathData (interpolate(points, curve).toString) as
 *     the `d` fed to rough — so hand-drawn and classic edges share the SAME geometry (markers +
 *     stroke land on the same endpoints — the ISS-1363 geometry-consistency rule). The edge is
 *     rough-sketched with the EDGE-SPECIFIC options (roughness 0.3, seed = handDrawnSeed — NOT
 *     userNodeOverrides), matching the ported Rough oracle built with exactly those opts.
 *   - The sketch path STILL carries the classic edge styling: stroke, stroke-width, stroke-
 *     dasharray (when set), inline style (when set), marker-start/marker-end (when present), and
 *     the `transition` class added (edges.js:522 `strokeClasses += ' transition'`). The edge group
 *     keeps its id + edge classes. Only the LINE geometry becomes sketchy; the styling/markers are
 *     unchanged.
 *   - look="classic" (and the default) still emit the plain interpolated <path> as a direct child
 *     of the edge group (regression guard), with markers/stroke unchanged.
 *
 * The rough-sketch expectations use the ported Rough as the oracle (computed directly in the test):
 * the edge must route the SAME pathData/opts/seed into Rough, so any threading, coordinate, opts,
 * roughness, or branch mutation makes the emitted sketch `d` diverge from the independently-built
 * oracle.
 */
package ssg
package mermaid
package render
package edges

import munit.FunSuite

import lowlevel.Nullable
import ssg.graphs.commons.layout.dagre.Point
import ssg.graphs.commons.rough.{ Options, Rough }
import ssg.graphs.commons.svg.{ SvgBuilder, SvgElement }

final class EdgeHandDrawnIss1204Suite extends FunSuite {

  // ──────────────────────────────────────────────────────────────────────────
  // helpers
  // ──────────────────────────────────────────────────────────────────────────

  private val points: Array[Point] =
    Array(Point(0, 0), Point(50, 60), Point(120, 10), Point(180, 80))

  private val markerId: String = "flowchart-v2"

  private def render(style: EdgeStyle): SvgElement = {
    val parent = SvgBuilder.create("g")
    EdgeRenderer.renderEdge(parent, points, style, markerId).build()
  }

  /** The plain interpolated edge `d` — the exact `d` the classic edge draws AND the rough input. */
  private def plainD(curve: String): String =
    EdgeRenderer.interpolate(points, curve).toString

  /** The sketchy `d` the ported Rough produces for a given edge `d` + edge-specific opts. */
  private def roughEdgeD(d: String, seed: Int, roughness: Double = 0.3): String = {
    val g = Rough.svg().path(d, Some(Options(roughness = Some(roughness), seed = Some(seed))))
    g.findAllByTag("path").toVector.head.attr("d").getOrElse("")
  }

  /** The single <path> the emitted edge carries (classic: direct child; handDrawn: inside rough <g>). */
  private def edgePath(built: SvgElement): SvgElement =
    built.findAllByTag("path").toVector.head

  // ──────────────────────────────────────────────────────────────────────────
  // handDrawn → grafts a rough sketch <g>/<path>, NOT the plain interpolated <path>
  // ──────────────────────────────────────────────────────────────────────────

  test("edge handDrawn: grafts a rough sketch <g>/<path>, NOT the plain interpolated <path>") {
    val style = EdgeStyle(id = "L-A-B-0", curve = "basis", look = "handDrawn", handDrawnSeed = 7, markerEnd = Nullable(MarkerType.Normal))
    val built = render(style)

    // The edge group's first child is a rough <g> (the graft), not a plain <path>.
    assertEquals(built.children.toVector.head.tagName, "g", "hand-drawn edge must graft a rough <g>, not append a plain <path>")

    // Exactly one sketch <path>, and it is nested inside the rough <g> (not a direct child).
    val paths = built.findAllByTag("path").toVector
    assertEquals(paths.size, 1, "hand-drawn edge must emit exactly one rough sketch <path>")
    assert(
      !built.children.toVector.exists(_.tagName == "path"),
      "the sketch <path> must be nested in the rough <g>, not a direct child"
    )

    // Its `d` is the rough sketch of the edge's OWN pathData, NOT the plain interpolated `d`.
    val d = edgePath(built).attr("d").getOrElse("")
    assertEquals(
      d,
      roughEdgeD(plainD("basis"), seed = 7),
      "the sketch `d` must be Rough.svg().path(<edge pathData>, roughness 0.3, seed) — the geometry-consistency reuse"
    )
    assertNotEquals(d, plainD("basis"), "hand-drawn edge must NOT be the plain interpolated `d`")
  }

  test(
    "edge handDrawn: sketch path keeps stroke, stroke-width, marker-start/end, transition class; group keeps id + classes"
  ) {
    val style = EdgeStyle(
      id = "L-A-B-0",
      stroke = "#333",
      strokeWidth = 1.5,
      cssClass = "edge-thickness-normal edge-pattern-solid",
      curve = "basis",
      look = "handDrawn",
      handDrawnSeed = 7,
      markerStart = Nullable(MarkerType.Circle),
      markerEnd = Nullable(MarkerType.Normal)
    )
    val built = render(style)
    val path  = edgePath(built)

    // Stroke styling preserved on the sketch path (overwriting rough's default-option stroke).
    assertEquals(path.attr("stroke").getOrElse(""), "#333", "hand-drawn edge stroke must be preserved")
    assertEquals(path.attr("stroke-width").getOrElse(""), "1.5", "hand-drawn edge stroke-width must be preserved")
    assertEquals(path.attr("fill").getOrElse(""), "none", "hand-drawn edge fill must be none")

    // Markers preserved (arrowheads must still attach to the sketch path endpoints).
    assertEquals(
      path.attr("marker-start").getOrElse(""),
      ArrowMarkers.markerUrl(MarkerType.Circle, markerId),
      "marker-start must be preserved on the sketch path"
    )
    assertEquals(
      path.attr("marker-end").getOrElse(""),
      ArrowMarkers.markerUrl(MarkerType.Normal, markerId),
      "marker-end must be preserved on the sketch path"
    )

    // strokeClasses += ' transition'
    assert(path.hasClass("transition"), "hand-drawn edge sketch path must carry the `transition` class")

    // The edge group keeps its id + edge classes (+ edge-path).
    assertEquals(built.attr("id").getOrElse(""), "L-A-B-0", "edge group must keep its id")
    assert(built.hasClass("edge-path"), "edge group must keep the edge-path class")
    assert(built.hasClass("edge-thickness-normal"), "edge group must keep the edge thickness class")
    assert(built.hasClass("edge-pattern-solid"), "edge group must keep the edge pattern class")
  }

  test("edge handDrawn: stroke-dasharray is preserved when set, absent when empty") {
    val dashed = render(EdgeStyle(look = "handDrawn", handDrawnSeed = 7, strokeDasharray = "3", markerEnd = Nullable(MarkerType.Normal)))
    assertEquals(edgePath(dashed).attr("stroke-dasharray").getOrElse(""), "3", "hand-drawn dashed edge must keep its stroke-dasharray")

    val solid = render(EdgeStyle(look = "handDrawn", handDrawnSeed = 7, strokeDasharray = "", markerEnd = Nullable(MarkerType.Normal)))
    assert(edgePath(solid).attr("stroke-dasharray").isEmpty, "hand-drawn solid edge must not carry a stroke-dasharray")
  }

  test("edge handDrawn: inline style is preserved when set") {
    val styled = render(EdgeStyle(look = "handDrawn", handDrawnSeed = 7, style = "stroke:#f00"))
    assertEquals(edgePath(styled).attr("style").getOrElse(""), "stroke:#f00", "hand-drawn edge inline style must be preserved")
  }

  test("edge handDrawn: markers are absent when the style carries none (not spuriously added)") {
    val built = render(EdgeStyle(look = "handDrawn", handDrawnSeed = 7, markerStart = Nullable.empty, markerEnd = Nullable.empty))
    val path  = edgePath(built)
    assert(path.attr("marker-start").isEmpty, "no marker-start when style has none")
    assert(path.attr("marker-end").isEmpty, "no marker-end when style has none")
  }

  test("edge handDrawn: roughness is exactly 0.3 (not any other value)") {
    val style = EdgeStyle(curve = "basis", look = "handDrawn", handDrawnSeed = 7)
    val d     = edgePath(render(style)).attr("d").getOrElse("")
    assertEquals(d, roughEdgeD(plainD("basis"), seed = 7, roughness = 0.3), "roughness must be 0.3")
    assertNotEquals(d, roughEdgeD(plainD("basis"), seed = 7, roughness = 0.5), "roughness 0.3 must differ from 0.5")
    assertNotEquals(d, roughEdgeD(plainD("basis"), seed = 7, roughness = 1.0), "roughness 0.3 must differ from 1.0")
  }

  test("edge handDrawn: seed threads to opts — determinism + distinct seeds diverge + wrong seed diverges") {
    val base = EdgeStyle(curve = "basis", look = "handDrawn")

    // Deterministic for a fixed seed.
    val a1 = edgePath(render(base.copy(handDrawnSeed = 5))).attr("d").getOrElse("")
    val a2 = edgePath(render(base.copy(handDrawnSeed = 5))).attr("d").getOrElse("")
    assertEquals(a1, a2, "same seed must yield the same sketch")

    // Matches the oracle for THAT seed, not a different one (catches an un-threaded / hardcoded seed).
    assertEquals(a1, roughEdgeD(plainD("basis"), seed = 5), "sketch must match the oracle for seed 5")
    assertNotEquals(a1, roughEdgeD(plainD("basis"), seed = 0), "sketch for seed 5 must NOT equal the seed-0 oracle (seed must thread)")

    // Distinct seeds diverge.
    val b = edgePath(render(base.copy(handDrawnSeed = 6))).attr("d").getOrElse("")
    assertNotEquals(a1, b, "distinct seeds must yield distinct sketches")
  }

  // ──────────────────────────────────────────────────────────────────────────
  // classic look → plain interpolated <path> (regression guard)
  // ──────────────────────────────────────────────────────────────────────────

  test("edge classic look: emits the plain interpolated <path> as a direct child, no rough graft") {
    val style = EdgeStyle(
      id = "L-A-B-0",
      stroke = "#333",
      strokeWidth = 1.5,
      cssClass = "edge-thickness-normal edge-pattern-solid",
      curve = "basis",
      look = "classic",
      markerEnd = Nullable(MarkerType.Normal)
    )
    val built = render(style)

    // The classic path is a DIRECT child <path> of the edge group (no intermediate rough <g>).
    assertEquals(built.children.toVector.head.tagName, "path", "classic edge appends a plain <path> directly")
    val paths = built.findAllByTag("path").toVector
    assertEquals(paths.size, 1)

    val path = paths.head
    assertEquals(path.attr("d").getOrElse(""), plainD("basis"), "classic edge `d` must be the plain interpolated path (byte-identical)")
    assertEquals(path.attr("stroke").getOrElse(""), "#333")
    assertEquals(path.attr("fill").getOrElse(""), "none")
    assertEquals(path.attr("marker-end").getOrElse(""), ArrowMarkers.markerUrl(MarkerType.Normal, markerId))
    assert(!path.hasClass("transition"), "classic edge path must NOT carry the transition class")
  }

  test("edge default look (classic): plain interpolated <path>, no rough sketch") {
    val built = render(EdgeStyle(curve = "basis", markerEnd = Nullable(MarkerType.Normal)))
    assertEquals(built.children.toVector.head.tagName, "path")
    assertEquals(edgePath(built).attr("d").getOrElse(""), plainD("basis"))
    assert(!edgePath(built).hasClass("transition"))
  }
}
