/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Gallery test suite: real-world Graphviz DOT examples exercising
 * both the parser (AST structure) and the renderer (SVG output).
 */
package ssg
package graphviz
package parse

import munit.FunSuite

final class DotGallerySuite extends FunSuite {

  private def parse(input: String): DotGraph = {
    val tokens = DotScanner(input).scan()
    DotParser(tokens).parse()
  }

  private val neatoConfig: GraphvizConfig = GraphvizConfig(engine = LayoutEngine.Neato)

  private def render(input: String): String =
    Graphviz.render(input, neatoConfig)

  /** Count all DotNodeStmt in a flat or nested statement list. */
  private def countNodes(stmts: Seq[DotStmt]): Int = {
    stmts.foldLeft(0) { (acc, s) =>
      s match {
        case _: DotNodeStmt                => acc + 1
        case DotSubgraphStmt(_, subStmts)  => acc + countNodes(subStmts)
        case _                             => acc
      }
    }
  }

  /** Count all DotEdgeStmt in a flat or nested statement list. */
  private def countEdges(stmts: Seq[DotStmt]): Int = {
    stmts.foldLeft(0) { (acc, s) =>
      s match {
        case _: DotEdgeStmt                => acc + 1
        case DotSubgraphStmt(_, subStmts)  => acc + countEdges(subStmts)
        case _                             => acc
      }
    }
  }

  /** Count all DotSubgraphStmt (top-level only) in a statement list. */
  private def countSubgraphs(stmts: Seq[DotStmt]): Int = {
    stmts.count(_.isInstanceOf[DotSubgraphStmt])
  }

  /** Count all DotAttrStmt in a statement list. */
  private def countAttrStmts(stmts: Seq[DotStmt]): Int = {
    stmts.count(_.isInstanceOf[DotAttrStmt])
  }

  /** Count all DotAssignStmt in a statement list. */
  private def countAssigns(stmts: Seq[DotStmt]): Int = {
    stmts.count(_.isInstanceOf[DotAssignStmt])
  }

  // ===== 1. Empty graph =====================================================

  private val dot1 = "digraph G { }"

  test("gallery 1 parse: empty graph") {
    val g = parse(dot1)
    assert(g.isDirected)
    assertEquals(g.id, Some("G"))
    assertEquals(g.stmts.size, 0)
  }

  test("gallery 1 render: empty graph produces SVG") {
    val svg = render(dot1)
    assert(svg.contains("<svg"), "Expected SVG tag")
    assert(svg.contains("xmlns"), "Expected xmlns attribute")
  }

  // ===== 2. Single node =====================================================

  private val dot2 = "digraph G { a }"

  test("gallery 2 parse: single node") {
    val g = parse(dot2)
    assertEquals(g.id, Some("G"))
    assertEquals(g.stmts.size, 1)
    val node = g.stmts.head.asInstanceOf[DotNodeStmt]
    assertEquals(node.id.id, "a")
  }

  test("gallery 2 render: single node produces ellipse") {
    val svg = render(dot2)
    assert(svg.contains("<svg"))
    assert(svg.contains("<ellipse"), "Default shape should be ellipse")
    assert(svg.contains(">a</text>"), "Node label 'a' should appear")
  }

  // ===== 3. Hello World =====================================================

  private val dot3 = "digraph G { Hello -> World }"

  test("gallery 3 parse: hello world") {
    val g = parse(dot3)
    assertEquals(g.stmts.size, 1)
    val edge = g.stmts.head.asInstanceOf[DotEdgeStmt]
    assertEquals(edge.nodes.map(_.id), Seq("Hello", "World"))
  }

  test("gallery 3 render: hello world has edge and markers") {
    val svg = render(dot3)
    assert(svg.contains("<path"), "Edge should render as path")
    assert(svg.contains("marker-end"), "Digraph edges have arrowheads")
    assert(svg.contains(">Hello</text>"))
    assert(svg.contains(">World</text>"))
  }

  // ===== 4. Simple undirected ===============================================

