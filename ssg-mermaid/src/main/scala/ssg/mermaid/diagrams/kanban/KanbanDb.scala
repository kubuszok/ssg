/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * SSG-native implementation of the Kanban board data model.
 * Follows the Mermaid diagram API pattern (Db/Parser/Renderer/Styles).
 * Original Mermaid author: Knut Sveidqvist and contributors
 * Original license: MIT
 */
package ssg
package mermaid
package diagrams
package kanban

import scala.collection.mutable

/** A card in a Kanban column. */
final case class KanbanCard(id: String, label: String, priority: String = "")

/** A column in a Kanban board. */
final case class KanbanColumn(id: String, label: String, cards: mutable.ArrayBuffer[KanbanCard] = mutable.ArrayBuffer.empty)

/** Mutable database for Kanban board data. */
final class KanbanDb {

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  val columns: mutable.ArrayBuffer[KanbanColumn] = mutable.ArrayBuffer.empty

  def addColumn(id: String, label: String): KanbanColumn = {
    val col = KanbanColumn(id, label)
    columns += col
    col
  }

  def addCard(columnId: String, id: String, label: String, priority: String = ""): Unit =
    columns.find(_.id == columnId).foreach { col =>
      col.cards += KanbanCard(id, label, priority)
    }

  def addCardToLast(id: String, label: String, priority: String = ""): Unit =
    if (columns.nonEmpty) {
      columns.last.cards += KanbanCard(id, label, priority)
    }

  def clear(): Unit = {
    title = ""; accTitle = ""; accDescription = ""; columns.clear()
  }
}
