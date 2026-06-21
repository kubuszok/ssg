/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Regression test for ISS-1197 — gantt forward `after` references must resolve
 * via a multi-pass compile.
 *
 * Oracle: upstream `ganttDb.js:156-161` runs `compileTasks()` inside a loop
 *   `while (!allItemsProcessed && iterationCount < maxDepth)` (maxDepth = 10)
 *   so a task that says `after <id>` where `<id>` is declared LATER in the
 *   source is resolved on a subsequent pass once the referenced task exists.
 *
 * SSG resolves `after <id>` at ADD-TIME inside `GanttDb.addTask`
 *   (GanttDb.scala:228-231: `findTaskEndDate(afterId).getOrElse(lastEndDate)`),
 *   and `GanttDb.compileTasks` (GanttDb.scala:375-386) is an unwired near-no-op
 *   that is never called. A FORWARD `after` reference therefore never resolves:
 *   when the dependent task is added, its dependency does not yet exist, so the
 *   start falls back to `lastEndDate` instead of the dependency's end date.
 */
package ssg
package mermaid
package diagrams
package gantt

import munit.FunSuite

final class GanttForwardAfterIss1197Suite extends FunSuite {

  // RED — a FORWARD `after` reference: B depends on C, but C is declared AFTER B.
  // Upstream resolves this on a later compile pass; SSG resolves at add-time so
  // B's `after c` is unresolved (C does not exist yet) and B falls back to a
  // wrong start instead of C's end.
  test("Iss1197 forward after: B starts at C's end though C is declared later (ganttDb.js:156-161)") {
    val db = GanttParser.parse(
      """gantt
        |    dateFormat YYYY-MM-DD
        |    section S
        |    Task B :b, after c, 2d
        |    Task C :c, 2024-01-10, 3d""".stripMargin
    )
    val b = db.tasks.find(_.id == "b").get
    val c = db.tasks.find(_.id == "c").get
    assertEquals(
      b.startDate,
      c.endDate,
      s"forward `after c` should start B at C's end: B.startDate=${b.startDate} C.endDate=${c.endDate}"
    )
  }

  // GUARD — a BACKWARD `after` reference: D depends on A, and A is declared
  // BEFORE D. This already resolves at add-time and must keep working both
  // before and after the fix.
  test("Iss1197 backward after: D starts at A's end (regression guard)") {
    val db = GanttParser.parse(
      """gantt
        |    dateFormat YYYY-MM-DD
        |    section S
        |    Task A :a, 2024-01-10, 3d
        |    Task D :d, after a, 2d""".stripMargin
    )
    val a = db.tasks.find(_.id == "a").get
    val d = db.tasks.find(_.id == "d").get
    assertEquals(d.startDate, a.endDate)
  }
}