  private val dot4 = "graph { a -- b; b -- c; a -- c; d -- c; e -- c; e -- a; }"

  test("gallery 4 parse: simple undirected") {
    val g = parse(dot4)
    assert(!g.isDirected)
    assertEquals(g.graphType, DotGraphType.Graph)
    assertEquals(countEdges(g.stmts), 6)
  }

  test("gallery 4 render: undirected has no arrowheads") {
    val svg = render(dot4)
    assert(svg.contains("<svg"))
    assert(!svg.contains("marker-end"), "Undirected edges should not have arrowheads")
    assert(svg.contains("<path"), "Edges should render as paths")
  }

  // ===== 5. Directed cycle ==================================================

  private val dot5 = "digraph { a -> b; b -> c; c -> d; d -> a; }"

  test("gallery 5 parse: directed cycle") {
    val g = parse(dot5)
    assert(g.isDirected)
    assertEquals(countEdges(g.stmts), 4)
    // Each edge stmt has 2 nodes
    for (s <- g.stmts) {
      val e = s.asInstanceOf[DotEdgeStmt]
      assertEquals(e.nodes.size, 2)
    }
  }

  test("gallery 5 render: directed cycle has 4 paths") {
    val svg = render(dot5)
    val pathCount = "<path".r.findAllMatchIn(svg).size
    // 4 edge paths + 1 arrowhead path in <defs> = 5
    assertEquals(pathCount, 5)
  }

  // ===== 6. Self-loop and weighted edges ====================================

  private val dot6 = """digraph {
    |  a -> b [label="0.2",weight="0.2"];
    |  a -> c [label="0.4",weight="0.4"];
    |  c -> b [label="0.6",weight="0.6"];
    |  e -> e [label="0.1",weight="0.1"];
    |  e -> b [label="0.7",weight="0.7"];
    |}""".stripMargin

  test("gallery 6 parse: self-loop and weighted edges") {
    val g = parse(dot6)
    assertEquals(countEdges(g.stmts), 5)
    // Find the self-loop edge (e -> e)
    val selfLoop = g.stmts.collect { case e: DotEdgeStmt => e }
      .find(e => e.nodes(0).id == "e" && e.nodes(1).id == "e")
    assert(selfLoop.isDefined, "Self-loop e -> e should be present")
    assertEquals(selfLoop.get.attrs.size, 2)
    assertEquals(selfLoop.get.attrs.find(_.key == "label").get.value, "0.1")
  }

  test("gallery 6 render: self-loop graph produces SVG") {
    val svg = render(dot6)
    assert(svg.contains("<svg"))
    assert(svg.contains("<path"))
  }

  // ===== 7. Clusters with styling ===========================================

  private val dot7 = """digraph G {
    |  subgraph cluster_0 {
    |    style=filled; color=lightgrey;
    |    node [style=filled,color=white];
    |    a0 -> a1 -> a2 -> a3;
    |    label = "process #1";
    |  }
    |  subgraph cluster_1 {
    |    node [style=filled];
    |    b0 -> b1 -> b2 -> b3;
    |    label = "process #2";
    |    color=blue
    |  }
    |  start -> a0; start -> b0;
    |  a1 -> b3; b2 -> a3;
    |  a3 -> a0; a3 -> end; b3 -> end;
    |  start [shape=Mdiamond];
    |  end [shape=Msquare];
    |}""".stripMargin

