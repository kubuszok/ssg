/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package wardley

import munit.FunSuite

final class WardleyDiagramSuite extends FunSuite {

  test("detect: wardley keyword") {
    assert(WardleyDiagram.detect("wardley\ncomponent User [0.9, 0.5]"))
  }

  test("detect: not a wardley diagram") {
    assert(!WardleyDiagram.detect("pie\n    \"A\" : 100"))
  }

  test("parse: components with coordinates") {
    val db = WardleyParser.parse("wardley\ntitle My Map\ncomponent User [0.9, 0.5]\ncomponent API [0.6, 0.7]")
    assertEquals(db.title, "My Map")
    assertEquals(db.components.size, 2)
    assertEquals(db.components(0).name, "User")
    assertEquals(db.components(0).visibility, 0.9)
    assertEquals(db.components(0).evolution, 0.5)
  }

  test("parse: links") {
    val db = WardleyParser.parse("wardley\ncomponent A [0.5, 0.5]\ncomponent B [0.3, 0.7]\nA --> B")
    assertEquals(db.links.size, 1)
    assertEquals(db.links(0).from, "A")
    assertEquals(db.links(0).to, "B")
  }

  test("render: produces valid SVG") {
    val svg = WardleyDiagram.render("wardley\ncomponent User [0.9, 0.5]")
    assert(svg.contains("<svg"))
    assert(svg.contains("<circle"))
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("wardley\ncomponent User [0.9, 0.5]")
    assert(svg.contains("<svg"))
  }
}
