/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/timeline/timelineDb.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces module-level mutable state with mutable class
 *   Idiom: Mutable collections for accumulation during parsing; clear() resets state
 *   Renames: timelineDb module functions -> TimelineDb methods
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package timeline

import scala.collection.mutable

/** A timeline period with associated events.
  *
  * @param title
  *   the period title/label
  * @param events
  *   events that occurred during this period
  * @param section
  *   the section this period belongs to
  */
final case class TimelinePeriod(
  title:       String,
  events:      mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty,
  var section: String = ""
)

/** A section in the timeline. */
final case class TimelineSection(
  name:    String,
  periods: mutable.ArrayBuffer[TimelinePeriod] = mutable.ArrayBuffer.empty
)

/** Mutable database for timeline diagram data.
  *
  * Accumulates periods, events, and sections during parsing. The renderer reads from this database to produce SVG output.
  *
  * Ports the module-level mutable state and functions from `timelineDb.ts`.
  */
final class TimelineDb {

  val periods:  mutable.ArrayBuffer[TimelinePeriod]  = mutable.ArrayBuffer.empty
  val sections: mutable.ArrayBuffer[TimelineSection] = mutable.ArrayBuffer.empty

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  private var currentSection: String = ""

  /** Adds a section to the timeline. */
  def addSection(name: String): Unit = {
    currentSection = name
    sections += TimelineSection(name = name)
  }

  /** Adds a period with events to the timeline.
    *
    * @param periodTitle
    *   the period label
    * @param events
    *   colon-separated events for this period
    */
  def addPeriod(periodTitle: String, events: Array[String]): Unit = {
    val period = TimelinePeriod(
      title = periodTitle,
      events = mutable.ArrayBuffer.from(events),
      section = currentSection
    )
    periods += period
    sections.lastOption.foreach(_.periods += period)
  }

  /** Adds a single event to the most recent period. */
  def addEvent(event: String): Unit =
    periods.lastOption.foreach(_.events += event)

  /** Clears all state for parsing a new diagram. */
  def clear(): Unit = {
    periods.clear()
    sections.clear()
    currentSection = ""
    title = ""
    accTitle = ""
    accDescription = ""
  }
}
