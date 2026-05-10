/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package cynefin

import munit.FunSuite

final class CynefinDiagramSuite extends FunSuite {

  test("detect: cynefin keyword") {
    assert(CynefinDiagram.detect("cynefin\nComplex: Item1"))
  }

  test("detect: not a cynefin diagram") {
    assert(!CynefinDiagram.detect("pie\n    \"A\" : 100"))
  }

  test("parse: domains with items") {
    val db = CynefinParser.parse("cynefin\nComplex: Agile, Scrum\nClear: Best Practice")
    assertEquals(db.items.size, 3)
    assertEquals(db.itemsInDomain("Complex").size, 2)
    assertEquals(db.itemsInDomain("Clear").size, 1)
  }

  test("render: produces valid SVG") {
    val svg = CynefinDiagram.render("cynefin\nComplex: Agile")
    assert(svg.contains("<svg"))
    assert(svg.contains("<rect"))
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("cynefin\nComplex: Agile")
    assert(svg.contains("<svg"))
  }
}
