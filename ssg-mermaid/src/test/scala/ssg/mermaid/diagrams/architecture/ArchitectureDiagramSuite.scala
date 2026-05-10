/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package architecture

import munit.FunSuite

final class ArchitectureDiagramSuite extends FunSuite {

  test("detect: architecture-beta keyword") {
    assert(ArchitectureDiagram.detect("architecture-beta\n    service api(API)"))
  }

  test("detect: not an architecture diagram") {
    assert(!ArchitectureDiagram.detect("pie\n    \"A\" : 100"))
  }

  test("parse: service and group") {
    val db = ArchitectureParser.parse("architecture-beta\n    group cloud(Cloud)\n    service api(API Server) in cloud")
    assertEquals(db.groups.size, 1)
    assertEquals(db.nodes.size, 1)
    assertEquals(db.nodes(0).label, "API Server")
  }

  test("render: produces valid SVG") {
    val svg = ArchitectureDiagram.render("architecture-beta\n    service api(API)")
    assert(svg.contains("<svg"))
    assert(svg.contains("<rect"))
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("architecture-beta\n    service api(API)")
    assert(svg.contains("<svg"))
  }
}
