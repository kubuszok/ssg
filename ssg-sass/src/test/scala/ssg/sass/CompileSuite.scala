/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass

import ssg.sass.visitor.OutputStyle

final class CompileSuite extends munit.FunSuite {

  test("compiles empty stylesheet") {
    val result = Compile.compileString("")
    assertEquals(result.css, "")
  }

  test("compiles a simple style rule") {
    val result = Compile.compileString("a { color: red; }")
    assert(result.css.contains("a"))
    assert(result.css.contains("color: red"))
  }

  test("compiles compressed output") {
    val result = Compile.compileString("a { color: red; }", OutputStyle.Compressed)
    assertEquals(result.css, "a{color:red;}")
  }

  test("compiles multiple rules") {
    val result = Compile.compileString("""
      a { color: red; }
      b { color: blue; }
    """)
    assert(result.css.contains("a {"))
    assert(result.css.contains("b {"))
    assert(result.css.contains("red"))
    assert(result.css.contains("blue"))
  }

  test("compiles variable substitution") {
    val result = Compile.compileString("""
      $c: red;
      a { color: $c; }
    """)
    assert(result.css.contains("color: red"))
  }

  test("compiles numeric variable with unit") {
    val result = Compile.compileString("""
      $w: 10px;
      .box { width: $w; }
    """)
    assert(result.css.contains("width: 10px"))
  }

  test("variables don't emit CSS") {
    val result = Compile.compileString("""
      $c: red;
      a { color: $c; }
    """)
    // Variable declarations themselves should not appear in output
    assert(!result.css.contains("$c"))
  }

  test("compiles multiple declarations") {
    val result = Compile.compileString("""
      .button {
        color: red;
        padding: 10px;
        border: 1px solid gray;
      }
    """)
    assert(result.css.contains("color: red"))
    assert(result.css.contains("padding: 10px"))
    assert(result.css.contains("border"))
  }

  test("compiles @charset at-rule") {
    val result = Compile.compileString("""@charset "UTF-8";""")
    assert(result.css.contains("@charset"))
  }

  test("compressed output has no whitespace") {
    val result = Compile.compileString(
      """
        a { color: red; }
        b { color: blue; }
      """,
      OutputStyle.Compressed
    )
    assert(!result.css.contains("\n"))
    assert(!result.css.contains("  "))
  }

  test("compiles @media rule") {
    val result = Compile.compileString("""
      @media (min-width: 768px) {
        a { color: red; }
      }
    """)
    assert(result.css.contains("@media"))
    assert(result.css.contains("min-width"))
    assert(result.css.contains("color: red"))
  }

  test("compiles @supports rule") {
    val result = Compile.compileString("""
      @supports (display: grid) {
        .grid { display: grid; }
      }
    """)
    assert(result.css.contains("@supports"))
    assert(result.css.contains("display: grid"))
  }

  test("compiles variable with integer value") {
    val result = Compile.compileString("""
      $size: 100;
      .box { width: $size; }
    """)
    // Should NOT have trailing .0
    assert(result.css.contains("width: 100"))
    assert(!result.css.contains("100.0"))
  }

  test("compiles pixel variable without trailing decimal") {
    val result = Compile.compileString("""
      $w: 10px;
      .box { width: $w; }
    """)
    assert(result.css.contains("10px"))
    assert(!result.css.contains("10.0px"))
  }
}
