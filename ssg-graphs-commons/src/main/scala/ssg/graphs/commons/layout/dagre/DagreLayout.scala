/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Main entry point for the dagre Sugiyama graph layout algorithm.
 * Computes x/y positions for all nodes and edge bend points.
 *
 * Original source: dagre (dagrejs/dagre) lib/layout.js
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

import lowlevel.Nullable
import ssg.graphs.commons.layout.graph.Graph

object DagreLayout {

  def layout(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    DagreUtil.resetIdCounter()

    // The phase order below mirrors dagre's `runLayout()` in lib/layout.js
    // exactly (steps 1..27). The ordering is load-bearing: `nestingGraph.run`
    // MUST precede `rank`, because the nesting root it inserts connects all
    // otherwise-disconnected components of a compound graph. Without it
    // `Rank.feasibleTree` can never find a tight tree edge spanning a
    // disconnected component and spins forever (ISS-1195).

    // dagre 1: makeSpaceForEdgeLabels
    makeSpaceForEdgeLabels(g)

    // dagre 2: removeSelfEdges (stash them for later)
    removeSelfEdges(g)

    // dagre 3: acyclic.run — cycle removal
    Acyclic.run(g)

    // dagre 4: nestingGraph.run — compound graph nesting (BEFORE rank, so the
    // nesting root joins all components for feasibleTree)
    Nesting.run(g)

    // dagre 5: rank — rank assignment (runs on the now-connected graph)
    Rank.rank(g)

    // dagre 6: injectEdgeLabelProxies — create proxy nodes for edge labels
    // so they participate in rank assignment and ordering
    injectEdgeLabelProxies(g)

    // dagre 7: removeEmptyRanks
    DagreUtil.removeEmptyRanks(g)

    // dagre 8: nestingGraph.cleanup (after removeEmptyRanks, before normalizeRanks)
    Nesting.cleanup(g)

    // dagre 9: normalizeRanks — ensure ranks start at 0
    DagreUtil.normalizeRanks(g)

    // dagre 10: assignRankMinMax — assign minRank/maxRank on compound nodes
    assignRankMinMax(g)

    // dagre 11: removeEdgeLabelProxies
    removeEdgeLabelProxies(g)

    // dagre 12: normalize.run — edge normalization (insert dummy nodes for long edges)
    Normalize.run(g)

    // dagre 13: parentDummyChains — handle dummy chains crossing subgraph borders
    ParentDummyChains.run(g)

    // dagre 14: addBorderSegments — add border segments to compound graphs
    AddBorderSegments.run(g)

    // dagre 15: order — crossing minimization
    Order.order(g)

    // dagre 16: insertSelfEdges — insert self-edges back
    insertSelfEdges(g)

    // dagre 17: coordinateSystem.adjust — coordinate system adjustment for rankdir
    CoordinateSystem.adjust(g)

    // dagre 18: position — position assignment
    Position.position(g)

    // dagre 19: positionSelfEdges — position self-edge labels
    positionSelfEdges(g)

    // dagre 20: removeBorderNodes — remove border nodes from nesting
    removeBorderNodes(g)

    // dagre 21: normalize.undo — undo normalization (remove dummy nodes, set edge points)
    Normalize.undo(g)

    // dagre 22: fixupEdgeLabelCoords — fix up edge label coordinates
    fixupEdgeLabelCoords(g)

    // dagre 23: undoCoordinateSystemAdjust — undo coordinate system adjustment
    CoordinateSystem.undo(g)

    // dagre 24: translateGraph — translate graph to positive coordinates
    translateGraph(g)

    // dagre 25: assignNodeIntersects — assign edge endpoints at node intersections
    assignNodeIntersects(g)

    // dagre 26: reversePointsForReversedEdges
    reversePointsForReversedEdges(g)

    // dagre 27: acyclic.undo — undo cycle removal
    Acyclic.undo(g)
  }

