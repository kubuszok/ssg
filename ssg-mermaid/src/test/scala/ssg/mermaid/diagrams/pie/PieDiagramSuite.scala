/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Tests ported from:
 *   mermaid/packages/mermaid/src/diagrams/pie/pie.spec.ts
 */
package ssg
package mermaid
package diagrams
package pie

import munit.FunSuite

final class PieDiagramSuite extends FunSuite {

  // ────────────────────────────────────────────────────────────────────────────
  // Detection tests
  // ────────────────────────────────────────────────────────────────────────────

  test("detect: pie keyword") {
    assert(PieDiagram.detect("pie\n    \"Dogs\" : 386"))
  }

  test("detect: not a pie chart") {
    assert(!PieDiagram.detect("graph TD\n    A-->B"))
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Parse tests (from pie.spec.ts)
  // ────────────────────────────────────────────────────────────────────────────

  test("should handle very simple pie") {
    val db = PieParser.parse("pie\n      \"ash\": 100\n      ")
    assertEquals(db.sections.length, 1)
    assertEquals(db.sections(0).label, "ash")
    assertEquals(db.sections(0).value, 100.0)
  }

  test("should handle simple pie") {
    val db = PieParser.parse("pie\n      \"ash\" : 60\n      \"bat\" : 40\n      ")
    assertEquals(db.sections.length, 2)
    assertEquals(db.sections(0).label, "ash")
    assertEquals(db.sections(0).value, 60.0)
    assertEquals(db.sections(1).label, "bat")
    assertEquals(db.sections(1).value, 40.0)
  }

  test("should handle simple pie with showData") {
    val db = PieParser.parse("pie showData\n      \"ash\" : 60\n      \"bat\" : 40\n      ")
    assert(db.showData)
    assertEquals(db.sections.length, 2)
    assertEquals(db.sections(0).value, 60.0)
    assertEquals(db.sections(1).value, 40.0)
  }

  test("should handle simple pie with comments") {
    val db = PieParser.parse("pie\n      %% comments\n      \"ash\" : 60\n      \"bat\" : 40\n      ")
    assertEquals(db.sections.length, 2)
    assertEquals(db.sections(0).value, 60.0)
    assertEquals(db.sections(1).value, 40.0)
  }

  test("should handle simple pie with a title") {
    val db = PieParser.parse("pie title a 60/40 pie\n      \"ash\" : 60\n      \"bat\" : 40\n      ")
    assertEquals(db.title, "a 60/40 pie")
    assertEquals(db.sections.length, 2)
  }

  test("should handle simple pie with an acc title") {
    val db = PieParser.parse(
      "pie title a neat chart\n      accTitle: a neat acc title\n      \"ash\" : 60\n      \"bat\" : 40\n      "
    )
    assertEquals(db.title, "a neat chart")
    assertEquals(db.accTitle, "a neat acc title")
    assertEquals(db.sections.length, 2)
  }

  test("should handle simple pie with an acc description") {
    val db = PieParser.parse(
      "pie title a neat chart\n      accDescr: a neat description\n      \"ash\" : 60\n      \"bat\" : 40\n      "
    )
    assertEquals(db.title, "a neat chart")
    assertEquals(db.accDescription, "a neat description")
    assertEquals(db.sections.length, 2)
  }

  test("should handle simple pie with positive decimal") {
    val db = PieParser.parse("pie\n      \"ash\" : 60.67\n      \"bat\" : 40\n      ")
    assertEquals(db.sections(0).value, 60.67)
    assertEquals(db.sections(1).value, 40.0)
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Original existing tests (preserved)
  // ────────────────────────────────────────────────────────────────────────────

  test("parse: simple pie chart with sections") {
    val db = PieParser.parse("pie\n    \"Dogs\" : 386\n    \"Cats\" : 85\n    \"Rats\" : 15")
    assertEquals(db.sections.length, 3)
    assertEquals(db.sections(0).label, "Dogs")
    assertEquals(db.sections(0).value, 386.0)
    assertEquals(db.sections(1).label, "Cats")
    assertEquals(db.sections(2).label, "Rats")
  }

  test("parse: pie chart with title") {
    val db = PieParser.parse("pie\n    title Pets\n    \"Dogs\" : 386\n    \"Cats\" : 85")
    assertEquals(db.title, "Pets")
    assertEquals(db.sections.length, 2)
  }

  test("parse: pie chart with showData") {
    val db = PieParser.parse("pie showData\n    \"Dogs\" : 386\n    \"Cats\" : 85")
    assert(db.showData)
    assertEquals(db.sections.length, 2)
  }

  test("parse: total calculation") {
    val db = PieParser.parse("pie\n    \"A\" : 100\n    \"B\" : 200\n    \"C\" : 300")
    assertEquals(db.total, 600.0)
  }

  test("render: produces valid SVG") {
    val svg = PieDiagram.render("pie\n    \"Dogs\" : 386\n    \"Cats\" : 85")
    assert(svg.contains("<svg"), s"Should contain <svg tag")
    assert(svg.contains("viewBox"), "Should have viewBox")
    assert(svg.contains("<path"), "Should contain arc paths")
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("pie\n    \"Dogs\" : 386\n    \"Cats\" : 85")
    assert(svg.contains("<svg"), "Mermaid dispatch should produce SVG")
    assert(!svg.startsWith("<!--"), "Should not be unsupported")
  }
}
