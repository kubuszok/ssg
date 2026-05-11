/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Kamada-Kawai energy-minimization spring layout algorithm.
 * Produces aesthetically pleasing layouts by treating edges as springs
 * with ideal lengths proportional to graph-theoretic distances.
 */
package ssg
package graphs
package commons
package layout
package spring

import scala.collection.mutable

import ssg.graphs.commons.layout.dagre.{ EdgeLabel, NodeLabel, Point }
import ssg.graphs.commons.layout.graph.{ Graph, GraphAlgorithms }

object SpringLayout {

  def layout(g: Graph[NodeLabel, EdgeLabel], width: Double = 600, height: Double = 400): Unit = {
    val allNodes = g.nodes()
    if (allNodes.length == 1) {
      val label = g.node(allNodes(0))
      label.x = width / 2.0
      label.y = height / 2.0
      setEdgePoints(g)
    } else if (allNodes.length > 1) {
      val comps = GraphAlgorithms.components(g)
      if (comps.length == 1) {
        layoutComponent(g, allNodes, width, height)
      } else {
        layoutDisconnected(g, comps, width, height)
      }
      setEdgePoints(g)
    }
  }

  private def layoutDisconnected(
    g:      Graph[NodeLabel, EdgeLabel],
    comps:  Array[Array[String]],
    width:  Double,
    height: Double
  ): Unit = {
    val cols  = scala.math.max(1, scala.math.ceil(scala.math.sqrt(comps.length.toDouble)).toInt)
    val rows  = scala.math.max(1, scala.math.ceil(comps.length.toDouble / cols).toInt)
    val cellW = width / cols
    val cellH = height / rows
    var col   = 0
    var row   = 0

    for (comp <- comps) {
      val cx = col * cellW
      val cy = row * cellH
      layoutComponent(g, comp, cellW, cellH)

      var minX = Double.MaxValue
      var minY = Double.MaxValue
      for (v <- comp) {
        val n = g.node(v)
        if (n.x < minX) minX = n.x
        if (n.y < minY) minY = n.y
      }
      val dx = cx - minX + cellW * 0.1
      val dy = cy - minY + cellH * 0.1
      for (v <- comp) {
        val n = g.node(v)
        n.x = n.x + dx
        n.y = n.y + dy
      }

      col += 1
      if (col >= cols) {
        col = 0
        row += 1
      }
    }
  }

  private def layoutComponent(
    g:       Graph[NodeLabel, EdgeLabel],
    nodeIds: Array[String],
    width:   Double,
    height:  Double
  ): Unit = {
    val n = nodeIds.length
    if (n == 1) {
      val label = g.node(nodeIds(0))
      label.x = width / 2.0
      label.y = height / 2.0
    } else if (n >= 2) {
      layoutComponentCore(g, nodeIds, n, width, height)
    }
  }

  private def layoutComponentCore(
    g:       Graph[NodeLabel, EdgeLabel],
    nodeIds: Array[String],
    n:       Int,
    width:   Double,
    height:  Double
  ): Unit = {
    val idxOf = mutable.HashMap.empty[String, Int]
    for (i <- nodeIds.indices)
      idxOf(nodeIds(i)) = i

    // All-pairs shortest paths via Floyd-Warshall on the component subgraph
    val dist = Array.fill(n, n)(Double.PositiveInfinity)
    for (i <- 0 until n)
      dist(i)(i) = 0.0
    for (e <- g.edges()) {
      val vi = idxOf.get(e.v)
      val wi = idxOf.get(e.w)
      if (vi.isDefined && wi.isDefined) {
        dist(vi.get)(wi.get) = 1.0
        dist(wi.get)(vi.get) = 1.0
      }
    }
    for {
      k <- 0 until n
      i <- 0 until n
      j <- 0 until n
    } {
      val through = dist(i)(k) + dist(k)(j)
      if (through < dist(i)(j)) {
        dist(i)(j) = through
      }
    }

    val area   = width * height
    val idealL = scala.math.sqrt(area / n)

    // Spring constants and ideal distances
    val d         = Array.ofDim[Double](n, n)
    val k         = Array.ofDim[Double](n, n)
    val kStrength = 1.0
    for {
      i <- 0 until n
      j <- 0 until n
    }
      if (i != j) {
        val pathLen = if (dist(i)(j).isInfinite) scala.math.sqrt(n.toDouble) else dist(i)(j)
        d(i)(j) = pathLen * idealL
        k(i)(j) = kStrength / (d(i)(j) * d(i)(j))
      }

    // Initial circular placement
    val posX   = new Array[Double](n)
    val posY   = new Array[Double](n)
    val cx     = width / 2.0
    val cy     = height / 2.0
    val radius = scala.math.min(width, height) * 0.35
    for (i <- 0 until n) {
      val angle = 2.0 * scala.math.Pi * i / n
      posX(i) = cx + radius * scala.math.cos(angle)
      posY(i) = cy + radius * scala.math.sin(angle)
    }

    val maxOuterIter = 300
    val maxInnerIter = 100
    val epsilon      = 1e-4

    var iter      = 0
    var converged = false
    while (iter < maxOuterIter && !converged) {
      var maxDelta = 0.0
      var maxIdx   = 0
      for (m <- 0 until n) {
        val delta = computeDelta(m, n, posX, posY, d, k)
        if (delta > maxDelta) {
          maxDelta = delta
          maxIdx = m
        }
      }

      if (maxDelta < epsilon) {
        converged = true
      } else {
        newtonRaphson(maxIdx, n, posX, posY, d, k, maxInnerIter, epsilon)
        iter += 1
      }
    }

    for (i <- 0 until n) {
      val label = g.node(nodeIds(i))
      label.x = posX(i)
      label.y = posY(i)
    }
  }

