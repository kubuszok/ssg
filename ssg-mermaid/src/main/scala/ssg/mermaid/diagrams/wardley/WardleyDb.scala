/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 * Original source: mermaid wardley map
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package wardley

import scala.collection.mutable

/** A component in a Wardley map (position on evolution/value chain axes). */
final case class WardleyComponent(name: String, visibility: Double, evolution: Double)

/** A dependency link. */
final case class WardleyLink(from: String, to: String)

/** Mutable database for Wardley map data. */
final class WardleyDb {

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  val components: mutable.ArrayBuffer[WardleyComponent] = mutable.ArrayBuffer.empty
  val links:      mutable.ArrayBuffer[WardleyLink]      = mutable.ArrayBuffer.empty

  def addComponent(name: String, visibility: Double, evolution: Double): Unit =
    components += WardleyComponent(name, visibility, evolution)

  def addLink(from: String, to: String): Unit = links += WardleyLink(from, to)

  def clear(): Unit = { title = ""; accTitle = ""; accDescription = ""; components.clear(); links.clear() }
}
