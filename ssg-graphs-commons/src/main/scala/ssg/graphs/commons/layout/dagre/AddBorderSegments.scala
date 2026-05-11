/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Adds border segments to compound graphs.
 * For each subgraph that spans multiple ranks, this module adds border nodes
 * (left and right) at each rank so that the subgraph's border is represented
 * in the node ordering. This ensures child nodes stay within their parent
 * subgraph's visual bounds.
 *
 * Original source: dagre (dagrejs/dagre) lib/add-border-segments.js
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

object AddBorderSegments {

  /** Adds left and right border nodes at each rank for compound nodes.
    *
    * For each node that has children (a subgraph), border nodes are created at each rank between minRank and maxRank. These border nodes establish the visual boundaries of the subgraph.
    */
  def run(g: Graph[NodeLabel, EdgeLabel]): Unit =
    if (!g.isCompound) { () }
    else {

      // Iterate over all nodes that have children (subgraphs)
      for (v <- g.nodes()) {
        val children = g.children(v)
        if (children.nonEmpty) {
          val node = g.node(v)
          if (node.minRank < Int.MaxValue && node.maxRank > Int.MinValue) {
            // Add border nodes for each rank in the subgraph's range
            for (rank <- node.minRank to node.maxRank)
              addBorderNodeForRank(g, v, rank)
          }
        }
      }
    }

  /** Adds left and right border nodes at a specific rank for a subgraph.
    *
    * Creates two border dummy nodes and records them in the parent node's borderLeft/borderRight maps.
    */
  private def addBorderNodeForRank(
    g:        Graph[NodeLabel, EdgeLabel],
    subgraph: String,
    rank:     Int
  ): Unit = {
    val node = g.node(subgraph)

    // Add left border node
    val leftName = DagreUtil.addBorderNode(g, "bl", rank, 0)
    node.borderLeft(rank) = leftName
    g.setParent(leftName, subgraph)

    // Add right border node
    val rightName = DagreUtil.addBorderNode(g, "br", rank, 0)
    node.borderRight(rank) = rightName
    g.setParent(rightName, subgraph)
  }
}
