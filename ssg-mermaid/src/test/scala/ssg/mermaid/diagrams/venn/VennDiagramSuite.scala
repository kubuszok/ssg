/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package venn

import munit.FunSuite

final class VennDiagramSuite extends FunSuite {

  test("detect: venn-beta keyword") {
    assert(VennDiagram.detect("venn-beta\n    set A[\"Set A\"]"))
  }

  test("detect: not a venn diagram") {
    assert(!VennDiagram.detect("pie\n    \"A\" : 100"))
  }

  test("parse: sets") {
    val db = VennParser.parse("venn-beta\n    title Groups\n    set A[\"Alpha\"]\n    set B[\"Beta\"]")
    assertEquals(db.title, "Groups")
    assertEquals(db.sets.size, 2)
    assertEquals(db.sets(0).label, "Alpha")
    assertEquals(db.sets(1).label, "Beta")
  }

  test("render: produces valid SVG") {
    val svg = VennDiagram.render("venn-beta\n    set A[\"Set A\"]\n    set B[\"Set B\"]")
    assert(svg.contains("<svg"))
    assert(svg.contains("<circle"))
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("venn-beta\n    set A[\"Set A\"]")
    assert(svg.contains("<svg"))
  }
}
