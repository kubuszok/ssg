/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package graphviz
package layout

import munit.FunSuite

import ssg.graphs.commons.layout.dagre.{ EdgeLabel, NodeLabel }
import ssg.graphs.commons.layout.graph.Graph
import ssg.graphs.commons.layout.spring.SpringLayout
import ssg.graphs.commons.layout.circular.CircularLayout
import ssg.graphs.commons.layout.radial.RadialLayout

final class LayoutSuite extends FunSuite {

  private def simpleGraph(): Graph[NodeLabel, EdgeLabel] = {
    val g = new Graph[NodeLabel, EdgeLabel](
      isDirected = true,
      isMultigraph = false,
      isCompound = false
    )
    val a = new NodeLabel()
    a.width = 40
    a.height = 30
    a.label = "A"
    val b = new NodeLabel()
    b.width = 40
    b.height = 30
    b.label = "B"
    val c = new NodeLabel()
    c.width = 40
    c.height = 30
    c.label = "C"
    g.setNode("A", a)
    g.setNode("B", b)
    g.setNode("C", c)
    g.setEdge("A", "B", new EdgeLabel())
    g.setEdge("B", "C", new EdgeLabel())
    g
  }

  private def singleNodeGraph(): Graph[NodeLabel, EdgeLabel] = {
    val g = new Graph[NodeLabel, EdgeLabel](
      isDirected = true,
      isMultigraph = false,
      isCompound = false
    )
    val a = new NodeLabel()
    a.width = 40
    a.height = 30
    a.label = "A"
    g.setNode("A", a)
    g
  }

  private def emptyGraph(): Graph[NodeLabel, EdgeLabel] =
    new Graph[NodeLabel, EdgeLabel](
      isDirected = true,
      isMultigraph = false,
      isCompound = false
    )

  private def disconnectedGraph(): Graph[NodeLabel, EdgeLabel] = {
    val g = new Graph[NodeLabel, EdgeLabel](
      isDirected = true,
      isMultigraph = false,
      isCompound = false
    )
    val a = new NodeLabel()
    a.width = 40
    a.height = 30
    a.label = "A"
    val b = new NodeLabel()
    b.width = 40
    b.height = 30
    b.label = "B"
    val c = new NodeLabel()
    c.width = 40
    c.height = 30
    c.label = "C"
    g.setNode("A", a)
    g.setNode("B", b)
    g.setNode("C", c)
    g.setEdge("A", "B", new EdgeLabel())
    // C is disconnected
    g
  }

  // --- Spring layout ---

  test("spring: all nodes get positions") {
    val g = simpleGraph()
    SpringLayout.layout(g)
    for (id <- g.nodes()) {
      val nl = g.node(id)
      // At least one coordinate should be non-zero for a multi-node graph
      assert(nl.x != 0.0 || nl.y != 0.0, s"Node $id should have been positioned")
    }
  }

  test("spring: deterministic") {
    val g1 = simpleGraph()
    SpringLayout.layout(g1)
    val g2 = simpleGraph()
    SpringLayout.layout(g2)
    for (id <- g1.nodes()) {
      val n1 = g1.node(id)
      val n2 = g2.node(id)
      assertEqualsDouble(n1.x, n2.x, 0.001)
      assertEqualsDouble(n1.y, n2.y, 0.001)
    }
  }

  test("spring: single node") {
    val g = singleNodeGraph()
    SpringLayout.layout(g)
    val nl = g.node("A")
    assert(!nl.x.isNaN, "Single node x should not be NaN")
    assert(!nl.y.isNaN, "Single node y should not be NaN")
  }

  test("spring: empty graph") {
    val g = emptyGraph()
    SpringLayout.layout(g)
    assertEquals(g.nodeCount, 0)
  }

  test("spring: disconnected graph") {
    val g = disconnectedGraph()
    SpringLayout.layout(g)
    for (id <- g.nodes()) {
      val nl = g.node(id)
      assert(!nl.x.isNaN, s"Node $id x should not be NaN")
      assert(!nl.y.isNaN, s"Node $id y should not be NaN")
    }
  }

  test("spring: nodes are spread apart") {
    val g = simpleGraph()
    SpringLayout.layout(g)
    val a        = g.node("A")
    val b        = g.node("B")
    val c        = g.node("C")
    val allSameX = a.x == b.x && b.x == c.x
    val allSameY = a.y == b.y && b.y == c.y
    assert(!(allSameX && allSameY), "Nodes should be spread apart, not all at the same point")
  }

  // --- Circular layout ---

  test("circular: nodes on circle") {
    val g = simpleGraph()
    CircularLayout.layout(g)
    val nodes = g.nodes()
    val dists = nodes.map { id =>
      val nl = g.node(id)
      math.sqrt(nl.x * nl.x + nl.y * nl.y)
    }
    val meanDist = dists.sum / dists.length
    for (d <- dists)
      assertEqualsDouble(d, meanDist, meanDist * 0.01)
  }

  test("circular: single node at center") {
    val g = singleNodeGraph()
    CircularLayout.layout(g)
    val nl = g.node("A")
    assertEqualsDouble(nl.x, 0.0, 0.001)
    assertEqualsDouble(nl.y, 0.0, 0.001)
  }

  test("circular: empty graph") {
    val g = emptyGraph()
    CircularLayout.layout(g)
    assertEquals(g.nodeCount, 0)
  }

  test("circular: nodes are distinct") {
    val g = simpleGraph()
    CircularLayout.layout(g)
    val positions = g.nodes().map { id =>
      val nl = g.node(id)
      (nl.x, nl.y)
    }
    assertEquals(positions.distinct.length, positions.length)
  }

  // --- Radial layout ---

  test("radial: root at center") {
    val g = simpleGraph()
    RadialLayout.layout(g)
    val root = g.nodes().head
    val nl   = g.node(root)
    assertEqualsDouble(nl.x, 0.0, 0.001)
    assertEqualsDouble(nl.y, 0.0, 0.001)
  }

  test("radial: children farther from center") {
    val g = simpleGraph()
    RadialLayout.layout(g)
    val root         = g.nodes().head
    val rootNl       = g.node(root)
    val rootDist     = math.sqrt(rootNl.x * rootNl.x + rootNl.y * rootNl.y)
    var foundFarther = false
    for (id <- g.nodes())
      if (id != root) {
        val nl   = g.node(id)
        val dist = math.sqrt(nl.x * nl.x + nl.y * nl.y)
        if (dist > rootDist + 1.0) {
          foundFarther = true
        }
      }
    assert(foundFarther, "Children should be placed on outer ring")
  }

  test("radial: single node") {
    val g = singleNodeGraph()
    RadialLayout.layout(g)
    val nl = g.node("A")
    assertEqualsDouble(nl.x, 0.0, 0.001)
    assertEqualsDouble(nl.y, 0.0, 0.001)
  }

  test("radial: empty graph") {
    val g = emptyGraph()
    RadialLayout.layout(g)
    assertEquals(g.nodeCount, 0)
  }

  test("radial: disconnected graph") {
    val g = disconnectedGraph()
    RadialLayout.layout(g)
    for (id <- g.nodes()) {
      val nl = g.node(id)
      assert(!nl.x.isNaN, s"Node $id x should not be NaN")
      assert(!nl.y.isNaN, s"Node $id y should not be NaN")
    }
  }

}
