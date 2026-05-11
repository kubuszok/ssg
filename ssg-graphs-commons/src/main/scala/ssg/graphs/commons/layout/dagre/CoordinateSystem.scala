/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Coordinate system transformations for different rank directions.
 *
 * Original source: dagre (dagrejs/dagre) lib/coordinate-system.js
 * Original author: Chris Pettitt
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package graphs
package commons
package layout
package dagre

import ssg.graphs.commons.layout.graph.Graph

object CoordinateSystem {

  def adjust(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    val rankdir = g.graph[GraphLabel]().rankdir.toLowerCase
    if (rankdir == "lr" || rankdir == "rl") {
      swapWidthHeight(g)
    }
  }

  def undo(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    val rankdir = g.graph[GraphLabel]().rankdir.toLowerCase
    if (rankdir == "bt" || rankdir == "rl") {
      reverseY(g)
    }
    if (rankdir == "lr" || rankdir == "rl") {
      swapXY(g)
      swapWidthHeight(g)
    }
  }

  private def swapWidthHeight(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    for (v <- g.nodes()) {
      val node = g.node(v)
      val tmp  = node.width
      node.width = node.height
      node.height = tmp
    }
    for (e <- g.edges()) {
      val edge = g.edge(e)
      val tmp  = edge.width
      edge.width = edge.height
      edge.height = tmp
    }
  }

  private def reverseY(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    for (v <- g.nodes()) {
      val node = g.node(v)
      node.y = -node.y
    }
    for (e <- g.edges()) {
      val edge = g.edge(e)
      edge.points = edge.points.map(p => Point(p.x, -p.y))
      edge.y = -edge.y
    }
  }

  private def swapXY(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    for (v <- g.nodes()) {
      val node = g.node(v)
      val tmp  = node.x
      node.x = node.y
      node.y = tmp
    }
    for (e <- g.edges()) {
      val edge = g.edge(e)
      edge.points = edge.points.map(p => Point(p.y, p.x))
      val tmp = edge.x
      edge.x = edge.y
      edge.y = tmp
    }
  }
}
