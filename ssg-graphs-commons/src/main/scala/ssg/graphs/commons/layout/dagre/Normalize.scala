/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Edge normalization: insert/remove dummy nodes for long edges.
 *
 * Original source: dagre (dagrejs/dagre) lib/normalize.js
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

import ssg.commons.Nullable
import ssg.graphs.commons.layout.graph.{ EdgeObj, Graph }

object Normalize {

  def run(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    val gl = g.graph[GraphLabel]()
    for (e <- g.edges()) {
      val edgeLabel  = g.edge(e)
      val sourceRank = g.node(e.v).rank
      val targetRank = g.node(e.w).rank

      if (targetRank - sourceRank > 1) {
        g.removeEdge(e)
        var v          = e.v
        var rank       = sourceRank + 1
        var i          = 0
        var firstDummy = ""
        while (rank < targetRank) {
          val attrs = new NodeLabel
          attrs.width = 0
          attrs.height = 0
          attrs.edgeLabel = Nullable(edgeLabel)
          attrs.edgeObj = Nullable(e)
          attrs.rank = rank

          val dummyName = DagreUtil.addDummyNode(g, "edge", attrs, s"_d${e.v}_${e.w}_$i")

          if (i == 0) { firstDummy = dummyName }

          if (rank == sourceRank + 1) {
            attrs.labelRank = edgeLabel.labelRank
          }

          val dummyEdge = new EdgeLabel
          dummyEdge.weight = edgeLabel.weight
          g.setEdge(v, dummyName, dummyEdge)
          v = dummyName
          rank += 1
          i += 1
        }

        val lastEdge = new EdgeLabel
        lastEdge.weight = edgeLabel.weight
        g.setEdge(v, e.w, lastEdge)

        gl.dummyChains += firstDummy
      }
    }
  }

  def undo(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    val gl = g.graph[GraphLabel]()
    for (chainStart <- gl.dummyChains) {
      var v         = chainStart
      val node      = g.node(v)
      val origLabel = node.edgeLabel.get
      val origEdge  = node.edgeObj.get
      val points    = mutable.ArrayBuffer.empty[Point]

      // Walk the chain collecting bend points
      var done = false
      while (!done) {
        points += Point(g.node(v).x, g.node(v).y)
        val outEdges = g.outEdges(v).getOrElse(Array.empty)
        val next     = if (outEdges.nonEmpty) outEdges(0).w else ""
        g.removeNode(v)
        if (next.nonEmpty && g.hasNode(next) && g.node(next).dummy.exists(_ == "edge")) {
          v = next
        } else {
          done = true
        }
      }

      origLabel.points = points.toArray
      g.setEdge(origEdge.v, origEdge.w, origLabel, origEdge.name.getOrElse(""))
    }
  }
}
