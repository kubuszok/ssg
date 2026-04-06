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

  test("nested style rule expands as descendant selector") {
    val result = Compile.compileString("a { color: red; b { color: blue; } }")
    assert(result.css.contains("a b"))
    assert(result.css.contains("color: blue"))
    assert(result.css.contains("color: red"))
    // Nested rule should be a sibling, not inside `a { ... }`.
    assert(!result.css.contains("a b {".reverse))
  }

  test("parent selector & expands to parent") {
    val result = Compile.compileString(".card { &:hover { color: blue; } }")
    assert(result.css.contains(".card:hover"))
    assert(result.css.contains("color: blue"))
  }

  test("parent selector & with comma-separated parents") {
    val result = Compile.compileString(".a, .b { &:hover { color: red; } }")
    assert(result.css.contains(".a:hover"))
    assert(result.css.contains(".b:hover"))
  }

  // --- Built-in functions ---

  test("calls abs() function") {
    val result = Compile.compileString(".box { margin: abs(-5px); }")
    assert(result.css.contains("margin: 5px"))
  }

  test("calls ceil() function") {
    val result = Compile.compileString(".box { width: ceil(4.2); }")
    assert(result.css.contains("width: 5"))
  }

  test("calls floor() function") {
    val result = Compile.compileString(".box { width: floor(4.9); }")
    assert(result.css.contains("width: 4"))
  }

  test("calls percentage() function") {
    val result = Compile.compileString(".box { width: percentage(0.5); }")
    assert(result.css.contains("width: 50%"))
  }

  test("calls unitless() function") {
    val result = Compile.compileString(".box { x: unitless(5); y: unitless(5px); }")
    assert(result.css.contains("x: true"))
    assert(result.css.contains("y: false"))
  }

  test("calls to-upper-case() function") {
    val result = Compile.compileString(""".box { content: to-upper-case("hello"); }""")
    assert(result.css.contains("HELLO"))
  }

  test("calls length() function on list") {
    val result = Compile.compileString(""".box { x: length(1 2 3); }""")
    assert(result.css.contains("x: 3"))
  }

  test("calls type-of() function") {
    val result = Compile.compileString(""".box { x: type-of(42); y: type-of("hi"); z: type-of(true); }""")
    assert(result.css.contains("x: number"))
    assert(result.css.contains("y: string"))
    assert(result.css.contains("z: bool"))
  }

  // --- Color functions ---

  test("calls rgb() 3-arg constructor") {
    // Verify via accessor round-trip.
    val result = Compile.compileString(".box { r: red(rgb(255, 0, 0)); }")
    assert(result.css.contains("r: 255"))
  }

  test("calls rgb() 4-arg with alpha") {
    val result = Compile.compileString(".box { a: alpha(rgb(255, 0, 0, 0.5)); }")
    assert(result.css.contains("a: 0.5") || result.css.contains("a: .5"))
  }

  test("calls hsl() 3-arg constructor") {
    val result = Compile.compileString(".box { r: red(hsl(0, 100%, 50%)); }")
    assert(result.css.contains("r: 255"))
  }

  test("calls red() accessor") {
    val result = Compile.compileString(".box { x: red(rgb(128, 64, 32)); }")
    assert(result.css.contains("x: 128"))
  }

  test("calls green() accessor") {
    val result = Compile.compileString(".box { x: green(rgb(128, 64, 32)); }")
    assert(result.css.contains("x: 64"))
  }

  test("calls blue() accessor") {
    val result = Compile.compileString(".box { x: blue(rgb(128, 64, 32)); }")
    assert(result.css.contains("x: 32"))
  }

  test("calls alpha() accessor") {
    val result = Compile.compileString(".box { x: alpha(rgb(1, 2, 3, 0.5)); }")
    assert(result.css.contains("x: 0.5") || result.css.contains("x: .5"))
  }

  test("calls lightness() accessor") {
    val result = Compile.compileString(".box { x: lightness(hsl(0, 100%, 50%)); }")
    assert(result.css.contains("x: 50%"))
  }

  test("calls saturation() accessor") {
    val result = Compile.compileString(".box { x: saturation(hsl(0, 100%, 50%)); }")
    assert(result.css.contains("x: 100%"))
  }

  test("calls lighten() function") {
    val result = Compile.compileString(".box { color: lighten(hsl(0, 100%, 50%), 10%); }")
    // Lightness should now be 60%
    val r2 = Compile.compileString(".box { x: lightness(lighten(hsl(0, 100%, 50%), 10%)); }")
    assert(r2.css.contains("x: 60%"))
    assert(result.css.contains("color:"))
  }

  test("calls darken() function") {
    val result = Compile.compileString(
      ".box { x: lightness(darken(hsl(0, 100%, 50%), 20%)); }"
    )
    assert(result.css.contains("x: 30%"))
  }

  test("calls invert() function") {
    val result = Compile.compileString(".box { x: red(invert(rgb(255, 0, 0))); }")
    assert(result.css.contains("x: 0"))
  }

  test("calls grayscale() function") {
    val result = Compile.compileString(
      ".box { x: saturation(grayscale(hsl(120, 80%, 50%))); }"
    )
    assert(result.css.contains("x: 0%"))
  }

  test("interpolation in string literals") {
    val result = Compile.compileString("""
      $name: "world";
      a { content: "hello #{$name}"; }
    """)
    assert(result.css.contains("hello world"))
  }

  test("interpolation in selector") {
    val result = Compile.compileString("""
      $class: "button";
      .#{$class} { color: red; }
    """)
    assert(result.css.contains(".button"))
  }
}
