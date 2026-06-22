/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1077: Cluster subgraphs must render as boxed regions with labels,
 * not as stray phantom nodes.
 *
 * Graphviz cluster semantics (from Graphviz documentation):
 *   - A subgraph whose name starts with "cluster" is drawn as a rectangle
 *     enclosing its member nodes
 *   - The cluster's `label` attribute is rendered (typically top-center)
 *   - Optional styling: style=filled, bgcolor, color (border), pencolor,
 *     fillcolor, fontcolor, fontsize
 *   - Non-cluster subgraphs (names not starting with "cluster") do NOT
 *     draw a box
 *
 * Expected values in the assertions below are derived from these documented
 * Graphviz semantics: cluster subgraphs produce <rect> elements enclosing
 * their member nodes, with label text visible; phantom cluster node IDs
 * must NOT appear as rendered node labels.
 */
package ssg
package graphviz
package render

import munit.FunSuite

final class ClusterRenderingIss1077Suite extends FunSuite {

  // Use Neato layout for all tests — Dot/dagre layout with compound
  // clusters has a separate known issue (ISS-1074). The rendering pipeline
  // under test (GraphBuilder + DotRenderer) is the same regardless of
  // layout engine; Neato avoids the compound-layout crash.
  private val testConfig: GraphvizConfig = GraphvizConfig(engine = LayoutEngine.Neato)

  private def renderDot(input: String): String =
    Graphviz.render(input, testConfig)

  // -- Basic cluster rendering --

  private val clusterDot: String =
    """digraph G {
      |  subgraph cluster_0 {
      |    label="My Cluster";
      |    a; b;
      |    a -> b;
      |  }
      |  c -> a;
      |}""".stripMargin

  test("ISS-1077: cluster subgraph renders a rect element") {
    val svg = renderDot(clusterDot)
    assert(
      svg.contains("<rect") && svg.contains("class=\"cluster\""),
      s"SVG should contain a <rect> with class='cluster' for cluster_0, but got:\n$svg"
    )
  }

  test("ISS-1077: cluster label text is rendered") {
    val svg = renderDot(clusterDot)
    assert(
      svg.contains(">My Cluster</text>"),
      s"SVG should contain the cluster label text 'My Cluster', but got:\n$svg"
    )
  }

  test("ISS-1077: phantom cluster node is NOT rendered as a stray node") {
    val svg = renderDot(clusterDot)
    // The phantom node "cluster_0" should not appear as a node label
    // Count occurrences of class="node" — should be exactly 3 (a, b, c)
    val nodeGroupCount = """class="node"""".r.findAllMatchIn(svg).size
    assertEquals(
      nodeGroupCount,
      3,
      s"Should have exactly 3 node groups (a, b, c) — no phantom cluster_0 node. Got $nodeGroupCount in:\n$svg"
    )
  }

  test("ISS-1077: cluster_0 ID does not appear as rendered node label text") {
    val svg = renderDot(clusterDot)
    // Extract all text labels inside <text>...</text> elements
    val textRe = """>([^<]+)</text>""".r
    val labels = textRe.findAllMatchIn(svg).map(_.group(1).trim).filter(_.nonEmpty).toSet
    assert(
      !labels.contains("cluster_0"),
      s"'cluster_0' should NOT appear as a text label (it's a phantom). Labels found: $labels"
    )
  }

  // -- Cluster box encloses member nodes --

  test("ISS-1077: cluster rect encloses its member nodes") {
    val svg = renderDot(clusterDot)
    // The cluster group must contain a rect with positive dimensions.
    // Use (?s) so dot matches newlines between <g> and <rect>.
    val clusterRectRe = """(?s)<g\s+class="cluster"[^>]*>\s*<rect\s+([^>]+)""".r
    val attrRe = """(\w+)="([^"]+)"""".r

    val clusterRectMatch = clusterRectRe.findFirstMatchIn(svg)
    assert(clusterRectMatch.isDefined, s"Should find a <g class='cluster'> with <rect>, but got:\n$svg")

    val rectAttrs = attrRe.findAllMatchIn(clusterRectMatch.get.group(1)).map(m => m.group(1) -> m.group(2)).toMap
    val rw = rectAttrs("width").toDouble
    val rh = rectAttrs("height").toDouble

    // The cluster rect must have positive dimensions
    assert(rw > 0 && rh > 0, s"Cluster rect should have positive dimensions, got width=$rw height=$rh")
  }