  private def computeDelta(
    m:    Int,
    n:    Int,
    posX: Array[Double],
    posY: Array[Double],
    d:    Array[Array[Double]],
    k:    Array[Array[Double]]
  ): Double = {
    var dEdX = 0.0
    var dEdY = 0.0
    for (i <- 0 until n)
      if (i != m) {
        val dx   = posX(m) - posX(i)
        val dy   = posY(m) - posY(i)
        val dist = scala.math.sqrt(dx * dx + dy * dy)
        if (dist > 1e-10) {
          val common = k(m)(i) * (1.0 - d(m)(i) / dist)
          dEdX += common * dx
          dEdY += common * dy
        }
      }
    scala.math.sqrt(dEdX * dEdX + dEdY * dEdY)
  }

  private def newtonRaphson(
    m:       Int,
    n:       Int,
    posX:    Array[Double],
    posY:    Array[Double],
    d:       Array[Array[Double]],
    k:       Array[Array[Double]],
    maxIter: Int,
    epsilon: Double
  ): Unit = {
    var iter  = 0
    var delta = computeDelta(m, n, posX, posY, d, k)
    while (iter < maxIter && delta > epsilon) {
      var dEdX   = 0.0
      var dEdY   = 0.0
      var d2EdX2 = 0.0
      var d2EdY2 = 0.0
      var d2EdXY = 0.0

      for (i <- 0 until n)
        if (i != m) {
          val dx     = posX(m) - posX(i)
          val dy     = posY(m) - posY(i)
          val distSq = dx * dx + dy * dy
          val dist   = scala.math.sqrt(distSq)
          if (dist > 1e-10) {
            val common = k(m)(i) * (1.0 - d(m)(i) / dist)
            dEdX += common * dx
            dEdY += common * dy

            val dCubed = dist * distSq
            val kmidi  = k(m)(i) * d(m)(i)
            d2EdX2 += k(m)(i) - kmidi * dy * dy / dCubed
            d2EdY2 += k(m)(i) - kmidi * dx * dx / dCubed
            d2EdXY += kmidi * dx * dy / dCubed
          }
        }

      val det = d2EdX2 * d2EdY2 - d2EdXY * d2EdXY
      if (scala.math.abs(det) > 1e-10) {
        val deltaX = (d2EdY2 * dEdX - d2EdXY * dEdY) / det
        val deltaY = (d2EdX2 * dEdY - d2EdXY * dEdX) / det
        posX(m) -= deltaX
        posY(m) -= deltaY
      }

      delta = computeDelta(m, n, posX, posY, d, k)
      iter += 1
    }
  }

  private def setEdgePoints(g: Graph[NodeLabel, EdgeLabel]): Unit =
    for (e <- g.edges()) {
      val label = g.edge(e)
      val src   = g.node(e.v)
      val tgt   = g.node(e.w)
      label.points = Array(Point(src.x, src.y), Point(tgt.x, tgt.y))
    }
}
