/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package graphviz
package render

import munit.FunSuite

final class GraphvizRendererSuite extends FunSuite {

  // Use Neato (spring) layout for tests — the Dagre layout engine
  // has known issues with certain graph topologies during the
  // Sugiyama phase ordering step that can cause hangs
  private val testConfig: GraphvizConfig = GraphvizConfig(engine = LayoutEngine.Neato)

  private def render(input: String, config: GraphvizConfig = testConfig): String =
    Graphviz.render(input, config)

  // --- End-to-end rendering ---

  test("render: simple digraph produces SVG") {
    val svg = render("digraph { A -> B }")
    assert(svg.contains("<svg"), s"SVG output missing <svg> tag: $svg")
    assert(svg.contains("</svg>"), "SVG output missing closing tag")
    assert(svg.contains("xmlns"), "SVG output missing xmlns attribute")
  }

  test("render: graph with node shapes") {
    val svg = render("""digraph { node [shape=box]; A -> B }""")
    assert(svg.contains("<rect"), "Box shape should render as <rect>")
  }

  test("render: graph with edge labels") {
    val svg = render("""digraph { A -> B [label="test edge"] }""")
    assert(svg.contains("<path"), "Edges should render as <path>")
  }

  test("render: graph with styling attributes") {
    val svg = render("""digraph { A [fillcolor=yellow, color=blue] }""")
    assert(svg.contains("fill=\"yellow\""), "fillcolor should map to fill attribute")
    assert(svg.contains("stroke=\"blue\""), "color should map to stroke attribute")
  }

  test("render: graph with subgraphs") {
    val svg = render(
      """digraph {
        |  subgraph cluster_0 { A; B }
        |  subgraph cluster_1 { C; D }
        |  A -> C
        |}""".stripMargin
    )
    assert(svg.contains("<svg"))
    assert(svg.contains("class=\"nodes\""))
  }

  test("render: empty graph") {
    val svg = render("digraph {}")
    assert(svg.contains("<svg"))
  }

  test("render: single node") {
    val svg = render("digraph { A }")
    assert(svg.contains("<svg"))
    assert(svg.contains("class=\"nodes\""))
    assert(svg.contains("<ellipse"), "Default node shape should be ellipse")
  }

  // --- Layout engine dispatch ---

  test("render: neato engine (spring)") {
    val config = GraphvizConfig(engine = LayoutEngine.Neato)
    val svg = Graphviz.render("digraph { A -> B -> C }", config)
    assert(svg.contains("<svg"))
    assert(svg.contains("<path"))
  }

  test("render: circo engine (circular)") {
    val config = GraphvizConfig(engine = LayoutEngine.Circo)
    val svg = Graphviz.render("digraph { A -> B -> C }", config)
    assert(svg.contains("<svg"))
    assert(svg.contains("<path"))
  }

  test("render: twopi engine (radial)") {
    val config = GraphvizConfig(engine = LayoutEngine.Twopi)
    val svg = Graphviz.render("digraph { A -> B -> C }", config)
    assert(svg.contains("<svg"))
    assert(svg.contains("<path"))
  }

  // --- SVG structure verification ---

  test("render: SVG has xmlns attribute") {
    val svg = render("digraph { A -> B }")
    assert(svg.contains("xmlns=\"http://www.w3.org/2000/svg\""))
  }

  test("render: SVG has viewBox") {
    val svg = render("digraph { A -> B }")
    assert(svg.contains("viewBox=\"0 0"))
  }

  test("render: directed graph has arrow marker defs") {
    val svg = render("digraph { A -> B }")
    assert(svg.contains("<defs>"), "Directed graph should have <defs>")
    assert(svg.contains("<marker"), "Directed graph should have <marker>")
    assert(svg.contains("id=\"arrowhead\""), "Marker should have id arrowhead")
    assert(svg.contains("marker-end=\"url(#arrowhead)\""), "Edges should reference arrowhead")
  }

  test("render: undirected graph has no arrow markers") {
    val svg = render("graph { A -- B }")
    assert(!svg.contains("<marker"), "Undirected graph should not have arrow markers")
    assert(!svg.contains("marker-end"), "Undirected edges should not have marker-end")
  }

  test("render: nodes are rendered as shapes") {
    val svg = render("digraph { A; B }")
    assert(svg.contains("<ellipse"))
  }

  test("render: edges are rendered as paths") {
    val svg = render("digraph { A -> B }")
    assert(svg.contains("<path"))
    assert(svg.contains("class=\"edge\""))
  }

  test("render: node labels are rendered as text") {
    val svg = render("digraph { A }")
    assert(svg.contains("<text"))
    assert(svg.contains("text-anchor=\"middle\""))
    assert(svg.contains(">A</text>"))
  }

  // --- Node shapes ---

  test("render: ellipse shape (default)") {
    val svg = render("digraph { A }")
    assert(svg.contains("<ellipse"))
  }

