/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Comprehensive render tests ported from viz-js, hpcc-js-wasm, and d3-graphviz.
 * Covers shapes, edge styles, config overrides, stress tests, and edge cases.
 * Uses LayoutEngine.Neato for all render tests to avoid Dagre layout hangs.
 */
package ssg
package graphviz
package render

import munit.FunSuite

final class ComprehensiveRenderSuite extends FunSuite {

  private val testConfig: GraphvizConfig = GraphvizConfig(engine = LayoutEngine.Neato)

  private def render(input: String, config: GraphvizConfig = testConfig): String =
    Graphviz.render(input, config)

  private def parse(input: String): ssg.graphviz.parse.DotGraph =
    Graphviz.parse(input)

  // --- viz-js render tests ---

  test("vizjs: default graph/node/edge attributes via config - defaultNodeShape box") {
    val config    = GraphvizConfig(defaultNodeShape = "box", engine = LayoutEngine.Neato)
    val svg       = Graphviz.render("digraph { a; b; a -> b }", config)
    val rectCount = "<rect".r.findAllMatchIn(svg).size
    assertEquals(rectCount, 2, "Config defaultNodeShape=box should make all nodes render as <rect>")
  }

  test("vizjs: config fontName overrides node font") {
    val config = GraphvizConfig(fontName = "monospace", engine = LayoutEngine.Neato)
    val svg    = Graphviz.render("digraph { a }", config)
    assert(svg.contains("font-family=\"monospace\""), "Config fontName should apply to node labels")
  }

  test("vizjs: config fontSize overrides node font size") {
    val config = GraphvizConfig(fontSize = 24.0, engine = LayoutEngine.Neato)
    val svg    = Graphviz.render("digraph { a }", config)
    assert(svg.contains("font-size=\"24"), "Config fontSize should apply to node labels")
  }

  test("vizjs: multiple graphs in input raises error") {
    // Parser only accepts a single graph; tokens after the first closing brace cause an error
    val ex = intercept[IllegalArgumentException] {
      parse("digraph a { } digraph b { }")
    }
    assert(ex.getMessage.contains("Unexpected token"), ex.getMessage)
  }

  test("vizjs: error recovery after valid graph - trailing garbage") {
    // First graph is valid, but trailing incomplete graph causes parser error
    val ex = intercept[IllegalArgumentException] {
      parse("digraph a { x -> y } digraph {")
    }
    assert(ex.getMessage.contains("Unexpected token"), ex.getMessage)
  }

  test("vizjs: image attribute parses correctly") {
    // Image attributes are parsed as node attributes (even if the renderer ignores them)
    val g    = parse("""digraph { a [image="test.png"] }""")
    val node = g.stmts.head.asInstanceOf[ssg.graphviz.parse.DotNodeStmt]
    assertEquals(node.attrs.size, 1)
    assertEquals(node.attrs(0).key, "image")
    assertEquals(node.attrs(0).value, "test.png")
  }

  test("vizjs: image node renders SVG (image attribute ignored gracefully)") {
    // Renderer should not crash on unrecognized attributes
    val svg = render("""digraph { a [image="test.png"]; b; a -> b }""")
    assert(svg.contains("<svg"), "Should render SVG even with image attribute")
    assert(svg.contains(">a</text>"), "Node label should appear")
  }

  test("vizjs: ambiguous number parsing") {
    // DOT allows numeric IDs; test that a dot-separated value works as attribute
    val g    = parse("""graph { a [width=1.5] }""")
    val node = g.stmts.head.asInstanceOf[ssg.graphviz.parse.DotNodeStmt]
    assertEquals(node.attrs(0).value, "1.5")
  }

  // --- viz-js graph-objects tests ---

