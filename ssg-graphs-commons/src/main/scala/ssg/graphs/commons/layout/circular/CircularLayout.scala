/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Circular layout: arranges nodes evenly on a circle,
 * ordered by degree (descending) with alphabetical tie-breaking.
 */
package ssg
package graphs
package commons
package layout
package circular

import ssg.graphs.commons.layout.dagre.{ EdgeLabel, NodeLabel, Point }
import ssg.graphs.commons.layout.graph.Graph

object CircularLayout {

  def layout(g: Graph[NodeLabel, EdgeLabel], nodeSep: Double = 50, minRadius: Double = 100): Unit = {
    val allNodes = g.nodes()
    if (allNodes.length == 1) {
      val label = g.node(allNodes(0))
      label.x = 0
      label.y = 0
      setEdgePoints(g)
    } else if (allNodes.length > 1) {
      val sorted = allNodes.sortWith { (a, b) =>
        val degA = degree(g, a)
        val degB = degree(g, b)
        if (degA != degB) degA > degB
        else a < b
      }

      val n             = sorted.length
      val circumference = n * nodeSep
      val radius        = scala.math.max(minRadius, circumference / (2.0 * scala.math.Pi))

      for (i <- 0 until n) {
        val angle = 2.0 * scala.math.Pi * i / n
        val label = g.node(sorted(i))
        label.x = radius * scala.math.cos(angle)
        label.y = radius * scala.math.sin(angle)
      }

      setEdgePoints(g)
    }
  }

  private def degree(g: Graph[NodeLabel, EdgeLabel], v: String): Int = {
    val inCount  = g.inEdges(v).fold(0)(_.length)
    val outCount = g.outEdges(v).fold(0)(_.length)
    inCount + outCount
  }

  private def setEdgePoints(g: Graph[NodeLabel, EdgeLabel]): Unit =
    for (e <- g.edges()) {
      val label = g.edge(e)
      val src   = g.node(e.v)
      val tgt   = g.node(e.w)
      label.points = Array(Point(src.x, src.y), Point(tgt.x, tgt.y))
    }
}
