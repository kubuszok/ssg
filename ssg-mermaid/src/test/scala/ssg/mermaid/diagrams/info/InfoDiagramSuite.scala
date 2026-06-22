/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package info

import munit.FunSuite

final class InfoDiagramSuite extends FunSuite {

  test("detect: info keyword") {
    assert(InfoDiagram.detect("info"))
  }

  test("detect: not an info diagram") {
    assert(!InfoDiagram.detect("pie\n    \"A\" : 100"))
  }

  test("parse: creates db with version") {
    val db = InfoParser.parse("info")
    assertEquals(db.version, ssg.mermaid.UpstreamVersion)
  }

  test("render: produces valid SVG with version") {
    val svg = InfoDiagram.render("info")
    assert(svg.contains("<svg"))
    assert(svg.contains(ssg.mermaid.UpstreamVersion), s"Should contain version number")
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("info")
    assert(svg.contains("<svg"))
    assert(svg.contains("mermaid version"))
  }
}
