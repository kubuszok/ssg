/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Original source: mermaid treemap diagram
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package treemap

import scala.collection.mutable

/** A treemap node (may contain children). */
final case class TreemapNode(label: String, value: Double, children: mutable.ArrayBuffer[TreemapNode] = mutable.ArrayBuffer.empty) {
  def totalValue: Double = if (children.isEmpty) value else children.foldLeft(0.0)(_ + _.totalValue)
}

/** Mutable database for treemap diagram data. */
final class TreemapDb {

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  val roots: mutable.ArrayBuffer[TreemapNode] = mutable.ArrayBuffer.empty

  def addRoot(label: String, value: Double): TreemapNode = {
    val node = TreemapNode(label, value); roots += node; node
  }

  def clear(): Unit = { title = ""; accTitle = ""; accDescription = ""; roots.clear() }
}
