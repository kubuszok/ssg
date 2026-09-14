/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/sankey/sankeyDb.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces module-level mutable state with mutable class
 *   Idiom: Mutable collections for accumulation during parsing
 *   Renames: sankeyDb module functions -> SankeyDb methods
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package sankey

import scala.collection.mutable

/** A flow (link) between two nodes in a Sankey diagram.
  *
  * @param source
  *   the source node name
  * @param target
  *   the target node name
  * @param value
  *   the flow value (width proportional to this)
  */
final case class SankeyFlow(source: String, target: String, value: Double)

/** Mutable database for Sankey diagram data. */
final class SankeyDb {

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  val nodes: mutable.LinkedHashSet[String]   = mutable.LinkedHashSet.empty
  val flows: mutable.ArrayBuffer[SankeyFlow] = mutable.ArrayBuffer.empty

  /** Adds a flow between two nodes. Both nodes are registered if not already present. */
  def addFlow(source: String, target: String, value: Double): Unit = {
    nodes += source
    nodes += target
    flows += SankeyFlow(source, target, value)
  }

  /** Returns the total value flowing out of the given node. */
  def outflowFor(node: String): Double =
    flows.filter(_.source == node).foldLeft(0.0)(_ + _.value)

  /** Returns the total value flowing into the given node. */
  def inflowFor(node: String): Double =
    flows.filter(_.target == node).foldLeft(0.0)(_ + _.value)

  /** Clears all state. */
  def clear(): Unit = {
    title = ""
    accTitle = ""
    accDescription = ""
    nodes.clear()
    flows.clear()
  }
}
