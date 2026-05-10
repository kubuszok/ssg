/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package kanban

import munit.FunSuite

final class KanbanDiagramSuite extends FunSuite {

  test("detect: kanban keyword") {
    assert(KanbanDiagram.detect("kanban\nTodo\n  task1"))
  }

  test("detect: not a kanban board") {
    assert(!KanbanDiagram.detect("pie\n    \"A\" : 100"))
  }

  test("parse: columns and cards") {
    val db = KanbanParser.parse("kanban\nTodo[To Do]\n  task1[Write tests]\n  task2[Review]\nDone[Done]\n  task3[Deploy]")
    assertEquals(db.columns.size, 2)
    assertEquals(db.columns(0).label, "To Do")
    assertEquals(db.columns(0).cards.size, 2)
    assertEquals(db.columns(1).label, "Done")
    assertEquals(db.columns(1).cards.size, 1)
  }

  test("render: produces valid SVG") {
    val svg = KanbanDiagram.render("kanban\nTodo\n  task1[Write tests]")
    assert(svg.contains("<svg"))
    assert(svg.contains("<rect"))
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("kanban\nTodo\n  task1")
    assert(svg.contains("<svg"))
  }
}