  /** Creates proxy nodes for edge labels so they participate in rank assignment and ordering. The proxy node takes the place of the label within the ranking.
    *
    * Ports `injectEdgeLabelProxies()` from layout.js.
    */
  private def injectEdgeLabelProxies(g: Graph[NodeLabel, EdgeLabel]): Unit =
    for (e <- g.edges()) {
      val label = g.edge(e)
      if (label.width != 0 || label.height != 0) {
        val srcNode = g.node(e.v)
        val tgtNode = g.node(e.w)
        val attrs   = new NodeLabel
        attrs.rank = (tgtNode.rank - srcNode.rank) / 2 + srcNode.rank
        attrs.edgeLabel = Nullable(label)
        attrs.edgeObj = Nullable(e)
        attrs.width = label.width
        attrs.height = label.height
        label.labelRank = attrs.rank
        DagreUtil.addDummyNode(g, "edge-proxy", attrs, s"_lp${e.v}_${e.w}")
      }
    }

  /** Removes edge label proxy nodes after ordering is complete.
    *
    * Ports `removeEdgeLabelProxies()` from layout.js.
    */
  private def removeEdgeLabelProxies(g: Graph[NodeLabel, EdgeLabel]): Unit =
    for (v <- g.nodes()) {
      val node = g.node(v)
      if (node.dummy.exists(_ == "edge-proxy")) {
        node.edgeLabel.foreach { el =>
          el.labelRank = node.rank
        }
        g.removeNode(v)
      }
    }

  /** Sets minRank and maxRank for compound nodes based on the ranks of their descendant nodes.
    *
    * Ports `assignRankMinMax()` from layout.js.
    */
  private def assignRankMinMax(g: Graph[NodeLabel, EdgeLabel]): Unit =
    for (v <- g.nodes()) {
      val children = g.children(v)
      if (children.nonEmpty) {
        val node = g.node(v)
        var minR = Int.MaxValue
        var maxR = Int.MinValue
        for (child <- children) {
          val childNode = g.node(child)
          if (childNode.rank >= 0) {
            if (childNode.rank < minR) minR = childNode.rank
            if (childNode.rank > maxR) maxR = childNode.rank
          }
          // Also check child's own min/max (for nested subgraphs)
          if (childNode.minRank < minR) minR = childNode.minRank
          if (childNode.maxRank > maxR) maxR = childNode.maxRank
        }
        if (minR < Int.MaxValue) node.minRank = minR
        if (maxR > Int.MinValue) node.maxRank = maxR
      }
    }

  /** Removes border nodes from the graph after layout is complete.
    *
    * Ports `removeBorderNodes()` from layout.js.
    */
  private def removeBorderNodes(g: Graph[NodeLabel, EdgeLabel]): Unit =
    for (v <- g.nodes()) {
      val node = g.node(v)
      if (node.dummy.exists(_ == "border")) {
        g.removeNode(v)
      }
    }

  private def makeSpaceForEdgeLabels(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    val gl = g.graph[GraphLabel]()
    gl.ranksep = gl.ranksep / 2.0

    for (e <- g.edges()) {
      val label = g.edge(e)
      label.minlen = label.minlen * 2
      if (label.labelpos != "c") {
        if (gl.rankdir == "TB" || gl.rankdir == "BT") {
          label.width = label.width + label.labeloffset
        } else {
          label.height = label.height + label.labeloffset
        }
      }
    }
  }

  private def removeSelfEdges(g: Graph[NodeLabel, EdgeLabel]): Unit =
    for (e <- g.edges())
      if (e.v == e.w) {
        val node = g.node(e.v)
        node.selfEdges += SelfEdgeInfo(e, g.edge(e))
        g.removeEdge(e)
      }

  private def insertSelfEdges(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    val layering = DagreUtil.buildLayerMatrix(g)
    for (layer <- layering) {
      var orderShift = 0
      for (i <- layer.indices) {
        val v    = layer(i)
        val node = g.node(v)
        node.order = i + orderShift
        for (selfEdge <- node.selfEdges) {
          val attrs = new NodeLabel
          attrs.width = selfEdge.label.width
          attrs.height = selfEdge.label.height
          attrs.rank = node.rank
          orderShift += 1
          attrs.order = i + orderShift
          attrs.edgeLabel = Nullable(selfEdge.label)
          attrs.edgeObj = Nullable(selfEdge.e)
          val dummyName  = DagreUtil.addDummyNode(g, "selfedge", attrs, "")
          val dummyEdge1 = new EdgeLabel
          dummyEdge1.weight = selfEdge.label.weight
          dummyEdge1.minlen = 1
          g.setEdge(v, dummyName, dummyEdge1)
          val dummyEdge2 = new EdgeLabel
          dummyEdge2.weight = selfEdge.label.weight
          dummyEdge2.minlen = 1
          g.setEdge(dummyName, v, dummyEdge2)
        }
      }
    }
  }

