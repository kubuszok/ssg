/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Rank assignment for the Sugiyama algorithm.
 * Implements longest-path ranking and the network simplex algorithm.
 *
 * Original source: dagre (dagrejs/dagre) lib/rank
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
import scala.util.boundary
import scala.util.boundary.break

import lowlevel.Nullable
import ssg.graphs.commons.layout.graph.{ EdgeObj, Graph, GraphAlgorithms }

object Rank {

  def rank(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    val ranker = g.graph[GraphLabel]().ranker
    ranker match {
      case "network-simplex" => networkSimplexRank(g)
      case "tight-tree"      => tightTreeRank(g)
      case "longest-path"    => longestPath(g)
      case _                 => networkSimplexRank(g)
    }
  }

  def longestPath(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    val visited = mutable.Set.empty[String]

    def dfs(v: String): Int = boundary {
      if (visited.contains(v)) {
        break(g.node(v).rank)
      }
      visited += v
      val outEdges = g.outEdges(v).getOrElse(Array.empty)
      var minRank  = Int.MaxValue
      for (e <- outEdges) {
        val targetRank = dfs(e.w)
        val minlen     = g.edge(e).minlen
        if (targetRank - minlen < minRank) {
          minRank = targetRank - minlen
        }
      }
      if (minRank == Int.MaxValue) { minRank = 0 }
      g.node(v).rank = minRank
      minRank
    }

    for (v <- g.sources())
      dfs(v)
  }

  private def tightTreeRank(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    longestPath(g)
    networkSimplex(g)
  }

  private def networkSimplexRank(g: Graph[NodeLabel, EdgeLabel]): Unit =
    networkSimplex(g)

  // ---- Network Simplex Algorithm ----

  def networkSimplex(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    val simplified = DagreUtil.simplify(g)
    longestPath(simplified)
    val tree = feasibleTree(simplified)
    initLowLimValues(tree)
    initCutValues(tree, simplified)

    var edge = leaveEdge(tree)
    while (edge.isDefined) {
      val enter = enterEdge(tree, simplified, edge.get)
      exchangeEdges(tree, simplified, edge.get, enter)
      edge = leaveEdge(tree)
    }

    // Copy ranks back to original graph
    for (v <- simplified.nodes())
      g.node(v).rank = simplified.node(v).rank
  }

  private def feasibleTree(g: Graph[NodeLabel, EdgeLabel]): Graph[NodeLabel, EdgeLabel] = {
    val tree = new Graph[NodeLabel, EdgeLabel](isDirected = false)

    val start     = g.nodes()(0)
    val nodeCount = g.nodeCount

    tree.setNode(start, g.node(start))

    var edge = tightEdge(tree, g)
    while (tree.nodeCount < nodeCount) {
      edge match {
        case Some(e) =>
          val edgeLabel = g.edge(e)
          val vInTree   = tree.hasNode(e.v)
          val newNode   = if (vInTree) e.w else e.v
          tree.setNode(newNode, g.node(newNode))
          tree.setEdge(e.v, e.w, edgeLabel)
          val nl = new EdgeLabel
          tree.setEdge(e.v, e.w, nl)
          tighten(tree, g, newNode)
        case None =>
          // No tight edge found — shift ranks
          val delta = slack(g, tree)
          for (v <- g.nodes())
            if (!tree.hasNode(v)) {
              g.node(v).rank = g.node(v).rank - delta
            }
      }
      edge = tightEdge(tree, g)
    }

    tree
  }

  private def tightEdge(
    tree: Graph[NodeLabel, EdgeLabel],
    g:    Graph[NodeLabel, EdgeLabel]
  ): Option[EdgeObj] = boundary {
    for (v <- tree.nodes())
      for (e <- g.nodeEdges(v).getOrElse(Array.empty)) {
        val other = if (e.v == v) e.w else e.v
        if (!tree.hasNode(other) && edgeSlack(g, e) == 0) {
          break(Some(e))
        }
      }
    None
  }

  private def tighten(
    tree:    Graph[NodeLabel, EdgeLabel],
    g:       Graph[NodeLabel, EdgeLabel],
    newNode: String
  ): Unit =
    for (e <- g.nodeEdges(newNode).getOrElse(Array.empty)) {
      val other = if (e.v == newNode) e.w else e.v
      if (!tree.hasNode(other) && edgeSlack(g, e) == 0) {
        tree.setNode(other, g.node(other))
        tree.setEdge(e.v, e.w, new EdgeLabel)
        tighten(tree, g, other)
      }
    }

  private def edgeSlack(g: Graph[NodeLabel, EdgeLabel], e: EdgeObj): Int =
    g.node(e.w).rank - g.node(e.v).rank - g.edge(e).minlen

  private def slack(g: Graph[NodeLabel, EdgeLabel], tree: Graph[NodeLabel, EdgeLabel]): Int = {
    var min = Int.MaxValue
    for (e <- g.edges()) {
      val s       = edgeSlack(g, e)
      val vInTree = tree.hasNode(e.v)
      val wInTree = tree.hasNode(e.w)
      if (vInTree != wInTree && s < min) {
        min = s
      }
    }
    min
  }

  // DFS numbering for cut value computation
  private def initLowLimValues(tree: Graph[NodeLabel, EdgeLabel], root: String = ""): Unit = {
    val r = if (root.isEmpty) tree.nodes()(0) else root
    dfsAssignLowLim(tree, mutable.Set.empty, 1, r)
  }

