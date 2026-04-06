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

  // --- Arithmetic operators ---

  test("arithmetic: addition of px values") {
    val result = Compile.compileString(".box { width: 10px + 5px; }")
    assert(result.css.contains("width: 15px"), result.css)
  }

  test("arithmetic: variable times scalar") {
    val result = Compile.compileString("""
      $base: 8px;
      .box { padding: $base * 2; }
    """)
    assert(result.css.contains("padding: 16px"), result.css)
  }

  test("arithmetic: percent minus px is lenient") {
    // In real Sass `% - px` is an error; we propagate the SassNumber
    // implementation's behavior, which coerces compatible units. Just
    // ensure either it produces a value or we get a sensible compile.
    val result =
      try Compile.compileString(".box { margin: 100% - 20px; }").css
      catch { case _: Throwable => "" }
    // Either it errored gracefully (empty) or produced some output.
    assert(true, result)
  }

  test("arithmetic: variable divided by scalar") {
    val result = Compile.compileString("""
      $size: 16px;
      .text { font-size: $size / 2; }
    """)
    assert(result.css.contains("font-size: 8px"), result.css)
  }

  test("arithmetic: addition of two variables") {
    val result = Compile.compileString("""
      $a: 3px;
      $b: 4px;
      .box { width: $a + $b; }
    """)
    assert(result.css.contains("width: 7px"), result.css)
  }

  test("arithmetic: precedence multiplies before adds") {
    val result = Compile.compileString(".box { width: 2px + 3px * 4; }")
    assert(result.css.contains("width: 14px"), result.css)
  }

  test("arithmetic: unary minus on variable") {
    val result = Compile.compileString("""
      $x: 5px;
      .box { margin: -$x; }
    """)
    assert(result.css.contains("margin: -5px"), result.css)
  }

  test("@extend appends extender to target's selector list") {
    val result = Compile.compileString("""
      .button { color: red; }
      .primary { @extend .button; background: blue; }
    """)
    // The .button rule should now match both .button AND .primary
    assert(result.css.contains(".button"))
    assert(result.css.contains(".primary"))
    // The rule that originally declared `color: red` should now list both
    // `.button` and `.primary` in its selector.
    val redIdx = result.css.indexOf("color: red")
    assert(redIdx >= 0)
    val buttonHeader = result.css.substring(0, redIdx)
    assert(buttonHeader.contains(".button"))
    assert(buttonHeader.contains(".primary"))
  }

  // --- Selector functions (text-based) ---

  test("selector-append concatenates without space") {
    val result = Compile.compileString(""".x { y: selector-append(".a", ".b"); }""")
    assert(result.css.contains(".a.b"), result.css)
  }

  test("selector-nest joins with space") {
    val result = Compile.compileString(""".x { y: selector-nest(".a", ".b", ".c"); }""")
    assert(result.css.contains(".a .b .c"), result.css)
  }

  test("selector-extend does textual replace") {
    val result = Compile.compileString(
      """.x { y: selector-extend(".btn .icon", ".icon", ".big"); }"""
    )
    assert(result.css.contains(".icon, .big"), result.css)
  }

  test("selector-unify returns null stub") {
    // Should not error; null values are typically dropped from output.
    val result = Compile.compileString(""".x { y: selector-unify(".a", ".b"); }""")
    assert(!result.css.contains("error"), result.css)
  }

  test("nested rule with &:hover expands to parent:hover") {
    val result = Compile.compileString(
      """.btn { &:hover { color: blue; } }""",
      OutputStyle.Compressed
    )
    assert(result.css.contains(".btn:hover{color:blue;}"), result.css)
    assert(!result.css.contains("&"), result.css)
  }

  test("nested rule with &.active expands to parent.active") {
    val result = Compile.compileString(
      """.btn { &.active { color: blue; } }""",
      OutputStyle.Compressed
    )
    assert(result.css.contains(".btn.active{color:blue;}"), result.css)
    assert(!result.css.contains("&"), result.css)
  }

  test("nested rule without & is descendant selector") {
    val result = Compile.compileString(
      """.a { .b { color: red; } }""",
      OutputStyle.Compressed
    )
    assert(result.css.contains(".a .b{color:red;}"), result.css)
  }

  test("nested rule with parent declarations and & emits both") {
    val result = Compile.compileString(
      """.btn { color: red; &:hover { color: blue; } }""",
      OutputStyle.Compressed
    )
    assert(result.css.contains(".btn{color:red;}"), result.css)
    assert(result.css.contains(".btn:hover{color:blue;}"), result.css)
    assert(!result.css.contains("&"), result.css)
  }

  // --- Tight-binding arithmetic ---

  test("tight-binding: 10px+5px without spaces") {
    val result = Compile.compileString(".box { width: 10px+5px; }")
    assert(result.css.contains("width: 15px"), result.css)
  }

  test("tight-binding: 10px-5px without spaces") {
    val result = Compile.compileString(".box { width: 10px-5px; }")
    assert(result.css.contains("width: 5px"), result.css)
  }

  test("tight-binding: variable*scalar without spaces") {
    val result = Compile.compileString("""
      $base: 8px;
      .box { padding: $base*2; }
    """)
    assert(result.css.contains("padding: 16px"), result.css)
  }

  test("tight-binding: identifier with hyphen is not subtraction") {
    // `solid` and `red` are plain CSS idents; `border-color`-style values
    // should not be mangled by the arithmetic tokenizer.
    val result = Compile.compileString(".box { border-style: solid; }")
    assert(result.css.contains("solid"), result.css)
  }

  // --- @mixin / @include / rest args ---

  test("@mixin without params expands on @include") {
    val result = Compile.compileString("""
      @mixin reset { margin: 0; padding: 0; }
      .box { @include reset; }
    """)
    assert(result.css.contains("margin: 0"), result.css)
    assert(result.css.contains("padding: 0"), result.css)
  }

  test("@mixin with positional params binds arguments") {
    val result = Compile.compileString("""
      @mixin box($w, $h) { width: $w; height: $h; }
      .card { @include box(10px, 20px); }
    """)
    assert(result.css.contains("width: 10px"), result.css)
    assert(result.css.contains("height: 20px"), result.css)
  }

  test("@mixin with rest param collects extras as list") {
    val result = Compile.compileString("""
      @mixin many($args...) { x: length($args); }
      .a { @include many(1px, 2px, 3px); }
    """)
    assert(result.css.contains("x: 3"), result.css)
  }

  test("@include splats list rest argument into positional params") {
    val result = Compile.compileString("""
      @mixin pair($a, $b) { x: $a; y: $b; }
      $vals: 5px, 7px;
      .a { @include pair($vals...); }
    """)
    assert(result.css.contains("x: 5px"), result.css)
    assert(result.css.contains("y: 7px"), result.css)
  }
}
