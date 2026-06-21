/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Regression test for ISS-1196 — gantt task-date parsing must honor the
 * configured `dateFormat`, not a fixed set of hardcoded patterns.
 *
 * Oracle: upstream `ganttDb.js` getStartDate:292 parses task dates with
 *   `dayjs(str, dateFormat.trim(), true)`
 * i.e. using the CONFIGURED `dateFormat` directive. SSG's `parseDateString`
 * (GanttDb.scala:445-456) instead tries 4 hardcoded java.time patterns
 * (yyyy-MM-dd, yyyy/MM/dd, MM-dd-yyyy, dd-MM-yyyy) and ignores `db.dateFormat`.
 * A configured format outside those 4 patterns therefore fails to parse and the
 * task falls back to a wrong start date.
 */
package ssg
package mermaid
package diagrams
package gantt

import java.time.LocalDate

import munit.FunSuite

final class GanttDateFormatIss1196Suite extends FunSuite {

  // ──────────────────────────────────────────────────────────────────────────
  // RED — a custom `dateFormat` must drive task-date parsing.
  // ganttDb.js getStartDate:292 `dayjs(str, dateFormat.trim(), true)`.
  // ──────────────────────────────────────────────────────────────────────────

  test("ISS-1196 custom dateFormat MM/DD/YYYY drives task-date parsing") {
    val db = GanttParser.parse(
      """gantt
        |    dateFormat MM/DD/YYYY
        |    section S
        |    Task A :a, 02/01/2019, 3d""".stripMargin
    )
    val a = db.tasks.find(_.id == "a").get
    assertEquals(
      a.startDate,
      LocalDate.of(2019, 2, 1),
      s"configured dateFormat MM/DD/YYYY should parse 02/01/2019 as 2019-02-01, got ${a.startDate}"
    )
  }

  // ──────────────────────────────────────────────────────────────────────────
  // GUARD — the default `dateFormat YYYY-MM-DD` still parses correctly
  // (passes today and after the fix; proves no regression).
  // ──────────────────────────────────────────────────────────────────────────

  test("ISS-1196 default dateFormat YYYY-MM-DD parses correctly (guard)") {
    val db = GanttParser.parse(
      """gantt
        |    dateFormat YYYY-MM-DD
        |    section S
        |    Task A :a, 2019-02-01, 3d""".stripMargin
    )
    val a = db.tasks.find(_.id == "a").get
    assertEquals(a.startDate, LocalDate.of(2019, 2, 1))
  }
}