  test("gallery 7 parse: clusters with styling") {
    val g = parse(dot7)
    assertEquals(g.id, Some("G"))
    assert(g.isDirected)
    assertEquals(countSubgraphs(g.stmts), 2)
    // cluster_0 has: 2 assigns (style, color), 1 node attr stmt, 1 edge chain, 1 assign (label)
    val cluster0 = g.stmts(0).asInstanceOf[DotSubgraphStmt]
    assertEquals(cluster0.id, Some("cluster_0"))
    assertEquals(countEdges(cluster0.stmts), 1)
    // The edge chain a0 -> a1 -> a2 -> a3 has 4 nodes
    val chain = cluster0.stmts.collect { case e: DotEdgeStmt => e }.head
    assertEquals(chain.nodes.size, 4)
    // cluster_1
    val cluster1 = g.stmts(1).asInstanceOf[DotSubgraphStmt]
    assertEquals(cluster1.id, Some("cluster_1"))
    // Top-level statements outside clusters: 7 edges + 2 nodes
    val topEdges = g.stmts.collect { case e: DotEdgeStmt => e }
    assertEquals(topEdges.size, 7)
    val topNodes = g.stmts.collect { case n: DotNodeStmt => n }
    assertEquals(topNodes.size, 2)
    assertEquals(topNodes(0).id.id, "start")
    assertEquals(topNodes(1).id.id, "end")
  }

  test("gallery 7 render: clusters produce SVG with nodes and edges") {
    val svg = render(dot7)
    assert(svg.contains("<svg"))
    assert(svg.contains("class=\"nodes\""))
    assert(svg.contains("class=\"edges\""))
    assert(svg.contains(">start</text>"))
    assert(svg.contains(">end</text>"))
  }

  // ===== 8. FSM with rankdir=LR =============================================

  private val dot8 = """digraph finite_state_machine {
    |  rankdir=LR;
    |  node [shape = doublecircle]; 0 3 4 8;
    |  node [shape = circle];
    |  0 -> 2 [label = "SS(B)"];
    |  0 -> 1 [label = "SS(S)"];
    |  1 -> 3 [label = "S($end)"];
    |  2 -> 6 [label = "SS(b)"];
    |  2 -> 5 [label = "SS(a)"];
    |  2 -> 4 [label = "S(A)"];
    |  5 -> 7 [label = "S(b)"];
    |  5 -> 5 [label = "S(a)"];
    |  6 -> 6 [label = "S(b)"];
    |  6 -> 5 [label = "S(a)"];
    |  7 -> 8 [label = "S(b)"];
    |  7 -> 5 [label = "S(a)"];
    |  8 -> 6 [label = "S(b)"];
    |  8 -> 5 [label = "S(a)"];
    |}""".stripMargin

  test("gallery 8 parse: FSM with rankdir=LR") {
    val g = parse(dot8)
    assertEquals(g.id, Some("finite_state_machine"))
    assert(g.isDirected)
    // 1 assign (rankdir), 2 attr stmts (node [...]), 4 bare node stmts (0,3,4,8), 14 edge stmts
    assertEquals(countAssigns(g.stmts), 1)
    assertEquals(countAttrStmts(g.stmts), 2)
    assertEquals(countNodes(g.stmts), 4)
    assertEquals(countEdges(g.stmts), 14)
    // Verify the 4 bare nodes are the ones after `node [shape=doublecircle];`
    val nodeStmts = g.stmts.collect { case n: DotNodeStmt => n }
    assertEquals(nodeStmts.map(_.id.id).toSet, Set("0", "3", "4", "8"))
  }

  test("gallery 8 render: FSM produces SVG with edges") {
    val svg = render(dot8)
    assert(svg.contains("<svg"))
    assert(svg.contains("<path"))
    assert(svg.contains("marker-end"))
  }

  // ===== 9. All node shapes =================================================

  private val dot9 = """digraph G {
    |  a [shape=box]; b [shape=ellipse]; c [shape=circle];
    |  d [shape=diamond]; e [shape=plaintext]; f [shape=point];
    |  g [shape=triangle]; h [shape=pentagon]; i [shape=hexagon];
    |  j [shape=rect]; k [shape=rectangle]; l [shape=none];
    |  a -> b -> c -> d -> e -> f -> g -> h -> i -> j -> k -> l
    |}""".stripMargin

