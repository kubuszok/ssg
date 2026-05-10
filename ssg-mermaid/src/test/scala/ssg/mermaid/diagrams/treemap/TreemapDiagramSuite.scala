/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package treemap

import munit.FunSuite

final class TreemapDiagramSuite extends FunSuite {

  test("detect: treemap keyword") {
    assert(TreemapDiagram.detect("treemap\nA: 10"))
  }

  test("detect: not a treemap diagram") {
    assert(!TreemapDiagram.detect("pie\n    \"A\" : 100"))
  }

  test("parse: leaf nodes with values") {
    val db = TreemapParser.parse("treemap\nA: 100\nB: 200\nC: 300")
    assertEquals(db.roots.size, 3)
    assertEquals(db.roots(0).label, "A")
    assertEquals(db.roots(0).value, 100.0)
    assertEquals(db.roots(1).value, 200.0)
  }

  test("render: produces valid SVG") {
    val svg = TreemapDiagram.render("treemap\nA: 100\nB: 200")
    assert(svg.contains("<svg"))
    assert(svg.contains("<rect"))
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("treemap\nA: 100")
    assert(svg.contains("<svg"))
  }
}
