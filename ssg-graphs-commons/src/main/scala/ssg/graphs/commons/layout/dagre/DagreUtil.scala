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

import ssg.commons.Nullable
import ssg.graphs.commons.layout.graph.Graph

object DagreUtil {

  private var idCounter: Int = 0

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

  def simplify(g: Graph[NodeLabel, EdgeLabel]): Graph[NodeLabel, EdgeLabel] = {
    val simplified = new Graph[NodeLabel, EdgeLabel](isDirected = true, isMultigraph = false)
    simplified.setGraph(g.graph[GraphLabel]())
    for (v <- g.nodes())
      simplified.setNode(v, g.node(v))
    for (e <- g.edges()) {
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

  def buildLayerMatrix(g: Graph[NodeLabel, EdgeLabel]): Array[Array[String]] = {
    val mr = maxRank(g)
    if (mr < 0) { Array.empty[Array[String]] }
    else {
      val layers = Array.fill(mr + 1)(mutable.ArrayBuffer.empty[String])
      for (v <- g.nodes()) {
        val node = g.node(v)
        if (node.rank >= 0 && node.rank <= mr) {
          layers(node.rank) += v
        }
      }
      layers.map { layer =>
        layer.sortBy(v => g.node(v).order).toArray
      }
    }
  }

  def removeEmptyRanks(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    val offset = g.nodes().filter(v => g.node(v).rank >= 0).map(v => g.node(v).rank).minOption.getOrElse(0)
    val layers = mutable.Map.empty[Int, Boolean]
    for (v <- g.nodes()) {
      val node = g.node(v)
      if (node.rank >= 0) {
        node.rank = node.rank - offset
        layers(node.rank) = true
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

  def normalizeRanks(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    val minRank = g.nodes().filter(v => g.node(v).rank >= 0).map(v => g.node(v).rank).minOption.getOrElse(0)
    for (v <- g.nodes()) {
      val node = g.node(v)
      if (node.rank >= 0) {
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
