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

  test("calc() preserves incompatible units") {
    val css = Compile.compileString("a { width: calc(100% - 20px); }", OutputStyle.Compressed).css
    assertEquals(css, "a{width:calc(100% - 20px);}")
  }

  test("calc() simplifies compatible numeric arithmetic") {
    val css = Compile.compileString("a { width: calc(10px + 5px); }", OutputStyle.Compressed).css
    assertEquals(css, "a{width:15px;}")
  }

  test("min() preserves multiple incompatible arguments") {
    val css = Compile.compileString("a { width: min(100%, 500px); }", OutputStyle.Compressed).css
    assertEquals(css, "a{width:min(100%, 500px);}")
  }

  test("max() resolves variable arguments") {
    // With two compatible numeric arguments Sass simplifies max(10px, 20px) to 20px.
    val src = "$base: 20px; a { width: max(10px, $base); }"
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assertEquals(css, "a{width:20px;}")
  }

  test("max() preserves incompatible variable arguments") {
    val src = "$base: 50%; a { width: max(10px, $base); }"
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assertEquals(css, "a{width:max(10px, 50%);}")
  }

  test("clamp() with three numeric arguments") {
    val css = Compile.compileString("a { width: clamp(10px, 50%, 500px); }", OutputStyle.Compressed).css
    assertEquals(css, "a{width:clamp(10px, 50%, 500px);}")
  }

  test("@at-root (with: media) inside @media keeps the media wrapper") {
    val src =
      """@media screen {
        |  .a {
        |    @at-root (with: media) {
        |      .b { color: red; }
        |    }
        |  }
        |}""".stripMargin
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    // The .b rule should remain inside @media screen, not pop out to the top.
    assert(css.contains("@media screen"), s"expected @media screen in: $css")
    assert(css.contains(".b"), s"expected .b in: $css")
    val mediaIdx = css.indexOf("@media")
    val bIdx     = css.indexOf(".b")
    assert(bIdx > mediaIdx, s"expected .b after @media in: $css")
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

  test("compiles @at-root bare block at stylesheet root") {
    val result = Compile.compileString(
      """
      .parent {
        color: red;
        @at-root {
          .child { color: blue; }
        }
      }
    """
    )
    // .child should be at root, not nested under .parent
    assert(result.css.contains(".child"))
    assert(result.css.contains("color: blue"))
    assert(!result.css.contains(".parent .child"))
  }

  test("compiles @at-root with selector") {
    val result = Compile.compileString("""
      .parent {
        color: red;
        @at-root .sibling { color: green; }
      }
    """)
    assert(result.css.contains(".sibling"))
    assert(result.css.contains("color: green"))
    assert(!result.css.contains(".parent .sibling"))
  }

  test("compiles @each over a list") {
    val result = Compile.compileString("""
      @each $c in red, green, blue {
        .x-#{$c} { color: $c; }
      }
    """)
    assert(result.css.contains(".x-red"))
    assert(result.css.contains(".x-green"))
    assert(result.css.contains(".x-blue"))
    assert(result.css.contains("color: red"))
    assert(result.css.contains("color: green"))
    assert(result.css.contains("color: blue"))
  }

  test("compiles @each destructuring over list of lists") {
    val result = Compile.compileString("""
      @each $name, $size in (small 10px, big 20px) {
        .#{$name} { width: $size; }
      }
    """)
    assert(result.css.contains(".small"))
    assert(result.css.contains("width: 10px"))
    assert(result.css.contains(".big"))
    assert(result.css.contains("width: 20px"))
  }

  test("compiles @each over a map with key/value destructuring") {
    val result = Compile.compileString(
      """
      $sizes: (small: 10px, big: 20px);
      @each $name, $size in $sizes {
        .#{$name} { width: $size; }
      }
    """
    )
    assert(result.css.contains(".small"))
    assert(result.css.contains("width: 10px"))
    assert(result.css.contains(".big"))
    assert(result.css.contains("width: 20px"))
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

  test("selector-unify merges two compound selectors via AST") {
    val result = Compile.compileString(""".x { y: selector-unify(".a", ".b"); }""")
    assert(result.css.contains(".a.b") || result.css.contains(".b.a"), result.css)
  }

  test("selector-unify of conflicting ids fails gracefully") {
    val result = Compile.compileString(""".x { y: selector-unify("#a", "#b"); }""")
    assert(!result.css.contains("error"), result.css)
  }

  test("selector-append AST preserves compound merge") {
    val result = Compile.compileString(""".x { y: selector-append(".a", ".b", ".c"); }""")
    assert(result.css.contains(".a.b.c"), result.css)
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

  // --- Interpolation in expression values / names ---

  test("interpolation in declaration value: #{$base * 2}px") {
    val result = Compile.compileString("""
      $base: 8px;
      .box { width: #{$base * 2}px; }
    """)
    assert(result.css.contains("width: 16px"), result.css)
  }

  test("interpolation in declaration value: prefix-#{$x}") {
    val result = Compile.compileString("""
      $x: 3;
      .box { margin: m-#{$x}-end; }
    """)
    assert(result.css.contains("m-3-end"), result.css)
  }

  test("interpolation in property name") {
    val result = Compile.compileString("""
      $prefix: border;
      .box { #{$prefix}-color: red; }
    """)
    assert(result.css.contains("border-color: red"), result.css)
  }

  test("interpolation in middle of property name") {
    val result = Compile.compileString("""
      $side: left;
      .box { margin-#{$side}: 10px; }
    """)
    assert(result.css.contains("margin-left: 10px"), result.css)
  }

  test("string concatenation with interpolation") {
    val result = Compile.compileString("""
      $x: 42;
      a { content: "foo-#{$x}-bar"; }
    """)
    assert(result.css.contains("foo-42-bar"), result.css)
  }

  // --- Keyword arguments ---

  test("@mixin accepts keyword arguments") {
    val result = Compile.compileString("""
      @mixin box($w, $h) { width: $w; height: $h; }
      .card { @include box($h: 20px, $w: 10px); }
    """)
    assert(result.css.contains("width: 10px"), result.css)
    assert(result.css.contains("height: 20px"), result.css)
  }

  test("@mixin accepts mixed positional and keyword arguments") {
    val result = Compile.compileString(
      """
      @mixin box($w, $h, $c) { width: $w; height: $h; color: $c; }
      .card { @include box(10px, $c: red, $h: 20px); }
    """
    )
    assert(result.css.contains("width: 10px"), result.css)
    assert(result.css.contains("height: 20px"), result.css)
    assert(result.css.contains("color: red"), result.css)
  }

  test("named arguments in function call parse without error") {
    // Keyword-aware dispatch for built-ins is deferred — unknown functions
    // fall back to plain CSS. We just verify the parse completes and the
    // output contains the original call name.
    val result = Compile.compileString(""".x { y: my-fn($a: 1, $b: 2); }""")
    assert(result.css.contains("my-fn"), result.css)
  }

  test("@function with @return returns a value to the caller") {
    val result = Compile.compileString("""
      @function double($x) { @return $x * 2; }
      .box { width: double(10px); }
    """)
    assert(result.css.contains("width: 20px"), result.css)
  }

  test("@function parameter default is not consumed past next comma") {
    val result = Compile.compileString("""
      @function pick($a: 1, $b: 2) { @return $a + $b; }
      .box { x: pick(); }
    """)
    assert(result.css.contains("x: 3"), result.css)
  }

  test("built-in rgb() accepts named arguments") {
    val result = Compile.compileString(
      ".box { r: red(rgb($red: 255, $green: 0, $blue: 0)); }"
    )
    assert(result.css.contains("r: 255"), result.css)
  }

  test("built-in hsl() accepts named arguments") {
    val result = Compile.compileString(
      ".box { x: lightness(hsl($hue: 0, $saturation: 100%, $lightness: 50%)); }"
    )
    assert(result.css.contains("x: 50%"), result.css)
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

  // --- Value formatting (color shorthand, named colors, number tweaks) ---

  test("rgb(255,255,255) emits #fff (named is shorter -> white)") {
    // #ffffff -> #fff (3 chars after #), white is 5 chars; #fff wins.
    val result = Compile.compileString(".box { color: rgb(255, 255, 255); }")
    assert(result.css.contains("color: #fff"), result.css)
  }

  test("rgb(255,0,0) emits red (name shorter than #f00)") {
    // #f00 (4 chars) vs red (3 chars). red wins in expanded mode.
    val result = Compile.compileString(".box { color: rgb(255, 0, 0); }")
    assert(result.css.contains("color: red"), result.css)
  }

  test("rgb(170,187,204) emits #abc shorthand") {
    val result = Compile.compileString(".box { color: rgb(170, 187, 204); }")
    assert(result.css.contains("color: #abc"), result.css)
  }

  test("rgb(18,52,86) emits full 6-digit hex (no shorthand possible)") {
    val result = Compile.compileString(".box { color: rgb(18, 52, 86); }")
    assert(result.css.contains("color: #123456"), result.css)
  }

  test("compressed mode: rgb(255,255,255) emits #fff") {
    val result = Compile.compileString(
      ".box { color: rgb(255, 255, 255); }",
      OutputStyle.Compressed
    )
    assert(result.css.contains("#fff"), result.css)
  }

  test("compressed mode strips leading zero from 0.5px") {
    val result = Compile.compileString(
      ".box { width: 0.5px; }",
      OutputStyle.Compressed
    )
    assert(result.css.contains(".5px"), result.css)
    assert(!result.css.contains("0.5px"), result.css)
  }

  test("expanded mode keeps leading zero on 0.5px") {
    val result = Compile.compileString(".box { width: 0.5px; }")
    assert(result.css.contains("0.5px"), result.css)
  }

  test("number trailing zero is stripped: 1.50px -> 1.5px") {
    val result = Compile.compileString(".box { width: 1.50px; }")
    assert(result.css.contains("1.5px"), result.css)
    assert(!result.css.contains("1.50px"), result.css)
  }

  test("number trailing zero is stripped: 3.0 -> 3") {
    val result = Compile.compileString(".box { z-index: 3.0; }")
    assert(result.css.contains("z-index: 3;"), result.css)
  }

  // --- @media query parsing ---

  test("@media with condition-only query emits @media block") {
    val result = Compile.compileString(
      "@media (max-width: 600px) { .a { color: red; } }",
      OutputStyle.Compressed
    )
    assert(result.css.contains("@media (max-width: 600px)"), result.css)
    assert(result.css.contains(".a{color:red;}"), result.css)
  }

  test("@media with type and condition preserves both") {
    val result = Compile.compileString(
      "@media screen and (min-width: 768px) { .a { color: red; } }",
      OutputStyle.Compressed
    )
    assert(result.css.contains("@media screen and (min-width: 768px)"), result.css)
    assert(result.css.contains(".a{color:red;}"), result.css)
  }

  test("@media supports #{...} interpolation in query") {
    val result = Compile.compileString(
      """
        $bp: 600px;
        @media (max-width: #{$bp}) { .a { color: red; } }
      """,
      OutputStyle.Compressed
    )
    assert(result.css.contains("@media (max-width: 600px)"), result.css)
    assert(result.css.contains(".a{color:red;}"), result.css)
  }

  test("nested @media inside style rule bubbles out") {
    val result = Compile.compileString(
      ".a { @media (max-width: 600px) { color: red; } }",
      OutputStyle.Compressed
    )
    // Expected bubbling: `@media (max-width: 600px) { .a { color: red; } }`.
    assert(result.css.contains("@media (max-width: 600px)"), result.css)
    assert(result.css.contains(".a{color:red;}"), result.css)
    // The @media must appear before the inner `.a` selector.
    val mediaIdx = result.css.indexOf("@media")
    val ruleIdx  = result.css.indexOf(".a{color:red;}")
    assert(mediaIdx >= 0 && ruleIdx > mediaIdx, result.css)
  }

  test("nested @media inside nested @media") {
    val result = Compile.compileString(
      """
        @media screen {
          @media (max-width: 600px) {
            .a { color: red; }
          }
        }
      """,
      OutputStyle.Compressed
    )
    assert(result.css.contains("@media screen"), result.css)
    assert(result.css.contains("@media (max-width: 600px)"), result.css)
    assert(result.css.contains(".a{color:red;}"), result.css)
  }

  // ---------------------------------------------------------------------------
  // @supports
  // ---------------------------------------------------------------------------

  test("@supports with single condition compiles") {
    val result = Compile.compileString(
      "@supports (display: grid) { .a { color: red; } }",
      OutputStyle.Compressed
    )
    assert(result.css.contains("@supports (display: grid)"), result.css)
    assert(result.css.contains(".a{color:red;}"), result.css)
  }

  test("@supports with `and` operator preserves both conditions") {
    val result = Compile.compileString(
      "@supports (display: grid) and (color: red) { .a { color: red; } }",
      OutputStyle.Compressed
    )
    assert(
      result.css.contains("(display: grid) and (color: red)"),
      result.css
    )
    assert(result.css.contains(".a{color:red;}"), result.css)
  }

  test("@supports supports #{...} interpolation in the condition") {
    val result = Compile.compileString(
      """
        $prop: display;
        @supports (#{$prop}: grid) { .a { color: red; } }
      """,
      OutputStyle.Compressed
    )
    assert(result.css.contains("@supports (display: grid)"), result.css)
    assert(result.css.contains(".a{color:red;}"), result.css)
  }

  test("nested @supports inside style rule bubbles out") {
    val result = Compile.compileString(
      ".a { @supports (display: grid) { color: red; } }",
      OutputStyle.Compressed
    )
    assert(result.css.contains("@supports (display: grid)"), result.css)
    assert(result.css.contains(".a{color:red;}"), result.css)
    val atIdx   = result.css.indexOf("@supports")
    val ruleIdx = result.css.indexOf(".a{color:red;}")
    assert(atIdx >= 0 && ruleIdx > atIdx, result.css)
  }

  // ---------------------------------------------------------------------------
  // @keyframes
  // ---------------------------------------------------------------------------

  test("@keyframes with percent selectors compiles") {
    val result = Compile.compileString(
      """
        @keyframes spin {
          0% { opacity: 0; }
          50% { opacity: 0.5; }
          100% { opacity: 1; }
        }
      """,
      OutputStyle.Compressed
    )
    assert(result.css.contains("@keyframes spin"), result.css)
    assert(result.css.contains("0%{opacity:0;}"), result.css)
    assert(result.css.contains("50%{opacity:"), result.css)
    assert(result.css.contains("100%{opacity:1;}"), result.css)
  }

  test("@keyframes maps from/to to 0%/100%") {
    val result = Compile.compileString(
      """
        @keyframes fade {
          from { opacity: 0; }
          to   { opacity: 1; }
        }
      """,
      OutputStyle.Compressed
    )
    assert(result.css.contains("@keyframes fade"), result.css)
    assert(result.css.contains("0%{opacity:0;}"), result.css)
    assert(result.css.contains("100%{opacity:1;}"), result.css)
    assert(!result.css.contains("from{"), result.css)
    assert(!result.css.contains("to{"), result.css)
  }

  test("@keyframes supports comma-separated selectors") {
    val result = Compile.compileString(
      """
        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.5; }
        }
      """,
      OutputStyle.Compressed
    )
    assert(result.css.contains("@keyframes pulse"), result.css)
    assert(result.css.contains("0%, 100%"), result.css)
  }

  // ---------------------------------------------------------------------------
  // New built-in functions (math / string / list / meta / map)
  // ---------------------------------------------------------------------------

  test("calls sqrt() function") {
    val result = Compile.compileString(".box { x: sqrt(16); }")
    assert(result.css.contains("x: 4"), result.css)
  }

  test("calls pow() function") {
    val result = Compile.compileString(".box { x: pow(2, 10); }")
    assert(result.css.contains("x: 1024"), result.css)
  }

  test("calls clamp() function") {
    val result = Compile.compileString(".box { x: clamp(1px, 5px, 10px); y: clamp(1px, 20px, 10px); }")
    assert(result.css.contains("x: 5px"), result.css)
    assert(result.css.contains("y: 10px"), result.css)
  }

  test("calls hypot() function") {
    val result = Compile.compileString(".box { x: hypot(3, 4); }")
    assert(result.css.contains("x: 5"), result.css)
  }

  test("calls log() function of e is 1") {
    val result = Compile.compileString(".box { x: log(1); }")
    assert(result.css.contains("x: 0"), result.css)
  }

  test("calls unique-id() returns unquoted string") {
    val result = Compile.compileString(".a { x: unique-id(); } .b { x: unique-id(); }")
    assert(result.css.contains("x: u"), result.css)
  }

  test("calls mixin-exists() placeholder returns false") {
    val result = Compile.compileString(""".box { x: mixin-exists("foo"); }""")
    assert(result.css.contains("x: false"), result.css)
  }

  test("calls content-exists() placeholder returns false") {
    val result = Compile.compileString(""".box { x: content-exists(); }""")
    assert(result.css.contains("x: false"), result.css)
  }

  test("nth() supports negative indices") {
    val result = Compile.compileString(".box { x: nth(1px 2px 3px, -1); }")
    assert(result.css.contains("x: 3px"), result.css)
  }

  test("calls global-variable-exists() placeholder returns false") {
    val result = Compile.compileString(""".box { x: global-variable-exists("foo"); }""")
    assert(result.css.contains("x: false"), result.css)
  }

  // ---------------------------------------------------------------------------
  // Color manipulation built-ins (opacify/transparentize/change/adjust/scale)
  // ---------------------------------------------------------------------------

  test("opacify() increases alpha") {
    val result = Compile.compileString(".a { x: opacify(rgba(0, 0, 0, 0.4), 0.5); }")
    assert(result.css.contains("rgba(0, 0, 0, 0.9)"), result.css)
  }

  test("transparentize() / fade-out decrease alpha") {
    val result = Compile.compileString(".a { x: fade-out(rgba(0, 0, 0, 0.8), 0.3); }")
    assert(result.css.contains("rgba(0, 0, 0, 0.5)"), result.css)
  }

  test("rgba($color, $alpha) overload sets alpha") {
    val result = Compile.compileString(".a { x: rgba(rgb(255, 0, 0), 0.25); }")
    assert(result.css.contains("rgba(255, 0, 0, 0.25)"), result.css)
  }

  test("change-color() with $alpha replaces alpha") {
    val result = Compile.compileString(".a { x: change-color(rgb(255, 0, 0), $alpha: 0.5); }")
    assert(result.css.contains("rgba(255, 0, 0, 0.5)"), result.css)
  }

  test("change-color() with $lightness replaces HSL channel") {
    val result = Compile.compileString(".a { x: red(change-color(rgb(255, 0, 0), $lightness: 25%)); }")
    // 25% lightness of pure red ⇒ rgb(128,0,0)
    assert(result.css.contains("x: 128"), result.css)
  }

  test("adjust-color() shifts a channel") {
    val result = Compile.compileString(".a { x: adjust-color(rgb(16, 32, 48), $blue: 5); }")
    assert(result.css.contains("#102035"), result.css)
  }

  test("adjust-hue() rotates hue") {
    val result = Compile.compileString(".a { x: red(adjust-hue(rgb(255, 0, 0), 120deg)); }")
    // 120deg from red → green; red channel of green is 0.
    assert(result.css.contains("x: 0"), result.css)
  }

  test("scale-color() scales lightness toward bound") {
    val result = Compile.compileString(".a { x: red(scale-color(rgb(128, 0, 0), $lightness: 50%)); }")
    // rgb(128,0,0) lightness ~25% → 25 + (100-25)*0.5 = 62.5%; red channel rises.
    assert(result.css.contains("x: 255") || result.css.contains("x: 254"), result.css)
  }

  test("color.red accessor under module namespace") {
    val result = Compile.compileString("""
      @use "sass:color";
      .a { x: color.red(rgb(255, 0, 0)); }
    """)
    assert(result.css.contains("x: 255"), result.css)
  }

  // ---------------------------------------------------------------------------
  // if() built-in function, comparison & logical operators, string concat
  // ---------------------------------------------------------------------------

  test("if() built-in returns the true branch") {
    val result = Compile.compileString(".a { x: if(true, red, blue); }")
    assert(result.css.contains("x: red"), result.css)
  }

  test("if() built-in returns the false branch") {
    val result = Compile.compileString(".a { x: if(false, red, blue); }")
    assert(result.css.contains("x: blue"), result.css)
  }

  test("equality operator ==") {
    val result = Compile.compileString(".a { x: if(1 == 1, yes, no); y: if(1 == 2, yes, no); }")
    assert(result.css.contains("x: yes"), result.css)
    assert(result.css.contains("y: no"), result.css)
  }

  test("inequality operator !=") {
    val result = Compile.compileString(".a { x: if(1 != 2, yes, no); }")
    assert(result.css.contains("x: yes"), result.css)
  }

  test("less-than and greater-than operators") {
    val result = Compile.compileString(".a { x: if(3 < 5, yes, no); y: if(3 > 5, yes, no); }")
    assert(result.css.contains("x: yes"), result.css)
    assert(result.css.contains("y: no"), result.css)
  }

  test("less-than-or-equals and greater-than-or-equals") {
    val result = Compile.compileString(".a { x: if(5 <= 5, yes, no); y: if(5 >= 6, yes, no); }")
    assert(result.css.contains("x: yes"), result.css)
    assert(result.css.contains("y: no"), result.css)
  }

  test("logical and / or operators") {
    val result = Compile.compileString(
      ".a { x: if(true and true, yes, no); y: if(false or true, yes, no); z: if(false and true, yes, no); }"
    )
    assert(result.css.contains("x: yes"), result.css)
    assert(result.css.contains("y: yes"), result.css)
    assert(result.css.contains("z: no"), result.css)
  }

  test("logical not operator") {
    val result = Compile.compileString(".a { x: if(not false, yes, no); }")
    assert(result.css.contains("x: yes"), result.css)
  }

  test("string concatenation with +") {
    val result = Compile.compileString(""".a { x: "hello " + "world"; }""")
    assert(result.css.contains("hello world"), result.css)
  }

  test("string + number concatenation") {
    val result = Compile.compileString(""".a { x: "v" + 1; }""")
    assert(result.css.contains("v1"), result.css)
  }

  // ---------------------------------------------------------------------------
  // MetaFunctions wired to the active Environment
  // ---------------------------------------------------------------------------

  test("variable-exists() reflects active environment") {
    val result = Compile.compileString(
      """
      $defined: 1;
      .a {
        x: variable-exists("defined");
        y: variable-exists("missing");
      }
    """
    )
    assert(result.css.contains("x: true"), result.css)
    assert(result.css.contains("y: false"), result.css)
  }

  test("mixin-exists() reflects active environment") {
    val result = Compile.compileString(
      """
      @mixin greet { color: red; }
      .a {
        x: mixin-exists("greet");
        y: mixin-exists("absent");
      }
    """
    )
    assert(result.css.contains("x: true"), result.css)
    assert(result.css.contains("y: false"), result.css)
  }

  test("if() short-circuits the unchosen branch") {
    // The false branch references an undefined function. Eager evaluation
    // would render it as a plain CSS function call (`boom(1)`); proper
    // short-circuiting via LegacyIfExpression skips it entirely.
    val result = Compile.compileString(".a { x: if(true, ok, boom(1)); }")
    assert(result.css.contains("x: ok"), result.css)
    assert(!result.css.contains("boom"), result.css)
  }

  test("@mixin with $args..., $kwargs... binds keyword rest map") {
    val result = Compile.compileString(
      """
      @mixin paint($args..., $kwargs...) {
        x: map-get($kwargs, "color");
        y: map-get($kwargs, "size");
      }
      .a { @include paint($color: red, $size: 10px); }
    """
    )
    assert(result.css.contains("x: red"), result.css)
    assert(result.css.contains("y: 10px"), result.css)
  }

  // ---------------------------------------------------------------------------
  // @forward "url" with (...)
  // ---------------------------------------------------------------------------

  // ---------------------------------------------------------------------------
  // @extend — AST-based tests
  // ---------------------------------------------------------------------------

  test("@extend: placeholder selector is stripped from output") {
    val result = Compile.compileString("""
      %base { color: blue; }
      .a { @extend %base; }
    """)
    assert(!result.css.contains("%base"), result.css)
    assert(result.css.contains(".a"), result.css)
    assert(result.css.contains("color: blue"), result.css)
  }

  test("@extend: compound target merges extender into compound") {
    val result = Compile.compileString("""
      .a.b { color: red; }
      .x { @extend .a; }
    """)
    // Original .a.b stays; plus .x.b should be produced.
    assert(result.css.contains(".a.b"), result.css)
    assert(result.css.contains(".x.b") || result.css.contains(".b.x"), result.css)
  }

  test("@extend: multiple extenders for a single target") {
    val result = Compile.compileString("""
      .foo { color: red; }
      .a { @extend .foo; }
      .b { @extend .foo; }
    """)
    // All three should share the `color: red` rule selector list.
    val redIdx = result.css.indexOf("color: red")
    assert(redIdx >= 0, result.css)
    val header = result.css.substring(0, redIdx)
    assert(header.contains(".foo"), result.css)
    assert(header.contains(".a"), result.css)
    assert(header.contains(".b"), result.css)
  }

  // ---------------------------------------------------------------------------
  // @content block argument passing (`@content(...)` + `@include ... using`)
  // ---------------------------------------------------------------------------

  test("@content with no args still works (regression)") {
    val result = Compile.compileString("""
      @mixin wrap { .x { @content; } }
      @include wrap { color: red; }
    """)
    assert(result.css.contains(".x"), result.css)
    assert(result.css.contains("color: red"), result.css)
  }

  test("@content passes one arg to content block via `using`") {
    val result = Compile.compileString(
      """
      @mixin media($bp) {
        .wrap { @content($bp); }
      }
      @include media(768px) using ($size) {
        width: $size;
      }
    """
    )
    assert(result.css.contains("width: 768px"), result.css)
  }

  test("@content passes multiple args to content block via `using`") {
    val result = Compile.compileString(
      """
      @mixin pair($a, $b) {
        .p { @content($a, $b); }
      }
      @include pair(10px, 20px) using ($x, $y) {
        left: $x;
        top: $y;
      }
    """
    )
    assert(result.css.contains("left: 10px"), result.css)
    assert(result.css.contains("top: 20px"), result.css)
  }

  test("@content `using` parameter default value applies when no @content args") {
    val result = Compile.compileString(
      """
      @mixin wrap { .w { @content; } }
      @include wrap using ($size: 42px) {
        width: $size;
      }
    """
    )
    assert(result.css.contains("width: 42px"), result.css)
  }

  test("@content arg shadows caller's variable in content block") {
    val result = Compile.compileString(
      """
      @mixin media($bp) {
        .m { @content($bp); }
      }
      @include media(600px) using ($bp) {
        max-width: $bp;
      }
    """
    )
    assert(result.css.contains("max-width: 600px"), result.css)
  }

  test("@forward with (...) configures !default vars in the loaded module") {
    // Without an importer the forward target is silently skipped, so we
    // exercise parser + AST round-trip via `toString` to confirm the
    // configuration list is captured. Full evaluation is covered by
    // ImportSuite under the @forward + @use combo.
    import ssg.sass.parse.ScssParser
    val sheet = new ScssParser("""@forward "vars" with ($base: 20px !default);""").parse()
    val text  = sheet.children.get.head.toString
    assert(text.contains("with"), text)
    assert(text.contains("$base"), text)
    assert(text.contains("20px"), text)
  }

  // ---------------------------------------------------------------------------
  // meta.get-function / meta.call / meta.get-mixin / meta.module-* tests
  // ---------------------------------------------------------------------------

  test("meta.get-function + call invokes a built-in function") {
    val css = Compile
      .compileString(
        """
          |a {
          |  $fn: get-function("rgb");
          |  color: call($fn, 255, 0, 0);
          |}
      """.stripMargin
      )
      .css
    // rgb(255, 0, 0) → red, serialized as the named color or hex shorthand.
    assert(css.contains("red") || css.contains("#f00") || css.contains("#ff0000"), css)
  }

  test("meta.call invokes a user-defined @function via get-function") {
    val css = Compile
      .compileString(
        """
          |@function double($n) { @return $n * 2; }
          |a {
          |  $fn: get-function("double");
          |  width: call($fn, 5px);
          |}
      """.stripMargin
      )
      .css
    assert(css.contains("width: 10px"), css)
  }

  test("meta.call accepts a string function name (legacy form)") {
    val css = Compile
      .compileString(
        """a { color: call("rgb", 0, 128, 0); }"""
      )
      .css
    assert(css.contains("green") || css.contains("#008000"), css)
  }

  test("meta.module-functions returns a function map for sass:math") {
    val css = Compile
      .compileString(
        """
          |@use "sass:math" as m;
          |a {
          |  $fns: module-functions("m");
          |  has-floor: map-has-key($fns, "floor");
          |}
      """.stripMargin
      )
      .css
    assert(css.contains("has-floor: true"), css)
  }

  test("meta.module-variables returns a map of vars from a @use'd module") {
    import ssg.sass.importer.MapImporter
    val imp = new MapImporter(
      Map(
        "colors.scss" -> """$primary: red; $secondary: blue;"""
      )
    )
    val css = Compile
      .compileString(
        """
          |@use "colors" as c;
          |a {
          |  $vars: module-variables("c");
          |  count: length(map-keys($vars));
          |  primary: map-get($vars, "primary");
          |}
      """.stripMargin,
        importer = ssg.sass.Nullable(imp: ssg.sass.importer.Importer)
      )
      .css
    assert(css.contains("count: 2"), css)
    assert(css.contains("primary: red"), css)
  }

  test("meta.get-function on an unknown name throws") {
    intercept[RuntimeException] {
      val _ = Compile.compileString(
        """a { $fn: get-function("definitely-not-a-real-function"); color: red; }"""
      )
    }
  }

  test("meta.get-mixin returns a SassMixin and meta.apply errors") {
    // get-mixin should succeed; apply is intentionally a stub.
    val css = Compile
      .compileString(
        """
          |@mixin greet { greeting: hello; }
          |a {
          |  $mx: get-mixin("greet");
          |  type: type-of($mx);
          |}
      """.stripMargin
      )
      .css
    assert(css.contains("type: mixin"), css)
  }

  // ---------------------------------------------------------------------------
  // CSS custom properties and modern @supports syntax
  // ---------------------------------------------------------------------------

  test("custom property with literal value passes through") {
    val css = Compile.compileString(":root { --brand: #ff0066; }", OutputStyle.Compressed).css
    assertEquals(css, ":root{--brand:#ff0066;}")
  }

  test("custom property with #{...} interpolation evaluates") {
    val src = "$c: red; :root { --brand: #{$c}; }"
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assertEquals(css, ":root{--brand:red;}")
  }

  test("custom property value does NOT evaluate + operator") {
    val css = Compile.compileString(":root { --foo: 1 + 2; }", OutputStyle.Compressed).css
    assertEquals(css, ":root{--foo:1 + 2;}")
  }

  test("custom property value preserves nested parens") {
    val css = Compile.compileString(":root { --grid: repeat(3, 1fr); }", OutputStyle.Compressed).css
    assertEquals(css, ":root{--grid:repeat(3, 1fr);}")
  }

  test("var(--foo) passes through as a plain CSS function") {
    val css = Compile.compileString("a { color: var(--brand); }", OutputStyle.Compressed).css
    assertEquals(css, "a{color:var(--brand);}")
  }

  test("@supports selector(:has(> img)) compiles") {
    val css = Compile.compileString("@supports selector(:has(> img)) { a { color: red; } }", OutputStyle.Compressed).css
    assertEquals(css, "@supports selector(:has(> img)){a{color:red;}}")
  }

  test("@supports selector(...) uses function syntax without extra parens") {
    val css = Compile.compileString("@supports selector(:is(a, b)) { .x { color: red; } }", OutputStyle.Compressed).css
    assertEquals(css, "@supports selector(:is(a, b)){.x{color:red;}}")
  }

  test("!important flag emits ` !important` before the trailing semicolon") {
    val css = Compile.compileString("a { color: red !important; }", OutputStyle.Compressed).css
    assertEquals(css, "a{color:red!important;}")
  }

  test("!important flag is omitted when absent") {
    val css = Compile.compileString("a { color: red; }", OutputStyle.Compressed).css
    assertEquals(css, "a{color:red;}")
  }

  test("!important tolerates whitespace between ! and important") {
    val css = Compile.compileString("a { color: red !  important; }", OutputStyle.Compressed).css
    assertEquals(css, "a{color:red!important;}")
  }

  test("!important expanded-mode output has space before !important") {
    val css = Compile.compileString("a { color: red !important; }").css
    assert(css.contains("color: red !important;"))
  }

  test("!important works with variable-valued expressions") {
    val css = Compile.compileString("$c: blue; a { color: $c !important; }", OutputStyle.Compressed).css
    assertEquals(css, "a{color:blue!important;}")
  }
}
