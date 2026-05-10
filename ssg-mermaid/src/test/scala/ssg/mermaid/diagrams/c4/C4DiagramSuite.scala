/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package c4

import munit.FunSuite

final class C4DiagramSuite extends FunSuite {

  test("detect: C4Context keyword") {
    assert(C4Diagram.detect("C4Context\n    Person(user, \"User\")"))
  }

  test("detect: C4Container keyword") {
    assert(C4Diagram.detect("C4Container\n    Container(api, \"API\")"))
  }

  test("detect: not a C4 diagram") {
    assert(!C4Diagram.detect("pie\n    \"A\" : 100"))
  }

  test("parse: person and system with relationship") {
    val db = C4Parser.parse(
      "C4Context\n    Person(user, \"User\", \"A user\")\n    System(sys, \"System\", \"The system\")\n    Rel(user, sys, \"Uses\")"
    )
    assertEquals(db.entities.size, 2)
    assertEquals(db.entities(0).label, "User")
    assertEquals(db.entities(0).entityType, "person")
    assertEquals(db.entities(1).label, "System")
    assertEquals(db.relationships.size, 1)
    assertEquals(db.relationships(0).label, "Uses")
  }

  test("render: produces valid SVG") {
    val svg = C4Diagram.render("C4Context\n    Person(user, \"User\")\n    System(sys, \"System\")")
    assert(svg.contains("<svg"))
    assert(svg.contains("<rect") || svg.contains("<circle"))
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("C4Context\n    Person(user, \"User\")")
    assert(svg.contains("<svg"))
  }
}
