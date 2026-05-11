/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * AST types for the Graphviz DOT language.
 */
package ssg
package graphviz
package parse

enum DotGraphType {
  case Graph
  case Digraph
}

final case class DotAttr(key: String, value: String)

final case class DotNodeId(id: String, port: Option[String] = None, compass: Option[String] = None)

sealed trait DotStmt

final case class DotNodeStmt(id: DotNodeId, attrs: Seq[DotAttr] = Seq.empty) extends DotStmt

final case class DotEdgeStmt(
  nodes: Seq[DotNodeId],
  attrs: Seq[DotAttr] = Seq.empty
) extends DotStmt

final case class DotAttrStmt(target: DotAttrTarget, attrs: Seq[DotAttr]) extends DotStmt

enum DotAttrTarget {
  case Graph, Node, Edge
}

final case class DotSubgraphStmt(
  id:    Option[String],
  stmts: Seq[DotStmt]
) extends DotStmt

final case class DotAssignStmt(key: String, value: String) extends DotStmt

final case class DotGraph(
  strict:    Boolean,
  graphType: DotGraphType,
  id:        Option[String],
  stmts:     Seq[DotStmt]
) {

  def isDirected: Boolean = graphType == DotGraphType.Digraph
}
