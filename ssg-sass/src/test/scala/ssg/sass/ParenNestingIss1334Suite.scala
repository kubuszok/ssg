/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package sass

import ssg.sass.visitor.OutputStyle

/** Regression guard for ISS-1334 — mixed paren nesting.
  *
  * The ISS-1334 peel optimisation (stack-safe deep-nested parens) must handle
  * mixed nesting correctly: after a peeled level's inner content, the parser
  * must RESUME expression parsing (space-list/operator/comma tail) before
  * consuming the outer `)`. These inputs exercise the resume path — each one
  * must produce the faithful CSS, not throw "Expected ')'".
  *
  * Additionally, a deep pure-nesting sanity test confirms the ISS-1000 fix
  * remains stack-safe.
  */
final class ParenNestingIss1334Suite extends munit.FunSuite {

  private def compileCss(scss: String): String =
    Compile.compileString(scss, OutputStyle.Expanded).css.trim

  test("ISS-1334: ((1) 2) produces space-list") {
    val css = compileCss("a { x: ((1) 2); }")
    assert(css.contains("x: 1 2;"), s"expected 'x: 1 2;' in:\n$css")
  }

  test("ISS-1334: ((1) + 2) evaluates addition") {
    val css = compileCss("a { x: ((1) + 2); }")
    assert(css.contains("x: 3;"), s"expected 'x: 3;' in:\n$css")
  }

  test("ISS-1334: ((1), 2) produces comma-list") {
    val css = compileCss("a { x: ((1), 2); }")
    assert(css.contains("x: 1, 2;"), s"expected 'x: 1, 2;' in:\n$css")
  }

  test("ISS-1334: (((1) 2)) double-nested space-list") {
    val css = compileCss("a { x: (((1) 2)); }")
    assert(css.contains("x: 1 2;"), s"expected 'x: 1 2;' in:\n$css")
  }

  test("ISS-1334: ((1 2) (3 4)) space-list of space-lists") {
    val css = compileCss("a { x: ((1 2) (3 4)); }")
    assert(css.contains("x: 1 2 3 4;"), s"expected 'x: 1 2 3 4;' in:\n$css")
  }

  test("ISS-1334: (((((1))))) deep pure nesting") {
    val css = compileCss("a { x: (((((1))))); }")
    assert(css.contains("x: 1;"), s"expected 'x: 1;' in:\n$css")
  }
}