  private def positionSelfEdges(g: Graph[NodeLabel, EdgeLabel]): Unit =
    for (v <- g.nodes()) {
      val node = g.node(v)
      if (node.dummy.exists(_ == "selfedge")) {
        val selfNode = g.node(node.edgeObj.get.v)
        val x        = selfNode.x + selfNode.width / 2.0
        val y        = selfNode.y
        val dx       = node.x - x
        val points   = Array(
          Point(x + 2.0 * dx / 3.0, y - node.edgeLabel.get.height),
          Point(x + 5.0 * dx / 6.0, y - node.edgeLabel.get.height),
          Point(x + dx, y),
          Point(x + 5.0 * dx / 6.0, y + node.edgeLabel.get.height),
          Point(x + 2.0 * dx / 3.0, y + node.edgeLabel.get.height)
        )
        node.edgeLabel.get.points = points
        node.edgeLabel.get.x = node.x
        node.edgeLabel.get.y = node.y
        g.removeNode(v)
      }
    }

  private def fixupEdgeLabelCoords(g: Graph[NodeLabel, EdgeLabel]): Unit =
    for (e <- g.edges()) {
      val label = g.edge(e)
      if (label.x != 0 || label.y != 0) {
        // Label position already set by dummy node placement
      } else if (label.points.nonEmpty) {
        // Place label at midpoint of edge
        val mid = label.points(label.points.length / 2)
        label.x = mid.x
        label.y = mid.y
      }
    }

  private def translateGraph(g: Graph[NodeLabel, EdgeLabel]): Unit = {
    var minX = Double.MaxValue
    var minY = Double.MaxValue
    var maxX = Double.MinValue
    var maxY = Double.MinValue

    for (v <- g.nodes()) {
      val node   = g.node(v)
      val left   = node.x - node.width / 2.0
      val right  = node.x + node.width / 2.0
      val top    = node.y - node.height / 2.0
      val bottom = node.y + node.height / 2.0
      if (left < minX) minX = left
      if (right > maxX) maxX = right
      if (top < minY) minY = top
      if (bottom > maxY) maxY = bottom
    }

    for (e <- g.edges()) {
      val label = g.edge(e)
      for (p <- label.points) {
        if (p.x < minX) minX = p.x
        if (p.x > maxX) maxX = p.x
        if (p.y < minY) minY = p.y
        if (p.y > maxY) maxY = p.y
      }
    }

    val gl = g.graph[GraphLabel]()
    val dx = gl.marginx - minX
    val dy = gl.marginy - minY

    for (v <- g.nodes()) {
      val node = g.node(v)
      node.x = node.x + dx
      node.y = node.y + dy
    }

    for (e <- g.edges()) {
      val label = g.edge(e)
      label.points = label.points.map(p => Point(p.x + dx, p.y + dy))
      label.x = label.x + dx
      label.y = label.y + dy
    }

    gl.width = maxX - minX + 2 * gl.marginx
    gl.height = maxY - minY + 2 * gl.marginy
  }

  private def assignNodeIntersects(g: Graph[NodeLabel, EdgeLabel]): Unit =
    for (e <- g.edges()) {
      val label = g.edge(e)
      val src   = g.node(e.v)
      val tgt   = g.node(e.w)

      if (label.points.nonEmpty) {
        val p1 = label.points(0)
        val p2 = label.points(label.points.length - 1)

        // Clip start point to source node boundary
        val start = DagreUtil.intersectRect(src, p1)
        // Clip end point to target node boundary
        val end = DagreUtil.intersectRect(tgt, p2)

        label.points = Array(start) ++ label.points.slice(1, label.points.length - 1) ++ Array(end)
      } else {
        // Direct edge with no bend points
        val srcPoint = Point(src.x, src.y)
        val tgtPoint = Point(tgt.x, tgt.y)
        val start    = DagreUtil.intersectRect(src, tgtPoint)
        val end      = DagreUtil.intersectRect(tgt, srcPoint)
        label.points = Array(start, end)
      }
    }

  private def reversePointsForReversedEdges(g: Graph[NodeLabel, EdgeLabel]): Unit =
    for (e <- g.edges()) {
      val label = g.edge(e)
      if (label.reversed) {
        label.points = label.points.reverse
      }
    }
}
