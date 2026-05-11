/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Graph layout and SVG infrastructure — Scala 3 port
 *
 * Ported from: dagre-d3-es/src/graphlib/graph.js (edge object)
 * Original author: Chris Pettitt and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: JS plain object {v, w, name} -> final case class EdgeObj
 *   Idiom: name is Nullable[String] instead of undefined
 *   Renames: edgeObj -> EdgeObj
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package graphs
package commons
package layout
package graph

import ssg.commons.Nullable

/** An edge object that uniquely identifies an edge in a [[Graph]].
  *
  * In the original graphlib, edges are plain JS objects `{v, w, name}`. Here we use a final case class with `name` as `Nullable[String]` (empty when no name is provided, i.e., non-multigraph edges or
  * multigraph edges with auto-generated names).
  */
final case class EdgeObj(v: String, w: String, name: Nullable[String] = Nullable.Null) {

  /** Returns the string ID used as a key in edge maps.
    *
    * Format matches the original EDGE_KEY_DELIM separator.
    */
  def toId(isDirected: Boolean): String =
    EdgeObj.edgeArgsToId(isDirected, v, w, name)
}

object EdgeObj {

  /** Delimiter used between v, w, and name components in edge IDs. Matches the original EDGE_KEY_DELIM = 0x01 (SOH).
    */
  private[graph] val EdgeKeyDelim: Char = 1.toChar

  /** Default edge name used when no name is specified. Matches the original DEFAULT_EDGE_NAME = 0x00 (NUL).
    */
  private[graph] val DefaultEdgeName: String = 0.toChar.toString

  /** Sentinel node ID used for the root of compound graph hierarchies. Matches the original GRAPH_NODE = 0x00 (NUL).
    */
  private[graph] val GraphNode: String = 0.toChar.toString

  /** Builds an edge ID string from components.
    *
    * For undirected graphs, the smaller string (lexicographically) is always placed first to ensure consistent edge identity regardless of argument order.
    */
  private[graph] def edgeArgsToId(
    isDirected: Boolean,
    v:          String,
    w:          String,
    name:       Nullable[String]
  ): String = {
    var vv = v
    var ww = w
    if (!isDirected && vv > ww) {
      val tmp = vv
      vv = ww
      ww = tmp
    }
    vv + EdgeKeyDelim + ww + EdgeKeyDelim + name.getOrElse(DefaultEdgeName)
  }

  /** Builds an [[EdgeObj]] from components, normalizing vertex order for undirected graphs. */
  private[graph] def edgeArgsToObj(
    isDirected: Boolean,
    v:          String,
    w:          String,
    name:       Nullable[String]
  ): EdgeObj = {
    var vv = v
    var ww = w
    if (!isDirected && vv > ww) {
      val tmp = vv
      vv = ww
      ww = tmp
    }
    EdgeObj(vv, ww, name)
  }
}