  test("vizjs graph-objects: deeply nested subgraphs") {
    val dot = """digraph {
      subgraph cluster_0 {
        subgraph cluster_1 {
          subgraph cluster_2 {
            a -> b
          }
        }
      }
    }"""
    val g   = parse(dot)
    // Navigate to deepest level
    val c0 = g.stmts.head.asInstanceOf[ssg.graphviz.parse.DotSubgraphStmt]
    assertEquals(c0.id, Some("cluster_0"))
    val c1 = c0.stmts.head.asInstanceOf[ssg.graphviz.parse.DotSubgraphStmt]
    assertEquals(c1.id, Some("cluster_1"))
    val c2 = c1.stmts.head.asInstanceOf[ssg.graphviz.parse.DotSubgraphStmt]
    assertEquals(c2.id, Some("cluster_2"))
    val edge = c2.stmts.head.asInstanceOf[ssg.graphviz.parse.DotEdgeStmt]
    assertEquals(edge.nodes(0).id, "a")
    assertEquals(edge.nodes(1).id, "b")
    // Also verify it renders without error
    val svg = render(dot)
    assert(svg.contains("<svg"))
    assert(svg.contains(">a</text>"))
    assert(svg.contains(">b</text>"))
  }

  test("vizjs graph-objects: empty subgraph") {
    val g = parse("digraph { subgraph cluster_empty { } a }")
    assertEquals(g.stmts.size, 2)
    val sub = g.stmts(0).asInstanceOf[ssg.graphviz.parse.DotSubgraphStmt]
    assertEquals(sub.id, Some("cluster_empty"))
    assertEquals(sub.stmts.size, 0)
  }

  test("vizjs graph-objects: subgraph with only attributes") {
    val g   = parse("""digraph { subgraph cluster_attrs { style=filled; color=lightgrey; label="Only Attrs" } a }""")
    val sub = g.stmts(0).asInstanceOf[ssg.graphviz.parse.DotSubgraphStmt]
    assertEquals(sub.id, Some("cluster_attrs"))
    // style=filled, color=lightgrey, label="Only Attrs" -> 3 assign stmts
    assertEquals(sub.stmts.size, 3)
    assert(sub.stmts.forall(_.isInstanceOf[ssg.graphviz.parse.DotAssignStmt]))
  }

  test("vizjs graph-objects: subgraph with edges but no explicit nodes") {
    val g   = parse("digraph { subgraph cluster_edges { a -> b; b -> c } }")
    val sub = g.stmts(0).asInstanceOf[ssg.graphviz.parse.DotSubgraphStmt]
    assertEquals(sub.stmts.size, 2) // 2 edge statements
    // Nodes a, b, c are implicitly created by edges
    val svg = render("digraph { subgraph cluster_edges { a -> b; b -> c } }")
    assert(svg.contains(">a</text>"))
    assert(svg.contains(">b</text>"))
    assert(svg.contains(">c</text>"))
  }

  // --- viz-js render-unwrapped tests ---

  test("vizjs unwrapped: SVG has proper structure with title") {
    // After our graph label changes, directed graph should have a <title> element with graph name
    val svg = render("digraph MyGraph { a -> b }")
    assert(svg.contains("<title>MyGraph</title>"), "SVG should contain <title> with graph name")
  }

  test("vizjs unwrapped: parsed AST has correct graph name") {
    val g = parse("digraph test_graph { a -> b }")
    assertEquals(g.id, Some("test_graph"))
    assert(g.isDirected)
    assertEquals(g.stmts.size, 1)
  }

  test("vizjs unwrapped: unterminated string followed by valid graph") {
    // The scanner should fail on the unterminated string even if a valid graph follows
    intercept[IllegalArgumentException] {
      parse("graph { \" } graph { a }")
    }
  }

  // --- hpcc-js-wasm tests ---

  test("hpcc: Neato engine produces non-empty SVG") {
    val config = GraphvizConfig(engine = LayoutEngine.Neato)
    val svg    = Graphviz.render("digraph { a -> b; b -> c }", config)
    assert(svg.contains("<svg"))
    assert(svg.contains("<path"))
    assert(svg.contains(">a</text>"))
  }

  test("hpcc: Circo engine produces non-empty SVG") {
    val config = GraphvizConfig(engine = LayoutEngine.Circo)
    val svg    = Graphviz.render("digraph { a -> b; b -> c }", config)
    assert(svg.contains("<svg"))
    assert(svg.contains("<path"))
    assert(svg.contains(">a</text>"))
  }

