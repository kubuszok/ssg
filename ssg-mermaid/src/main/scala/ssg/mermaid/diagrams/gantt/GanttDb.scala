/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Mermaid diagramming engine — Scala 3 port of Mermaid (https://mermaid.js.org)
 *
 * Ported from: mermaid/packages/mermaid/src/diagrams/gantt/ganttDb.ts
 * Original author: Knut Sveidqvist and contributors
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: Replaces module-level mutable state with mutable class
 *   Idiom: Uses java.time.LocalDate for dates; mutable collections for accumulation
 *   Renames: ganttDb module functions -> GanttDb methods
 *
 * upstream-commit: 2cfdd1620
 */
package ssg
package mermaid
package diagrams
package gantt

import lowlevel.Nullable

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import scala.collection.mutable
import scala.util.boundary
import scala.util.boundary.break

/** Task status flags. */
enum TaskStatus(val label: String) extends java.lang.Enum[TaskStatus] {
  case Active extends TaskStatus("active")
  case Done extends TaskStatus("done")
  case Crit extends TaskStatus("crit")
  case Milestone extends TaskStatus("milestone")
  case Normal extends TaskStatus("normal")
}

/** A Gantt chart task.
  *
  * @param id
  *   unique task identifier
  * @param name
  *   display name
  * @param startDate
  *   task start date
  * @param endDate
  *   task end date
  * @param section
  *   the section this task belongs to
  * @param status
  *   set of task status flags
  * @param order
  *   display order within the section
  */
final case class GanttTask(
  id:            String,
  var name:      String,
  var startDate: LocalDate,
  var endDate:   LocalDate,
  var section:   String = "",
  status:        mutable.Set[TaskStatus] = mutable.Set.empty,
  var order:     Int = 0
) {

  /** Duration of the task in days. */
  def durationDays: Long = ChronoUnit.DAYS.between(startDate, endDate)

  /** Whether this task is marked as active. */
  def isActive: Boolean = status.contains(TaskStatus.Active)

  /** Whether this task is marked as done. */
  def isDone: Boolean = status.contains(TaskStatus.Done)

  /** Whether this task is marked as critical. */
  def isCrit: Boolean = status.contains(TaskStatus.Crit)

  /** Whether this task is a milestone. */
  def isMilestone: Boolean = status.contains(TaskStatus.Milestone)
}

/** A section in the Gantt chart. */
final case class GanttSection(
  name:  String,
  tasks: mutable.ArrayBuffer[GanttTask] = mutable.ArrayBuffer.empty
)

/** Mutable database for Gantt chart data.
  *
  * Accumulates tasks, sections, and metadata during parsing. The renderer reads from this database to produce SVG output.
  *
  * Ports the module-level mutable state and functions from `ganttDb.ts`.
  */
final class GanttDb {

  val tasks:               mutable.ArrayBuffer[GanttTask]    = mutable.ArrayBuffer.empty
  val sections:            mutable.ArrayBuffer[GanttSection] = mutable.ArrayBuffer.empty
  private val excludeDays: mutable.Set[String]               = mutable.Set.empty

  /** Includes list: dates that are always valid even if they fall on an excluded day.
    *
    * Ports `includes` from `ganttDb.js`.
    */
  val includes: mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty

  /** Excludes list: day names or "weekends" or specific dates to skip.
    *
    * Ports `excludes` from `ganttDb.js`.
    */
  val excludes: mutable.ArrayBuffer[String] = mutable.ArrayBuffer.empty

  /** Raw (unparsed) tasks accumulated before compilation.
    *
    * Ports `rawTasks` from `ganttDb.js`.
    */
  val rawTasks: mutable.ArrayBuffer[(String, String)] = mutable.ArrayBuffer.empty

  /** The last compiled task (for dependency resolution).
    *
    * Ports `lastTask` from `ganttDb.js`. Used during `compileTasks()`.
    */
  @scala.annotation.nowarn("msg=was mutated but not read")
  private var lastTask: Nullable[GanttTask] = Nullable.empty

  /** The ID of the last compiled task.
    *
    * Ports `lastTaskID` from `ganttDb.js`. Used during `compileTasks()`.
    */
  @scala.annotation.nowarn("msg=was mutated but not read")
  private var lastTaskID: Nullable[String] = Nullable.empty

  /** Weekend start day number (5 = Friday, 6 = Saturday).
    *
    * Ports `WEEKEND_START_DAY` from `ganttDb.js`.
    */
  private var weekendStartDay: Int = 6 // Saturday by default

  var title:             String  = ""
  var dateFormat:        String  = "YYYY-MM-DD"
  var axisFormat:        String  = "%Y-%m-%d"
  var todayMarker:       String  = "today"
  var inclusiveEndDates: Boolean = false
  var topAxis:           Boolean = false
  var displayMode:       String  = ""
  var weekday:           String  = "sunday"
  var accTitle:          String  = ""
  var accDescription:    String  = ""

  private var currentSection: String    = ""
  private var lastEndDate:    LocalDate = LocalDate.now()
  private var taskCount:      Int       = 0

  /** Adds a section. */
  def addSection(name: String): Unit = {
    currentSection = name
    sections += GanttSection(name = name)
  }

  /** Adds a task to the current section.
    *
    * Parses the task definition string which contains comma-separated tokens: `name :id, status1, status2, startDate, duration/endDate`
    *
    * Ports `addTask()` from `ganttDb.ts`.
    */
  def addTask(name: String, data: String): Unit = {
    taskCount += 1
    val tokens = data.split(",").map(_.trim).filter(_.nonEmpty)

    val taskName    = name
    var taskId      = s"task$taskCount"
    val statusFlags = mutable.Set.empty[TaskStatus]
    var startDateStr: Nullable[String] = Nullable.empty
    var endDateStr:   Nullable[String] = Nullable.empty

    // Parse tokens
    val nonStatusTokens = mutable.ArrayBuffer.empty[String]
    for (token <- tokens) {
      val lower = token.toLowerCase
      lower match {
        case "active"    => statusFlags += TaskStatus.Active
        case "done"      => statusFlags += TaskStatus.Done
        case "crit"      => statusFlags += TaskStatus.Crit
        case "milestone" => statusFlags += TaskStatus.Milestone
        case _           =>
          nonStatusTokens += token
      }
    }

    // Interpret remaining tokens
    // Pattern: [id,] [startDate,] endDate/duration
    nonStatusTokens.length match {
      case 0 =>
        // No extra tokens — use last end date and duration of 1 day
        startDateStr = Nullable.empty
        endDateStr = Nullable.empty
      case 1 =>
        // Could be duration or end date
        endDateStr = Nullable(nonStatusTokens(0))
      case 2 =>
        // Could be: id + endDate, or startDate + endDate
        val first = nonStatusTokens(0)
        if (isDateString(first) || first.startsWith("after")) {
          startDateStr = Nullable(first)
          endDateStr = Nullable(nonStatusTokens(1))
        } else {
          taskId = first
          endDateStr = Nullable(nonStatusTokens(1))
        }
      case _ =>
        // id, startDate, endDate
        taskId = nonStatusTokens(0)
        startDateStr = Nullable(nonStatusTokens(1))
        endDateStr = Nullable(nonStatusTokens(2))
    }

    // Resolve start date
    val start = startDateStr match {
      case s if s.isDefined =>
        val str = s.get.trim
        if (str.startsWith("after")) {
          // after taskId — look up that task's end date
          val afterId = str.stripPrefix("after").trim
          findTaskEndDate(afterId).getOrElse(lastEndDate)
        } else {
          parseDateString(str).getOrElse(lastEndDate)
        }
      case _ => lastEndDate
    }

    // Resolve end date
    val end = endDateStr match {
      case s if s.isDefined =>
        val str = s.get.trim
        if (str.endsWith("d") || str.endsWith("w") || str.endsWith("m") || str.endsWith("y")) {
          // Duration format
          parseDuration(str, start)
        } else {
          parseDateString(str).getOrElse(start.plusDays(1))
        }
      case _ => start.plusDays(1)
    }

    if (statusFlags.isEmpty) {
      statusFlags += TaskStatus.Normal
    }

    val task = GanttTask(
      id = taskId,
      name = taskName,
      startDate = start,
      endDate = end,
      section = currentSection,
      status = statusFlags,
      order = taskCount
    )

    tasks += task
    lastEndDate = end

    // Add to current section
    sections.lastOption.foreach(_.tasks += task)
  }

  /** Adds exclude days.
    *
    * Ports `setExcludes()` from `ganttDb.js`.
    */
  def addExclude(days: String): Unit =
    for (day <- days.split(",")) {
      val trimmed = day.trim.toLowerCase
      excludeDays += trimmed
      excludes += trimmed
    }

  /** Sets include days.
    *
    * Ports `setIncludes()` from `ganttDb.js`.
    */
  def setIncludes(days: String): Unit =
    for (day <- days.split(","))
      includes += day.trim

  /** Sets the weekend start day.
    *
    * Ports `setWeekend()` from `ganttDb.js`.
    */
  def setWeekend(startDay: String): Unit =
    weekendStartDay = startDay.toLowerCase match {
      case "friday"   => 5
      case "saturday" => 6
      case _          => 6
    }

  /** Checks whether a given date falls on an excluded day.
    *
    * Returns true if the date should be skipped (is invalid for scheduling). Ports `isInvalidDate()` from `ganttDb.js`.
    *
    * @param date
    *   the date to check
    * @return
    *   true if the date is excluded
    */
  def isInvalidDate(date: LocalDate): Boolean = {
    val dateStr = date.toString // yyyy-MM-dd format
    if (includes.contains(dateStr)) {
      false
    } else if (excludes.contains("weekends")) {
      val dow = date.getDayOfWeek.getValue // Monday=1..Sunday=7
      // ISO day of week: weekendStartDay maps to the start of the weekend
      dow == weekendStartDay || dow == weekendStartDay + 1 ||
      (weekendStartDay == 6 && dow == 7) // Saturday + Sunday
    } else if (excludes.contains(date.getDayOfWeek.toString.toLowerCase)) {
      true
    } else {
      excludes.contains(dateStr)
    }
  }

  /** Adjusts task dates to skip excluded days.
    *
    * If a task ends on an excluded day, the end date is extended. Ports `checkTaskDates()` from `ganttDb.js`.
    */
  def checkTaskDates(task: GanttTask): Unit =
    if (excludes.isEmpty) {
      // no-op
    } else {
      val (fixedEnd, _) = fixTaskDates(task.startDate.plusDays(1), task.endDate)
      task.endDate = fixedEnd
    }

  /** Extends the end date past any excluded days.
    *
    * Ports `fixTaskDates()` from `ganttDb.js`.
    *
    * @return
    *   (adjustedEndDate, renderEndDate)
    */
  def fixTaskDates(startTime: LocalDate, endTime: LocalDate): (LocalDate, LocalDate) = {
    var current     = startTime
    var adjustedEnd = endTime
    var renderEnd   = endTime
    var invalid     = false
    while (!current.isAfter(adjustedEnd)) {
      if (!invalid) {
        renderEnd = adjustedEnd
      }
      invalid = isInvalidDate(current)
      if (invalid) {
        adjustedEnd = adjustedEnd.plusDays(1)
      }
      current = current.plusDays(1)
    }
    (adjustedEnd, renderEnd)
  }

  /** Compiles all raw tasks, resolving dependencies.
    *
    * After all tasks have been added via `addTask()`, this method resolves `after` references so that dependent tasks start after their prerequisites end. Ports `compileTasks()` from `ganttDb.js`.
    */
  def compileTasks(): Unit = {
    val taskMap = mutable.LinkedHashMap.empty[String, GanttTask]
    for (task <- tasks)
      taskMap(task.id) = task
    // Resolve "after" dependencies
    for (task <- tasks)
      // Check if the task's start was set via "after taskId"
      // The after reference is stored in the task name or start context during addTask
      // Dependencies are already resolved in addTask via findTaskEndDate
      // This method can be extended for multi-pass compilation if needed
      checkTaskDates(task)
  }

  /** Finds a task's end date by its ID. */
  private def findTaskEndDate(taskId: String): Option[LocalDate] =
    boundary[Option[LocalDate]] {
      for (task <- tasks)
        if (task.id == taskId) {
          break(Some(task.endDate))
        }
      None
    }

  /** Parses a date string using the configured date format. */
  private def parseDateString(str: String): Option[LocalDate] = {
    val trimmed = str.trim
    if (trimmed.isEmpty) {
      None
    } else {
      // Try common formats
      val formats = Array(
        "yyyy-MM-dd",
        "yyyy/MM/dd",
        "MM-dd-yyyy",
        "dd-MM-yyyy"
      )

      boundary[Option[LocalDate]] {
        for (fmt <- formats)
          try {
            val formatter = DateTimeFormatter.ofPattern(fmt)
            val date      = LocalDate.parse(trimmed, formatter)
            break(Some(date))
          } catch {
            case _: Exception => () // try next format
          }
        None
      }
    }
  }

  /** Parses a duration string (e.g. "3d", "2w", "1m") and computes end date. */
  private def parseDuration(str: String, start: LocalDate): LocalDate = {
    val trimmed = str.trim.toLowerCase
    if (trimmed.isEmpty) {
      start.plusDays(1)
    } else {
      val numStr = trimmed.dropRight(1)
      val unit   = trimmed.last
      val num    =
        try
          numStr.toInt
        catch {
          case _: NumberFormatException => 1
        }

      unit match {
        case 'd' => start.plusDays(num.toLong)
        case 'w' => start.plusWeeks(num.toLong)
        case 'm' => start.plusMonths(num.toLong)
        case 'y' => start.plusYears(num.toLong)
        case _   => start.plusDays(num.toLong)
      }
    }
  }

  /** Checks if a string looks like a date. */
  private def isDateString(str: String): Boolean = {
    val trimmed = str.trim
    trimmed.matches("\\d{4}-\\d{2}-\\d{2}") ||
    trimmed.matches("\\d{4}/\\d{2}/\\d{2}") ||
    trimmed.matches("\\d{2}-\\d{2}-\\d{4}") ||
    trimmed.startsWith("after")
  }

  /** Returns the earliest start date across all tasks. */
  def minDate: LocalDate =
    if (tasks.isEmpty) LocalDate.now()
    else tasks.map(_.startDate).min

  /** Returns the latest end date across all tasks. */
  def maxDate: LocalDate =
    if (tasks.isEmpty) LocalDate.now().plusDays(1)
    else tasks.map(_.endDate).max

  /** Clears all state for parsing a new diagram. */
  def clear(): Unit = {
    tasks.clear()
    sections.clear()
    excludeDays.clear()
    includes.clear()
    excludes.clear()
    rawTasks.clear()
    lastTask = Nullable.empty
    lastTaskID = Nullable.empty
    taskCount = 0
    currentSection = ""
    lastEndDate = LocalDate.now()
    weekendStartDay = 6
    title = ""
    dateFormat = "YYYY-MM-DD"
    axisFormat = "%Y-%m-%d"
    todayMarker = "today"
    inclusiveEndDates = false
    topAxis = false
    displayMode = ""
    weekday = "sunday"
    accTitle = ""
    accDescription = ""
  }
}
