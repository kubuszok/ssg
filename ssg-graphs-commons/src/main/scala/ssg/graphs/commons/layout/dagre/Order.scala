/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Crossing minimization via layer-by-layer sweep with barycenter heuristic.
 *
 * Original source: dagre (dagrejs/dagre) lib/order
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

object Order {

  private val MaxIterations = 24

  def order(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    val mr = DagreUtil.maxRank(g)
    if (mr >= 0) {

      // Initial ordering via DFS
      val layering = initOrder(g, mr)
      assignOrder(g, layering)

      val bestLayering = layering.map(_.clone())
      var bestCC       = crossCount(g, layering)

      var i        = 0
      var lastBest = 0
      while (i < MaxIterations && lastBest < 4) {
        val reverse = i % 2 == 1
        sweepLayers(g, layering, reverse)
        // Add subgraph constraints after each sweep
        for (r <- layering.indices)
          addSubgraphConstraints(g, layering(r))
        assignOrder(g, layering)
        val cc = crossCount(g, layering)
        if (cc < bestCC) {
          bestCC = cc
          lastBest = 0
          for (r <- layering.indices)
            bestLayering(r) = layering(r).clone()
        } else {
          lastBest += 1
          // Restore best
          for (r <- bestLayering.indices)
            layering(r) = bestLayering(r).clone()
          assignOrder(g, layering)
        }
        i += 1
      }

      assignOrder(g, bestLayering)
    } // end if mr >= 0
  }

  private def initOrder(g: Graph[NodeLabel, EdgeLabel], maxRank: Int): Array[Array[String]] = {
    val layers  = Array.fill(maxRank + 1)(mutable.ArrayBuffer.empty[String])
    val visited = mutable.Set.empty[String]
    val sources = g.sources()

    def dfs(v: String): Unit =
      if (!visited.contains(v)) {
        visited += v
        val node = g.node(v)
        if (node.rank >= 0 && node.rank <= maxRank) {
          layers(node.rank) += v
        }
        for (e <- g.outEdges(v).getOrElse(Array.empty))
          dfs(e.w)
      }

    for (v <- sources) dfs(v)
    // Ensure all nodes are included
    for (v <- g.nodes())
      if (!visited.contains(v)) {
        val node = g.node(v)
        if (node.rank >= 0 && node.rank <= maxRank) {
          layers(node.rank) += v
        }
      }

    layers.map(_.toArray)
  }

  private def sweepLayers(
    g:        Graph[NodeLabel, EdgeLabel],
    layering: Array[Array[String]],
    reverse:  Boolean
  ): Unit = {
    val indices = if (reverse) {
      layering.length - 2 to 0 by -1
    } else {
      1 until layering.length
    }

    for (i <- indices) {
      val fixedLayer   = layering(if (reverse) i + 1 else i - 1)
      val movableLayer = layering(i)
      layering(i) = sortLayer(g, fixedLayer, movableLayer, reverse)
    }
  }

  private def sortLayer(
    g:            Graph[NodeLabel, EdgeLabel],
    fixedLayer:   Array[String],
    movableLayer: Array[String],
    reverse:      Boolean
  ): Array[String] = {
    // Build position map for fixed layer
    val fixedPos = mutable.Map.empty[String, Int]
    for (i <- fixedLayer.indices)
      fixedPos(fixedLayer(i)) = i

    // Compute barycenter for each movable node
    val barycenters = mutable.Map.empty[String, Double]
    for (v <- movableLayer) {
      val edges =
        if (reverse) g.outEdges(v).getOrElse(Array.empty)
        else g.inEdges(v).getOrElse(Array.empty)

      if (edges.nonEmpty) {
        var sum   = 0.0
        var count = 0
        for (e <- edges) {
          val neighbor = if (reverse) e.w else e.v
          if (fixedPos.contains(neighbor)) {
            sum += fixedPos(neighbor) * g.edge(e).weight
            count += g.edge(e).weight.toInt
          }
        }
        if (count > 0) {
          barycenters(v) = sum / count
        }
      }
    }

    // Sort by barycenter, preserving relative order for nodes without connections
    movableLayer.sortBy { v =>
      barycenters.getOrElse(v, g.node(v).order.toDouble)
    }
  }

  def crossCount(g: Graph[NodeLabel, EdgeLabel], layering: Array[Array[String]]): Int = {
    var cc = 0
    for (i <- 1 until layering.length)
      cc += twoLayerCrossCount(g, layering(i - 1), layering(i))
    cc
  }

  private def twoLayerCrossCount(
    g:          Graph[NodeLabel, EdgeLabel],
    northLayer: Array[String],
    southLayer: Array[String]
  ): Int = {
    // Build position maps
    val southPos = mutable.Map.empty[String, Int]
    for (i <- southLayer.indices)
      southPos(southLayer(i)) = i

    // Collect edges as (northPos, southPos) pairs
    val edges = mutable.ArrayBuffer.empty[(Int, Int)]
    for (i <- northLayer.indices) {
      val v = northLayer(i)
      for (e <- g.outEdges(v).getOrElse(Array.empty))
        if (southPos.contains(e.w)) {
          edges += ((i, southPos(e.w)))
        }
    }

    // Count inversions (crossings) via merge-sort
    val southPositions = edges.sortBy(_._1).map(_._2).toArray
    countInversions(southPositions)
  }