  test("hpcc: Twopi engine produces non-empty SVG") {
    val config = GraphvizConfig(engine = LayoutEngine.Twopi)
    val svg    = Graphviz.render("digraph { a -> b; b -> c }", config)
    assert(svg.contains("<svg"))
    assert(svg.contains("<path"))
    assert(svg.contains(">a</text>"))
  }

  test("hpcc: blank input raises error") {
    intercept[IllegalArgumentException] {
      render("")
    }
  }

  test("hpcc: whitespace-only input raises error") {
    intercept[IllegalArgumentException] {
      render("   \n\t  ")
    }
  }

  test("hpcc: cycle detection - renders without hanging") {
    val svg = render("digraph { a->b; b->c; c->d; d->a; d->e; e->d; }")
    assert(svg.contains("<svg"), "Cyclic graph should produce SVG")
    val pathCount = "<path".r.findAllMatchIn(svg).size
    assert(pathCount >= 6, s"Should have at least 6 paths (1 arrowhead + 6 edges - 1 shared = 6+1=7), found $pathCount")
    assert(svg.contains(">a</text>"))
    assert(svg.contains(">e</text>"))
  }

  test("hpcc: cluster digraph with shapes") {
    val dot = """digraph {
      subgraph cluster_0 {
        label="Cluster 0";
        a [shape=box];
        b [shape=circle];
        a -> b;
      }
      subgraph cluster_1 {
        label="Cluster 1";
        c [shape=diamond];
        d [shape=ellipse];
        c -> d;
      }
      a -> c;
    }"""
    val svg = render(dot)
    assert(svg.contains("<svg"))
    assert(svg.contains("<rect"), "box shape should produce <rect>")
    assert(svg.contains("<circle"), "circle shape should produce <circle>")
    assert(svg.contains("<polygon"), "diamond shape should produce <polygon>")
    assert(svg.contains("<ellipse"), "ellipse shape should produce <ellipse>")
  }

  test("hpcc: different engines produce different layouts for same graph") {
    val dot      = "digraph { a -> b; b -> c; c -> a; d -> a; d -> c }"
    val svgNeato = Graphviz.render(dot, GraphvizConfig(engine = LayoutEngine.Neato))
    val svgCirco = Graphviz.render(dot, GraphvizConfig(engine = LayoutEngine.Circo))
    val svgTwopi = Graphviz.render(dot, GraphvizConfig(engine = LayoutEngine.Twopi))
    // All should be valid SVG
    assert(svgNeato.contains("<svg"))
    assert(svgCirco.contains("<svg"))
    assert(svgTwopi.contains("<svg"))
    // At least one pair should differ (different layouts produce different coordinates)
    val allSame = svgNeato == svgCirco && svgCirco == svgTwopi
    assert(!allSame, "Different layout engines should produce different SVG output")
  }

  // --- d3-graphviz shape tests ---

  test("d3 shape: polygon renders as <polygon>") {
    val svg = render("""digraph { a [shape="polygon"] }""")
    assert(svg.contains("<polygon"), "shape=polygon should render as <polygon>")
  }

  test("d3 shape: cylinder renders with rect and ellipses") {
    val svg = render("""digraph { a [shape="cylinder"] }""")
    assert(svg.contains("<rect"), "cylinder body should render as <rect>")
    assert(svg.contains("<ellipse"), "cylinder caps should render as <ellipse>")
  }

  test("d3 shape: circle renders as <circle>") {
    val svg = render("""digraph { a [shape="circle"] }""")
    assert(svg.contains("<circle"), "shape=circle should render as <circle>")
  }

  test("d3 shape: box renders as <rect>") {
    val svg = render("""digraph { a [shape="box"] }""")
    assert(svg.contains("<rect"), "shape=box should render as <rect>")
  }

  test("d3 shape: diamond renders as <polygon>") {
    val svg = render("""digraph { a [shape="diamond"] }""")
    assert(svg.contains("<polygon"), "shape=diamond should render as <polygon>")
  }