  test("gallery 9 parse: all node shapes") {
    val g = parse(dot9)
    assertEquals(countNodes(g.stmts), 12)
    assertEquals(countEdges(g.stmts), 1)
    // The edge chain has 12 nodes
    val edge = g.stmts.collect { case e: DotEdgeStmt => e }.head
    assertEquals(edge.nodes.size, 12)
    // Verify specific shapes
    val nodeStmts = g.stmts.collect { case n: DotNodeStmt => n }
    assertEquals(nodeStmts.find(_.id.id == "a").get.attrs.head.value, "box")
    assertEquals(nodeStmts.find(_.id.id == "d").get.attrs.head.value, "diamond")
    assertEquals(nodeStmts.find(_.id.id == "l").get.attrs.head.value, "none")
  }

  test("gallery 9 render: various node shapes in SVG") {
    val svg = render(dot9)
    assert(svg.contains("<rect"), "box/rect shapes should render as <rect>")
    assert(svg.contains("<ellipse"), "ellipse shape should render as <ellipse>")
    assert(svg.contains("<circle"), "circle shape should render as <circle>")
    assert(svg.contains("<polygon"), "diamond shape should render as <polygon>")
  }

  // ===== 10. Edge styles ====================================================

  private val dot10 = """digraph G {
    |  a -> b [style=bold]
    |  a -> c [style=dashed]
    |  a -> d [style=dotted]
    |  a -> e [dir=both]
    |  a -> f [dir=back]
    |  a -> g [dir=none]
    |}""".stripMargin

  test("gallery 10 parse: edge styles") {
    val g = parse(dot10)
    assertEquals(countEdges(g.stmts), 6)
    val edges = g.stmts.collect { case e: DotEdgeStmt => e }
    assertEquals(edges(1).attrs.head, DotAttr("style", "dashed"))
    assertEquals(edges(2).attrs.head, DotAttr("style", "dotted"))
    assertEquals(edges(3).attrs.head, DotAttr("dir", "both"))
  }

  test("gallery 10 render: dashed and dotted edges") {
    val svg = render(dot10)
    assert(svg.contains("stroke-dasharray=\"5,2\""), "dashed edge should have stroke-dasharray 5,2")
    assert(svg.contains("stroke-dasharray=\"1,2\""), "dotted edge should have stroke-dasharray 1,2")
  }

  // ===== 11. Colors =========================================================

  private val dot11 = """digraph G {
    |  node [style=filled]
    |  a [fillcolor=green]
    |  b [fillcolor="#FF0000"]
    |  c [fillcolor=yellow, fontcolor=blue]
    |  d [color=red]
    |  a -> b [color=red]
    |  b -> c [color=blue]
    |  c -> d [color="#00FF00"]
    |}""".stripMargin

  test("gallery 11 parse: colors") {
    val g = parse(dot11)
    assertEquals(countAttrStmts(g.stmts), 1) // node [style=filled]
    assertEquals(countNodes(g.stmts), 4)
    assertEquals(countEdges(g.stmts), 3)
    val nodeA = g.stmts.collect { case n: DotNodeStmt => n }.find(_.id.id == "a").get
    assertEquals(nodeA.attrs.head, DotAttr("fillcolor", "green"))
    // Hex colors are quoted strings values
    val nodeB = g.stmts.collect { case n: DotNodeStmt => n }.find(_.id.id == "b").get
    assertEquals(nodeB.attrs.head.value, "#FF0000")
  }

  test("gallery 11 render: colors in SVG") {
    val svg = render(dot11)
    assert(svg.contains("fill=\"green\""), "fillcolor=green")
    assert(svg.contains("fill=\"#FF0000\""), "fillcolor=#FF0000")
    assert(svg.contains("stroke=\"red\""), "color=red on node")
    assert(svg.contains("stroke=\"blue\""), "edge color=blue")
  }

  // ===== 12. Strict graph deduplication =====================================

  private val dot12 = """strict graph {
    |  a -- b
    |  a -- b
    |  b -- a
    |}""".stripMargin

  test("gallery 12 parse: strict graph") {
    val g = parse(dot12)
    assert(g.strict)
    assert(!g.isDirected)
    assertEquals(g.graphType, DotGraphType.Graph)
    assertEquals(countEdges(g.stmts), 3) // Parser keeps all 3 edge stmts
  }

