/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package graphviz
package parse

import munit.FunSuite

final class DotParserSuite extends FunSuite {

  private def parse(input: String): DotGraph = {
    val tokens = DotScanner(input).scan()
    DotParser(tokens).parse()
  }

  // --- Basic graph types ---

  test("parse: simple digraph") {
    val g = parse("digraph { A -> B }")
    assert(g.isDirected)
    assertEquals(g.graphType, DotGraphType.Digraph)
    assert(!g.strict)
    assertEquals(g.id, None)
    assertEquals(g.stmts.size, 1)
    val edge = g.stmts.head.asInstanceOf[DotEdgeStmt]
    assertEquals(edge.nodes.size, 2)
    assertEquals(edge.nodes(0).id, "A")
    assertEquals(edge.nodes(1).id, "B")
  }

  test("parse: simple graph") {
    val g = parse("graph { A -- B }")
    assert(!g.isDirected)
    assertEquals(g.graphType, DotGraphType.Graph)
    assertEquals(g.stmts.size, 1)
    val edge = g.stmts.head.asInstanceOf[DotEdgeStmt]
    assertEquals(edge.nodes.size, 2)
    assertEquals(edge.nodes(0).id, "A")
    assertEquals(edge.nodes(1).id, "B")
  }

  test("parse: strict digraph") {
    val g = parse("strict digraph { A -> B }")
    assert(g.strict)
    assert(g.isDirected)
    assertEquals(g.stmts.size, 1)
  }

  test("parse: named graph") {
    val g = parse("digraph G { A -> B }")
    assertEquals(g.id, Some("G"))
    assert(g.isDirected)
  }

  test("parse: empty graph") {
    val g = parse("digraph {}")
    assert(g.isDirected)
    assertEquals(g.stmts.size, 0)
  }

  // --- Node statements ---

  test("parse: node with attributes") {
    val g = parse("""digraph { A [shape=box, label="Hello"] }""")
    assertEquals(g.stmts.size, 1)
    val node = g.stmts.head.asInstanceOf[DotNodeStmt]
    assertEquals(node.id.id, "A")
    assertEquals(node.attrs.size, 2)
    assertEquals(node.attrs(0), DotAttr("shape", "box"))
    assertEquals(node.attrs(1), DotAttr("label", "Hello"))
  }

  test("parse: node with quoted id") {
    val g    = parse("""digraph { "node 1" -> "node 2" }""")
    val edge = g.stmts.head.asInstanceOf[DotEdgeStmt]
    assertEquals(edge.nodes(0).id, "node 1")
    assertEquals(edge.nodes(1).id, "node 2")
  }

  test("parse: node with port in node stmt") {
    val g    = parse("digraph { A:port1 [shape=box] }")
    val node = g.stmts.head.asInstanceOf[DotNodeStmt]
    assertEquals(node.id.id, "A")
    assertEquals(node.id.port, Some("port1"))
  }

  test("parse: node with compass in node stmt") {
    val g    = parse("digraph { A:n [shape=box] }")
    val node = g.stmts.head.asInstanceOf[DotNodeStmt]
    assertEquals(node.id.id, "A")
    assertEquals(node.id.compass, Some("n"))
  }

  // --- Edge statements ---

  test("parse: edge chain") {
    val g = parse("digraph { A -> B -> C -> D }")
    assertEquals(g.stmts.size, 1)
    val edge = g.stmts.head.asInstanceOf[DotEdgeStmt]
    assertEquals(edge.nodes.size, 4)
    assertEquals(edge.nodes.map(_.id), Seq("A", "B", "C", "D"))
  }

  test("parse: edge with attributes") {
    val g    = parse("""digraph { A -> B [label="edge", color=red] }""")
    val edge = g.stmts.head.asInstanceOf[DotEdgeStmt]
    assertEquals(edge.attrs.size, 2)
    assertEquals(edge.attrs(0), DotAttr("label", "edge"))
    assertEquals(edge.attrs(1), DotAttr("color", "red"))
  }

  test("parse: undirected edge") {
    val g = parse("graph { A -- B }")
    assert(!g.isDirected)
    val edge = g.stmts.head.asInstanceOf[DotEdgeStmt]
    assertEquals(edge.nodes.size, 2)
  }

  // --- Attribute statements ---

  test("parse: graph attributes") {
    val g    = parse("digraph { graph [rankdir=LR] }")
    val attr = g.stmts.head.asInstanceOf[DotAttrStmt]
    assertEquals(attr.target, DotAttrTarget.Graph)
    assertEquals(attr.attrs.size, 1)
    assertEquals(attr.attrs(0), DotAttr("rankdir", "LR"))
  }

  test("parse: default node attributes") {
    val g    = parse("digraph { node [shape=box] }")
    val attr = g.stmts.head.asInstanceOf[DotAttrStmt]
    assertEquals(attr.target, DotAttrTarget.Node)
    assertEquals(attr.attrs.head.key, "shape")
    assertEquals(attr.attrs.head.value, "box")
  }

