/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package er

import munit.FunSuite

final class ErDiagramSuite extends FunSuite {

  test("detect: erDiagram keyword") {
    assert(ErDiagram.detect("erDiagram\n    CUSTOMER ||--o{ ORDER : places"))
  }

  test("detect: not an ER diagram") {
    assert(!ErDiagram.detect("graph TD\n    A-->B"))
  }

  test("parse: entities from relationship") {
    val db = ErParser.parse("erDiagram\n    CUSTOMER ||--o{ ORDER : places")
    assert(db.entities.contains("CUSTOMER"), "CUSTOMER entity should exist")
    assert(db.entities.contains("ORDER"), "ORDER entity should exist")
    assertEquals(db.relationships.length, 1)
    assertEquals(db.relationships(0).entityA, "CUSTOMER")
    assertEquals(db.relationships(0).entityB, "ORDER")
    assertEquals(db.relationships(0).label, "places")
  }

  test("parse: cardinality markers") {
    val db  = ErParser.parse("erDiagram\n    CUSTOMER ||--o{ ORDER : places")
    val rel = db.relationships(0)
    assertEquals(rel.roleA, Cardinality.ExactlyOne)
    assertEquals(rel.roleB, Cardinality.ZeroOrMore)
    assertEquals(rel.relSpec, Identification.Identifying)
  }

  test("parse: non-identifying relationship") {
    val db  = ErParser.parse("erDiagram\n    CUSTOMER ||..o{ ORDER : places")
    val rel = db.relationships(0)
    assertEquals(rel.relSpec, Identification.NonIdentifying)
  }

  test("parse: entity with attributes") {
    val db = ErParser.parse(
      """erDiagram
        |    CUSTOMER {
        |        string name
        |        int age PK
        |    }""".stripMargin
    )
    assert(db.entities.contains("CUSTOMER"))
    val attrs = db.entities("CUSTOMER").attributes
    assertEquals(attrs.length, 2)
    assertEquals(attrs(0).attributeType, "string")
    assertEquals(attrs(0).attributeName, "name")
    assertEquals(attrs(1).attributeKeyType, "PK")
  }

  test("render: produces valid SVG") {
    val svg = ErDiagram.render("erDiagram\n    CUSTOMER ||--o{ ORDER : places")
    assert(svg.contains("<svg"), "Should contain <svg tag")
    assert(svg.contains("CUSTOMER"), "Should contain entity name")
    assert(svg.contains("ORDER"), "Should contain entity name")
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("erDiagram\n    CUSTOMER ||--o{ ORDER : places")
    assert(svg.contains("<svg"), "Mermaid dispatch should produce SVG")
  }
}