  private def dfsAssignLowLim(
    tree:       Graph[NodeLabel, EdgeLabel],
    visited:    mutable.Set[String],
    nextLim:    Int,
    v:          String,
    parentNode: Nullable[String] = Nullable.Null
  ): Int = {
    visited += v
    val low = nextLim
    var lim = nextLim

    for (w <- tree.neighbors(v).getOrElse(Array.empty))
      if (!visited.contains(w)) {
        lim = dfsAssignLowLim(tree, visited, lim, w, Nullable(v))
      }

    val node = tree.node(v)
    node.low = low
    node.lim = lim
    node.parent = parentNode
    lim + 1
  }

  private def initCutValues(
    tree: Graph[NodeLabel, EdgeLabel],
    g:    Graph[NodeLabel, EdgeLabel]
  ): Unit = {
    val vs = GraphAlgorithms.postorder(tree, Array(tree.nodes()(0)))
    // Skip the root (last in postorder is the root when processed this way)
    for (v <- vs)
      calcCutValue(tree, g, v)
  }

  private def calcCutValue(
    tree:  Graph[NodeLabel, EdgeLabel],
    g:     Graph[NodeLabel, EdgeLabel],
    child: String
  ): Unit = {
    val parentOpt = tree.node(child).parent

    if (parentOpt.isEmpty) { () }
    else {

      val parent    = parentOpt.get
      val childNode = tree.node(child)

      var cutValue = 0
      for (e <- g.nodeEdges(child).getOrElse(Array.empty)) {
        val other      = if (e.v == child) e.w else e.v
        val isOutEdge  = e.v == child
        val otherNode  = g.node(other)
        val edgeWeight = g.edge(e).weight.toInt

        if (other != parent) {
          val otherInSubtree = otherNode.low >= childNode.low &&
            otherNode.lim <= childNode.lim
          if (isOutEdge) {
            if (!otherInSubtree) cutValue += edgeWeight else cutValue -= edgeWeight
          } else {
            if (!otherInSubtree) cutValue -= edgeWeight else cutValue += edgeWeight
          }
        }
      }

      // Store cut value on the tree edge between child and parent
      val treeEdge =
        if (tree.hasEdge(child, parent)) tree.edgeOpt(child, parent)
        else tree.edgeOpt(parent, child)
      treeEdge.foreach(_.cutvalue = cutValue)
    } // end if parentOpt.nonEmpty
  }

  private def leaveEdge(tree: Graph[NodeLabel, EdgeLabel]): Option[EdgeObj] = boundary {
    for (e <- tree.edges()) {
      val label = tree.edge(e)
      if (label.cutvalue < 0) {
        break(Some(e))
      }
    }
    None
  }

  private def enterEdge(
    tree: Graph[NodeLabel, EdgeLabel],
    g:    Graph[NodeLabel, EdgeLabel],
    edge: EdgeObj
  ): EdgeObj = {
    val vNode = tree.node(edge.v)
    val wNode = tree.node(edge.w)

    // Determine which side of the tree edge is the "tail" (child component)
    val tailLabel = if (vNode.lim > wNode.lim) wNode else vNode
    val flip      = vNode.lim > wNode.lim

    var bestEdge: EdgeObj = g.edges()(0)
    var bestSlack = Int.MaxValue

    for (e <- g.edges()) {
      val eVNode = tree.node(e.v)
      val eWNode = tree.node(e.w)

      // Check if exactly one endpoint is in the tail component
      val vInTail = eVNode.low >= tailLabel.low && eVNode.lim <= tailLabel.lim
      val wInTail = eWNode.low >= tailLabel.low && eWNode.lim <= tailLabel.lim

      if (vInTail != wInTail) {
        val isCorrectDirection = if (flip) wInTail else vInTail
        if (isCorrectDirection) {
          val s = edgeSlack(g, e)
          if (s < bestSlack) {
            bestSlack = s
            bestEdge = e
          }
        }
      }
    }

    bestEdge
  }

  private def exchangeEdges(
    tree:     Graph[NodeLabel, EdgeLabel],
    g:        Graph[NodeLabel, EdgeLabel],
    leaving:  EdgeObj,
    entering: EdgeObj
  ): Unit = {
    tree.removeEdge(leaving)
    tree.setEdge(entering.v, entering.w, new EdgeLabel)

    initLowLimValues(tree)
    initCutValues(tree, g)

    // Update ranks: shift subtree ranks to satisfy new tree edge
    updateRanks(tree, g)
  }

  private def updateRanks(
    tree: Graph[NodeLabel, EdgeLabel],
    g:    Graph[NodeLabel, EdgeLabel]
  ): Unit = {
    val root    = tree.nodes().find(v => tree.node(v).parent.isEmpty).getOrElse(tree.nodes()(0))
    val visited = mutable.Set.empty[String]

    def dfs(v: String): Unit =
      if (!visited.contains(v)) {
        visited += v
        for (w <- tree.neighbors(v).getOrElse(Array.empty))
          if (!visited.contains(w)) {
            // Find the edge in g to determine the correct rank delta
            if (g.hasEdge(v, w)) {
              g.node(w).rank = g.node(v).rank + g.edge(v, w).minlen
            } else if (g.hasEdge(w, v)) {
              g.node(w).rank = g.node(v).rank - g.edge(w, v).minlen
            }
            dfs(w)
          }
      }

    g.node(root).rank = 0
    dfs(root)
  }
}