  test("render: box shape") {
    val svg = render("digraph { A [shape=box] }")
    assert(svg.contains("<rect"))
  }

  test("render: rect shape alias") {
    val svg = render("digraph { A [shape=rect] }")
    assert(svg.contains("<rect"))
  }

  test("render: rectangle shape alias") {
    val svg = render("digraph { A [shape=rectangle] }")
    assert(svg.contains("<rect"))
  }

  test("render: circle shape") {
    val svg = render("digraph { A [shape=circle] }")
    assert(svg.contains("<circle"))
  }

  test("render: diamond shape") {
    val svg = render("digraph { A [shape=diamond] }")
    assert(svg.contains("<polygon"))
  }

  test("render: plaintext shape (no shape element)") {
    val svg = render("digraph { A [shape=plaintext] }")
    assert(!svg.contains("<ellipse"))
    assert(!svg.contains("<rect"))
    assert(!svg.contains("<circle"))
  }

  // --- Styling ---

  test("render: node fillcolor applies to fill") {
    val svg = render("digraph { A [fillcolor=lightblue] }")
    assert(svg.contains("fill=\"lightblue\""))
  }

  test("render: node color applies to stroke") {
    val svg = render("digraph { A [color=red] }")
    assert(svg.contains("stroke=\"red\""))
  }

  test("render: dashed style applies stroke-dasharray") {
    val svg = render("digraph { A [style=dashed] }")
    assert(svg.contains("stroke-dasharray"))
  }

  test("render: dotted style applies stroke-dasharray") {
    val svg = render("digraph { A [style=dotted] }")
    assert(svg.contains("stroke-dasharray=\"1,2\""))
  }

  test("render: default node attributes apply to all nodes") {
    val svg = render("digraph { node [shape=box]; A; B; C }")
    val rectCount = "<rect".r.findAllMatchIn(svg).size
    assertEquals(rectCount, 3)
  }

  test("render: fontsize attribute") {
    val svg = render("digraph { A [fontsize=20] }")
    assert(svg.contains("font-size=\"20\""))
  }

  test("render: fontname attribute") {
    val svg = render("digraph { A [fontname=monospace] }")
    assert(svg.contains("font-family=\"monospace\""))
  }

  test("render: fontcolor attribute") {
    val svg = render("digraph { A [fontcolor=blue] }")
    assert(svg.contains("fill=\"blue\""))
  }

  // --- Cross-platform determinism ---

  test("render: same input produces same output") {
    val input = "digraph { A -> B; B -> C; C -> A }"
    val svg1 = render(input)
    val svg2 = render(input)
    assertEquals(svg1, svg2)
  }

  // --- Edge styling ---

  test("render: edge color attribute") {
    val svg = render("digraph { edge [color=green]; A -> B }")
    assert(svg.contains("stroke=\"green\""))
  }

  test("render: edge dashed style") {
    val svg = render("digraph { A -> B [style=dashed] }")
    assert(svg.contains("stroke-dasharray=\"5,2\""))
  }

  // --- Config ---

  test("render: custom font name in config") {
    val config = GraphvizConfig(fontName = "Courier", engine = LayoutEngine.Neato)
    val svg = Graphviz.render("digraph { A }", config)
    assert(svg.contains("font-family=\"Courier\""))
  }

  test("render: custom font size in config") {
    val config = GraphvizConfig(fontSize = 18.0, engine = LayoutEngine.Neato)
    val svg = Graphviz.render("digraph { A }", config)
    assert(svg.contains("font-size=\"18"))
  }

  test("render: custom default node shape in config") {
    val config = GraphvizConfig(defaultNodeShape = "box", engine = LayoutEngine.Neato)
    val svg = Graphviz.render("digraph { A }", config)
    assert(svg.contains("<rect"))
  }

  // --- Complex graphs ---

  test("render: multiple edges") {
    val svg = render("digraph { A -> B; A -> C; B -> D; C -> D }")
    // 4 edge paths + 1 arrowhead path in <defs> = 5 total <path> elements
    val pathCount = "<path".r.findAllMatchIn(svg).size
    assertEquals(pathCount, 5)
  }

  test("render: graph with node label override") {
    val svg = render("""digraph { A [label="Alpha"]; B [label="Beta"]; A -> B }""")
    assert(svg.contains(">Alpha</text>"))
    assert(svg.contains(">Beta</text>"))
  }

  test("render: via Graphviz.render entry point") {
    val svg = render("digraph { X -> Y }")
    assert(svg.contains("<svg"))
    assert(svg.contains("<path"))
    assert(svg.contains(">X</text>"))
    assert(svg.contains(">Y</text>"))
  }

  test("render: via Graphviz.parse entry point") {
    val ast = Graphviz.parse("digraph { A -> B }")
    assert(ast.isDirected)
    assertEquals(ast.stmts.size, 1)
  }
}