  test("parse: default edge attributes") {
    val g    = parse("digraph { edge [color=blue] }")
    val attr = g.stmts.head.asInstanceOf[DotAttrStmt]
    assertEquals(attr.target, DotAttrTarget.Edge)
    assertEquals(attr.attrs.head.key, "color")
    assertEquals(attr.attrs.head.value, "blue")
  }

  test("parse: assignment statement") {
    val g      = parse("digraph { rankdir=LR }")
    val assign = g.stmts.head.asInstanceOf[DotAssignStmt]
    assertEquals(assign.key, "rankdir")
    assertEquals(assign.value, "LR")
  }

  // --- Subgraphs ---

  test("parse: named subgraph") {
    val g = parse("digraph { subgraph cluster_0 { A; B } }")
    assertEquals(g.stmts.size, 1)
    val sub = g.stmts.head.asInstanceOf[DotSubgraphStmt]
    assertEquals(sub.id, Some("cluster_0"))
    assertEquals(sub.stmts.size, 2)
  }

  test("parse: anonymous subgraph") {
    val g = parse("digraph { { A; B } }")
    assertEquals(g.stmts.size, 1)
    val sub = g.stmts.head.asInstanceOf[DotSubgraphStmt]
    assertEquals(sub.id, None)
    assertEquals(sub.stmts.size, 2)
  }

  test("parse: nested subgraphs") {
    val g        = parse("digraph { subgraph cluster_0 { subgraph cluster_1 { A } } }")
    val outerSub = g.stmts.head.asInstanceOf[DotSubgraphStmt]
    assertEquals(outerSub.id, Some("cluster_0"))
    val innerSub = outerSub.stmts.head.asInstanceOf[DotSubgraphStmt]
    assertEquals(innerSub.id, Some("cluster_1"))
    val node = innerSub.stmts.head.asInstanceOf[DotNodeStmt]
    assertEquals(node.id.id, "A")
  }

  test("parse: subgraph in edge") {
    val g = parse("digraph { subgraph { A; B } -> C }")
    assertEquals(g.stmts.size, 1)
    val edge = g.stmts.head.asInstanceOf[DotEdgeStmt]
    assertEquals(edge.nodes.size, 2)
    assertEquals(edge.nodes(1).id, "C")
  }

  // --- Complex cases ---

  test("parse: multiple attribute lists") {
    val g    = parse("digraph { A [shape=box][color=red] }")
    val node = g.stmts.head.asInstanceOf[DotNodeStmt]
    assertEquals(node.attrs.size, 2)
    assertEquals(node.attrs(0), DotAttr("shape", "box"))
    assertEquals(node.attrs(1), DotAttr("color", "red"))
  }

  test("parse: semicolons optional") {
    val g = parse("digraph { A -> B\nC -> D }")
    assertEquals(g.stmts.size, 2)
    val edge1 = g.stmts(0).asInstanceOf[DotEdgeStmt]
    val edge2 = g.stmts(1).asInstanceOf[DotEdgeStmt]
    assertEquals(edge1.nodes(0).id, "A")
    assertEquals(edge2.nodes(0).id, "C")
  }

  test("parse: comments skipped") {
    val g = parse("// comment\ndigraph { A -> B }")
    assertEquals(g.stmts.size, 1)
    val edge = g.stmts.head.asInstanceOf[DotEdgeStmt]
    assertEquals(edge.nodes(0).id, "A")
  }

  test("parse: block comments") {
    val g = parse("/* comment */ digraph { A -> B }")
    assertEquals(g.stmts.size, 1)
    val edge = g.stmts.head.asInstanceOf[DotEdgeStmt]
    assertEquals(edge.nodes(0).id, "A")
  }

  test("parse: hash comments") {
    val g = parse("# comment\ndigraph { A -> B }")
    assertEquals(g.stmts.size, 1)
  }

  test("parse: quoted strings with escapes") {
    val g    = parse("""digraph { A [label="hello \"world\""] }""")
    val node = g.stmts.head.asInstanceOf[DotNodeStmt]
    assertEquals(node.attrs(0).value, "hello \"world\"")
  }

  test("parse: numeric ids") {
    val g    = parse("digraph { 0 -> 1 -> 2 }")
    val edge = g.stmts.head.asInstanceOf[DotEdgeStmt]
    assertEquals(edge.nodes.map(_.id), Seq("0", "1", "2"))
  }

  // --- Error cases ---

  test("parse: missing graph type throws") {
    val ex = intercept[IllegalArgumentException] {
      parse("{ A -> B }")
    }
    assert(ex.getMessage.contains("Expected 'graph'"), ex.getMessage)
  }

