/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package timeline

import munit.FunSuite

final class TimelineDiagramSuite extends FunSuite {

  test("detect: timeline keyword") {
    assert(TimelineDiagram.detect("timeline\n    title History"))
  }

  test("detect: not a timeline") {
    assert(!TimelineDiagram.detect("graph TD\n    A-->B"))
  }

  test("parse: title") {
    val db = TimelineParser.parse("timeline\n    title History of Internet\n    2000 : Dot-com bubble")
    assertEquals(db.title, "History of Internet")
  }

  test("parse: period with events") {
    val db = TimelineParser.parse("timeline\n    2000 : Dot-com : Y2K\n    2010 : Mobile era")
    assertEquals(db.periods.length, 2)
    assertEquals(db.periods(0).title, "2000")
    assertEquals(db.periods(0).events.length, 2)
    assertEquals(db.periods(0).events(0), "Dot-com")
    assertEquals(db.periods(0).events(1), "Y2K")
    assertEquals(db.periods(1).title, "2010")
  }

  test("parse: sections") {
    val db = TimelineParser.parse(
      """timeline
        |    section Early
        |    2000 : Event A
        |    section Later
        |    2020 : Event B""".stripMargin
    )
    assertEquals(db.sections.length, 2)
    assertEquals(db.sections(0).name, "Early")
    assertEquals(db.sections(1).name, "Later")
  }

  test("render: produces valid SVG") {
    val svg = TimelineDiagram.render("timeline\n    2000 : Dot-com\n    2010 : Mobile")
    assert(svg.contains("<svg"), "Should contain <svg tag")
    assert(svg.contains("2000"), "Should contain period title")
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("timeline\n    2000 : Event")
    assert(svg.contains("<svg"), "Mermaid dispatch should produce SVG")
  }
}
