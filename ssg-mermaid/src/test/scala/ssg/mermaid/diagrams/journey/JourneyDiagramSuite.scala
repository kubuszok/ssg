/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package journey

import munit.FunSuite

final class JourneyDiagramSuite extends FunSuite {

  test("detect: journey keyword") {
    assert(JourneyDiagram.detect("journey\n    title My Working Day"))
  }

  test("detect: not a journey") {
    assert(!JourneyDiagram.detect("graph TD\n    A-->B"))
  }

  test("parse: title") {
    val db = JourneyParser.parse("journey\n    title My Working Day\n    section Work\n    Do stuff: 5: Me")
    assertEquals(db.title, "My Working Day")
  }

  test("parse: tasks with scores and actors") {
    val db = JourneyParser.parse(
      """journey
        |    section Morning
        |    Wake up: 1: Me
        |    Have coffee: 5: Me, Cat""".stripMargin
    )
    assertEquals(db.tasks.length, 2)
    assertEquals(db.tasks(0).name, "Wake up")
    assertEquals(db.tasks(0).score, 1)
    assertEquals(db.tasks(1).name, "Have coffee")
    assertEquals(db.tasks(1).score, 5)
    assertEquals(db.tasks(1).actors.length, 2)
  }

  test("parse: sections") {
    val db = JourneyParser.parse(
      """journey
        |    section Morning
        |    Task A: 3: Me
        |    section Evening
        |    Task B: 4: Me""".stripMargin
    )
    assertEquals(db.sections.length, 2)
    assertEquals(db.sections(0).name, "Morning")
    assertEquals(db.sections(1).name, "Evening")
  }

  test("parse: actors are tracked") {
    val db = JourneyParser.parse(
      """journey
        |    section Work
        |    Task: 3: Alice, Bob
        |    Task2: 4: Alice, Charlie""".stripMargin
    )
    assert(db.actors.contains("Alice"))
    assert(db.actors.contains("Bob"))
    assert(db.actors.contains("Charlie"))
  }

  test("render: produces valid SVG") {
    val svg = JourneyDiagram.render(
      """journey
        |    title Working Day
        |    section Morning
        |    Wake up: 1: Me""".stripMargin
    )
    assert(svg.contains("<svg"), "Should contain <svg tag")
    assert(svg.contains("Wake up"), "Should contain task name")
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("journey\n    section Work\n    Do stuff: 3: Me")
    assert(svg.contains("<svg"), "Mermaid dispatch should produce SVG")
  }
}
