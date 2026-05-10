/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Tests ported from:
 *   mermaid/packages/mermaid/src/diagrams/gantt/ganttDb.spec.ts
 */
package ssg
package mermaid
package diagrams
package gantt

import munit.FunSuite

final class GanttDiagramSuite extends FunSuite {

  // ────────────────────────────────────────────────────────────────────────────
  // Detection tests
  // ────────────────────────────────────────────────────────────────────────────

  test("detect: gantt keyword") {
    assert(GanttDiagram.detect("gantt\n    title Project"))
  }

  test("detect: not a gantt chart") {
    assert(!GanttDiagram.detect("graph TD\n    A-->B"))
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Parse tests (from ganttDb.spec.ts)
  // ────────────────────────────────────────────────────────────────────────────

  test("parse: title") {
    val db = GanttParser.parse("gantt\n    title My Project\n    section Phase 1\n    Task A :a1, 2024-01-01, 7d")
    assertEquals(db.title, "My Project")
  }

  test("parse: section and task") {
    val db = GanttParser.parse(
      """gantt
        |    dateFormat YYYY-MM-DD
        |    section Phase 1
        |    Task A :a1, 2024-01-01, 7d""".stripMargin
    )
    assert(db.sections.nonEmpty, "Should have a section")
    assertEquals(db.sections(0).name, "Phase 1")
    assert(db.tasks.nonEmpty, "Should have a task")
    assertEquals(db.tasks(0).name, "Task A")
  }

  test("parse: task with status flags") {
    val db = GanttParser.parse(
      """gantt
        |    section Phase 1
        |    Done task :done, a1, 2024-01-01, 7d
        |    Active task :active, crit, a2, 2024-01-08, 5d""".stripMargin
    )
    assert(db.tasks(0).isDone, "First task should be done")
    assert(db.tasks(1).isActive, "Second task should be active")
    assert(db.tasks(1).isCrit, "Second task should be critical")
  }

  test("parse: dateFormat directive") {
    val db = GanttParser.parse("gantt\n    dateFormat YYYY-MM-DD\n    section S\n    T :2024-01-01, 3d")
    assertEquals(db.dateFormat, "YYYY-MM-DD")
  }

  test("parse: multiple tasks") {
    val db = GanttParser.parse(
      """gantt
        |    dateFormat YYYY-MM-DD
        |    section Phase 1
        |    Task A :a1, 2024-01-01, 7d
        |    Task B :a2, 2024-01-08, 5d""".stripMargin
    )
    assertEquals(db.tasks.size, 2)
    assertEquals(db.tasks(0).name, "Task A")
    assertEquals(db.tasks(1).name, "Task B")
  }

  test("parse: multiple sections") {
    val db = GanttParser.parse(
      """gantt
        |    dateFormat YYYY-MM-DD
        |    section Phase 1
        |    Task A :a1, 2024-01-01, 7d
        |    Task B :a2, 2024-01-08, 5d
        |    section Phase 2
        |    Task C :a3, 2024-01-15, 3d""".stripMargin
    )
    assertEquals(db.sections.size, 2)
    assertEquals(db.sections(0).name, "Phase 1")
    assertEquals(db.sections(1).name, "Phase 2")
    assertEquals(db.tasks.size, 3)
  }

  test("parse: axisFormat directive") {
    val db = GanttParser.parse(
      "gantt\n    dateFormat YYYY-MM-DD\n    axisFormat %Y-%m-%d\n    section S\n    T :2024-01-01, 3d"
    )
    assertEquals(db.axisFormat, "%Y-%m-%d")
  }

  test("parse: task name is preserved") {
    val db = GanttParser.parse(
      "gantt\n    section S\n    My Complex Task Name :2024-01-01, 3d"
    )
    assertEquals(db.tasks(0).name, "My Complex Task Name")
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Render tests
  // ────────────────────────────────────────────────────────────────────────────

  test("render: produces valid SVG") {
    val svg = GanttDiagram.render(
      """gantt
        |    title My Project
        |    section Phase 1
        |    Task A :a1, 2024-01-01, 7d
        |    Task B :a2, 2024-01-08, 5d""".stripMargin
    )
    assert(svg.contains("<svg"), "Should contain <svg tag")
    assert(svg.contains("Task A"), "Should contain task name")
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("gantt\n    section S\n    Task :2024-01-01, 3d")
    assert(svg.contains("<svg"), "Mermaid dispatch should produce SVG")
  }
}
