/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Graph layout and SVG infrastructure — Scala 3 port
 *
 * Ported from: dagre-d3-es/src/graphlib/json.js
 * Original author: Chris Pettitt and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: JS functions write/read -> Scala object methods writeGraph/readGraph
 *   Idiom: Scala case classes for serialization instead of plain JS objects
 *   Renames: write -> writeGraph, read -> readGraph
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package graphs
package commons
package layout
package graph

import lowlevel.Nullable

/** JSON-like serialization and deserialization for [[Graph]] instances.
  *
  * Provides `writeGraph` to produce a serializable representation and `readGraph` to reconstruct a Graph from that representation.
  */
object GraphJson {

  /** A serializable representation of a graph. */
  final case class GraphData(
    options: GraphOptions,
    nodes:   Array[NodeEntry],
    edges:   Array[EdgeEntry],
    value:   Nullable[Any] = Nullable.Null
  )

  /** Graph creation options. */
  final case class GraphOptions(
    directed:   Boolean,
    multigraph: Boolean,
    compound:   Boolean
  )

  /** A serializable representation of a node. */
  final case class NodeEntry(
    v:      String,
    value:  Nullable[Any] = Nullable.Null,
    parent: Nullable[String] = Nullable.Null
  )

  /** A serializable representation of an edge. */
  final case class EdgeEntry(
    v:     String,
    w:     String,
    name:  Nullable[String] = Nullable.Null,
    value: Nullable[Any] = Nullable.Null
  )

  /** Serializes a graph to a [[GraphData]] representation. */
  def writeGraph(g: Graph[Any, Any]): GraphData = {
    val graphValue: Nullable[Any] = {
      val v = g.graph[Any]()
      if (v == null || Nullable(v).isEmpty) Nullable.Null
      else Nullable(v)
    }

    GraphData(
      options = GraphOptions(
        directed = g.isDirected,
        multigraph = g.isMultigraph,
        compound = g.isCompound
      ),
      nodes = writeNodes(g),
      edges = writeEdges(g),
      value = graphValue
    )
  }

  private def writeNodes(g: Graph[Any, Any]): Array[NodeEntry] =
    g.nodes().map { v =>
      val nodeValue   = g.nodeOpt(v)
      val parentValue = g.parent(v)
      NodeEntry(v = v, value = nodeValue, parent = parentValue)
    }

  private def writeEdges(g: Graph[Any, Any]): Array[EdgeEntry] =
    g.edges().map { e =>
      val edgeValue = g.edgeOpt(e)
      EdgeEntry(v = e.v, w = e.w, name = e.name, value = edgeValue)
    }

  /** Deserializes a [[GraphData]] representation into a [[Graph]]. */
  def readGraph(json: GraphData): Graph[Any, Any] = {
    val g = new Graph[Any, Any](
      isDirected = json.options.directed,
      isMultigraph = json.options.multigraph,
      isCompound = json.options.compound
    )
    json.value.foreach(v => g.setGraph(v))

    json.nodes.foreach { entry =>
      entry.value.fold(g.setNode(entry.v))(v => g.setNode(entry.v, v))
      entry.parent.foreach(p => g.setParent(entry.v, p))
    }

    json.edges.foreach { entry =>
      val edgeObj = EdgeObj(entry.v, entry.w, entry.name)
      entry.value.fold(g.setEdgeObj(edgeObj))(v => g.setEdgeObj(edgeObj, v))
    }

    g
  }
}
