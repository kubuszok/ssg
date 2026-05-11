/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Radial tree layout: places the root at the center with children
 * on concentric rings, distributing angular wedges proportional
 * to subtree size.
 */
package ssg
package graphs
package commons
package layout
package radial

import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

import ssg.graphs.commons.layout.dagre.{ EdgeLabel, NodeLabel, Point }
import ssg.graphs.commons.layout.graph.{ Graph, GraphAlgorithms }

object RadialLayout {

  def layout(g: Graph[NodeLabel, EdgeLabel], ringSep: Double = 100, startAngle: Double = 0): Unit = {
    val allNodes = g.nodes()
    if (allNodes.length == 1) {
      val label = g.node(allNodes(0))
      label.x = 0
      label.y = 0
      setEdgePoints(g)
    } else if (allNodes.length > 1) {
      val comps = GraphAlgorithms.components(g)
      if (comps.length == 1) {
        layoutTree(g, allNodes, ringSep, startAngle, startAngle + 2.0 * scala.math.Pi)
      } else {
        layoutDisconnected(g, comps, ringSep, startAngle)
      }
      setEdgePoints(g)
    }
  }

  private def layoutDisconnected(
    g:          Graph[NodeLabel, EdgeLabel],
    comps:      Array[Array[String]],
    ringSep:    Double,
    startAngle: Double
  ): Unit = {
    var offsetX = 0.0
    for (comp <- comps) {
      layoutTree(g, comp, ringSep, startAngle, startAngle + 2.0 * scala.math.Pi)
      val maxRadius = comp.length.toDouble * ringSep * 0.5
      for (v <- comp) {
        val n = g.node(v)
        n.x = n.x + offsetX
      }
      offsetX += maxRadius * 2.0 + ringSep
    }
  }

  private def layoutTree(
    g:          Graph[NodeLabel, EdgeLabel],
    nodeIds:    Array[String],
    ringSep:    Double,
    wedgeStart: Double,
    wedgeEnd:   Double
  ): Unit = {
    val nodeSet = mutable.HashSet.from(nodeIds)

    val root = findRoot(g, nodeIds, nodeSet)

    // BFS to assign depths and build the BFS tree
    val depth       = mutable.HashMap.empty[String, Int]
    val bfsChildren = mutable.HashMap.empty[String, mutable.ArrayBuffer[String]]
    val queue       = mutable.ArrayDeque.empty[String]

    depth(root) = 0
    bfsChildren(root) = mutable.ArrayBuffer.empty
    queue += root

    while (queue.nonEmpty) {
      val v         = queue.removeHead()
      val succs     = g.successors(v).fold(Array.empty[String])(identity)
      val preds     = g.predecessors(v).fold(Array.empty[String])(identity)
      val neighbors = (succs ++ preds).distinct
      for (w <- neighbors)
        if (nodeSet.contains(w) && !depth.contains(w)) {
          depth(w) = depth(v) + 1
          bfsChildren.getOrElseUpdate(v, mutable.ArrayBuffer.empty) += w
          bfsChildren(w) = mutable.ArrayBuffer.empty
          queue += w
        }
    }

    // Compute subtree sizes for wedge allocation
    val subtreeSize = mutable.HashMap.empty[String, Int]
    computeSubtreeSize(root, bfsChildren, subtreeSize)

    assignPositions(g, root, depth, bfsChildren, subtreeSize, ringSep, wedgeStart, wedgeEnd)
  }

  private def findRoot(
    g:       Graph[NodeLabel, EdgeLabel],
    nodeIds: Array[String],
    nodeSet: mutable.HashSet[String]
  ): String =
    boundary {
      for (v <- nodeIds) {
        val preds = g.predecessors(v).fold(Array.empty[String])(identity)
        if (preds.forall(p => !nodeSet.contains(p))) {
          break(v)
        }
      }
      nodeIds(0)
    }

  private def computeSubtreeSize(
    v:           String,
    bfsChildren: mutable.HashMap[String, mutable.ArrayBuffer[String]],
    subtreeSize: mutable.HashMap[String, Int]
  ): Int = {
    val children = bfsChildren.getOrElse(v, mutable.ArrayBuffer.empty)
    var size     = 1
    for (child <- children)
      size += computeSubtreeSize(child, bfsChildren, subtreeSize)
    subtreeSize(v) = size
    size
  }

  private def assignPositions(
    g:           Graph[NodeLabel, EdgeLabel],
    v:           String,
    depth:       mutable.HashMap[String, Int],
    bfsChildren: mutable.HashMap[String, mutable.ArrayBuffer[String]],
    subtreeSize: mutable.HashMap[String, Int],
    ringSep:     Double,
    wedgeStart:  Double,
    wedgeEnd:    Double
  ): Unit = {
    val label    = g.node(v)
    val d        = subtreeSize.getOrElse(v, 1)
    val children = bfsChildren.getOrElse(v, mutable.ArrayBuffer.empty)
    val angle    = (wedgeStart + wedgeEnd) / 2.0
    val depthV   = depth.getOrElse(v, 0)
    val radius   = ringSep * depthV
    label.x = radius * scala.math.cos(angle)
    label.y = radius * scala.math.sin(angle)

    if (children.nonEmpty) {
      val childrenTotalSize = d - 1
      if (childrenTotalSize > 0) {
        var currentAngle = wedgeStart
        val wedgeWidth   = wedgeEnd - wedgeStart
        for (child <- children) {
          val childSize  = subtreeSize.getOrElse(child, 1)
          val childWedge = wedgeWidth * childSize.toDouble / childrenTotalSize
          assignPositions(g, child, depth, bfsChildren, subtreeSize, ringSep, currentAngle, currentAngle + childWedge)
          currentAngle += childWedge
        }
      }
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
