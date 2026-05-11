/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Parser tests ported from viz-js Lezer grammar test cases (lang-dot/test/graph.txt).
 * Verifies DOT grammar parsing against the DotScanner/DotParser pipeline.
 */
package ssg
package graphviz
package parse

import munit.FunSuite

final class VizJsParserSuite extends FunSuite {

  private def parse(input: String): DotGraph = {
    val tokens = DotScanner(input).scan()
    DotParser(tokens).parse()
  }

  // --- 1. Empty graph ---

  test("vizjs: minimal graph") {
    val g = parse("graph {}")
    assertEquals(g.graphType, DotGraphType.Graph)
    assert(!g.isDirected)
    assertEquals(g.stmts.size, 0)
  }

  // --- 2. Full header ---

  test("vizjs: full header (strict digraph with id)") {
    val g = parse("strict digraph test {}")
    assert(g.strict)
    assertEquals(g.graphType, DotGraphType.Digraph)
    assert(g.isDirected)
    assertEquals(g.id, Some("test"))
    assertEquals(g.stmts.size, 0)
  }

  // --- 3. Node statement ---

  test("vizjs: node statement") {
    val g = parse("graph { a }")
    assertEquals(g.stmts.size, 1)
    assert(g.stmts.head.isInstanceOf[DotNodeStmt])
    val node = g.stmts.head.asInstanceOf[DotNodeStmt]
    assertEquals(node.id.id, "a")
  }

  // --- 4. Ports on nodes ---

  test("vizjs: ports on nodes in edge") {
    val g = parse("digraph { a:b -> c:d:e }")
    assertEquals(g.stmts.size, 1)
    val edge = g.stmts.head.asInstanceOf[DotEdgeStmt]
    assertEquals(edge.nodes.size, 2)
    val first = edge.nodes(0)
    assertEquals(first.id, "a")
    assertEquals(first.port, Some("b"))
    val second = edge.nodes(1)
    assertEquals(second.id, "c")
    assertEquals(second.port, Some("d"))
    assertEquals(second.compass, Some("e"))
  }

  // --- 5. Optional semicolons ---

  test("vizjs: optional semicolons") {
    val g = parse("graph { a; b c; }")
    assertEquals(g.stmts.size, 3)
    assert(g.stmts(0).isInstanceOf[DotNodeStmt])
    assert(g.stmts(1).isInstanceOf[DotNodeStmt])
    assert(g.stmts(2).isInstanceOf[DotNodeStmt])
    assertEquals(g.stmts(0).asInstanceOf[DotNodeStmt].id.id, "a")
    assertEquals(g.stmts(1).asInstanceOf[DotNodeStmt].id.id, "b")
    assertEquals(g.stmts(2).asInstanceOf[DotNodeStmt].id.id, "c")
  }

  // --- 6. Subgraphs ---

  test("vizjs: subgraphs (anonymous and named)") {
    val g = parse("graph { { a b } subgraph another { c d } }")
    assertEquals(g.stmts.size, 2)
    val sub1 = g.stmts(0).asInstanceOf[DotSubgraphStmt]
    assertEquals(sub1.id, None)
    assertEquals(sub1.stmts.size, 2)
    val sub2 = g.stmts(1).asInstanceOf[DotSubgraphStmt]
    assertEquals(sub2.id, Some("another"))
    assertEquals(sub2.stmts.size, 2)
  }

  // --- 7. Edge chains ---

  test("vizjs: edge chain with 5 nodes") {
    val g = parse("digraph { a -> b -> c -> d -> e }")
    assertEquals(g.stmts.size, 1)
    val edge = g.stmts.head.asInstanceOf[DotEdgeStmt]
    assertEquals(edge.nodes.size, 5)
    assertEquals(edge.nodes.map(_.id), Seq("a", "b", "c", "d", "e"))
  }

  // --- 8. Attributes ---

