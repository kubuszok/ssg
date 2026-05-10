/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/user-journey/journeyDb.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces module-level mutable state with mutable class
 *   Idiom: Mutable collections for accumulation during parsing; clear() resets state
 *   Renames: journeyDb module functions -> JourneyDb methods
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package journey

import scala.collection.mutable

/** A task in the user journey.
  *
  * @param name
  *   task display name
  * @param score
  *   satisfaction score (1-5)
  * @param actors
  *   actors involved in this task
  * @param section
  *   section this task belongs to
  * @param order
  *   display order
  */
final case class JourneyTask(
  name:        String,
  var score:   Int = 3,
  actors:      mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty,
  var section: String = "",
  var order:   Int = 0
)

/** A section in the user journey. */
final case class JourneySection(
  name:  String,
  tasks: mutable.ArrayBuffer[JourneyTask] = mutable.ArrayBuffer.empty
)

/** Mutable database for user journey diagram data.
  *
  * Accumulates tasks, sections, and actors during parsing. The renderer reads from this database to produce SVG output.
  *
  * Ports the module-level mutable state and functions from `journeyDb.ts`.
  */
final class JourneyDb {

  val tasks:    mutable.ArrayBuffer[JourneyTask]    = mutable.ArrayBuffer.empty
  val sections: mutable.ArrayBuffer[JourneySection] = mutable.ArrayBuffer.empty
  val actors:   mutable.LinkedHashSet[String]       = mutable.LinkedHashSet.empty

  var title:          String = ""
  var accTitle:       String = ""
  var accDescription: String = ""

  private var currentSection: String = ""
  private var taskCount:      Int    = 0

  /** Adds a section to the journey. */
  def addSection(name: String): Unit = {
    currentSection = name
    sections += JourneySection(name = name)
  }

  /** Adds a task to the current section.
    *
    * @param name
    *   task display name
    * @param score
    *   satisfaction score (1-5)
    * @param taskActors
    *   actors involved (comma-separated)
    */
  def addTask(name: String, score: Int, taskActors: Array[String]): Unit = {
    taskCount += 1
    val task = JourneyTask(
      name = name,
      score = math.max(1, math.min(5, score)),
      actors = mutable.ArrayBuffer.from(taskActors.map(_.trim).filter(_.nonEmpty)),
      section = currentSection,
      order = taskCount
    )

    // Track all actors
    for (actor <- task.actors)
      actors += actor

    tasks += task
    sections.lastOption.foreach(_.tasks += task)
  }

  /** Clears all state for parsing a new diagram. */
  def clear(): Unit = {
    tasks.clear()
    sections.clear()
    actors.clear()
    currentSection = ""
    taskCount = 0
    title = ""
    accTitle = ""
    accDescription = ""
  }
}
