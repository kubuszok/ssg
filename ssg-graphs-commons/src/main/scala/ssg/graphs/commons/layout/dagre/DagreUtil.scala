/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Shared utility functions for the dagre layout algorithm.
 *
 * Original source: dagre (dagrejs/dagre) lib/util.js
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
import ssg.graphs.commons.layout.graph.Graph

object DagreUtil {

  private var idCounter: Int = 0

  /** "Does this node have a rank?" — the SSG equivalent of dagre's `_.has(node, "rank")` presence check used throughout `lib/util.js`.
    *
    * In dagre-js every node carries a `rank` property by the time the normalization helpers run; only compound *parent* nodes lack one because `simplify()` / `asNonCompoundGraph()` exclude nodes with
    * children from the graph that `rank()` operates on (their layout position is derived from their descendants by `assignRankMinMax`). SSG's `NodeLabel.rank` defaults to `-1`, which collides with
    * the legitimate NEGATIVE ranks that `Rank.longestPath` assigns (sinks at 0, sources negative), so a value-based `rank >= 0` test wrongly drops real nodes (e.g. a diamond's `b`/`c` at rank -1)
    * from rank normalization and positioning (ISS-1198). The presence predicate must therefore be STRUCTURAL, not value-based: a node has a rank iff it is not an excluded compound parent — exactly
    * the set `simplify()` keeps (`!g.isCompound || g.children(v).isEmpty`).
    */
  private def hasRank(g: Graph[NodeLabel, EdgeLabel], v: String): Boolean =
    !g.isCompound || g.children(v).isEmpty

  def uniqueId(prefix: String): String = {
    idCounter += 1
    s"$prefix$idCounter"
  }

  def resetIdCounter(): Unit =
    idCounter = 0

  def addDummyNode(
    g:     Graph[NodeLabel, EdgeLabel],
    typ:   String,
    attrs: NodeLabel,
    name:  String
  ): String = {
    val v = if (name.nonEmpty) name else uniqueId("_d")
    attrs.dummy = Nullable(typ)
    g.setNode(v, attrs)
    v
  }

  /** Creates a simplified (non-multigraph, non-compound) copy of `g` with parallel edges merged.
    *
    * Ports dagre lib/util.js `simplify()`. Compound parent nodes (those with children in the original compound graph) are excluded because they have no edges of their own — their layout position is
    * derived from their children's positions after ranking completes. Including them would leave isolated nodes that violate the connectivity precondition of `feasibleTree`.
    */
  def simplify(g: Graph[NodeLabel, EdgeLabel]): Graph[NodeLabel, EdgeLabel] = {
    val simplified = new Graph[NodeLabel, EdgeLabel](isDirected = true, isMultigraph = false)
    simplified.setGraph(g.graph[GraphLabel]())
    for (v <- g.nodes())
      // Skip compound parent nodes — they have no edges and would be isolated
      // in the simplified graph, breaking the connectivity precondition of
      // feasibleTree. Their position is derived from children after layout.
      if (!g.isCompound || g.children(v).isEmpty) {
        simplified.setNode(v, g.node(v))
      }
    for (e <- g.edges())
      if (simplified.hasNode(e.v) && simplified.hasNode(e.w)) {
        val simpleLabel = simplified.edgeOpt(e.v, e.w).getOrElse {
          val el = new EdgeLabel
          el.weight = 0
          el.minlen = 1
          el
        }
        val label = g.edge(e)
        simplified.setEdge(
          e.v,
          e.w, {
            simpleLabel.weight = simpleLabel.weight + label.weight
            simpleLabel.minlen = Math.max(simpleLabel.minlen, label.minlen)
            simpleLabel
          }
        )
      }
    simplified
  }

  def asNonCompoundGraph(g: Graph[NodeLabel, EdgeLabel]): Graph[NodeLabel, EdgeLabel] = {
    val result = new Graph[NodeLabel, EdgeLabel](
      isDirected = g.isDirected,
      isMultigraph = g.isMultigraph
    )
    result.setGraph(g.graph[GraphLabel]())
    for (v <- g.nodes())
      if (g.children(v).isEmpty) {
        result.setNode(v, g.node(v))
      }
    for (e <- g.edges())
      result.setEdge(e.v, e.w, g.edge(e))
    result
  }

