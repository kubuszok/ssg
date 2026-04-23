/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass

import ssg.sass.visitor.OutputStyle

final class ExtendSuite extends munit.FunSuite {

  test("@extend of missing target without !optional raises SassException") {
    val src =
      """.a { @extend .missing; color: red; }""".stripMargin
    val e = intercept[SassException] {
      Compile.compileString(src)
    }
    assert(
      e.sassMessage.contains("The target selector was not found"),
      s"unexpected message: ${e.sassMessage}"
    )
    assert(
      e.sassMessage.contains("!optional"),
      s"expected !optional hint in: ${e.sassMessage}"
    )
  }

  test("@extend of missing target with !optional is silent") {
    val src    = ".a { @extend .missing !optional; color: red; }"
    val result = Compile.compileString(src, OutputStyle.Compressed)
    assertEquals(result.css, ".a{color:red}")
    assertEquals(result.warnings, Nil)
  }

  test("@extend with compound target raises 'compound selectors may no longer be extended'") {
    val src =
      """.base { color: red; }
        |.x { @extend .a.b; }""".stripMargin
    val e = intercept[SassException] {
      Compile.compileString(src)
    }
    assert(
      e.sassMessage.contains("compound selectors may no longer be extended"),
      s"unexpected message: ${e.sassMessage}"
    )
  }

  test("@extend with complex (descendant) target raises the complex-selector error") {
    val src =
      """.a .b { color: red; }
        |.x { @extend .a .b; }""".stripMargin
    val e = intercept[SassException] {
      Compile.compileString(src)
    }
    assert(
      e.sassMessage.contains("complex selectors may not be extended"),
      s"unexpected message: ${e.sassMessage}"
    )
  }

  test("@extend inside @media raises cross-media query error when target also exists at top level") {
    // dart-sass: when .foo exists both at top level and inside @media,
    // extending .foo from within @media raises "You may not @extend
    // selectors across media queries" because the top-level .foo is
    // registered first and has no media context.
    val src =
      """.foo { color: red; }
        |@media screen {
        |  .foo { color: blue; }
        |  .bar { @extend .foo; }
        |}""".stripMargin
    val e = intercept[SassException] {
      Compile.compileString(src, OutputStyle.Compressed)
    }
    assert(
      e.sassMessage.contains("@extend selectors across media queries"),
      s"unexpected message: ${e.sassMessage}"
    )
  }

  test("bogus leading-combinator-only extender does not NSE (directives/extend/bogus.hrx!only)") {
    // Regression: `+ {@extend a}` is a single-component complex selector whose
    // sole component has an empty compound ("+") — previously `components.last`
    // in the extend pipeline would NSE. Should either emit `a { b: c; }` or
    // raise a clean SassException, never a NoSuchElementException.
    val src =
      """a {b: c}
        |+ {@extend a}
        |""".stripMargin
    try {
      val r = Compile.compileString(src, OutputStyle.Compressed)
      assert(r.css.contains("a{b:c"), s"expected base rule to survive, got: ${r.css}")
    } catch {
      case _: SassException => () // clean error is acceptable
    }
  }

  test("CompileResult exposes an empty warnings list by default") {
    val result = Compile.compileString(".a { color: red; }")
    assertEquals(result.warnings, Nil)
  }

  test("extend selector_list ordering: .bang before .baz") {
    // non_conformant/extend-tests/selector_list.hrx
    val src =
      """.foo {a: b}
        |.bar {x: y}
        |.baz {@extend .foo, .bar}
        |.bang {@extend .foo, .bar}""".stripMargin
    val result = Compile.compileString(src)
    assert(
      result.css.contains(".foo, .bang, .baz"),
      s"expected '.foo, .bang, .baz' in output, got:\n${result.css}"
    )
    assert(
      result.css.contains(".bar, .bang, .baz"),
      s"expected '.bar, .bang, .baz' in output, got:\n${result.css}"
    )
  }

  test("mixin splat preserves list separator") {
    // non_conformant/scss-tests/071_test_mixin_splat_args_with_var_args_preserves_separator.hrx
    val src =
      """@mixin foo($a, $b...) {
        |  a: $a;
        |  b: $b;
        |}
        |$list: 3 4 5;
        |.foo {@include foo(1, 2, $list...)}""".stripMargin
    val result = Compile.compileString(src)
    assert(
      result.css.contains("b: 2 3 4 5"),
      s"expected 'b: 2 3 4 5' (space-separated) in output, got:\n${result.css}"
    )
  }

  test("function splat preserves list separator") {
    // non_conformant/scss-tests/090_test_function_splat_args_with_var_args_preserves_separator.hrx
    val src =
      """@function foo($a, $b...) {
        |  @return "a: #{$a}, b: #{$b}";
        |}
        |$list: 3 4 5;
        |.foo {val: foo(1, 2, $list...)}""".stripMargin
    val result = Compile.compileString(src)
    assert(
      result.css.contains("""val: "a: 1, b: 2 3 4 5""""),
      s"expected space-separated in function splat, got:\n${result.css}"
    )
  }
}
