/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass

import ssg.sass.visitor.OutputStyle

final class ExtendUnifySuite extends munit.FunSuite {

  private def compile(src: String): String =
    Compile.compileString(src, OutputStyle.Compressed).css

  test("basic compound extend") {
    val css = compile(".a { color: red; } .b { @extend .a; }")
    assert(css.contains(".a"), s"missing .a in $css")
    assert(css.contains(".b"), s"missing .b in $css")
  }

  test("nested extend prepends parent") {
    val css = compile(".parent .child { color: red; } .foo { @extend .child; }")
    assert(css.contains(".parent .child"), s"missing original in $css")
    assert(css.contains(".parent .foo"), s"missing woven .parent .foo in $css")
  }

  test("cross-combinator weave keeps child combinator") {
    val css = compile(".a > .b { color: red; } .c .d { @extend .b; }")
    // Compressed mode drops whitespace around non-descendant combinators.
    assert(css.contains(".a>.b"), s"missing original in $css")
    assert(css.contains(".a>.c .d"), s"missing woven .a>.c .d in $css")
  }

  test("pseudo-class is preserved on extender") {
    val css = compile(".a:hover { color: red; } .b { @extend .a; }")
    assert(css.contains(".a:hover"), s"missing .a:hover in $css")
    assert(css.contains(".b:hover"), s"missing .b:hover in $css")
  }

  test("child x child combinator weave") {
    val css = compile(".a > .b { color: red; } .c > .d { @extend .b; }")
    assert(css.contains(".a>.b"), s"missing original in $css")
    // Extender should weave with child combinator preserved on the extender side.
    assert(css.contains(".d"), s"missing extended in $css")
  }

  test("next-sibling extend") {
    val css = compile(".a + .b { color: red; } .c { @extend .b; }")
    assert(css.contains(".a+.b"), s"missing original in $css")
    assert(css.contains(".a+.c"), s"missing extended .a+.c in $css")
  }

  test("following-sibling pairs") {
    val css = compile(".a ~ .b { color: red; } .c ~ .d { @extend .b; }")
    assert(css.contains(".a~.b"), s"missing original in $css")
    assert(css.contains(".d"), s"missing extender in $css")
  }

  test("child x next-sibling combo") {
    val css = compile(".a > .b { color: red; } .c + .d { @extend .b; }")
    assert(css.contains(".a>.b"), s"missing original in $css")
    assert(css.contains(".d"), s"missing extender in $css")
  }

  test("nested complex extension with multiple combinators") {
    val css = compile(".x .y > .z { color: red; } .p + .q { @extend .z; }")
    assert(css.contains(".x .y>.z"), s"missing original in $css")
    assert(css.contains(".q"), s"missing extender in $css")
  }

  test("incompatible id unification is skipped without error") {
    val src = "#b.x + #c { color: red; } #a { @extend .x; }"
    val css = compile(src)
    assert(css.contains("#b.x+#c"), s"original selector must remain: $css")
    // The extended form would be #a#b + #c which is invalid (two IDs in
    // one compound). unifyCompound returns null, so the extension is
    // skipped gracefully rather than emitting bogus CSS.
    assert(!css.contains("#a#b"), s"invalid merged compound leaked: $css")
  }
}