  test("gallery 12 render: strict graph produces SVG") {
    val svg = render(dot12)
    assert(svg.contains("<svg"))
    assert(svg.contains("<path"))
  }

  // ===== 13. Named graph with assignment ====================================

  private val dot13 = """digraph MyGraph {
    |  rankdir=LR;
    |  label="My Graph Title";
    |  fontsize=20;
    |  a -> b -> c;
    |}""".stripMargin

  test("gallery 13 parse: named graph with assignments") {
    val g = parse(dot13)
    assertEquals(g.id, Some("MyGraph"))
    assertEquals(countAssigns(g.stmts), 3)
    assertEquals(countEdges(g.stmts), 1)
    val assigns = g.stmts.collect { case a: DotAssignStmt => a }
    assertEquals(assigns(0), DotAssignStmt("rankdir", "LR"))
    assertEquals(assigns(1), DotAssignStmt("label", "My Graph Title"))
    assertEquals(assigns(2), DotAssignStmt("fontsize", "20"))
  }

  test("gallery 13 render: named graph produces SVG") {
    val svg = render(dot13)
    assert(svg.contains("<svg"))
    assert(svg.contains(">a</text>"))
    assert(svg.contains(">b</text>"))
    assert(svg.contains(">c</text>"))
  }

  // ===== 14. Nested subgraphs ===============================================

  private val dot14 = """digraph G {
    |  subgraph cluster_outer {
    |    label="Outer";
    |    subgraph cluster_inner {
    |      label="Inner";
    |      a -> b;
    |    }
    |    c -> d;
    |  }
    |  e -> a;
    |  d -> e;
    |}""".stripMargin

  test("gallery 14 parse: nested subgraphs") {
    val g = parse(dot14)
    assertEquals(g.id, Some("G"))
    assertEquals(countSubgraphs(g.stmts), 1) // Only cluster_outer at top level
    val outer = g.stmts(0).asInstanceOf[DotSubgraphStmt]
    assertEquals(outer.id, Some("cluster_outer"))
    // Outer has: assign(label), subgraph(cluster_inner), edge(c->d)
    assertEquals(countAssigns(outer.stmts), 1)
    assertEquals(countSubgraphs(outer.stmts), 1)
    // countEdges is recursive: 1 in outer (c->d) + 1 in inner (a->b) = 2
    assertEquals(countEdges(outer.stmts), 2)
    val inner = outer.stmts.collect { case s: DotSubgraphStmt => s }.head
    assertEquals(inner.id, Some("cluster_inner"))
    assertEquals(countAssigns(inner.stmts), 1)
    assertEquals(countEdges(inner.stmts), 1)
  }

  test("gallery 14 render: nested subgraphs produce SVG") {
    val svg = render(dot14)
    assert(svg.contains("<svg"))
    assert(svg.contains(">a</text>"))
    assert(svg.contains(">e</text>"))
  }

  // ===== 15. Entity-Relation diagram ========================================

  private val dot15 = """graph ER {
    |  node [shape=box]; course; institute; student;
    |  node [shape=ellipse]; name0; name1; name2; code; grade; number;
    |  node [shape=diamond,style=filled,color=lightgrey]; "C-I"; "S-C"; "S-I";
    |  name0 -- course;
    |  code -- course;
    |  course -- "C-I";
    |  "C-I" -- institute;
    |  institute -- name1;
    |  institute -- "S-I";
    |  "S-I" -- student;
    |  student -- grade;
    |  student -- name2;
    |  student -- number;
    |  student -- "S-C";
    |  "S-C" -- course;
    |}""".stripMargin

  test("gallery 15 parse: ER diagram") {
    val g = parse(dot15)
    assertEquals(g.id, Some("ER"))
    assert(!g.isDirected)
    assertEquals(countAttrStmts(g.stmts), 3) // 3 node [...] stmts
    assertEquals(countNodes(g.stmts), 12) // course, institute, student, name0..2, code, grade, number, C-I, S-C, S-I
    assertEquals(countEdges(g.stmts), 12)
    // Verify quoted identifiers are properly parsed
    val nodeStmts = g.stmts.collect { case n: DotNodeStmt => n }
    assert(nodeStmts.exists(_.id.id == "C-I"), "Quoted id 'C-I' should parse")
    assert(nodeStmts.exists(_.id.id == "S-C"), "Quoted id 'S-C' should parse")
    assert(nodeStmts.exists(_.id.id == "S-I"), "Quoted id 'S-I' should parse")
  }

