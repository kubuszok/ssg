/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Coordinate assignment using a simplified Brandes-Kopf algorithm.
 *
 * Original source: dagre (dagrejs/dagre) lib/position
 * Original author: Chris Pettitt
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package layout
package dagre

import ssg.mermaid.layout.graph.Graph

object Position {

  def position(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    positionY(g)
    positionX(g)
  }

  private def positionY(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    val layering = DagreUtil.buildLayerMatrix(g)
    val gl       = g.graph[GraphLabel]()
    val ranksep  = gl.ranksep
    var prevY    = 0.0

    for (layer <- layering) {
      var maxHeight = 0.0
      for (v <- layer) {
        val node = g.node(v)
        node.y = prevY + maxHeight / 2.0
        if (node.height > maxHeight) {
          maxHeight = node.height
        }
      }
      // Set all nodes in this layer to the same y
      for (v <- layer)
        g.node(v).y = prevY + maxHeight / 2.0
      prevY += maxHeight + ranksep
    }
  }

  private def positionX(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    val layering = DagreUtil.buildLayerMatrix(g)
    val gl       = g.graph[GraphLabel]()
    val nodesep  = gl.nodesep

    // Simple left-to-right placement with centering
    for (layer <- layering) {
      var x = 0.0
      for (v <- layer) {
        val node = g.node(v)
        node.x = x + node.width / 2.0
        x += node.width + nodesep
      }
    }

    // Center-align layers relative to the widest layer
    val layerWidths = layering.map { layer =>
      if (layer.isEmpty) 0.0
      else {
        val lastNode = g.node(layer.last)
        lastNode.x + lastNode.width / 2.0
      }
    }
    val maxWidth = if (layerWidths.isEmpty) 0.0 else layerWidths.max

    for (i <- layering.indices) {
      val offset = (maxWidth - layerWidths(i)) / 2.0
      for (v <- layering(i))
        g.node(v).x = g.node(v).x + offset
    }

    // Barycenter-based refinement: shift nodes toward their neighbors' average x
    for (_ <- 0 until 4)
      for (layer <- layering) {
        for (v <- layer) {
          val node     = g.node(v)
          val inEdges  = g.inEdges(v).getOrElse(Array.empty)
          val outEdges = g.outEdges(v).getOrElse(Array.empty)

          if (inEdges.nonEmpty || outEdges.nonEmpty) {
            var sumX  = 0.0
            var count = 0
            for (e <- inEdges) {
              val w = g.edge(e).weight
              sumX += g.node(e.v).x * w
              count += w.toInt
            }
            for (e <- outEdges) {
              val w = g.edge(e).weight
              sumX += g.node(e.w).x * w
              count += w.toInt
            }
            if (count > 0) {
              val targetX = sumX / count
              node.x = targetX
            }
          }
        }

        // Resolve overlaps: ensure minimum separation
        resolveOverlaps(g, layer, nodesep)
      }
  }

  private def resolveOverlaps(
    g:       Graph[NodeLabel, EdgeLabel],
    layer:   Array[String],
    nodesep: Double
  ): Unit =
    if (layer.length > 1) {

      val sorted = layer.sortBy(v => g.node(v).x)
      for (i <- 1 until sorted.length) {
        val prev = g.node(sorted(i - 1))
        val curr = g.node(sorted(i))
        val minX = prev.x + prev.width / 2.0 + nodesep + curr.width / 2.0
        if (curr.x < minX) {
          curr.x = minX
        }
      }
    }
}
