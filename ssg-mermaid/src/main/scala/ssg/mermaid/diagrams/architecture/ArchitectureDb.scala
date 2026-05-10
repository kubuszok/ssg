/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * SSG-native implementation of the architecture diagram data model.
 * Follows the Mermaid diagram API pattern (Db/Parser/Renderer/Styles).
 * Original Mermaid author: Knut Sveidqvist and contributors
 * Original license: MIT
 */
package ssg
package mermaid
package diagrams
package architecture

import scala.collection.mutable

/** A service, group, or junction in the architecture diagram. */
final case class ArchNode(id: String, label: String, nodeType: String, group: String = "", icon: String = "")

/** An edge between architecture nodes. */
final case class ArchEdge(fromId: String, toId: String, fromSide: String = "", toSide: String = "", label: String = "")

/** Mutable database for architecture diagram data. */
final class ArchitectureDb {

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  val nodes:  mutable.ArrayBuffer[ArchNode]         = mutable.ArrayBuffer.empty
  val edges:  mutable.ArrayBuffer[ArchEdge]         = mutable.ArrayBuffer.empty
  val groups: mutable.LinkedHashMap[String, String] = mutable.LinkedHashMap.empty // id -> label

  def addService(id: String, label: String, icon: String = "", group: String = ""): Unit =
    nodes += ArchNode(id, label, "service", group, icon)

  def addJunction(id: String, group: String = ""): Unit =
    nodes += ArchNode(id, "", "junction", group)

  def addGroup(id: String, label: String): Unit =
    groups(id) = label

  def addEdge(fromId: String, toId: String, fromSide: String = "", toSide: String = "", label: String = ""): Unit =
    edges += ArchEdge(fromId, toId, fromSide, toSide, label)

  def clear(): Unit = {
    title = ""; accTitle = ""; accDescription = ""
    nodes.clear(); edges.clear(); groups.clear()
  }
}
