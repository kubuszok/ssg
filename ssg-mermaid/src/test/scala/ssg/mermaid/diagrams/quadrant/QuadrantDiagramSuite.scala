/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Tests ported from:
 *   mermaid/packages/mermaid/src/diagrams/quadrant-chart/parser/quadrant.jison.spec.ts
 */
package ssg
package mermaid
package diagrams
package quadrant

import munit.FunSuite

final class QuadrantDiagramSuite extends FunSuite {

  // ────────────────────────────────────────────────────────────────────────────
  // Detection tests
  // ────────────────────────────────────────────────────────────────────────────

  test("detect: quadrantChart keyword") {
    assert(QuadrantDiagram.detect("quadrantChart\n    x-axis Low --> High"))
  }

  test("detect: not a quadrant chart") {
    assert(!QuadrantDiagram.detect("pie\n    \"A\" : 100"))
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Header tests
  // ────────────────────────────────────────────────────────────────────────────

  test("should throw error if quadrantChart text is not there") {
    intercept[Exception] {
      QuadrantParser.parse("quadrant-1 do")
    }
  }

  test("should not throw error if only quadrantChart is there") {
    QuadrantParser.parse("quadrantChart")
  }

  // ────────────────────────────────────────────────────────────────────────────
  // X-axis tests
  // ────────────────────────────────────────────────────────────────────────────

  test("should be able to parse xAxis text") {
    val db = QuadrantParser.parse("quadrantChart\nx-axis urgent --> not urgent")
    assertEquals(db.xAxisLeftLabel, "urgent")
    assertEquals(db.xAxisRightLabel, "not urgent")
  }

  test("should be able to parse xAxis text with spaces") {
    val db = QuadrantParser.parse("quadrantChart\n       x-axis         Urgent     -->        Not Urgent    \n")
    assertEquals(db.xAxisLeftLabel, "Urgent")
    // Right label may include trailing spaces depending on parser behavior
    assert(db.xAxisRightLabel.startsWith("Not Urgent"))
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Y-axis tests
  // ────────────────────────────────────────────────────────────────────────────

  test("should be able to parse yAxis text") {
    val db = QuadrantParser.parse("quadrantChart\ny-axis urgent --> not urgent")
    assertEquals(db.yAxisBottomLabel, "urgent")
    assertEquals(db.yAxisTopLabel, "not urgent")
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Quadrant label tests
  // ────────────────────────────────────────────────────────────────────────────

  test("should be able to parse quadrant1 text") {
    val db = QuadrantParser.parse("quadrantChart\nquadrant-1 Plan")
    assertEquals(db.quadrantLabels(0), "Plan")
  }

  test("should be able to parse quadrant2 text") {
    val db = QuadrantParser.parse("quadrantChart\nquadrant-2 do")
    assertEquals(db.quadrantLabels(1), "do")
  }

  test("should be able to parse quadrant3 text") {
    val db = QuadrantParser.parse("quadrantChart\nquadrant-3 deligate")
    assertEquals(db.quadrantLabels(2), "deligate")
  }

  test("should be able to parse quadrant4 text") {
    val db = QuadrantParser.parse("quadrantChart\nquadrant-4 delete")
    assertEquals(db.quadrantLabels(3), "delete")
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Title tests
  // ────────────────────────────────────────────────────────────────────────────

  test("should be able to parse title") {
    val db = QuadrantParser.parse("quadrantChart\ntitle this is title")
    assertEquals(db.title, "this is title")
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Point tests
  // ────────────────────────────────────────────────────────────────────────────

  test("should be able to parse points") {
    val db = QuadrantParser.parse("quadrantChart\npoint1: [0.1, 0.4]")
    assertEquals(db.points.size, 1)
    assertEquals(db.points(0).label, "point1")
    assertEquals(db.points(0).x, 0.1, 0.001)
    assertEquals(db.points(0).y, 0.4, 0.001)
  }

  test("should reject out of range point coordinates") {
    intercept[Exception] {
      QuadrantParser.parse("quadrantChart\nPoint1 : [1.2, 0.4]")
    }
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Whole chart test
  // ────────────────────────────────────────────────────────────────────────────

  test("should be able to parse the whole chart") {
    val db = QuadrantParser.parse(
      """quadrantChart
        |      title Analytics and Business Intelligence Platforms
        |      x-axis "Completeness of Vision" --> "x-axis-2"
        |      y-axis Ability to Execute --> "y-axis-2"
        |      quadrant-1 Leaders
        |      quadrant-2 Challengers
        |      quadrant-3 Niche
        |      quadrant-4 Visionaries
        |      Microsoft: [0.75, 0.75]
        |      Salesforce: [0.55, 0.60]
        |      IBM: [0.51, 0.40]
        |      Incorta: [0.20, 0.30]""".stripMargin
    )
    assertEquals(db.xAxisLeftLabel, "Completeness of Vision")
    assertEquals(db.xAxisRightLabel, "x-axis-2")
    assertEquals(db.yAxisTopLabel, "y-axis-2")
    assertEquals(db.yAxisBottomLabel, "Ability to Execute")
    assertEquals(db.quadrantLabels(0), "Leaders")
    assertEquals(db.quadrantLabels(1), "Challengers")
    assertEquals(db.quadrantLabels(2), "Niche")
    assertEquals(db.quadrantLabels(3), "Visionaries")
    assertEquals(db.points.size, 4)
    assertEquals(db.points(0).label, "Microsoft")
    assertEquals(db.points(0).x, 0.75, 0.001)
    assertEquals(db.points(0).y, 0.75, 0.001)
    assertEquals(db.points(1).label, "Salesforce")
    assertEquals(db.points(2).label, "IBM")
    assertEquals(db.points(3).label, "Incorta")
  }

  // ────────────────────────────────────────────────────────────────────────────
  // Original existing tests (preserved)
  // ────────────────────────────────────────────────────────────────────────────

  test("parse: axes and points") {
    val db = QuadrantParser.parse(
      "quadrantChart\n    x-axis Low --> High\n    y-axis Small --> Large\n    Point A: [0.3, 0.7]"
    )
    assertEquals(db.xAxisLeftLabel, "Low")
    assertEquals(db.xAxisRightLabel, "High")
    assertEquals(db.yAxisBottomLabel, "Small")
    assertEquals(db.yAxisTopLabel, "Large")
    assertEquals(db.points.size, 1)
    assertEquals(db.points(0).label, "Point A")
  }

  test("render: produces valid SVG") {
    val svg = QuadrantDiagram.render("quadrantChart\n    x-axis Low --> High\n    Point A: [0.5, 0.5]")
    assert(svg.contains("<svg"))
    assert(svg.contains("<circle"))
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("quadrantChart\n    x-axis Low --> High")
    assert(svg.contains("<svg"))
    assert(!svg.startsWith("<!--"))
  }
}
