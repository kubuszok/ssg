/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Compound graph nesting support for dagre layout.
 * Converts compound graphs to flat graphs with nesting edges and border nodes.
 *
 * Original source: dagre (dagrejs/dagre) lib/nesting-graph.js
 * Original author: Chris Pettitt
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package layout
package dagre

import scala.collection.mutable

import ssg.mermaid.layout.graph.Graph

object Nesting {

  def run(g: Graph[NodeLabel, EdgeLabel]): Unit =
    if (!g.isCompound) { () }
    else {

      val root = DagreUtil.addDummyNode(g, "root", new NodeLabel, "_root")
      g.graph[GraphLabel]().nestingRoot = root

      // Calculate nesting tree depths and edge weight sums for proper weighting
      val depths  = treeDepths(g)
      val height  = if (depths.isEmpty) 1 else depths.values.max + 1
      val weights = sumWeights(g)

      // Create nesting edges from root to all top-level children
      dfsNesting(g, root, "", height, weights)
    }

  /** Calculates the depth of each node in the nesting tree.
    *
    * The depth of a root-level node is 0. For each level of subgraph nesting, the depth increases by 1.
    *
    * Ports `treeDepths()` from nesting-graph.js.
    */
  def treeDepths(g: Graph[NodeLabel, EdgeLabel]): mutable.Map[String, Int] = {
    val result = mutable.Map.empty[String, Int]

    def dfs(v: String, depth: Int): Unit = {
      val children = g.children(v)
      for (child <- children) {
        result(child) = depth
        dfs(child, depth + 1)
      }
    }

    // Start from root-level children (no parent)
    for (child <- g.children()) {
      result(child) = 0
      dfs(child, 1)
    }

    result
  }

  /** Sums the edge weights for each node.
    *
    * For each node, adds the weight of all incoming and outgoing edges. Used to calculate proper nesting edge weights.
    *
    * Ports `sumWeights()` from nesting-graph.js.
    */
  def sumWeights(g: Graph[NodeLabel, EdgeLabel]): mutable.Map[String, Double] = {
    val result = mutable.Map.empty[String, Double]
    for (v <- g.nodes()) {
      var total = 0.0
      for (e <- g.outEdges(v).getOrElse(Array.empty))
        total += g.edge(e).weight
      for (e <- g.inEdges(v).getOrElse(Array.empty))
        total += g.edge(e).weight
      result(v) = total
    }
    result
  }

  private def dfsNesting(
    g:       Graph[NodeLabel, EdgeLabel],
    root:    String,
    parent:  String,
    height:  Int,
    weights: mutable.Map[String, Double]
  ): Unit = {
    val children = if (parent.isEmpty) g.children() else g.children(parent)
    for (child <- children) {
      // Calculate proper nesting edge weight:
      // weight = height + sumWeights + 1
      // This ensures nesting edges have lower priority than real edges
      // but higher priority than empty nesting edges
      val nodeWeight = weights.getOrElse(child, 0.0)
      val nestWeight = height + nodeWeight + 1

      if (g.children(child).nonEmpty) {
        // Subgraph: add border nodes and recurse
        val borderTop    = DagreUtil.addBorderNode(g, "bt", g.node(child).rank, 0)
        val borderBottom = DagreUtil.addBorderNode(g, "bb", g.node(child).rank, 0)
        g.node(child).borderTop = borderTop
        g.node(child).borderBottom = borderBottom

        // Nesting edges with proper weight
        val nestEdge = new EdgeLabel
        nestEdge.minlen = 0
        nestEdge.weight = nestWeight
        nestEdge.nestingEdge = true
        g.setEdge(root, child, nestEdge, DagreUtil.uniqueId("_nest"))

        dfsNesting(g, root, child, height, weights)
      } else {
        // Leaf node: connect to root with proper weight
        val nestEdge = new EdgeLabel
        nestEdge.minlen = 0
        nestEdge.weight = nestWeight
        nestEdge.nestingEdge = true
        g.setEdge(root, child, nestEdge, DagreUtil.uniqueId("_nest"))
      }
    }
  }

  def cleanup(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    val gl = g.graph[GraphLabel]()
    if (gl.nestingRoot.nonEmpty) {
      g.removeNode(gl.nestingRoot)
      gl.nestingRoot = ""
    }

    // Remove nesting edges
    for (e <- g.edges()) {
      val label = g.edge(e)
      if (label.nestingEdge) {
        g.removeEdge(e)
      }
    }

    // Remove border nodes
    for (v <- g.nodes()) {
      val node = g.node(v)
      if (node.dummy.exists(_ == "border")) {
        g.removeNode(v)
      }
    }
  }
}
