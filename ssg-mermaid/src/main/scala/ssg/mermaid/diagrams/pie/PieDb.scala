/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/pie/pieDb.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces module-level mutable state with mutable class
 *   Idiom: Mutable collections for accumulation during parsing; clear() resets state
 *   Renames: pieDb module functions -> PieDb methods
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package pie

import scala.collection.mutable

/** A single pie chart section (slice).
  *
  * @param label
  *   the display label for this section
  * @param value
  *   the numeric value determining arc size
  */
final case class PieSection(label: String, value: Double)

/** Mutable database for pie chart diagram data.
  *
  * Accumulates sections (label + value pairs) during parsing. The renderer reads from this database to produce SVG output.
  *
  * Ports the module-level mutable state and functions from `pieDb.ts`.
  */
final class PieDb {

  val sections: mutable.ArrayBuffer[PieSection] = mutable.ArrayBuffer.empty

  var title:          String  = ""
  var accTitle:       String  = ""
  var accDescription: String  = ""
  var showData:       Boolean = false

  /** Adds a section to the pie chart.
    *
    * Ports `addSection()` from `pieDb.ts`.
    *
    * @param label
    *   section label
    * @param value
    *   section numeric value
    */
  def addSection(label: String, value: Double): Unit =
    sections += PieSection(label = label, value = value)

  /** Returns the total value of all sections. */
  def total: Double =
    sections.foldLeft(0.0)(_ + _.value)

  /** Clears all state for parsing a new diagram.
    *
    * Ports `clear()` from `pieDb.ts`.
    */
  def clear(): Unit = {
    sections.clear()
    title = ""
    accTitle = ""
    accDescription = ""
    showData = false
  }
}
