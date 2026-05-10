/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package requirement

import munit.FunSuite

final class RequirementDiagramSuite extends FunSuite {

  test("detect: requirementDiagram keyword") {
    assert(RequirementDiagram.detect("requirementDiagram\n    requirement test_req {"))
  }

  test("detect: not a requirement diagram") {
    assert(!RequirementDiagram.detect("pie\n    \"A\" : 100"))
  }

  test("parse: requirement and element") {
    val db = RequirementParser.parse(
      "requirementDiagram\n    requirement test_req {\n        id: 1\n        text: Hello\n    }\n    element test_elem {\n        type: Simulation\n    }"
    )
    assertEquals(db.requirements.size, 1)
    assert(db.requirements.contains("test_req"))
    assertEquals(db.elements.size, 1)
    assert(db.elements.contains("test_elem"))
  }

  test("render: produces valid SVG") {
    val svg = RequirementDiagram.render(
      "requirementDiagram\n    requirement test_req {\n        id: 1\n    }"
    )
    assert(svg.contains("<svg"))
    assert(svg.contains("<rect"))
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("requirementDiagram\n    requirement test_req {\n    }")
    assert(svg.contains("<svg"))
  }
}
