/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1204 sub-chip 9b: FlowchartRenderer hand-drawn threading template.
 *
 * Proves the renderer-side half of 9b (the seam every later shape reuses):
 * FlowchartRenderer.buildShapeConfig maps the node's inline styles onto
 * ShapeConfig.cssStyles (upstream `node.cssStyles`), leaves cssCompiledStyles
 * empty (SSG applies node classes as class attributes, not resolved key:value
 * pairs), and threads the resolved theme onto ShapeConfig.themeVariables — so the
 * RectShape hand-drawn branch receives the stroke/fill/seed inputs it needs.
 */
package ssg
package mermaid
package diagrams
package flowchart

import munit.FunSuite

import ssg.graphs.commons.layout.dagre.NodeLabel
import ssg.mermaid.theme.ThemeVariables

final class FlowchartRendererHandDrawnThreadIss1204Suite extends FunSuite {

  private def nodeLabel(w: Double, h: Double, x: Double, y: Double): NodeLabel = {
    val nl = new NodeLabel
    nl.width = w
    nl.height = h
    nl.x = x
    nl.y = y
    nl
  }

  test("buildShapeConfig maps node.styles -> ShapeConfig.cssStyles (upstream node.cssStyles)") {
    val node = FlowNode(id = "A", text = "A")
    node.styles += "stroke: #ff0000"
    node.styles += "fill: #00ff00"
    val nl = nodeLabel(40, 30, 10, 20)
    val sc = FlowchartRenderer.buildShapeConfig("A", node, nl, "rect", MermaidConfig(look = "handDrawn"), 8.0)
    assertEquals(sc.cssStyles, Vector("stroke: #ff0000", "fill: #00ff00"))
    // SSG does not resolve node classes to key:value pairs at render time.
    assertEquals(sc.cssCompiledStyles, Vector.empty[String])
  }

  test("buildShapeConfig threads the resolved theme onto ShapeConfig.themeVariables") {
    val node = FlowNode(id = "A", text = "A")
    val nl   = nodeLabel(40, 30, 10, 20)
    val tv   = new ThemeVariables
    tv.nodeBorder = "#abcdef"
    tv.mainBkg = "#123456"
    val sc = FlowchartRenderer.buildShapeConfig("A", node, nl, "rect", MermaidConfig(look = "handDrawn"), 8.0, tv)
    // The exact instance is threaded through (reference identity), so the shape
    // renderer's userNodeOverrides reads the flowchart's theme colors.
    assert(sc.themeVariables eq tv)
    assertEquals(sc.themeVariables.nodeBorder, "#abcdef")
    assertEquals(sc.themeVariables.mainBkg, "#123456")
  }

  test("buildShapeConfig with no node styles yields empty cssStyles") {
    val node = FlowNode(id = "A", text = "A")
    val nl   = nodeLabel(40, 30, 10, 20)
    val sc   = FlowchartRenderer.buildShapeConfig("A", node, nl, "rect", MermaidConfig(), 8.0)
    assertEquals(sc.cssStyles, Vector.empty[String])
  }
}
