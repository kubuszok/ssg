/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1204 sub-chip 9h: FlowchartRenderer.buildEdgeStyle look/handDrawnSeed threading.
 *
 * Proves the renderer-side half of 9h: FlowchartRenderer.buildEdgeStyle copies config.look and
 * config.handDrawnSeed onto the EdgeStyle (edges.js:513/519), so the edge renderer receives the
 * look + seed inputs it needs to route to the rough.js sketch path. Classic config leaves the
 * EdgeStyle at the classic look (byte-identical edges).
 */
package ssg
package mermaid
package diagrams
package flowchart

import munit.FunSuite

import ssg.graphs.commons.layout.dagre.EdgeLabel

final class EdgeStyleHandDrawnThreadIss1204Suite extends FunSuite {

  private def edgeLabel(x: Double, y: Double): EdgeLabel = {
    val el = new EdgeLabel
    el.x = x
    el.y = y
    el
  }

  test("buildEdgeStyle threads config.look and config.handDrawnSeed onto EdgeStyle") {
    val edge   = FlowEdge(src = "A", dst = "B")
    val config = MermaidConfig(look = "handDrawn", handDrawnSeed = 42)
    val style  = FlowchartRenderer.buildEdgeStyle(edge, edgeLabel(10, 20), config, 0)
    assertEquals(style.look, "handDrawn", "look must be threaded from config.look")
    assertEquals(style.handDrawnSeed, 42, "handDrawnSeed must be threaded from config.handDrawnSeed")
  }

  test("buildEdgeStyle leaves classic look for a classic config (byte-identical edges)") {
    val edge   = FlowEdge(src = "A", dst = "B")
    val config = MermaidConfig()
    val style  = FlowchartRenderer.buildEdgeStyle(edge, edgeLabel(10, 20), config, 0)
    assertEquals(style.look, "classic", "default config must keep the classic look")
    assertEquals(style.handDrawnSeed, 0, "default handDrawnSeed must be 0")
  }

  test("buildEdgeStyle threads a distinct non-zero seed verbatim") {
    val edge   = FlowEdge(src = "A", dst = "B")
    val config = MermaidConfig(look = "handDrawn", handDrawnSeed = 12345)
    val style  = FlowchartRenderer.buildEdgeStyle(edge, edgeLabel(0, 0), config, 3)
    assertEquals(style.handDrawnSeed, 12345)
  }
}
