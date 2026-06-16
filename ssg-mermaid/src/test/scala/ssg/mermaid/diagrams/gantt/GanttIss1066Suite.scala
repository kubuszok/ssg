/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Regression tests for ISS-1066 — upstream gantt features:
 *   - `tickInterval` (gantt.jison:76,143; ganttDb.js:27,79-84; ganttRenderer.js:595-628)
 *   - `until` task-end keyword (ganttDb.js:348-371 getEndDate)
 */
package ssg
package mermaid
package diagrams
package gantt

import java.time.LocalDate

import munit.FunSuite

final class GanttIss1066Suite extends FunSuite {

  /** Counts non-overlapping occurrences of `needle` in `haystack`. */
  private def countOf(haystack: String, needle: String): Int = {
    var count = 0
    var idx   = haystack.indexOf(needle)
    while (idx >= 0) {
      count += 1
      idx = haystack.indexOf(needle, idx + needle.length)
    }
    count
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Feature 1 — tickInterval
  // gantt.jison:143 yy.setTickInterval($1.substr(13)); ganttDb.js:27,79-84
  // ──────────────────────────────────────────────────────────────────────────

  test("Iss1066 tickInterval: parsed into GanttDb (gantt.jison:143, ganttDb.js:79-84)") {
    val db = GanttParser.parse(
      """gantt
        |    dateFormat YYYY-MM-DD
        |    tickInterval 1week
        |    section S
        |    Task A :a1, 2024-01-01, 7d""".stripMargin
    )
    assertEquals(db.tickInterval, "1week")
  }

  test("Iss1066 tickInterval: applied to the rendered axis cadence (ganttRenderer.js:595-628)") {
    // A chart spanning ~4 months. The default heuristic ticks every 30 days
    // (totalDays <= 365); `tickInterval 1week` ticks weekly, producing strictly
    // more axis labels — proving the configured interval drives the cadence.
    val base =
      """gantt
        |    dateFormat YYYY-MM-DD
        |    section S
        |    Task A :a1, 2024-01-01, 120d""".stripMargin

    val withInterval =
      """gantt
        |    dateFormat YYYY-MM-DD
        |    tickInterval 1week
        |    section S
        |    Task A :a1, 2024-01-01, 120d""".stripMargin

    val defaultSvg  = GanttDiagram.render(base)
    val intervalSvg = GanttDiagram.render(withInterval)

    val defaultLabels  = countOf(defaultSvg, "axisLabel")
    val intervalLabels = countOf(intervalSvg, "axisLabel")

    assert(defaultLabels > 0, "default axis should have labels")
    assert(
      intervalLabels > defaultLabels,
      s"weekly tickInterval should produce more axis labels than the default 30-day cadence (default=$defaultLabels, interval=$intervalLabels)"
    )
  }

  // ──────────────────────────────────────────────────────────────────────────
  // Feature 2 — until task-end keyword
  // ganttDb.js:348-371 getEndDate — `until <ids>` ends at the EARLIEST start
  // among the referenced task ids.
  // ──────────────────────────────────────────────────────────────────────────

  test("Iss1066 until: task ends at the referenced task's start (ganttDb.js:348-371)") {
    val db = GanttParser.parse(
      """gantt
        |    dateFormat YYYY-MM-DD
        |    section S
        |    Task A :a, 2024-01-10, 5d
        |    Task B :b, 2024-01-01, until a""".stripMargin
    )
    val a = db.tasks.find(_.id == "a").get
    val b = db.tasks.find(_.id == "b").get
    // B ends exactly when A starts
    assertEquals(b.endDate, a.startDate)
    assertEquals(b.endDate, LocalDate.of(2024, 1, 10))
  }

  test("Iss1066 until: multiple ids take the EARLIEST start (ganttDb.js:356-363)") {
    val db = GanttParser.parse(
      """gantt
        |    dateFormat YYYY-MM-DD
        |    section S
        |    Task A :a, 2024-01-20, 5d
        |    Task C :c, 2024-01-10, 5d
        |    Task B :b, 2024-01-01, until a c""".stripMargin
    )
    val c = db.tasks.find(_.id == "c").get
    val b = db.tasks.find(_.id == "b").get
    // earliest of {A=2024-01-20, C=2024-01-10} is C
    assertEquals(b.endDate, c.startDate)
    assertEquals(b.endDate, LocalDate.of(2024, 1, 10))
  }

  test("Iss1066 until: a normal duration task is unaffected (regression guard)") {
    val db = GanttParser.parse(
      """gantt
        |    dateFormat YYYY-MM-DD
        |    section S
        |    Task A :a, 2024-01-01, 7d""".stripMargin
    )
    val a = db.tasks.find(_.id == "a").get
    assertEquals(a.startDate, LocalDate.of(2024, 1, 1))
    assertEquals(a.endDate, LocalDate.of(2024, 1, 8))
  }
}