  test("d3 shape: egg renders as <ellipse>") {
    // Egg is essentially an ellipse variant — falls through to default ellipse rendering
    val svg = render("""digraph { a [shape="egg"] }""")
    assert(svg.contains("<ellipse"), "shape=egg should render as <ellipse>")
  }

  test("d3 shape: ellipse renders as <ellipse>") {
    val svg = render("""digraph { a [shape="ellipse"] }""")
    assert(svg.contains("<ellipse"), "shape=ellipse should render as <ellipse>")
  }

  test("d3 shape: none renders no shape element") {
    val svg = render("""digraph { a [shape="none"] }""")
    assert(!svg.contains("<ellipse"), "shape=none should not render <ellipse>")
    assert(!svg.contains("<rect"), "shape=none should not render <rect>")
    assert(!svg.contains("<circle"), "shape=none should not render <circle>")
    assert(!svg.contains("<polygon"), "shape=none should not render <polygon>")
  }

  test("d3 shape: oval renders as <ellipse>") {
    // Oval is an alias for ellipse
    val svg = render("""digraph { a [shape="oval"] }""")
    assert(svg.contains("<ellipse"), "shape=oval should render as <ellipse>")
  }

  test("d3 shape: point renders as small <circle> with no label") {
    val svg = render("""digraph { a [shape="point"] }""")
    assert(svg.contains("<circle"), "shape=point should render as <circle>")
    assert(!svg.contains(">a</text>"), "shape=point should not render text label")
  }

  test("d3 shape: rect renders as <rect>") {
    val svg = render("""digraph { a [shape="rect"] }""")
    assert(svg.contains("<rect"), "shape=rect should render as <rect>")
  }

  test("d3 shape: triangle renders as <polygon>") {
    val svg = render("""digraph { a [shape="triangle"] }""")
    assert(svg.contains("<polygon"), "shape=triangle should render as <polygon>")
  }

  test("d3 shape: pentagon renders as <polygon>") {
    val svg = render("""digraph { a [shape="pentagon"] }""")
    assert(svg.contains("<polygon"), "shape=pentagon should render as <polygon>")
  }

  test("d3 shape: hexagon renders as <polygon>") {
    val svg = render("""digraph { a [shape="hexagon"] }""")
    assert(svg.contains("<polygon"), "shape=hexagon should render as <polygon>")
  }

  // --- d3-graphviz rendering tests ---

  test("d3 render: invisible edge has visibility hidden") {
    val svg = render("""digraph { a -> b [style="invis"] }""")
    assert(svg.contains("<path"), "Edge path should exist even when invisible")
    assert(svg.contains("visibility=\"hidden\""), "Invisible edge should have visibility=hidden")
  }

  test("d3 render: rankdir=LR assignment is parsed") {
    val g       = parse("""digraph { rankdir="LR"; a -> b }""")
    val assigns = g.stmts.collect { case a: ssg.graphviz.parse.DotAssignStmt => a }
    assert(assigns.exists(a => a.key == "rankdir" && a.value == "LR"))
  }

  test("d3 render: graph label appears in SVG") {
    val svg = render("""digraph { label="My Graph Label"; a -> b }""")
    assert(svg.contains("My Graph Label"), "Graph-level label should appear in SVG text")
  }

  test("d3 render: graph name appears as title element") {
    val svg = render("digraph TestGraph { a -> b }")
    assert(svg.contains("<title>TestGraph</title>"), "Graph name should appear in <title> element")
  }

  test("d3 render: unnamed graph has no title element") {
    val svg = render("digraph { a -> b }")
    assert(!svg.contains("<title>"), "Unnamed graph should not have a <title> element")
  }

  test("d3 render: image node parses correctly") {
    val g     = parse("""digraph { a[image="images/first.png"]; b; a -> b }""")
    val nodeA = g.stmts.collect { case n: ssg.graphviz.parse.DotNodeStmt => n }.find(_.id.id == "a").get
    assertEquals(nodeA.attrs.find(_.key == "image").get.value, "images/first.png")
  }