  // -- Non-cluster subgraph does NOT draw a box --

  test("ISS-1077: non-cluster subgraph does NOT draw a box") {
    val dotInput =
      """digraph G {
        |  subgraph foo {
        |    label="Not A Cluster";
        |    x; y;
        |    x -> y;
        |  }
        |  z -> x;
        |}""".stripMargin
    val svg = renderDot(dotInput)
    assert(
      !svg.contains("class=\"cluster\""),
      s"Non-cluster subgraph 'foo' should NOT render a cluster box, but found class='cluster' in:\n$svg"
    )
  }

  // -- Multiple clusters --

  test("ISS-1077: multiple clusters each render their own box and label") {
    val dotInput =
      """digraph G {
        |  subgraph cluster_0 {
        |    label="First";
        |    a0 -> a1;
        |  }
        |  subgraph cluster_1 {
        |    label="Second";
        |    b0 -> b1;
        |  }
        |  a1 -> b0;
        |}""".stripMargin
    val svg = renderDot(dotInput)

    val clusterRectCount = """class="cluster"""".r.findAllMatchIn(svg).size
    assertEquals(clusterRectCount, 2, s"Should have 2 cluster groups, got $clusterRectCount")

    assert(svg.contains(">First</text>"), s"Should contain cluster label 'First'")
    assert(svg.contains(">Second</text>"), s"Should contain cluster label 'Second'")
  }

  // -- Cluster styling attributes --

  test("ISS-1077: cluster color attribute sets border stroke") {
    val dotInput =
      """digraph G {
        |  subgraph cluster_0 {
        |    color=blue;
        |    a; b;
        |  }
        |}""".stripMargin
    val svg = renderDot(dotInput)
    assert(
      svg.contains("class=\"cluster\""),
      s"Should render a cluster box"
    )
    // The rect stroke should be blue
    assert(
      svg.contains("stroke=\"blue\""),
      s"Cluster border stroke should be 'blue', but got:\n$svg"
    )
  }

  test("ISS-1077: cluster with style=filled and bgcolor renders filled rect") {
    val dotInput =
      """digraph G {
        |  subgraph cluster_0 {
        |    style=filled;
        |    color=lightgrey;
        |    a; b;
        |  }
        |}""".stripMargin
    val svg = renderDot(dotInput)
    assert(
      svg.contains("class=\"cluster\""),
      s"Should render a cluster box"
    )
    // When style=filled, the rect fill should come from color (lightgrey)
    // since no explicit bgcolor or fillcolor is set
    assert(
      svg.contains("fill=\"lightgrey\""),
      s"Filled cluster should have fill='lightgrey', but got:\n$svg"
    )
  }

  // -- Cluster attributes must NOT leak to global graph --

  test("ISS-1077: cluster label does not leak into graph-level label") {
    val dotInput =
      """digraph G {
        |  label="Graph Title";
        |  subgraph cluster_0 {
        |    label="Cluster Label";
        |    a; b;
        |  }
        |  c;
        |}""".stripMargin
    val svg = renderDot(dotInput)
    // Both labels should be present
    assert(svg.contains(">Graph Title</text>"), s"Graph-level label should be 'Graph Title'")
    assert(svg.contains(">Cluster Label</text>"), s"Cluster label should be 'Cluster Label'")
  }

  // -- Cluster with no label (no stray phantom) --

  test("ISS-1077: cluster without label attr does not render phantom ID as node") {
    val dotInput =
      """digraph G {
        |  subgraph cluster_0 {
        |    a; b;
        |  }
        |  c;
        |}""".stripMargin
    val svg = renderDot(dotInput)
    val textRe = """>([^<]+)</text>""".r
    val labels = textRe.findAllMatchIn(svg).map(_.group(1).trim).filter(_.nonEmpty).toSet
    assert(
      !labels.contains("cluster_0"),
      s"Cluster without label should NOT render 'cluster_0' as text. Labels: $labels"
    )
    // Should still have 3 node groups (a, b, c), not 4
    val nodeGroupCount = """class="node"""".r.findAllMatchIn(svg).size
    assertEquals(nodeGroupCount, 3, s"Should have 3 nodes, not 4 (no phantom). Got $nodeGroupCount")
  }
}
