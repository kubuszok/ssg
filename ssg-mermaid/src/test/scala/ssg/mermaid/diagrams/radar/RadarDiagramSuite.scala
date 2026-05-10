/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package radar

import munit.FunSuite

final class RadarDiagramSuite extends FunSuite {

  test("detect: radar-beta keyword") {
    assert(RadarDiagram.detect("radar-beta\n    axis Speed, Quality"))
  }

  test("detect: not a radar diagram") {
    assert(!RadarDiagram.detect("pie\n    \"A\" : 100"))
  }

  test("parse: axes and series") {
    val db = RadarParser.parse("radar-beta\n    title Skills\n    axis Speed, Quality, Reliability\n    \"Team A\": 0.8, 0.6, 0.9")
    assertEquals(db.title, "Skills")
    assertEquals(db.axes.toSeq, Seq("Speed", "Quality", "Reliability"))
    assertEquals(db.series.size, 1)
    assertEquals(db.series(0).name, "Team A")
    assertEquals(db.series(0).values.toSeq, Seq(0.8, 0.6, 0.9))
  }

  test("render: produces valid SVG") {
    val svg = RadarDiagram.render("radar-beta\n    axis A, B, C\n    \"S1\": 0.5, 0.5, 0.5")
    assert(svg.contains("<svg"))
    assert(svg.contains("<polygon"))
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("radar-beta\n    axis A, B, C")
    assert(svg.contains("<svg"))
  }
}
