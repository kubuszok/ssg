/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Layout label types used by the dagre Sugiyama algorithm.
 *
 * Original source: dagre (dagrejs/dagre)
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
import ssg.graphs.commons.layout.graph.EdgeObj

final case class Point(x: Double, y: Double)

final case class SelfEdgeInfo(e: EdgeObj, label: EdgeLabel)

class NodeLabel {
  var width:        Double                            = 0
  var height:       Double                            = 0
  var x:            Double                            = 0
  var y:            Double                            = 0
  var rank:         Int                               = -1
  var order:        Int                               = -1
  var dummy:        Nullable[String]                  = Nullable.Null
  var borderLeft:   mutable.Map[Int, String]          = mutable.Map.empty
  var borderRight:  mutable.Map[Int, String]          = mutable.Map.empty
  var borderTop:    String                            = ""
  var borderBottom: String                            = ""
  var edgeLabel:    Nullable[EdgeLabel]               = Nullable.Null
  var edgeObj:      Nullable[EdgeObj]                 = Nullable.Null
  var labelRank:    Int                               = 0
  var minRank:      Int                               = Int.MaxValue
  var maxRank:      Int                               = Int.MinValue
  var padding:      Double                            = 0
  var selfEdges:    mutable.ArrayBuffer[SelfEdgeInfo] = mutable.ArrayBuffer.empty
  var label:        String                            = ""
  var low:          Int                               = 0
  var lim:          Int                               = 0
  var parent:       Nullable[String]                  = Nullable.Null
}

class EdgeLabel {
  var points:      Array[Point] = Array.empty
  var x:           Double       = 0
  var y:           Double       = 0
  var width:       Double       = 0
  var height:      Double       = 0
  var weight:      Double       = 1
  var minlen:      Int          = 1
  var labelpos:    String       = "r"
  var labeloffset: Double       = 10
  var forwardName: String       = ""
  var reverseName: String       = ""
  var reversed:    Boolean      = false
  var nestingEdge: Boolean      = false
  var cutvalue:    Int          = 0
  var labelRank:   Int          = 0
}

class GraphLabel {
  var rankdir:        String                      = "TB"
  var nodesep:        Double                      = 50
  var edgesep:        Double                      = 10
  var ranksep:        Double                      = 50
  var marginx:        Double                      = 0
  var marginy:        Double                      = 0
  var width:          Double                      = 0
  var height:         Double                      = 0
  var acyclicer:      String                      = "greedy"
  var ranker:         String                      = "network-simplex"
  var nestingRoot:    String                      = ""
  var nodeRankFactor: Double                      = 0
  var dummyChains:    mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty
}
