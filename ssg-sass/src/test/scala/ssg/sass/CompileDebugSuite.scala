/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass

final class CompileDebugSuite extends munit.FunSuite {

  test("@debug outputs to logger, not to warnings") {
    // dart-sass: visitDebugRule (evaluate.dart:1363-1370) only calls
    // _logger.debug — does NOT add to the warnings list.
    val result = Compile.compileString("""@debug "hello world";""")
    assertEquals(result.css, "")
    assert(
      !result.warnings.exists(_.contains("DEBUG")),
      s"@debug should not appear in warnings, got: ${result.warnings}"
    )
  }

  test("@debug evaluates an expression (variable + arithmetic) without adding to warnings") {
    val result = Compile.compileString(
      """$x: 2;
        |@debug $x + 3;""".stripMargin
    )
    assert(
      !result.warnings.exists(_.contains("DEBUG")),
      s"@debug should not appear in warnings, got: ${result.warnings}"
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

  // RC-4: .sass newline inside function parens
  test("RC4: sass function call with newlines inside parens") {
    val sass   = "@use \"sass:list\"\na\n  b: list.append(\n    c d, e)\n"
    val result = Compile.compileString(sass, syntax = Syntax.Sass)
    assert(result.css.contains("c d e"), s"css=${result.css}")
  }

  // RC-5: extend into pseudo :is() — regression for dart-sass#1297
  test("RC5: extend into pseudo :is()") {
    val scss =
      """:is(midstream) {@extend upstream}
        |downstream {@extend midstream}
        |upstream {a: b}""".stripMargin
    val result = Compile.compileString(scss)
    // Expected: upstream, :is(midstream), :is(midstream, downstream) { a: b; }
    assert(
      result.css.contains(":is(midstream)") && result.css.contains(":is(midstream, downstream)"),
      s"Expected ':is(midstream)' and ':is(midstream, downstream)' in output; css=${result.css}"
    )
  }

  // RC-7: directive interpolation in at-rule value
  test("RC7: directive interpolation in at-rule value") {
    val scss =
      """$baz: "value";
        |@foo bar#{$baz} qux {a: b}""".stripMargin
    val result = Compile.compileString(scss)
    assert(result.css.contains("@foo barvalue qux"), s"css=${result.css}")
  }
}
