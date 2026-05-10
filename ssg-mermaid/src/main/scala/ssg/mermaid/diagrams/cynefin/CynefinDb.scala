/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Original source: mermaid cynefin framework diagram
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package cynefin

import scala.collection.mutable

/** An item placed in a Cynefin domain. */
final case class CynefinItem(label: String, domain: String)

/** Mutable database for Cynefin diagram data. */
final class CynefinDb {

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  /** Items in each domain. */
  val items: mutable.ArrayBuffer[CynefinItem] = mutable.ArrayBuffer.empty

  def addItem(label: String, domain: String): Unit = items += CynefinItem(label, domain)

  def itemsInDomain(domain: String): Seq[CynefinItem] =
    items.filter(_.domain.toLowerCase == domain.toLowerCase).toSeq

  def clear(): Unit = { title = ""; accTitle = ""; accDescription = ""; items.clear() }
}