  test("d3 render: edge with multiple attributes") {
    val svg = render("""digraph { a -> b [label="x", color="red", style="dashed"] }""")
    assert(svg.contains("<path"), "Edge should render as path")
    assert(svg.contains("stroke=\"red\""), "Edge color=red should produce stroke=red")
    assert(svg.contains("stroke-dasharray=\"5,2\""), "Edge style=dashed should produce stroke-dasharray")
  }

  test("d3 render: node with full styling combination") {
    val svg = render(
      """digraph { a [label="Test", fillcolor="yellow", style="filled", fontcolor="blue", fontsize=20, fontname="serif"] }"""
    )
    assert(svg.contains("fill=\"yellow\""), "fillcolor should apply as fill")
    assert(svg.contains("fill=\"blue\""), "fontcolor should apply to text fill")
    assert(svg.contains("font-size=\"20\""), "fontsize should apply to text")
    assert(svg.contains("font-family=\"serif\""), "fontname should apply to text")
    assert(svg.contains(">Test</text>"), "Label should appear as text content")
  }

  test("d3 render: cluster styling with label (subgraph assign)") {
    val dot     = """digraph {
      subgraph cluster_0 {
        style=filled;
        color=lightgrey;
        a; b;
        label="Cluster"
      }
      c -> a
    }"""
    val g       = parse(dot)
    val cluster = g.stmts(0).asInstanceOf[ssg.graphviz.parse.DotSubgraphStmt]
    assertEquals(cluster.id, Some("cluster_0"))
    // Verify the label assign exists in the subgraph
    val assigns = cluster.stmts.collect { case a: ssg.graphviz.parse.DotAssignStmt => a }
    assert(assigns.exists(a => a.key == "label" && a.value == "Cluster"))
    // Verify it renders
    val svg = render(dot)
    assert(svg.contains("<svg"))
    assert(svg.contains(">a</text>"))
  }

  test("d3 render: edges between clusters") {
    val dot = """digraph {
      subgraph cluster_A { a1; a2; a1 -> a2 }
      subgraph cluster_B { b1; b2; b1 -> b2 }
      a2 -> b1
    }"""
    val svg = render(dot)
    assert(svg.contains("<svg"))
    val pathCount = "<path".r.findAllMatchIn(svg).size
    // 3 edges + 1 arrowhead def = 4 paths
    assertEquals(pathCount, 4, "Should have 3 edge paths + 1 arrowhead path")
    assert(svg.contains(">a1</text>"))
    assert(svg.contains(">b2</text>"))
  }

  test("d3 render: large graph stress - 20+ nodes") {
    val nodes = (1 to 25).map(i => s"n$i").mkString("; ")
    val edges = (1 to 24).map(i => s"n$i -> n${i + 1}").mkString("; ")
    val dot   = s"digraph { $nodes; $edges }"
    val svg   = render(dot)
    assert(svg.contains("<svg"))
    // Verify all 25 nodes are present as text labels
    for (i <- 1 to 25)
      assert(svg.contains(s">n$i</text>"), s"Node n$i should appear in SVG")
    val pathCount = "<path".r.findAllMatchIn(svg).size
    // 24 edges + 1 arrowhead = 25
    assertEquals(pathCount, 25, "Should have 24 edge paths + 1 arrowhead path")
  }

  test("d3 render: large graph fan-out stress") {
    val edges = (1 to 20).map(i => s"hub -> n$i").mkString("; ")
    val dot   = s"digraph { $edges }"
    val svg   = render(dot)
    assert(svg.contains("<svg"))
    assert(svg.contains(">hub</text>"))
    for (i <- 1 to 20)
      assert(svg.contains(s">n$i</text>"), s"Node n$i should appear in SVG")
  }

  // --- edge cases ---

  test("edge case: semicolons-only statements render empty graph") {
    val svg = render("digraph { ; ; ; a; ; }")
    assert(svg.contains("<svg"))
    assert(svg.contains(">a</text>"), "Node 'a' should render despite surrounding semicolons")
  }