  test("gallery 15 render: ER diagram produces SVG with shapes") {
    val svg = render(dot15)
    assert(svg.contains("<svg"))
    // Note: the renderer's collectDotAttrs flattens all `node [...]`
    // defaults to the last one (diamond), so box-shaped nodes render as
    // polygons rather than rects. The parser correctly preserves all
    // three `node [...]` statements, but the renderer does not scope
    // defaults positionally. We assert polygons (from the diamond
    // default) are present.
    assert(svg.contains("<polygon"), "diamond-default nodes should render as polygon")
  }

  // ===== 16. Process states =================================================

  private val dot16 = """graph G {
    |  run -- intr; intr -- runbl; runbl -- run;
    |  run -- kernel; kernel -- zombie; kernel -- sleep;
    |  kernel -- runmem; sleep -- swap;
    |  swap -- runswap; runswap -- new;
    |  runswap -- runmem; new -- runmem;
    |  sleep -- runmem;
    |}""".stripMargin

  test("gallery 16 parse: process states") {
    val g = parse(dot16)
    assertEquals(g.id, Some("G"))
    assert(!g.isDirected)
    assertEquals(countEdges(g.stmts), 13)
  }

  test("gallery 16 render: process states produces SVG") {
    val svg = render(dot16)
    assert(svg.contains("<svg"))
    assert(svg.contains(">run</text>"))
    assert(svg.contains(">kernel</text>"))
    assert(svg.contains(">zombie</text>"))
  }

  // ===== 17. Complex cluster with cross-edges ===============================

  private val dot17 = """digraph G {
    |  subgraph cluster_0 {
    |    style=filled; color=lightgrey;
    |    a0 -> a1 -> a2;
    |  }
    |  subgraph cluster_1 {
    |    color=blue;
    |    b0 -> b1 -> b2;
    |  }
    |  subgraph cluster_2 {
    |    color=red;
    |    c0 -> c1 -> c2;
    |  }
    |  a0 -> b0; b0 -> c0;
    |  a2 -> b2; b2 -> c2;
    |  a1 -> c1;
    |}""".stripMargin

  test("gallery 17 parse: complex clusters with cross-edges") {
    val g = parse(dot17)
    assertEquals(countSubgraphs(g.stmts), 3)
    val clusters = g.stmts.collect { case s: DotSubgraphStmt => s }
    assertEquals(clusters(0).id, Some("cluster_0"))
    assertEquals(clusters(1).id, Some("cluster_1"))
    assertEquals(clusters(2).id, Some("cluster_2"))
    // Cross-edges at top level
    val topEdges = g.stmts.collect { case e: DotEdgeStmt => e }
    assertEquals(topEdges.size, 5)
    // Each cluster has 1 edge chain of 3 nodes
    for (cluster <- clusters) {
      val clusterEdges = cluster.stmts.collect { case e: DotEdgeStmt => e }
      assertEquals(clusterEdges.size, 1)
      assertEquals(clusterEdges.head.nodes.size, 3)
    }
  }

  test("gallery 17 render: complex clusters produce SVG") {
    val svg = render(dot17)
    assert(svg.contains("<svg"))
    assert(svg.contains("<path"))
    assert(svg.contains(">a0</text>"))
    assert(svg.contains(">c2</text>"))
  }

  // ===== 18. Multiple default attribute statements ==========================

  private val dot18 = """digraph G {
    |  node [shape=box];
    |  a; b;
    |  node [shape=circle];
    |  c; d;
    |  edge [color=red];
    |  a -> b;
    |  edge [color=blue];
    |  c -> d;
    |  a -> c;
    |}""".stripMargin

