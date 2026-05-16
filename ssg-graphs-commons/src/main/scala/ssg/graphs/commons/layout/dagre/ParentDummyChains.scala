/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Handles dummy chains that cross subgraph borders in compound graphs.
 * When a long edge (one that spans multiple ranks) passes through a subgraph
 * boundary, the dummy nodes in the chain need to be assigned to the correct
 * parent so that nesting constraints are respected during ordering.
 *
 * Original source: dagre (dagrejs/dagre) lib/parent-dummy-chains.js
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

import scala.util.boundary
import scala.util.boundary.break

import lowlevel.Nullable
import ssg.graphs.commons.layout.graph.Graph

object ParentDummyChains {

  /** Adjusts the parent assignments for dummy nodes in chains that cross compound graph boundaries.
    *
    * For each dummy chain (a series of dummy nodes inserted by Normalize.run to break long edges into unit-length segments), this method finds the common ancestor of the source and target nodes and
    * assigns each dummy node in the chain to the deepest valid subgraph parent.
    */
  def run(g: Graph[NodeLabel, EdgeLabel]): Unit =
    if (!g.isCompound) { () }
    else {
      val gl = g.graph[GraphLabel]()
      for (chainStart <- gl.dummyChains) {
        var v       = chainStart
        val node    = g.node(v)
        val edgeObj = node.edgeObj
        if (edgeObj.isDefined) {
          val srcParent = g.parent(edgeObj.get.v).getOrElse("")
          val tgtParent = g.parent(edgeObj.get.w).getOrElse("")

          // Find the common ancestor path
          val srcPath = ancestorPath(g, srcParent)
          val tgtPath = ancestorPath(g, tgtParent)

          // Find lowest common ancestor
          val lca = findLCA(srcPath, tgtPath)

          // Walk the dummy chain and assign parents
          var done = false
          while (!done) {
            // Assign this dummy node to the LCA
            if (lca.nonEmpty && g.hasNode(lca)) {
              g.setParent(v, lca)
            }

            val outEdges = g.outEdges(v).getOrElse(Array.empty)
            val next     = if (outEdges.nonEmpty) outEdges(0).w else ""
            if (next.nonEmpty && g.hasNode(next) && g.node(next).dummy.exists(_ == "edge")) {
              v = next
            } else {
              done = true
            }
          }
        }
      }
    }

  /** Returns the path of ancestors from a node to the root (exclusive). */
  private def ancestorPath(g: Graph[NodeLabel, EdgeLabel], start: String): Array[String] = {
    val path    = scala.collection.mutable.ArrayBuffer.empty[String]
    var current = start
    while (current.nonEmpty && g.hasNode(current)) {
      path += current
      current = g.parent(current).getOrElse("")
    }
    path.toArray
  }

  /** Finds the lowest common ancestor (LCA) of two ancestor paths. Returns empty string if no common ancestor is found.
    */
  private def findLCA(pathA: Array[String], pathB: Array[String]): String = boundary {
    val setB = pathB.toSet
    for (a <- pathA)
      if (setB.contains(a)) {
        break(a)
      }
    ""
  }
}