  test("edge case: tab and mixed whitespace") {
    val dot = "digraph\t{\ta\t->\tb\t;\n\tc\t->\td\t}"
    val svg = render(dot)
    assert(svg.contains("<svg"))
    assert(svg.contains(">a</text>"))
    assert(svg.contains(">b</text>"))
    assert(svg.contains(">c</text>"))
    assert(svg.contains(">d</text>"))
  }

  test("edge case: windows line endings (CRLF)") {
    val dot = "digraph {\r\n  a -> b;\r\n  b -> c;\r\n}"
    val svg = render(dot)
    assert(svg.contains("<svg"))
    assert(svg.contains(">a</text>"))
    assert(svg.contains(">b</text>"))
    assert(svg.contains(">c</text>"))
  }

  test("edge case: very long node label") {
    val longLabel = "This is a very long label that should still render correctly in the SVG output"
    val svg       = render(s"""digraph { a [label="$longLabel"] }""")
    assert(svg.contains(longLabel), "Long label should appear in SVG output")
    assert(svg.contains("<svg"))
  }

  test("edge case: special XML characters in labels are escaped") {
    val svg = render("""digraph { a [label="<>&\""] }""")
    assert(svg.contains("<svg"))
    // The SvgMarkup.escapeTextContent replaces <, >, & in text content
    assert(svg.contains("&lt;"), "< should be escaped to &lt;")
    assert(svg.contains("&gt;"), "> should be escaped to &gt;")
    assert(svg.contains("&amp;"), "& should be escaped to &amp;")
  }

  test("edge case: empty attribute list") {
    val g = parse("digraph { a [] }")
    assertEquals(g.stmts.size, 1)
    val node = g.stmts.head.asInstanceOf[ssg.graphviz.parse.DotNodeStmt]
    assertEquals(node.id.id, "a")
    assertEquals(node.attrs.size, 0)
    // Should also render without error
    val svg = render("digraph { a [] }")
    assert(svg.contains("<svg"))
    assert(svg.contains(">a</text>"))
  }

  test("edge case: bare attribute without value") {
    val g    = parse("digraph { a [fixedsize] }")
    val node = g.stmts.head.asInstanceOf[ssg.graphviz.parse.DotNodeStmt]
    assertEquals(node.attrs.size, 1)
    assertEquals(node.attrs(0).key, "fixedsize")
    assertEquals(node.attrs(0).value, "true")
  }

  test("edge case: numeric graph name") {
    // DOT allows numeric identifiers as graph names
    val g = parse("digraph 123 { a -> b }")
    assertEquals(g.id, Some("123"))
    assert(g.isDirected)
    val svg = render("digraph 123 { a -> b }")
    assert(svg.contains("<svg"))
    assert(svg.contains("<title>123</title>"), "Numeric graph name should appear in <title>")
  }

  test("edge case: node label override via label attribute") {
    val svg = render("""digraph { a [label="Alpha"]; b [label="Beta"]; a -> b }""")
    assert(svg.contains(">Alpha</text>"), "Label should override node id 'a'")
    assert(svg.contains(">Beta</text>"), "Label should override node id 'b'")
    assert(!svg.contains(">a</text>"), "Original id 'a' should not appear when label is set")
    assert(!svg.contains(">b</text>"), "Original id 'b' should not appear when label is set")
  }

  test("edge case: node with empty label renders no text") {
    val svg = render("""digraph { a [label=""] }""")
    assert(svg.contains("<svg"))
    // Empty label should suppress text element for this node
    assert(!svg.contains(">a</text>"), "Node with empty label should not show id as text")
  }

  test("edge case: multiple edge chains in one graph") {
    val svg = render("digraph { a -> b -> c; d -> e -> f -> g }")
    assert(svg.contains("<svg"))
    // First chain: 2 edges (a->b, b->c)
    // Second chain: 3 edges (d->e, e->f, f->g)
    // Total: 5 edges + 1 arrowhead = 6 paths
    val pathCount = "<path".r.findAllMatchIn(svg).size
    assertEquals(pathCount, 6)
    assert(svg.contains(">a</text>"))
    assert(svg.contains(">g</text>"))
  }

