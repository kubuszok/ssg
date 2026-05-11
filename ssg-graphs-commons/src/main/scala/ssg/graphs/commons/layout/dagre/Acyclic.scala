/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Greedy cycle removal for directed graphs.
 *
 * Original source: dagre (dagrejs/dagre) lib/acyclic.js
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

import scala.collection.mutable

import ssg.graphs.commons.layout.graph.{ EdgeObj, Graph }

object Acyclic {

  def run(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    val visited = mutable.Set.empty[String]
    val stack   = mutable.Set.empty[String]

    def dfs(v: String): Unit =
      if (!visited.contains(v)) {
        visited += v
        stack += v
        for (e <- g.outEdges(v).getOrElse(Array.empty))
          if (stack.contains(e.w)) {
            reverseEdge(g, e)
          } else if (!visited.contains(e.w)) {
            dfs(e.w)
          }
        stack -= v
      }

    for (v <- g.nodes())
      dfs(v)
  }

  private def reverseEdge(g: Graph[NodeLabel, EdgeLabel], e: EdgeObj): Unit = {
    val label = g.edge(e)
    g.removeEdge(e)
    label.forwardName = e.name.getOrElse("")
    label.reversed = true
    g.setEdge(e.w, e.v, label, DagreUtil.uniqueId("rev"))
  }

  def undo(g: Graph[NodeLabel, EdgeLabel]): Unit =
    for (e <- g.edges()) {
      val label = g.edge(e)
      if (label.reversed) {
        g.removeEdge(e)
        val forwardName = label.forwardName
        label.reversed = false
        label.forwardName = ""
        label.points = label.points.reverse
        g.setEdge(e.w, e.v, label, forwardName)
      }
    }
}
