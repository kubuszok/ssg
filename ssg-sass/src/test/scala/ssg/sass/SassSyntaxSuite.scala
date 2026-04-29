/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package sass

final class SassSyntaxSuite extends munit.FunSuite {

  test("compiles a single rule (.sass)") {
    val src =
      """a
        |  color: red
        |""".stripMargin
    val result = Compile.compileString(src, syntax = Syntax.Sass)
    assert(result.css.contains("a"))
    assert(result.css.contains("color: red"))
  }

  test("compiles a variable declaration (.sass)") {
    val src =
      """$c: red
        |a
        |  color: $c
        |""".stripMargin
    val result = Compile.compileString(src, syntax = Syntax.Sass)
    assert(result.css.contains("color: red"))
  }

  test("compiles a nested rule (.sass)") {
    val src =
      """.outer
        |  color: red
        |  .inner
        |    color: blue
        |""".stripMargin
    val result = Compile.compileString(src, syntax = Syntax.Sass)
    assert(result.css.contains("color: red"))
    assert(result.css.contains("color: blue"))
  }

  test("compiles multiple rules (.sass)") {
    val src =
      """a
        |  color: red
        |b
        |  color: blue
        |""".stripMargin
    val result = Compile.compileString(src, syntax = Syntax.Sass)
    assert(result.css.contains("a"))
    assert(result.css.contains("b"))
    assert(result.css.contains("red"))
    assert(result.css.contains("blue"))
  }

  test("ignores // comments (.sass)") {
    val src =
      """// a leading comment
        |a
        |  // inside the rule
        |  color: red
        |""".stripMargin
    val result = Compile.compileString(src, syntax = Syntax.Sass)
    assert(result.css.contains("color: red"))
  }
}