  test("parse: missing closing brace throws") {
    val ex = intercept[IllegalArgumentException] {
      parse("digraph { A -> B")
    }
    assert(ex.getMessage.contains("Expected '}'"), ex.getMessage)
  }

  // --- Attribute access ---

  test("DotGraph.isDirected: true for digraph") {
    val g = parse("digraph { A }")
    assert(g.isDirected)
  }

  test("DotGraph.isDirected: false for graph") {
    val g = parse("graph { A }")
    assert(!g.isDirected)
  }

  // --- Multiple statements ---

  test("parse: mixed statements") {
    val g = parse(
      """digraph G {
        |  rankdir=LR
        |  node [shape=box]
        |  A [label="Start"]
        |  B [label="End"]
        |  A -> B [color=red]
        |}""".stripMargin
    )
    assertEquals(g.id, Some("G"))
    assertEquals(g.stmts.size, 5)
    assert(g.stmts(0).isInstanceOf[DotAssignStmt])
    assert(g.stmts(1).isInstanceOf[DotAttrStmt])
    assert(g.stmts(2).isInstanceOf[DotNodeStmt])
    assert(g.stmts(3).isInstanceOf[DotNodeStmt])
    assert(g.stmts(4).isInstanceOf[DotEdgeStmt])
  }

  test("parse: semicolons as statement separators") {
    val g = parse("digraph { A; B; C; A -> B; B -> C }")
    assertEquals(g.stmts.size, 5)
  }

  // --- Scanner integration ---

  test("scan: tokens are produced for simple digraph") {
    val tokens = DotScanner("digraph { A -> B }").scan()
    // digraph, {, A, ->, B, }, EOF
    assertEquals(tokens.length, 7)
    assertEquals(tokens(0).tpe, TokenType.Identifier)
    assertEquals(tokens(0).value, "digraph")
    assertEquals(tokens(1).tpe, TokenType.LBrace)
    assertEquals(tokens(2).tpe, TokenType.Identifier)
    assertEquals(tokens(2).value, "A")
    assertEquals(tokens(3).tpe, TokenType.Arrow)
    assertEquals(tokens(4).tpe, TokenType.Identifier)
    assertEquals(tokens(4).value, "B")
    assertEquals(tokens(5).tpe, TokenType.RBrace)
    assertEquals(tokens(6).tpe, TokenType.Eof)
  }

  test("scan: HTML string tokens") {
    val tokens = DotScanner("digraph { A [label=<B<SUB>1</SUB>>] }").scan()
    // Find the HTML string token
    val htmlToken = tokens.find(_.tpe == TokenType.HtmlString)
    assert(htmlToken.isDefined)
    assertEquals(htmlToken.get.value, "B<SUB>1</SUB>")
  }

  test("scan: number tokens") {
    val tokens   = DotScanner("digraph { A [width=1.5] }").scan()
    val numToken = tokens.find(_.tpe == TokenType.Number)
    assert(numToken.isDefined)
    assertEquals(numToken.get.value, "1.5")
  }

  test("scan: unterminated block comment throws") {
    val ex = intercept[IllegalArgumentException] {
      DotScanner("/* unclosed comment").scan()
    }
    assert(ex.getMessage.contains("Unterminated block comment"), ex.getMessage)
  }

  test("parse: graph with multiple edge chains") {
    val g = parse("digraph { A -> B; C -> D -> E }")
    assertEquals(g.stmts.size, 2)
    val e1 = g.stmts(0).asInstanceOf[DotEdgeStmt]
    assertEquals(e1.nodes.size, 2)
    val e2 = g.stmts(1).asInstanceOf[DotEdgeStmt]
    assertEquals(e2.nodes.size, 3)
  }

  test("parse: node with port and compass in node stmt") {
    val g    = parse("digraph { A:port1:n [shape=box] }")
    val node = g.stmts.head.asInstanceOf[DotNodeStmt]
    assertEquals(node.id.id, "A")
    assertEquals(node.id.port, Some("port1"))
    assertEquals(node.id.compass, Some("n"))
  }

  test("parse: case-insensitive keywords") {
    val g1 = parse("DIGRAPH { A -> B }")
    assert(g1.isDirected)
    val g2 = parse("DiGraph { A -> B }")
    assert(g2.isDirected)
    val g3 = parse("GRAPH { A -- B }")
    assert(!g3.isDirected)
  }

  test("parse: bare attribute value in attr list") {
    val g    = parse("digraph { A [fixedsize] }")
    val node = g.stmts.head.asInstanceOf[DotNodeStmt]
    assertEquals(node.attrs.size, 1)
    assertEquals(node.attrs(0).key, "fixedsize")
    assertEquals(node.attrs(0).value, "true")
  }

  test("parse: empty graph with semicolons") {
    val g = parse("digraph { ; ; ; }")
    assertEquals(g.stmts.size, 0)
  }
}