  def intersectRect(rect: NodeLabel, point: Point): Point = boundary {
    val x  = rect.x
    val y  = rect.y
    val dx = point.x - x
    val dy = point.y - y
    var w  = rect.width / 2.0
    var h  = rect.height / 2.0

    if (dx == 0 && dy == 0) {
      break(Point(x, y - h))
    }

    val sx: Double =
      if (dy == 0) {
        if (dx > 0) 1.0 else -1.0
      } else {
        val slope = Math.abs(dx / dy)
        if (slope * h > w) {
          if (dx < 0) { w = -w }
          w
        } else {
          if (dy < 0) { h = -h }
          val result = h * slope
          if (dx < 0) -result else result
        }
      }

    val sy: Double =
      if (dx == 0) {
        if (dy > 0) 1.0 else -1.0
      } else {
        val slope = Math.abs(dy / dx)
        if (slope * w > h) {
          if (dy < 0) { h = -h }
          h
        } else {
          if (dx < 0) { w = -w }
          val result = w * slope
          if (dy < 0) -result else result
        }
      }

    Point(x + sx, y + sy)
  }

  /** Buckets every ranked node into its layer.
    *
    * Ports dagre lib/util.js `buildLayerMatrix()`, which buckets each node with a defined rank into `layering[rank]` at index `node.order`. SSG guards with `hasRank` (dagre's
    * `_.has`/`!_.isUndefined(rank)` presence check) so the -1 sentinel of unranked compound parents is excluded; by the time this runs (Position, after normalizeRanks) every real rank is already >=
    * 0, so the bounds check `rank <= mr` keeps the index in range. Replacing the former `rank >= 0` positivity filter with `hasRank` keeps negative-rank handling consistent across the normalization
    * path (ISS-1198).
    */
  def buildLayerMatrix(g: Graph[NodeLabel, EdgeLabel]): Array[Array[String]] = {
    val mr = maxRank(g)
    if (mr < 0) { Array.empty[Array[String]] }
    else {
      val layers = Array.fill(mr + 1)(mutable.ArrayBuffer.empty[String])
      for (v <- g.nodes()) {
        val node = g.node(v)
        if (hasRank(g, v) && node.rank >= 0 && node.rank <= mr) {
          layers(node.rank) += v
        }
      }
      layers.map { layer =>
        layer.sortBy(v => g.node(v).order).toArray
      }
    }
  }

  /** Removes empty ranks inserted by nesting-graph border-node spacing.
    *
    * Faithful port of dagre lib/util.js `removeEmptyRanks()`. Empty layers whose index is NOT a multiple of `nodeRankFactor` are collapsed by shifting later nodes up. This undoes the
    * `minlen * nodeSep` spacing that `Nesting.run` introduced, removing ranks that only existed to separate border nodes from real nodes.
    */
  def removeEmptyRanks(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    // Ranks may not start at 0, so we need to offset them. dagre takes the min
    // over every node's rank; SSG restricts to ranked nodes (`hasRank`) so the
    // -1 sentinel that compound parents carry — they are excluded from ranking
    // by `simplify` — is not mistaken for a real rank (ISS-1198). Ranked nodes
    // may legitimately be negative (longestPath), so the offset can be < 0.
    val offset =
      g.nodes().filter(v => hasRank(g, v)).map(v => g.node(v).rank).minOption.getOrElse(0)

    // Build layer arrays indexed by rank (offset-adjusted).
    val layers = mutable.Map.empty[Int, mutable.ArrayBuffer[String]]
    for (v <- g.nodes()) {
      val node = g.node(v)
      if (hasRank(g, v)) {
        val rank = node.rank - offset
        node.rank = rank
        layers.getOrElseUpdate(rank, mutable.ArrayBuffer.empty) += v
      }
    }

    val nodeRankFactor = g.graph[GraphLabel]().nodeRankFactor
    if (nodeRankFactor > 0) {
      val maxRank = if (layers.isEmpty) 0 else layers.keys.max
      var delta   = 0
      for (i <- 0 to maxRank)
        layers.get(i) match {
          case None =>
            // Empty layer — remove it unless it aligns with the nodeRankFactor
            // grid (those are "real" rank positions that must be preserved).
            if (i % nodeRankFactor.toInt != 0) {
              delta -= 1
            }
          case Some(vs) =>
            if (delta != 0) {
              for (v <- vs)
                g.node(v).rank += delta
            }
        }
    }
  }