  private def countInversions(arr: Array[Int]): Int =
    if (arr.length <= 1) { 0 }
    else {
      val mid   = arr.length / 2
      val left  = arr.slice(0, mid)
      val right = arr.slice(mid, arr.length)
      var count = countInversions(left) + countInversions(right)

      // Merge and count
      var i = 0; var j = 0; var k = 0
      while (i < left.length && j < right.length) {
        if (left(i) <= right(j)) {
          arr(k) = left(i); i += 1
        } else {
          arr(k) = right(j); j += 1
          count += left.length - i
        }
        k += 1
      }
      while (i < left.length) { arr(k) = left(i); i += 1; k += 1 }
      while (j < right.length) { arr(k) = right(j); j += 1; k += 1 }
      count
    }

  private def assignOrder(g: Graph[NodeLabel, EdgeLabel], layering: Array[Array[String]]): Unit =
    for (layer <- layering)
      for (i <- layer.indices)
        g.node(layer(i)).order = i

  /** Builds a bipartite graph between two adjacent layers for use in barycenter sorting. The returned graph has edges from nodes in `fixedRank` to nodes in `movableRank` with weights matching the
    * original graph.
    *
    * Ports `buildLayerGraph()` from order/build-layer-graph.js.
    *
    * @param g
    *   the layout graph
    * @param rank
    *   the rank of the movable layer
    * @param direction
    *   "in" for top-down sweep, "out" for bottom-up sweep
    * @return
    *   a bipartite graph connecting the two layers
    */
  def buildLayerGraph(
    g:         Graph[NodeLabel, EdgeLabel],
    rank:      Int,
    direction: String
  ): Graph[NodeLabel, EdgeLabel] = {
    val lg = new Graph[NodeLabel, EdgeLabel](isDirected = true, isMultigraph = false)

    // Add all nodes at this rank to the layer graph
    for (v <- g.nodes()) {
      val node = g.node(v)
      if (node.rank == rank) {
        lg.setNode(v, node)

        // Add edges connecting this node to adjacent-layer neighbors
        val edges = if (direction == "in") {
          g.inEdges(v).getOrElse(Array.empty)
        } else {
          g.outEdges(v).getOrElse(Array.empty)
        }

        for (e <- edges) {
          val neighbor     = if (direction == "in") e.v else e.w
          val neighborNode = g.node(neighbor)
          if (!lg.hasNode(neighbor)) {
            lg.setNode(neighbor, neighborNode)
          }
          val edgeLabel = g.edge(e)
          val lgLabel   = lg.edgeOpt(neighbor, v).getOrElse {
            val el = new EdgeLabel
            el.weight = 0
            el
          }
          lgLabel.weight = lgLabel.weight + edgeLabel.weight
          lg.setEdge(neighbor, v, lgLabel)
        }
      }
    }

    lg
  }

  /** Enforces subgraph ordering constraints within a layer.
    *
    * If nodes in a layer belong to the same subgraph parent, they must appear consecutively. This method groups nodes by their parent and reorders them so that siblings stay together.
    *
    * Ports `addSubgraphConstraints()` from order/add-subgraph-constraints.js.
    *
    * @param g
    *   the layout graph
    * @param layer
    *   the current layer's node array (may be modified in place)
    */
  def addSubgraphConstraints(
    g:     Graph[NodeLabel, EdgeLabel],
    layer: Array[String]
  ): Unit =
    if (!g.isCompound) { () }
    else {

      // Group nodes by parent
      val parentGroups = mutable.LinkedHashMap.empty[String, mutable.ArrayBuffer[Int]]
      for (i <- layer.indices) {
        val v      = layer(i)
        val parent = g.parent(v).getOrElse("")
        if (parent.nonEmpty) {
          val group = parentGroups.getOrElseUpdate(parent, mutable.ArrayBuffer.empty)
          group += i
        }
      }

      // For each parent group, ensure nodes are consecutive
      // by sorting the group's positions and swapping nodes if needed
      for ((_, indices) <- parentGroups)
        if (indices.length > 1) {
          // Sort the indices by their current order values
          val nodesWithOrder = indices.map(i => (i, g.node(layer(i)).order))
          val sortedNodes    = nodesWithOrder.sortBy(_._2)

          // Place the sorted nodes back at consecutive positions
          val minPos = indices.min
          for (j <- sortedNodes.indices) {
            val srcIdx = sortedNodes(j)._1
            val dstIdx = minPos + j
            if (srcIdx != dstIdx) {
              // Swap the nodes in the layer
              val tmp = layer(dstIdx)
              layer(dstIdx) = layer(srcIdx)
              layer(srcIdx) = tmp
            }
          }
        }
    }
}
