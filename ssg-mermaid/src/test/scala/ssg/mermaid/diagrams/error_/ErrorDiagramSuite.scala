/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package mermaid
package diagrams
package error_

import munit.FunSuite

final class ErrorDiagramSuite extends FunSuite {

  test("detect: error keyword") {
    assert(ErrorDiagram.detect("error"))
  }

  test("detect: not an error diagram") {
    assert(!ErrorDiagram.detect("pie\n    \"A\" : 100"))
  }

  test("renderError: shows custom message") {
    val svg = ErrorDiagram.renderError("Something went wrong")
    assert(svg.contains("<svg"))
    assert(svg.contains("Something went wrong"))
  }

  test("render: produces valid SVG") {
    val svg = ErrorDiagram.render("error")
    assert(svg.contains("<svg"))
    assert(svg.contains("<circle"))
  }

  test("render: via Mermaid dispatch") {
    val svg = Mermaid.render("error")
    assert(svg.contains("<svg"))
  }
}