  def addBorderNode(
    g:      Graph[NodeLabel, EdgeLabel],
    prefix: String,
    rank:   Int,
    order:  Int
  ): String = {
    val node = new NodeLabel
    node.width = 0
    node.height = 0
    node.rank = rank
    node.order = order
    addDummyNode(g, "border", node, prefix + uniqueId("_b"))
  }

  def maxRank(g: Graph[NodeLabel, EdgeLabel]): Int = {
    var max = -1
    for (v <- g.nodes()) {
      val r = g.node(v).rank
      if (r > max) { max = r }
    }
    max
  }

  def partition[T: scala.reflect.ClassTag](
    items: Array[T],
    fn:    T => Boolean
  ): (Array[T], Array[T]) = {
    val lhs = mutable.ArrayBuffer.empty[T]
    val rhs = mutable.ArrayBuffer.empty[T]
    for (item <- items)
      if (fn(item)) lhs += item else rhs += item
    (lhs.toArray, rhs.toArray)
  }

  def range(start: Int, limit: Int): Array[Int] =
    (start until limit).toArray

  def range(limit: Int): Array[Int] =
    (0 until limit).toArray

  /** Shifts ranks so the minimum is 0.
    *
    * Faithful port of dagre lib/util.js `normalizeRanks()`:
    * {{{
    *   var min = Math.min(...nodes.map(v => g.node(v).rank));
    *   nodes.forEach(v => { var node = g.node(v);
    *     if (_.has(node, "rank")) node.rank -= min; });
    * }}}
    * `min` is taken over EVERY ranked node (including negatives that `Rank.longestPath` assigns — sinks at 0, sources negative) and the shift is applied to EVERY ranked node. The earlier `rank >= 0`
    * filter never normalized those negatives, so they (and the nodes Position then never laid out) overlapped at the default center coordinates (ISS-1198). The presence predicate is `hasRank`
    * (dagre's `_.has(node, "rank")`), not a positivity test, so legitimately-negative ranks — including the value -1 — participate.
    */
  def normalizeRanks(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    val minRank =
      g.nodes().filter(v => hasRank(g, v)).map(v => g.node(v).rank).minOption.getOrElse(0)
    for (v <- g.nodes()) {
      val node = g.node(v)
      if (hasRank(g, v)) {
        node.rank = node.rank - minRank
      }
    }
  }

  def successorWeights(g: Graph[NodeLabel, EdgeLabel]): mutable.Map[String, mutable.Map[String, Double]] = {
    val result = mutable.Map.empty[String, mutable.Map[String, Double]]
    for (v <- g.nodes()) {
      val sucs = mutable.Map.empty[String, Double]
      for (e <- g.outEdges(v).getOrElse(Array.empty))
        sucs(e.w) = sucs.getOrElse(e.w, 0.0) + g.edge(e).weight
      result(v) = sucs
    }
    result
  }

  def predecessorWeights(g: Graph[NodeLabel, EdgeLabel]): mutable.Map[String, mutable.Map[String, Double]] = {
    val result = mutable.Map.empty[String, mutable.Map[String, Double]]
    for (v <- g.nodes()) {
      val preds = mutable.Map.empty[String, Double]
      for (e <- g.inEdges(v).getOrElse(Array.empty))
        preds(e.v) = preds.getOrElse(e.v, 0.0) + g.edge(e).weight
      result(v) = preds
    }
    result
  }
}