  test("vizjs: node attributes") {
    val g = parse("graph { a [x=y, z=w] }")
    assertEquals(g.stmts.size, 1)
    val node = g.stmts.head.asInstanceOf[DotNodeStmt]
    assertEquals(node.id.id, "a")
    assertEquals(node.attrs.size, 2)
    assertEquals(node.attrs(0), DotAttr("x", "y"))
    assertEquals(node.attrs(1), DotAttr("z", "w"))
  }

  // --- 9. Multiple attribute blocks ---

  test("vizjs: multiple attribute blocks merged") {
    val g = parse("graph { a [x=y][z=w] }")
    assertEquals(g.stmts.size, 1)
    val node = g.stmts.head.asInstanceOf[DotNodeStmt]
    assertEquals(node.id.id, "a")
    assertEquals(node.attrs.size, 2)
    assertEquals(node.attrs(0), DotAttr("x", "y"))
    assertEquals(node.attrs(1), DotAttr("z", "w"))
  }

  // --- 10. Attribute statements ---

  test("vizjs: attribute statements and assign") {
    val g = parse("graph { graph [a=b]; edge [c=d]; node [e=f]; x=y }")
    assertEquals(g.stmts.size, 4)

    val graphAttr = g.stmts(0).asInstanceOf[DotAttrStmt]
    assertEquals(graphAttr.target, DotAttrTarget.Graph)
    assertEquals(graphAttr.attrs, Seq(DotAttr("a", "b")))

    val edgeAttr = g.stmts(1).asInstanceOf[DotAttrStmt]
    assertEquals(edgeAttr.target, DotAttrTarget.Edge)
    assertEquals(edgeAttr.attrs, Seq(DotAttr("c", "d")))

    val nodeAttr = g.stmts(2).asInstanceOf[DotAttrStmt]
    assertEquals(nodeAttr.target, DotAttrTarget.Node)
    assertEquals(nodeAttr.attrs, Seq(DotAttr("e", "f")))

    val assign = g.stmts(3).asInstanceOf[DotAssignStmt]
    assertEquals(assign.key, "x")
    assertEquals(assign.value, "y")
  }

  // --- 11. Identifier types ---

  test("vizjs: identifier types (alpha, alphanumeric, numeric)") {
    val g = parse("graph { a; A_1; 123 }")
    assertEquals(g.stmts.size, 3)
    assertEquals(g.stmts(0).asInstanceOf[DotNodeStmt].id.id, "a")
    assertEquals(g.stmts(1).asInstanceOf[DotNodeStmt].id.id, "A_1")
    assertEquals(g.stmts(2).asInstanceOf[DotNodeStmt].id.id, "123")
  }

  // --- 12. Negative numbers as IDs ---

  test("vizjs: negative and fractional number ids") {
    val g = parse("graph { -2; .99; 3. }")
    assertEquals(g.stmts.size, 3)
    assertEquals(g.stmts(0).asInstanceOf[DotNodeStmt].id.id, "-2")
    assertEquals(g.stmts(1).asInstanceOf[DotNodeStmt].id.id, ".99")
    assertEquals(g.stmts(2).asInstanceOf[DotNodeStmt].id.id, "3.")
  }

  // --- 13. Quoted strings ---

  test("vizjs: quoted strings with escapes in edge") {
    val g = parse("""digraph { "abc" -> "test \"quoted\"" }""")
    assertEquals(g.stmts.size, 1)
    val edge = g.stmts.head.asInstanceOf[DotEdgeStmt]
    assertEquals(edge.nodes(0).id, "abc")
    assertEquals(edge.nodes(1).id, "test \"quoted\"")
  }

  // --- 14. HTML-like strings ---

  test("vizjs: HTML-like label string") {
    val g = parse("graph { a [label=<<b>bold</b>>] }")
    assertEquals(g.stmts.size, 1)
    val node = g.stmts.head.asInstanceOf[DotNodeStmt]
    assertEquals(node.id.id, "a")
    assertEquals(node.attrs.size, 1)
    assertEquals(node.attrs(0).key, "label")
    assertEquals(node.attrs(0).value, "<b>bold</b>")
  }

