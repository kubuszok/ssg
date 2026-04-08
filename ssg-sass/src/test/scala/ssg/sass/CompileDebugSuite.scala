/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass

final class CompileDebugSuite extends munit.FunSuite {

  test("@debug appends DEBUG: message to warnings") {
    val result = Compile.compileString("""@debug "hello world";""")
    assertEquals(result.css, "")
    assert(
      result.warnings.contains("DEBUG: hello world"),
      s"warnings=${result.warnings}"
    )
  }

  test("@debug evaluates an expression (variable + arithmetic)") {
    val result = Compile.compileString(
      """$x: 2;
        |@debug $x + 3;""".stripMargin
    )
    assert(
      result.warnings.contains("DEBUG: 5"),
      s"warnings=${result.warnings}"
    )
  }

  test("@warn appends WARNING: message to warnings") {
    val result = Compile.compileString("""@warn "be careful";""")
    assertEquals(result.css, "")
    assert(
      result.warnings.contains("WARNING: be careful"),
      s"warnings=${result.warnings}"
    )
  }

  test("@warn supports interpolation expressions") {
    val result = Compile.compileString(
      """$name: world;
        |@warn "hello #{$name}";""".stripMargin
    )
    assert(
      result.warnings.exists(_ == "WARNING: hello world"),
      s"warnings=${result.warnings}"
    )
  }

  test("@error throws SassException with the rendered message") {
    val ex = intercept[SassException] {
      val _ = Compile.compileString("""@error "boom";""")
    }
    assert(ex.getMessage.contains("boom"), s"message=${ex.getMessage}")
  }
}
