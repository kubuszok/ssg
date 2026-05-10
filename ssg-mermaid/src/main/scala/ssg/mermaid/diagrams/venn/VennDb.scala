/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Original source: mermaid venn diagram concept
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package venn

import scala.collection.mutable

/** A set in a Venn diagram. */
final case class VennSet(id: String, label: String, size: Double = 1.0)

/** An intersection label. */
final case class VennIntersection(sets: Seq[String], label: String)

/** Mutable database for Venn diagram data. */
final class VennDb {

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  val sets:          mutable.ArrayBuffer[VennSet]          = mutable.ArrayBuffer.empty
  val intersections: mutable.ArrayBuffer[VennIntersection] = mutable.ArrayBuffer.empty

  def addSet(id: String, label: String, size: Double = 1.0): Unit =
    sets += VennSet(id, label, size)

  def addIntersection(setIds: Seq[String], label: String): Unit =
    intersections += VennIntersection(setIds, label)

  def clear(): Unit = {
    title = ""; accTitle = ""; accDescription = ""; sets.clear(); intersections.clear()
  }
}