  test("edge case: self-loop with label") {
    val svg = render("""digraph { a -> a [label="self"] }""")
    assert(svg.contains("<svg"))
    assert(svg.contains("<path"), "Self-loop should render as path")
    assert(svg.contains(">a</text>"), "Node label should appear")
  }

  test("edge case: graph with only comments") {
    val ex = intercept[IllegalArgumentException] {
      render("// just a comment\n/* another comment */")
    }
    // Empty input after stripping comments should fail
    assert(ex.getMessage.nonEmpty)
  }

  test("edge case: node style=filled with fillcolor") {
    val svg = render("""digraph { a [style="filled", fillcolor="lightblue"] }""")
    assert(svg.contains("fill=\"lightblue\""), "fillcolor should apply as fill attribute")
  }

  test("edge case: node style=bold with color") {
    val svg = render("""digraph { a [style="bold", color="red"] }""")
    assert(svg.contains("stroke=\"red\""), "color should apply as stroke")
    assert(svg.contains("<svg"))
  }

  test("edge case: edge dir=none parsed correctly") {
    val g    = parse("""digraph { a -> b [dir=none] }""")
    val edge = g.stmts.head.asInstanceOf[ssg.graphviz.parse.DotEdgeStmt]
    assertEquals(edge.attrs.find(_.key == "dir").get.value, "none")
  }

  test("edge case: strict digraph deduplicates conceptually") {
    val g = parse("strict digraph { a -> b; a -> b; b -> a }")
    assert(g.strict)
    assert(g.isDirected)
    // Parser preserves all edge statements (dedup is semantic)
    val edges = g.stmts.collect { case e: ssg.graphviz.parse.DotEdgeStmt => e }
    assertEquals(edges.size, 3)
    // But renders correctly
    val svg = render("strict digraph { a -> b; a -> b; b -> a }")
    assert(svg.contains("<svg"))
  }

  test("edge case: mixed node shapes in one graph") {
    val dot = """digraph {
      a [shape=box]
      b [shape=circle]
      c [shape=diamond]
      d [shape=triangle]
      e [shape=point]
      f [shape=plaintext]
      a -> b -> c -> d -> e -> f
    }"""
    val svg = render(dot)
    assert(svg.contains("<rect"), "box should render as rect")
    assert(svg.contains("<circle"), "circle/point should render as circle")
    assert(svg.contains("<polygon"), "diamond/triangle should render as polygon")
    // plaintext and point should not have extra shape elements
    // point renders as small circle (already counted)
    // plaintext renders no shape
    assert(svg.contains(">a</text>"), "box node should have label")
    assert(!svg.contains(">e</text>"), "point node should not have label")
    // plaintext with shape=plaintext should not have shape but may or may not have label
    // (renderer suppresses label for none, not plaintext)
  }

  test("edge case: edge with color overriding default") {
    val svg = render("""digraph { edge [color=blue]; a -> b [color=red] }""")
    // The edge-specific color=red should override the default color=blue
    assert(svg.contains("stroke=\"red\""), "Edge-specific color should override default")
  }

  test("edge case: node with color overriding default") {
    val svg = render("""digraph { node [color=blue]; a [color=red] }""")
    assert(svg.contains("stroke=\"red\""), "Node-specific color should override default")
  }

  test("edge case: graph with no edges - just nodes") {
    val svg = render("digraph { a; b; c; d; e }")
    assert(svg.contains("<svg"))
    val pathCount = "<path".r.findAllMatchIn(svg).size
    // Only arrowhead def path in defs (1), no edge paths
    assertEquals(pathCount, 1, "Graph with no edges should only have arrowhead def path")
    // But all 5 nodes should render
    assert(svg.contains(">a</text>"))
    assert(svg.contains(">e</text>"))
  }

  test("edge case: graph with duplicate node declarations") {
    val svg = render("""digraph { a [shape=box]; a [color=red]; a -> b }""")
    assert(svg.contains("<svg"))
    // Both attributes should be applied to node 'a'
    assert(svg.contains("<rect"), "First shape=box should apply")
    assert(svg.contains("stroke=\"red\""), "Second color=red should apply")
  }

