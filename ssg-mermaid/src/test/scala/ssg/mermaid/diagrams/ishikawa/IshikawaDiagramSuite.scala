/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package ishikawa

import munit.FunSuite

final class IshikawaDiagramSuite extends FunSuite {

  test("detect: ishikawa keyword") {
    assert(IshikawaDiagram.detect("ishikawa\nEffect\nCause1"))
  }

  test("detect: not an ishikawa diagram") {
    assert(!IshikawaDiagram.detect("pie\n    \"A\" : 100"))
  }

  test("parse: effect and branches") {
    val db = IshikawaParser.parse("ishikawa\nDefect\nMaterials\n  Poor quality\n  Wrong spec\nPeople\n  Untrained")
    assertEquals(db.effect, "Defect")
    assertEquals(db.branches.size, 2)
    assertEquals(db.branches(0).label, "Materials")
    assertEquals(db.branches(0).causes.size, 2)
    assertEquals(db.branches(1).label, "People")
    assertEquals(db.branches(1).causes.size, 1)
  }

  test("render: produces valid SVG") {
    val svg = IshikawaDiagram.render("ishikawa\nDefect\nMaterials\n  Poor quality")
    assert(svg.contains("<svg"))
    assert(svg.contains("<line"))
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("ishikawa\nDefect\nMaterials")
    assert(svg.contains("<svg"))
  }
}
