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
package graphs
package commons
package layout
package dagre

import scala.collection.mutable

import ssg.graphs.commons.layout.graph.Graph

object Nesting {

  /** Converts a compound graph to a flat graph with nesting edges and border nodes, ensuring the graph is connected.
    *
    * Faithful port of dagre (dagrejs/dagre) lib/nesting-graph.js `run()`. Border nodes (top/bottom) are created for every subgraph and parented to it; edges `top -> childTop` and
    * `childBottom -> bottom` connect each child to its enclosing subgraph's border pair. Existing edge minlens are multiplied by `nodeSep = 2*height + 1` so that real nodes never land on the same
    * rank as border nodes. `nodeRankFactor` is saved for later removal of empty border-only ranks in `removeEmptyRanks`.
    */
  def run(g: Graph[NodeLabel, EdgeLabel]): Unit =
    if (!g.isCompound) { () }
    else {

      val root   = DagreUtil.addDummyNode(g, "root", new NodeLabel, "_root")
      val depths = treeDepths(g)
      // dagre: `var height = _.max(_.values(depths)) - 1`
      // depths values start at 1 (top-level = 1), so height >= 0.
      val height  = if (depths.isEmpty) 0 else depths.values.max - 1
      val nodeSep = 2 * height + 1

      g.graph[GraphLabel]().nestingRoot = root

      // Multiply minlen by nodeSep to align nodes on non-border ranks.
      for (e <- g.edges())
        g.edge(e).minlen = g.edge(e).minlen * nodeSep

      // Calculate a weight that is sufficient to keep subgraphs vertically
      // compact — dagre uses the sum of ALL edge weights + 1.
      val weight = sumWeights(g) + 1

      // Create border nodes and link them up
      for (child <- g.children())
        dfs(g, root, nodeSep, weight, height, depths, child)

      // Save the multiplier for later removal of empty border layers.
      g.graph[GraphLabel]().nodeRankFactor = nodeSep
    }

  /** Calculates the depth of each node in the nesting tree.
    *
    * Every node that has children gets its depth stored; leaf nodes are not included. Top-level subgraphs have depth 1.
    *
    * Faithful port of dagre lib/nesting-graph.js `treeDepths()`.
    */
  def treeDepths(g: Graph[NodeLabel, EdgeLabel]): mutable.Map[String, Int] = {
    val result = mutable.Map.empty[String, Int]

    def dfs(v: String, depth: Int): Unit = {
      val children = g.children(v)
      if (children.nonEmpty) {
        for (child <- children)
          dfs(child, depth + 1)
      }
      result(v) = depth
    }

    // Start from root-level children (no parent) at depth 1.
    for (child <- g.children())
      dfs(child, 1)

    result
  }

  /** Sums all edge weights in the graph into a single scalar.
    *
    * Faithful port of dagre lib/nesting-graph.js `sumWeights()`.
    */
  def sumWeights(g: Graph[NodeLabel, EdgeLabel]): Double = {
    var total = 0.0
    for (e <- g.edges())
      total += g.edge(e).weight
    total
  }

  /** Recursive DFS that creates border nodes for subgraphs and wires nesting edges connecting children to their parent subgraph's border pair.
    *
    * Faithful port of dagre lib/nesting-graph.js `dfs()`.
    */
  private def dfs(
    g:       Graph[NodeLabel, EdgeLabel],
    root:    String,
    nodeSep: Int,
    weight:  Double,
    height:  Int,
    depths:  mutable.Map[String, Int],
    v:       String
  ): Unit = {
    val children = g.children(v)
    if (children.isEmpty) {
      // Leaf node: connect to root with weight 0 and minlen = nodeSep.
      if (v != root) {
        val e = new EdgeLabel
        e.weight = 0
        e.minlen = nodeSep
        e.nestingEdge = true
        g.setEdge(root, v, e, DagreUtil.uniqueId("_nest"))
      }
    } else {
      // Subgraph: create border top/bottom nodes, parented to this subgraph.
      val top    = DagreUtil.addBorderNode(g, "_bt", 0, 0)
      val bottom = DagreUtil.addBorderNode(g, "_bb", 0, 0)
      val label  = g.node(v)

      g.setParent(top, v)
      label.borderTop = top
      g.setParent(bottom, v)
      label.borderBottom = bottom

      for (child <- children) {
        dfs(g, root, nodeSep, weight, height, depths, child)

        val childNode  = g.node(child)
        val childTop   = if (childNode.borderTop.nonEmpty) childNode.borderTop else child
        val childBtm   = if (childNode.borderBottom.nonEmpty) childNode.borderBottom else child
        val thisWeight = if (childNode.borderTop.nonEmpty) weight else 2 * weight
        val minlen     = if (childTop != childBtm) 1 else height - depths.getOrElse(v, 1) + 1

        val topEdge = new EdgeLabel
        topEdge.weight = thisWeight
        topEdge.minlen = minlen
        topEdge.nestingEdge = true
        g.setEdge(top, childTop, topEdge, DagreUtil.uniqueId("_nest"))

        val btmEdge = new EdgeLabel
        btmEdge.weight = thisWeight
        btmEdge.minlen = minlen
        btmEdge.nestingEdge = true
        g.setEdge(childBtm, bottom, btmEdge, DagreUtil.uniqueId("_nest"))
      }

      // Only connect root to the top border node for top-level subgraphs
      // (those with no parent in the compound graph).
      if (g.parent(v).isEmpty) {
        val rootEdge = new EdgeLabel
        rootEdge.weight = 0
        rootEdge.minlen = height + depths.getOrElse(v, 1)
        rootEdge.nestingEdge = true
        g.setEdge(root, top, rootEdge, DagreUtil.uniqueId("_nest"))
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