  // --- 15. Empty HTML string ---

  test("vizjs: empty HTML-like string") {
    val g = parse("graph { a [label=<>] }")
    assertEquals(g.stmts.size, 1)
    val node = g.stmts.head.asInstanceOf[DotNodeStmt]
    assertEquals(node.attrs.size, 1)
    assertEquals(node.attrs(0).key, "label")
    assertEquals(node.attrs(0).value, "")
  }

  // --- 16. Concatenated strings ---

  test("vizjs: string concatenation with + operator") {
    val g = parse("""graph { a [label="hello" + " " + "world"] }""")
    assertEquals(g.stmts.size, 1)
    val node = g.stmts.head.asInstanceOf[DotNodeStmt]
    assertEquals(node.attrs.size, 1)
    assertEquals(node.attrs(0).key, "label")
    assertEquals(node.attrs(0).value, "hello world")
  }

  // --- 17. Line comments ---

  test("vizjs: line comments") {
    val g = parse("// comment\ngraph { a }")
    assertEquals(g.stmts.size, 1)
    assertEquals(g.stmts.head.asInstanceOf[DotNodeStmt].id.id, "a")
  }

  // --- 18. Hash comments ---

  test("vizjs: hash comments") {
    val g = parse("# comment\ngraph { a }")
    assertEquals(g.stmts.size, 1)
    assertEquals(g.stmts.head.asInstanceOf[DotNodeStmt].id.id, "a")
  }

  // --- 19. Block comments ---

  test("vizjs: block comments") {
    val g = parse("/* comment */graph { a }")
    assertEquals(g.stmts.size, 1)
    assertEquals(g.stmts.head.asInstanceOf[DotNodeStmt].id.id, "a")
  }

  // --- 20. Case-insensitive keywords ---

  test("vizjs: case-insensitive keywords") {
    val g = parse("STRICT DIGRAPH { SUBGRAPH cluster_a { a } }")
    assert(g.strict)
    assertEquals(g.graphType, DotGraphType.Digraph)
    assert(g.isDirected)
    assertEquals(g.stmts.size, 1)
    val sub = g.stmts.head.asInstanceOf[DotSubgraphStmt]
    assertEquals(sub.id, Some("cluster_a"))
    assertEquals(sub.stmts.size, 1)
  }

  // --- 21. Unicode identifiers ---

  test("vizjs: unicode identifiers") {
    val g = parse("graph { 図 -> café }")
    assertEquals(g.stmts.size, 1)
    val edge = g.stmts.head.asInstanceOf[DotEdgeStmt]
    assertEquals(edge.nodes(0).id, "図")
    assertEquals(edge.nodes(1).id, "café")
  }

  // --- 22. Edge with subgraph (brace shorthand) ---

  test("vizjs: edge with anonymous subgraph (brace shorthand)") {
    val g = parse("digraph { a -> { b c } }")
    assertEquals(g.stmts.size, 1)
    val edge = g.stmts.head.asInstanceOf[DotEdgeStmt]
    // The parser represents subgraph-in-edge as a DotNodeId with the subgraph's id
    // (empty string for anonymous subgraphs). Edge has 2 entries: "a" and subgraph.
    assertEquals(edge.nodes.size, 2)
    assertEquals(edge.nodes(0).id, "a")
  }

  // --- 23. Mixed comma/semicolon in attr lists ---

  test("vizjs: mixed comma and semicolon in attribute lists") {
    val g = parse("graph { a [x=y, z=w; q=r] }")
    assertEquals(g.stmts.size, 1)
    val node = g.stmts.head.asInstanceOf[DotNodeStmt]
    assertEquals(node.attrs.size, 3)
    assertEquals(node.attrs(0), DotAttr("x", "y"))
    assertEquals(node.attrs(1), DotAttr("z", "w"))
    assertEquals(node.attrs(2), DotAttr("q", "r"))
  }
}