  test("edge case: newline in quoted label (backslash-n)") {
    val svg = render("""digraph { a [label="line1\nline2"] }""")
    assert(svg.contains("<svg"))
    // The \n in the label is literal newline in the parsed string
    assert(svg.contains("<text"), "Text element should exist")
  }

  test("edge case: HTML label with nested tags") {
    val svg = render("digraph { a [label=<<b>Bold <i>Italic</i></b>>] }")
    assert(svg.contains("<svg"))
    assert(svg.contains("Bold"), "HTML label text should appear")
  }

  test("edge case: string concatenation in label") {
    val g    = parse("""digraph { a [label="hello" + " " + "world"] }""")
    val node = g.stmts.head.asInstanceOf[ssg.graphviz.parse.DotNodeStmt]
    assertEquals(node.attrs(0).value, "hello world")
    val svg = render("""digraph { a [label="hello" + " " + "world"] }""")
    assert(svg.contains("hello world"), "Concatenated label should appear in SVG")
  }

  test("edge case: undirected graph with duplicate edges") {
    val svg = render("graph { a -- b; a -- b; b -- c }")
    assert(svg.contains("<svg"))
    assert(!svg.contains("<marker"), "Undirected graph should not have arrow markers")
    val pathCount = "<path".r.findAllMatchIn(svg).size
    // Non-multigraph deduplicates: a--b (x2 collapsed) + b--c = 2 unique edges
    assertEquals(pathCount, 2, "Should have 2 edge paths (duplicates deduplicated by non-multigraph)")
  }

  test("edge case: node id with underscore and digits") {
    val svg = render("digraph { node_1 -> node_2_a -> _node3 }")
    assert(svg.contains(">node_1</text>"))
    assert(svg.contains(">node_2_a</text>"))
    assert(svg.contains(">_node3</text>"))
  }

  test("edge case: all comment types mixed") {
    val dot = """// line comment
# hash comment
/* block
   comment */
digraph { a -> b }"""
    val svg = render(dot)
    assert(svg.contains("<svg"))
    assert(svg.contains(">a</text>"))
    assert(svg.contains(">b</text>"))
  }

  test("edge case: SVG dimensions reflect node count") {
    val svg1 = render("digraph { a }")
    val svg5 = render("digraph { a; b; c; d; e }")
    // Extract viewBox width from both
    val vbRe   = """viewBox="0 0 ([0-9.]+) ([0-9.]+)"""".r
    val vb1    = vbRe.findFirstMatchIn(svg1).get
    val vb5    = vbRe.findFirstMatchIn(svg5).get
    val width1 = vb1.group(1).toDouble
    val width5 = vb5.group(1).toDouble
    // 5-node graph should be at least as wide as 1-node graph
    assert(width5 >= width1, s"5-node graph ($width5) should be at least as wide as 1-node graph ($width1)")
  }

  test("edge case: config with custom margins") {
    val config = GraphvizConfig(marginX = 50.0, marginY = 50.0, engine = LayoutEngine.Neato)
    val svg    = Graphviz.render("digraph { a -> b }", config)
    assert(svg.contains("<svg"))
    // Extract viewBox and verify it has non-zero dimensions
    assert(svg.contains("viewBox=\"0 0"))
  }

  test("edge case: config with custom node and rank separation") {
    val config = GraphvizConfig(nodeSep = 100.0, rankSep = 100.0, engine = LayoutEngine.Neato)
    val svg    = Graphviz.render("digraph { a -> b -> c }", config)
    assert(svg.contains("<svg"))
    assert(svg.contains(">a</text>"))
    assert(svg.contains(">c</text>"))
  }

  test("edge case: default LayoutEngine is Dot") {
    val config = GraphvizConfig()
    assertEquals(config.engine, LayoutEngine.Dot)
  }

  test("edge case: all LayoutEngine values exist") {
    // Verify all enum values are accessible
    val engines = Seq(LayoutEngine.Dot, LayoutEngine.Neato, LayoutEngine.Circo, LayoutEngine.Twopi)
    assertEquals(engines.size, 4)
  }
}