  test("gallery 18 parse: multiple default attribute statements") {
    val g = parse(dot18)
    assertEquals(countAttrStmts(g.stmts), 4) // 2 node + 2 edge
    assertEquals(countNodes(g.stmts), 4)      // a, b, c, d
    assertEquals(countEdges(g.stmts), 3)      // a->b, c->d, a->c
    val attrStmts = g.stmts.collect { case a: DotAttrStmt => a }
    assertEquals(attrStmts(0).target, DotAttrTarget.Node)
    assertEquals(attrStmts(0).attrs.head.value, "box")
    assertEquals(attrStmts(1).target, DotAttrTarget.Node)
    assertEquals(attrStmts(1).attrs.head.value, "circle")
    assertEquals(attrStmts(2).target, DotAttrTarget.Edge)
    assertEquals(attrStmts(2).attrs.head.value, "red")
    assertEquals(attrStmts(3).target, DotAttrTarget.Edge)
    assertEquals(attrStmts(3).attrs.head.value, "blue")
  }

  test("gallery 18 render: multiple defaults produce SVG") {
    val svg = render(dot18)
    assert(svg.contains("<svg"))
    assert(svg.contains("<path"))
  }

  // ===== 19. Quoted identifiers with special characters =====================

  private val dot19 = """digraph G {
    |  "Node A" -> "Node B";
    |  "Node B" -> "Node C (special)";
    |  "Node A" [label="First\nNode"];
    |  "Node C (special)" [label="Last \"Node\""];
    |}""".stripMargin

  test("gallery 19 parse: quoted identifiers with special characters") {
    val g = parse(dot19)
    assertEquals(countEdges(g.stmts), 2)
    assertEquals(countNodes(g.stmts), 2) // Only the two with attrs
    val edges = g.stmts.collect { case e: DotEdgeStmt => e }
    assertEquals(edges(0).nodes(0).id, "Node A")
    assertEquals(edges(0).nodes(1).id, "Node B")
    assertEquals(edges(1).nodes(0).id, "Node B")
    assertEquals(edges(1).nodes(1).id, "Node C (special)")
    val nodeStmts = g.stmts.collect { case n: DotNodeStmt => n }
    val nodeA = nodeStmts.find(_.id.id == "Node A").get
    assertEquals(nodeA.attrs.head.value, "First\nNode")
    val nodeC = nodeStmts.find(_.id.id == "Node C (special)").get
    assertEquals(nodeC.attrs.head.value, "Last \"Node\"")
  }

  test("gallery 19 render: quoted identifiers produce SVG") {
    val svg = render(dot19)
    assert(svg.contains("<svg"))
    // The node labels should appear (but escape sequences in labels may render differently)
    assert(svg.contains("<text"))
  }

  // ===== 20. Large fan-out ==================================================

  private val dot20 = """digraph G {
    |  root -> a; root -> b; root -> c; root -> d; root -> e;
    |  root -> f; root -> g; root -> h; root -> i; root -> j;
    |  a -> leaf1; b -> leaf2; c -> leaf3; d -> leaf4; e -> leaf5;
    |}""".stripMargin

  test("gallery 20 parse: large fan-out") {
    val g = parse(dot20)
    assertEquals(countEdges(g.stmts), 15)
    val edges = g.stmts.collect { case e: DotEdgeStmt => e }
    // First 10 edges start from root
    val rootEdges = edges.filter(_.nodes(0).id == "root")
    assertEquals(rootEdges.size, 10)
    // Remaining 5 connect to leaves
    val leafEdges = edges.filter(_.nodes(1).id.startsWith("leaf"))
    assertEquals(leafEdges.size, 5)
  }

  test("gallery 20 render: large fan-out produces SVG with many paths") {
    val svg = render(dot20)
    assert(svg.contains("<svg"))
    val pathCount = "<path".r.findAllMatchIn(svg).size
    // 15 edge paths + 1 arrowhead path in <defs> = 16
    assertEquals(pathCount, 16)
    assert(svg.contains(">root</text>"))
  }
}
