/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package sankey

import munit.FunSuite

final class SankeyDiagramSuite extends FunSuite {

  test("detect: sankey-beta keyword") {
    assert(SankeyDiagram.detect("sankey-beta\nA,B,100"))
  }

  test("detect: not a sankey diagram") {
    assert(!SankeyDiagram.detect("pie\n    \"A\" : 100"))
  }

  test("parse: CSV flows") {
    val db = SankeyParser.parse("sankey-beta\nA,B,100\nB,C,50\nA,C,30")
    assertEquals(db.nodes.size, 3)
    assertEquals(db.flows.size, 3)
    assertEquals(db.flows(0).source, "A")
    assertEquals(db.flows(0).target, "B")
    assertEquals(db.flows(0).value, 100.0)
  }

  test("parse: quoted names") {
    val db = SankeyParser.parse("sankey-beta\n\"Source A\",\"Target B\",200")
    assertEquals(db.flows.size, 1)
    assertEquals(db.flows(0).source, "Source A")
    assertEquals(db.flows(0).target, "Target B")
  }

  test("render: produces valid SVG") {
    val svg = SankeyDiagram.render("sankey-beta\nA,B,100\nB,C,50")
    assert(svg.contains("<svg"))
    assert(svg.contains("<rect"))
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("sankey-beta\nA,B,100")
    assert(svg.contains("<svg"))
  }
}
