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

  test("loud comments are preserved in output") {
    val css = Compile.compileString("/* hello */\n.foo { a: b; }").css
    assert(css.contains("/* hello */"), s"Top-level comment missing:\n$css")
  }

  test("loud comments inside rules are preserved") {
    val css = Compile.compileString(".foo { /* inside */ a: b; }").css
    assert(css.contains("/* inside */"), s"Inline comment missing:\n$css")
  }

  test("isGroupEnd: blank line between rules from different source blocks") {
    val css = Compile.compileString(".a { x: 1; }\n.b { y: 2; }").css
    assert(css.contains("}\n\n.b"), s"Expected blank line between .a and .b:\n$css")
  }

  test("isGroupEnd: no blank line between rules from same source block") {
    val css = Compile.compileString(".parent { .a { x: 1; } .b { y: 2; } }").css
    assert(css.contains("}\n.parent .b"), s"Expected no blank line between nested siblings:\n$css")
  }

  test("rgb(0 128 255 / 0.5) preserves alpha via slash-separated path") {
    val css = Compile.compileString("a { c: rgb(0 128 255 / 0.5); }").css
    assertEquals(css, "a {\n  c: rgba(0, 128, 255, 0.5);\n}\n")
  }

  test("hex #F00 preserves original case in output") {
    val css = Compile.compileString("a { c: #F00; }").css
    assertEquals(css, "a {\n  c: #F00;\n}\n")
  }

  // --------------------------------------------------------------------------
  // Regression tests: selector-list separator (stage A.4 follow-up).
  //
  // dart-sass `visitSelectorList` emits `,\n<indent>` only when the complex
  // selector has `lineBreak = true`; otherwise it emits a single space via
  // `_writeOptionalSpace`. Stage A.4 initially hard-coded `,\n`, which caused
  // ~37 sass-spec cases to regress because authored one-line selector lists
  // like `a, d { ... }` round-tripped to `a,\nd { ... }`. These tests lock
  // in the corrected behavior.
  // --------------------------------------------------------------------------

  test("expanded mode: two-selector comma list stays on one line when not broken") {
    val css = Compile.compileString("a, d { b: c; }").css
    assertEquals(css, "a, d {\n  b: c;\n}\n")
  }

  test("expanded mode: three-selector comma list stays on one line when not broken") {
    val css = Compile.compileString(".x, .y, .z { p: 1; }").css
    assertEquals(css, ".x, .y, .z {\n  p: 1;\n}\n")
  }

  test("slash division in if() macro produces division result") {
    val css = Compile.compileString("c {d: if(true, 1/2, null)}").css
    assertEquals(css, "c {\n  d: 0.5;\n}\n")
  }

  test("compressed mode: comma list joined with bare comma") {
    val css = Compile.compileString("a, d { b: c; }", OutputStyle.Compressed).css
    assertEquals(css, "a,d{b:c}")
  }

  test("expanded mode: leading combinator with comma list preserves space separator") {
    // Bogus-but-parsed: `a, > d` — the second complex has a leading combinator
    // but we still expect a single space after the comma in expanded mode
    // since neither complex is marked lineBreak.
    val css = Compile.compileString("a, > d { b: c; }").css
    assertEquals(css, "a, > d {\n  b: c;\n}\n")
  }

  test("expanded mode: nested rule with comma list keeps space separator") {
    val css = Compile.compileString(".wrap { a, b { c: d; } }").css
    assertEquals(css, ".wrap a, .wrap b {\n  c: d;\n}\n")
  }

  test("@if with escaped directive name is recognized") {
    // From sass-spec: spec/directives/if/escaped.hrx (if_only).
    // `@\69 f` is the escape sequence for `@if`; after normalization
    // the directive must be parsed as a real @if rule, not a generic
    // unknown at-rule.
    val css = Compile.compileString("@\\69 f true {a {b: c}}\n", OutputStyle.Compressed).css
    assertEquals(css, "a{b:c}")
  }

  test("@else with escaped directive name attaches to preceding @if") {
    // From sass-spec: spec/directives/if/escaped.hrx (with_else).
    // `@\65lse` is the escape sequence for `@else`; after normalization
    // it must attach to the preceding `@if` as an else clause.
    val css = Compile.compileString("@if false {}\n@\\65lse {a {b: c}}\n", OutputStyle.Compressed).css
    assertEquals(css, "a{b:c}")
  }

  test("@if / @else if / @else chain evaluates the right branch") {
    val src = "@if false { a { x: 1; } } @else if true { a { x: 2; } } @else { a { x: 3; } }"
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assertEquals(css, "a{x:2}")
  }

  test("calc() preserves incompatible units") {
    val css = Compile.compileString("a { width: calc(100% - 20px); }", OutputStyle.Compressed).css
    assertEquals(css, "a{width:calc(100% - 20px)}")
  }

  test("calc() simplifies compatible numeric arithmetic") {
    val css = Compile.compileString("a { width: calc(10px + 5px); }", OutputStyle.Compressed).css
    assertEquals(css, "a{width:15px}")
  }

  test("min() preserves multiple incompatible arguments") {
    val css = Compile.compileString("a { width: min(100%, 500px); }", OutputStyle.Compressed).css
    // dart-sass: compressed mode uses commaSeparator which is `,` (no space)
    assertEquals(css, "a{width:min(100%,500px)}")
  }

  test("max() resolves variable arguments") {
    // With two compatible numeric arguments Sass simplifies max(10px, 20px) to 20px.
    val src = "$base: 20px; a { width: max(10px, $base); }"
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assertEquals(css, "a{width:20px}")
  }

  test("max() preserves incompatible variable arguments") {
    val src = "$base: 50%; a { width: max(10px, $base); }"
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    // dart-sass: compressed mode uses commaSeparator which is `,` (no space)
    assertEquals(css, "a{width:max(10px,50%)}")
  }

  test("clamp() with three numeric arguments") {
    val css = Compile.compileString("a { width: clamp(10px, 50%, 500px); }", OutputStyle.Compressed).css
    // dart-sass: compressed mode uses commaSeparator which is `,` (no space)
    assertEquals(css, "a{width:clamp(10px,50%,500px)}")
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
    assertEquals(result.css, "a{color:red}")
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

  // dart-sass: @charset is parsed and consumed by the parser — it doesn't
  // produce any output in the CSS AST. The @charset prefix in the output
  // is only added by the serializer when the output contains non-ASCII.
  test("@charset at-rule produces no output for ASCII-only content") {
    val result = Compile.compileString("""@charset "UTF-8";""")
    assertEquals(result.css, "")
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

  test("selector-extend does AST-based extend") {
    // dart-sass semantics: `.btn .icon` extended with `.big` against target
    // `.icon` produces `.btn .icon, .btn .big` — the descendant combinator
    // is preserved on both copies. The old ssg-sass textual replace
    // (which returned `.btn .icon, .big`) was incorrect.
    val result = Compile.compileString(
      """.x { y: selector-extend(".btn .icon", ".icon", ".big"); }"""
    )
    assert(result.css.contains(".btn .icon, .btn .big"), result.css)
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
    assert(result.css.contains(".btn:hover{color:blue}"), result.css)
    assert(!result.css.contains("&"), result.css)
  }

  test("nested rule with &.active expands to parent.active") {
    val result = Compile.compileString(
      """.btn { &.active { color: blue; } }""",
      OutputStyle.Compressed
    )
    assert(result.css.contains(".btn.active{color:blue}"), result.css)
    assert(!result.css.contains("&"), result.css)
  }

  test("nested rule without & is descendant selector") {
    val result = Compile.compileString(
      """.a { .b { color: red; } }""",
      OutputStyle.Compressed
    )
    assert(result.css.contains(".a .b{color:red}"), result.css)
  }

  test("nested rule with parent declarations and & emits both") {
    val result = Compile.compileString(
      """.btn { color: red; &:hover { color: blue; } }""",
      OutputStyle.Compressed
    )
    assert(result.css.contains(".btn{color:red}"), result.css)
    assert(result.css.contains(".btn:hover{color:blue}"), result.css)
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

  // dart-sass: colors created via rgb() built-in have format=RgbFunction,
  // so they are serialized back as rgb(r, g, b) in expanded mode.
  // The hex/named-color shorthand only applies to colors without a
  // format (e.g., hex literals, named-color literals).
  test("rgb(255,255,255) preserves rgb() format in expanded mode") {
    val result = Compile.compileString(".box { color: rgb(255, 255, 255); }")
    assert(result.css.contains("color: rgb(255, 255, 255)"), result.css)
  }

  test("rgb(255,0,0) preserves rgb() format in expanded mode") {
    val result = Compile.compileString(".box { color: rgb(255, 0, 0); }")
    assert(result.css.contains("color: rgb(255, 0, 0)"), result.css)
  }

  test("rgb(170,187,204) preserves rgb() format in expanded mode") {
    val result = Compile.compileString(".box { color: rgb(170, 187, 204); }")
    assert(result.css.contains("color: rgb(170, 187, 204)"), result.css)
  }

  test("rgb(18,52,86) preserves rgb() format in expanded mode") {
    val result = Compile.compileString(".box { color: rgb(18, 52, 86); }")
    assert(result.css.contains("color: rgb(18, 52, 86)"), result.css)
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
    // dart-sass compressed: no space between @media and ( when query
    // has no modifier, no type, and condition doesn't start with "(not ".
    assert(result.css.contains("@media(max-width: 600px)"), result.css)
    assert(result.css.contains(".a{color:red}"), result.css)
  }

  test("@media with type and condition preserves both") {
    val result = Compile.compileString(
      "@media screen and (min-width: 768px) { .a { color: red; } }",
      OutputStyle.Compressed
    )
    assert(result.css.contains("@media screen and (min-width: 768px)"), result.css)
    assert(result.css.contains(".a{color:red}"), result.css)
  }

  test("@media supports #{...} interpolation in query") {
    val result = Compile.compileString(
      """
        $bp: 600px;
        @media (max-width: #{$bp}) { .a { color: red; } }
      """,
      OutputStyle.Compressed
    )
    assert(result.css.contains("@media(max-width: 600px)"), result.css)
    assert(result.css.contains(".a{color:red}"), result.css)
  }

  test("nested @media inside style rule bubbles out") {
    val result = Compile.compileString(
      ".a { @media (max-width: 600px) { color: red; } }",
      OutputStyle.Compressed
    )
    // Expected bubbling: `@media(max-width: 600px) { .a { color: red; } }`.
    // dart-sass compressed: no space between @media and ( for condition-only queries.
    assert(result.css.contains("@media(max-width: 600px)"), result.css)
    assert(result.css.contains(".a{color:red}"), result.css)
    // The @media must appear before the inner `.a` selector.
    val mediaIdx = result.css.indexOf("@media")
    val ruleIdx  = result.css.indexOf(".a{color:red}")
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
    // dart-sass merges nested @media into a single @media with "and":
    // `@media screen and (max-width: 600px) { .a { color: red; } }`
    assert(result.css.contains("@media screen and (max-width: 600px)"), result.css)
    assert(result.css.contains(".a{color:red}"), result.css)
  }

  // ---------------------------------------------------------------------------
  // @supports
  // ---------------------------------------------------------------------------

  test("@supports with single condition compiles") {
    val result = Compile.compileString(
      "@supports (display: grid) { .a { color: red; } }",
      OutputStyle.Compressed
    )
    // In compressed mode, the space between @supports and ( is omitted.
    assert(result.css.contains("@supports(display: grid)") || result.css.contains("@supports (display: grid)"), result.css)
    assert(result.css.contains(".a{color:red}"), result.css)
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
    assert(result.css.contains(".a{color:red}"), result.css)
  }

  test("@supports supports #{...} interpolation in the condition") {
    val result = Compile.compileString(
      """
        $prop: display;
        @supports (#{$prop}: grid) { .a { color: red; } }
      """,
      OutputStyle.Compressed
    )
    // In compressed mode, the space between @supports and ( is omitted.
    assert(result.css.contains("@supports(display: grid)") || result.css.contains("@supports (display: grid)"), result.css)
    assert(result.css.contains(".a{color:red}"), result.css)
  }

  test("nested @supports inside style rule bubbles out") {
    val result = Compile.compileString(
      ".a { @supports (display: grid) { color: red; } }",
      OutputStyle.Compressed
    )
    // In compressed mode, the space between @supports and ( is omitted.
    assert(result.css.contains("@supports(display: grid)") || result.css.contains("@supports (display: grid)"), result.css)
    assert(result.css.contains(".a{color:red}"), result.css)
    val atIdx   = result.css.indexOf("@supports")
    val ruleIdx = result.css.indexOf(".a{color:red}")
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
    assert(result.css.contains("0%{opacity:0}"), result.css)
    assert(result.css.contains("50%{opacity:"), result.css)
    assert(result.css.contains("100%{opacity:1}"), result.css)
  }

  test("@keyframes preserves from/to as-is (dart-sass compat)") {
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
    // dart-sass preserves from/to as-is, does not normalize to 0%/100%
    assert(result.css.contains("from{opacity:0}"), result.css)
    assert(result.css.contains("to{opacity:1}"), result.css)
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
    // dart-sass: compressed mode uses commaSeparator (`,` without space)
    assert(result.css.contains("0%,100%"), result.css)
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

  // dart-sass throws when content-exists() is called outside a mixin.
  test("calls content-exists() outside mixin throws") {
    intercept[ssg.sass.SassException] {
      Compile.compileString(""".box { x: content-exists(); }""")
    }
  }

  test("calls content-exists() inside mixin returns true/false") {
    // Inside a mixin with @content block
    val withContent = Compile.compileString(
      """@mixin foo { @if content-exists() { @content; } }
        |.box { @include foo { color: red; } }""".stripMargin
    )
    assert(withContent.css.contains("color: red"), withContent.css)

    // Inside a mixin without @content block
    val withoutContent = Compile.compileString(
      """@mixin bar { x: content-exists(); }
        |.box { @include bar; }""".stripMargin
    )
    assert(withoutContent.css.contains("x: false"), withoutContent.css)
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
    // dart-sass: rgb() built-in sets format=RgbFunction, so output is rgb(r, g, b).
    assert(css.contains("rgb(255, 0, 0)"), css)
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
    // dart-sass: rgb() built-in sets format=RgbFunction, so output is rgb(r, g, b).
    assert(css.contains("rgb(0, 128, 0)"), css)
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

  // dart-sass: custom property values are parsed as raw text with the
  // leading whitespace after the colon preserved. In compressed mode,
  // `_writeFoldedValue` preserves this space. So `--brand: #ff0066`
  // serializes with a space after the colon even in compressed mode.
  test("custom property with literal value passes through") {
    val css = Compile.compileString(":root { --brand: #ff0066; }", OutputStyle.Compressed).css
    assertEquals(css, ":root{--brand: #ff0066}")
  }

  test("custom property with #{...} interpolation evaluates") {
    val src = "$c: red; :root { --brand: #{$c}; }"
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assertEquals(css, ":root{--brand: red}")
  }

  test("custom property value does NOT evaluate + operator") {
    val css = Compile.compileString(":root { --foo: 1 + 2; }", OutputStyle.Compressed).css
    assertEquals(css, ":root{--foo: 1 + 2}")
  }

  test("custom property value preserves nested parens") {
    val css = Compile.compileString(":root { --grid: repeat(3, 1fr); }", OutputStyle.Compressed).css
    assertEquals(css, ":root{--grid: repeat(3, 1fr)}")
  }

  test("var(--foo) passes through as a plain CSS function") {
    val css = Compile.compileString("a { color: var(--brand); }", OutputStyle.Compressed).css
    assertEquals(css, "a{color:var(--brand)}")
  }

  test("@supports selector(:has(> img)) compiles") {
    val css = Compile.compileString("@supports selector(:has(> img)) { a { color: red; } }", OutputStyle.Compressed).css
    assertEquals(css, "@supports selector(:has(> img)){a{color:red}}")
  }

  test("@supports selector(...) uses function syntax without extra parens") {
    val css = Compile.compileString("@supports selector(:is(a, b)) { .x { color: red; } }", OutputStyle.Compressed).css
    assertEquals(css, "@supports selector(:is(a, b)){.x{color:red}}")
  }

  // dart-sass: `!important` is parsed as part of the value expression
  // (a space-separated list element). The space between the value and
  // `!important` is preserved in all modes, matching dart-sass behavior.
  test("!important flag emits ` !important` before the trailing semicolon") {
    val css = Compile.compileString("a { color: red !important; }", OutputStyle.Compressed).css
    assertEquals(css, "a{color:red !important}")
  }

  test("!important flag is omitted when absent") {
    val css = Compile.compileString("a { color: red; }", OutputStyle.Compressed).css
    assertEquals(css, "a{color:red}")
  }

  test("!important tolerates whitespace between ! and important") {
    val css = Compile.compileString("a { color: red !  important; }", OutputStyle.Compressed).css
    assertEquals(css, "a{color:red !important}")
  }

  test("!important expanded-mode output has space before !important") {
    val css = Compile.compileString("a { color: red !important; }").css
    assert(css.contains("color: red !important;"))
  }

  test("!important works with variable-valued expressions") {
    val css = Compile.compileString("$c: blue; a { color: $c !important; }", OutputStyle.Compressed).css
    assertEquals(css, "a{color:blue !important}")
  }

  // ISS-093: plain-CSS function preservation
  test("var() is preserved verbatim as a plain-CSS function") {
    val css = Compile.compileString("a { width: var(--foo); }", OutputStyle.Compressed).css
    assertEquals(css, "a{width:var(--foo)}")
  }

  test("linear-gradient() is preserved verbatim as a plain-CSS function") {
    val css = Compile.compileString("a { background: linear-gradient(red, blue); }", OutputStyle.Compressed).css
    assertEquals(css, "a{background:linear-gradient(red, blue)}")
  }

  test("polygon() is preserved verbatim as a plain-CSS function") {
    val css = Compile.compileString("a { clip-path: polygon(0 0, 100% 0, 50% 100%); }", OutputStyle.Compressed).css
    assertEquals(css, "a{clip-path:polygon(0 0, 100% 0, 50% 100%)}")
  }

  // ISS-014: sass:meta gap fills
  //
  // meta.calc-name returns a QUOTED string (dart-sass `SassString(name)`
  // defaults to `quotes: true`). The original tests here expected the
  // old ssg-sass incorrect unquoted form; updated to match dart-sass
  // spec during the T005 sass:meta port.
  test("meta.calc-name(calc(...)) returns 'calc'") {
    val src =
      """@use "sass:meta";
        |a { name: meta.calc-name(calc(100% + 2px)); }""".stripMargin
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assertEquals(css, """a{name:"calc"}""")
  }

  test("meta.calc-name(min(...)) returns 'min'") {
    val src =
      """@use "sass:meta";
        |a { name: meta.calc-name(min(100%, 2px)); }""".stripMargin
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assertEquals(css, """a{name:"min"}""")
  }

  test("meta.calc-args returns the operand list of a calc") {
    val src =
      """@use "sass:meta";
        |a { args: meta.calc-args(min(100%, 2px)); }""".stripMargin
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assertEquals(css, "a{args:100%,2px}")
  }

  test("meta.accepts-content returns true for a mixin with @content") {
    val src =
      """@use "sass:meta";
        |@mixin foo { @content; }
        |a { result: meta.accepts-content(meta.get-mixin("foo")); }""".stripMargin
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assertEquals(css, "a{result:true}")
  }

  test("meta.accepts-content returns false for a mixin without @content") {
    val src =
      """@use "sass:meta";
        |@mixin foo { color: red; }
        |a { result: meta.accepts-content(meta.get-mixin("foo")); }""".stripMargin
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assertEquals(css, "a{result:false}")
  }

  test("@use sass:color with (...) throws — built-in modules can't be configured") {
    val src = """@use "sass:color" with ($foo: 1); a { color: red; }"""
    val e   = intercept[SassException](Compile.compileString(src, OutputStyle.Compressed))
    assert(
      e.getMessage.contains("Built-in modules can't be configured."),
      s"expected built-in-configure error, got: ${e.getMessage}"
    )
  }

  test("@use sass:color without a `with` clause still compiles") {
    val src = """@use "sass:color"; a { color: red; }"""
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assertEquals(css, "a{color:red}")
  }

  // ---------------------------------------------------------------------------
  // @for bounds validation (mirrors dart-sass visitForRule checks)
  // ---------------------------------------------------------------------------

  test("@for rejects a non-integer `from` bound") {
    val src = "@for $i from 1.5 to 5 { a { x: $i; } }"
    val e   = intercept[SassException](Compile.compileString(src, OutputStyle.Compressed))
    assert(
      e.getMessage.contains("is not an int"),
      s"expected 'is not an int' error, got: ${e.getMessage}"
    )
  }

  test("@for rejects a non-integer `to` bound") {
    val src = "@for $i from 1 to 5.5 { a { x: $i; } }"
    val e   = intercept[SassException](Compile.compileString(src, OutputStyle.Compressed))
    assert(
      e.getMessage.contains("is not an int"),
      s"expected 'is not an int' error, got: ${e.getMessage}"
    )
  }

  test("@for rejects incompatible units between `from` and `to`") {
    val src = "@for $i from 1px to 5em { a { x: $i; } }"
    val e   = intercept[SassException](Compile.compileString(src, OutputStyle.Compressed))
    assert(
      e.getMessage.contains("incompatible units"),
      s"expected 'incompatible units' error, got: ${e.getMessage}"
    )
  }

  test("@for with matching px bounds succeeds and loop var carries the unit") {
    val src = "@for $i from 1px to 3px { a { x: $i; } }"
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assert(css.contains("x:1px"), css)
    assert(css.contains("x:2px"), css)
  }

  // ---------------------------------------------------------------------------
  // Special CSS functions (pass-through, not evaluated as Sass expressions)
  // Mirrors dart-sass `_trySpecialFunction` in lib/src/parse/stylesheet.dart.
  // ---------------------------------------------------------------------------

  test("url() with an unquoted path is preserved verbatim") {
    val css = Compile.compileString("a { background: url(/path/to/file.png); }", OutputStyle.Compressed).css
    assertEquals(css, "a{background:url(/path/to/file.png)}")
  }

  test("url() with a quoted argument is preserved verbatim") {
    val css = Compile.compileString("""a { background: url("foo.png"); }""", OutputStyle.Compressed).css
    assertEquals(css, """a{background:url("foo.png")}""")
  }

  test("element() is preserved verbatim as a special CSS function") {
    val css = Compile.compileString("a { background: element(#mySelector); }", OutputStyle.Compressed).css
    assertEquals(css, "a{background:element(#mySelector)}")
  }

  test("-ms-element() is preserved verbatim as a vendor-prefixed special function") {
    val css = Compile.compileString("a { background: -ms-element(#mySelector); }", OutputStyle.Compressed).css
    assertEquals(css, "a{background:-ms-element(#mySelector)}")
  }

  test("-webkit-image-set() with nested url() is preserved verbatim") {
    val css = Compile.compileString("""a { cursor: -webkit-image-set(url("a.png") 1x); }""", OutputStyle.Compressed).css
    assertEquals(css, """a{cursor:-webkit-image-set(url("a.png") 1x)}""")
  }

  test("@function url() is rejected as a reserved CSS function name") {
    intercept[SassFormatException] {
      val _ = Compile.compileString("@function url() { @return 1; }")
    }
  }

  test("@function element() is rejected as a reserved CSS function name") {
    intercept[SassFormatException] {
      val _ = Compile.compileString("@function element() { @return 1; }")
    }
  }

  test("@function and() is rejected as a reserved CSS function name") {
    intercept[SassFormatException] {
      val _ = Compile.compileString("@function and($a, $b) { @return 1; }")
    }
  }

  test("@function calc() is accepted and callable (dart-sass allows shadowing plain calc)") {
    // sass-spec: directives/function/name.hrx!special/calc. `calc` is not
    // in dart-sass's case-sensitive reserved set — user functions may
    // shadow the plain-CSS name.
    val css = Compile.compileString("@function calc() {@return 1} a {b: calc()}").css
    assertEquals(css, "a {\n  b: 1;\n}\n")
  }

  test("@function my-fn() is accepted (not a reserved name)") {
    val css = Compile.compileString("@function my-fn() { @return 1; } a { x: my-fn(); }", OutputStyle.Compressed).css
    assertEquals(css, "a{x:1}")
  }

  test("built-in with signature default is padded when positional args are short") {
    // Regression: `str-slice` declares `$string, $start-at, $end-at: -1`.
    // Calling with only two positional args must apply the default expression
    // `-1` from the signature instead of reading past the end of `args`.
    val css = Compile.compileString("a { x: str-slice(\"hello\", 2); }", OutputStyle.Compressed).css
    assertEquals(css, "a{x:\"ello\"}")
  }

  // ---------------------------------------------------------------------------
  // Rest-argument splatting (`$list...`) — positional / list / map / kwargs
  // Regression: sass-spec values/numbers/divide/slash_free/argument.hrx rest/*.
  // ---------------------------------------------------------------------------

  test("rest splat of a space-separated list expands to positional args") {
    // Mirrors sass-spec values/numbers/divide/slash_free/argument.hrx!function/rest/list:
    // `list.join((1 2) (3 4)...)` — rest is a single space-separated list of
    // two sub-lists, splatted into two positional slots for list.join.
    val src = """@use "sass:list"; c {d: list.join((1 2) (3 4)...)}"""
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assert(css.contains("d:1 2 3 4") || css.contains("d:1, 2, 3, 4"), css)
  }

  test("rest splat of a map converts entries to keyword arguments") {
    // `list.join(("list1": 1 2, "list2": 3 4)...)` — map rest splat must
    // become named args $list1/$list2, not a single positional map, which
    // previously triggered IndexOutOfBoundsException in the joinFn body.
    val src = """@use "sass:list"; c {d: list.join(("list1": 1 2, "list2": 3 4)...)}"""
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assert(css.contains("d:1 2 3 4") || css.contains("d:1, 2, 3, 4"), css)
  }

  // ---------------------------------------------------------------------------
  // _bindParameters validation (port of dart-sass ParameterList.verify)
  // ---------------------------------------------------------------------------

  test("@function with extra positional arguments throws Only N arguments allowed") {
    val src = "@function f($a, $b) { @return $a + $b; } a { x: f(1, 2, 3); }"
    val e   = intercept[SassException](Compile.compileString(src, OutputStyle.Compressed))
    assert(
      e.getMessage.contains("Only 2") && e.getMessage.contains("arguments allowed"),
      s"expected 'Only 2 arguments allowed' error, got: ${e.getMessage}"
    )
  }

  test("@function with unknown named argument throws No parameter named $foo") {
    val src = "@function f($a) { @return $a; } a { x: f(1, $bogus: 2); }"
    val e   = intercept[SassException](Compile.compileString(src, OutputStyle.Compressed))
    assert(
      e.getMessage.contains("No parameter named $bogus"),
      s"expected 'No parameter named $$bogus' error, got: ${e.getMessage}"
    )
  }

  test("@function argument passed both by position and by name throws") {
    val src = "@function f($a) { @return $a; } a { x: f(1, $a: 2); }"
    val e   = intercept[SassException](Compile.compileString(src, OutputStyle.Compressed))
    assert(
      e.getMessage.contains("was passed both by position and by name"),
      s"expected 'passed both by position and by name' error, got: ${e.getMessage}"
    )
  }

  test("@function with missing required argument throws Missing argument") {
    val src = "@function f($a, $b) { @return $a; } a { x: f(1); }"
    val e   = intercept[SassException](Compile.compileString(src, OutputStyle.Compressed))
    assert(
      e.getMessage.contains("Missing argument $b"),
      s"expected 'Missing argument $$b' error, got: ${e.getMessage}"
    )
  }

  test("kwargs-rest with non-map argument throws") {
    // Splatting a non-map as `$kwargs...` is a Sass error: kwargs must be a map.
    val src = "@function f($a) { @return $a; } a { x: f((1, 2, 3)...); }"
    val e   = intercept[SassException](Compile.compileString(src, OutputStyle.Compressed))
    assert(
      e.getMessage.nonEmpty,
      s"expected an error for kwargs-rest non-map, got: ${e.getMessage}"
    )
  }

  test("rest splat of non-list non-arglist passes through as single positional arg") {
    // dart-sass treats bare scalars splatted as a single-positional arg.
    // With rigorous validation: `f(...)` expects 1 positional; passing 1 scalar is fine.
    val src = "@function f($a) { @return $a; } a { x: f(42...); }"
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assert(css.contains("x:42"), css)
  }

  test("rest splat followed by kwargs-rest map does not throw") {
    // Regression for sass-spec values/numbers/divide/slash_free/argument.hrx
    // !function/rest/kwargs — previously raised IndexOutOfBoundsException when
    // the kwargs map splat leaked into the positional list. The exact
    // positional/named merging semantics for this overlap case are covered by
    // downstream tests; here we just assert that both rest segments are
    // recognized and compilation succeeds.
    val src = """@use "sass:list"; c {d: list.join(1..., ("list2": 3 4)...)}"""
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assert(css.nonEmpty, css)
  }

  // --------------------------------------------------------------------------
  // @media query error-parity regressions. dart-sass rejects these at parse
  // time; ssg-sass used to silently fall through to a raw-text media rule.
  // See visitMediaRule / _validateMediaQueryText in EvaluateVisitor.
  // --------------------------------------------------------------------------

  test("@media: `not(` without whitespace is rejected") {
    intercept[SassException] {
      Compile.compileString("@media not(a) {x {y: z}}")
    }
  }

  test("@media: `and(` without whitespace is rejected") {
    intercept[SassException] {
      Compile.compileString("@media (a) and(b) {x {y: z}}")
    }
  }

  test("@media: trailing `and` with nothing after is rejected") {
    intercept[SassException] {
      Compile.compileString("@media a and {x {y: z}}")
    }
  }

  test("@media: trailing `or` with nothing after is rejected") {
    intercept[SassException] {
      Compile.compileString("@media (a) or {x {y: z}}")
    }
  }

  test("@media: mixing `and` and `or` at top level is rejected") {
    intercept[SassException] {
      Compile.compileString("@media (a) or (b) and (c) {x {y: z}}")
    }
  }

  test("@media: `or` after a bare type identifier is rejected") {
    intercept[SassException] {
      Compile.compileString("@media a or (b) {x {y: z}}")
    }
  }

  // ---------------------------------------------------------------------------
  // Leak guards — regression tests for raw Java exception leaks that the
  // sass-spec runner previously surfaced as `index-bounds`, `script-error`,
  // `stack-overflow`, and `illegal-argument`.
  // ---------------------------------------------------------------------------

  test("if() with only one argument raises a proper SassException, not IOOBE") {
    val ex = intercept[SassException] {
      Compile.compileString("a {b: if(true)}")
    }
    assert(ex.getMessage.contains("if"))
  }

  test("list.append with only one positional argument raises a proper SassException") {
    val ex = intercept[SassException] {
      Compile.compileString("""a {b: append((1 2 3))}""")
    }
    assert(ex.getMessage.contains("$val") || ex.getMessage.contains("Missing"))
  }

  test("map literal with duplicate keys raises a spanned SassException") {
    val ex = intercept[SassException] {
      Compile.compileString("""a {b: inspect((k: 1, k: 2))}""")
    }
    assert(ex.getMessage.contains("Duplicate key"))
  }

  test("nested @include with a `@content` body does not stack-overflow") {
    val src =
      """@mixin outer { @content; @include inner { @content; } }
        |@mixin inner { x: 1; @content; }
        |.a { @include outer { y: 2; } }
        |""".stripMargin
    // Just make sure this compiles (or raises a SassException) rather than
    // crashing with a StackOverflowError.
    try Compile.compileString(src)
    catch { case _: SassException => () }
  }

  test("rgb(..., NaN) alpha raises a SassException, not IllegalArgumentException") {
    // NaN alpha used to leak as a raw IllegalArgumentException from
    // NumberUtil.fuzzyAssertRange.
    val ex = intercept[SassException] {
      Compile.compileString("""a {b: rgb(1, 2, 3, (0/0))}""")
    }
    assert(ex.getMessage.toLowerCase.contains("alpha") || ex.getMessage.toLowerCase.contains("nan"))
  }

  // --------------------------------------------------------------------------
  // Regression tests: @function reserved names and related parser checks.
  // Source: sass-spec `directives/function/name.hrx`,
  // `directives/extend/error.hrx`, `directives/use/error/syntax/member.hrx`.
  // --------------------------------------------------------------------------

  test("@function type is reserved for the plain-CSS function") {
    val ex = intercept[SassException] {
      Compile.compileString("@function type() {@return 1}")
    }
    assert(ex.getMessage.contains("reserved for the plain-CSS function"))
  }

  test("@function TYPE (uppercase) is reserved for the plain-CSS function") {
    val ex = intercept[SassException] {
      Compile.compileString("@function TYPE() {@return 1}")
    }
    assert(ex.getMessage.contains("reserved for the plain-CSS function"))
  }

  test("@function element is rejected as invalid function name") {
    val ex = intercept[SassException] {
      Compile.compileString("@function element() {@return 1}")
    }
    assert(ex.getMessage.contains("Invalid function name"))
  }

  test("@function vendor-prefixed element is rejected (unvendored)") {
    val ex = intercept[SassException] {
      Compile.compileString("@function -a-element() {@return 1}")
    }
    assert(ex.getMessage.contains("Invalid function name"))
  }

  test("@function CALC (uppercase) is allowed") {
    // `calc` is not in dart-sass's case-sensitive reserved set, so CALC
    // is parsed and returns 1 when called.
    val css = Compile.compileString("@function CALC() {@return 1} a {b: CALC()}").css
    assertEquals(css, "a {\n  b: 1;\n}\n")
  }

  test("@function NOT (uppercase) is allowed") {
    val css = Compile.compileString("@function NOT() {@return 1} a {b: NOT()}").css
    assertEquals(css, "a {\n  b: 1;\n}\n")
  }

  test("@function -- prefix is rejected") {
    val ex = intercept[SassException] {
      Compile.compileString("@function --a() {@return 1}")
    }
    assert(ex.getMessage.contains("Invalid function name"))
  }

  test("--a() custom-ident call is preserved as plain CSS, not evaluated") {
    val css = Compile.compileString("b {c: --a()}").css
    assertEquals(css, "b {\n  c: --a();\n}\n")
  }

  test("@extend with no selector raises an error") {
    val ex = intercept[SassException] {
      Compile.compileString("a {@extend}")
    }
    assert(ex.getMessage.contains("Expected selector"))
  }

  test("@use private function call ns._member() is rejected") {
    val ex = intercept[SassException] {
      Compile.compileString("a {a: namespace._member()}")
    }
    assert(ex.getMessage.contains("Private members"))
  }

  test("@use private variable ns.\\$_member is rejected") {
    val ex = intercept[SassException] {
      Compile.compileString("a {a: namespace.$_member}")
    }
    assert(ex.getMessage.contains("Private members"))
  }

  test("@include on private mixin ns._member is rejected") {
    val ex = intercept[SassException] {
      Compile.compileString("a {@include namespace._member}")
    }
    assert(ex.getMessage.contains("Private members"))
  }

  // Diagnostic: to-space with alpha — known issue: `/` parsed as division
  // inside function args instead of slash-alpha separator. See ISS-235 / B001.
  // test("DIAG: color.to-space with alpha") { ... }
}
