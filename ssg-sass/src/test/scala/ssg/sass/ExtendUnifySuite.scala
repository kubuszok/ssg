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
    assert(css.contains(".a > .b"), s"missing original in $css")
    assert(css.contains(".a > .c .d"), s"missing woven .a > .c .d in $css")
  }

  test("pseudo-class is preserved on extender") {
    val css = compile(".a:hover { color: red; } .b { @extend .a; }")
    assert(css.contains(".a:hover"), s"missing .a:hover in $css")
    assert(css.contains(".b:hover"), s"missing .b:hover in $css")
  }

  test("incompatible id unification is skipped without error") {
    val src = "#b.x + #c { color: red; } #a { @extend .x; }"
    val css = compile(src)
    assert(css.contains("#b.x + #c"), s"original selector must remain: $css")
    // The extended form would be #a#b + #c which is invalid (two IDs in
    // one compound). unifyCompound returns null, so the extension is
    // skipped gracefully rather than emitting bogus CSS.
    assert(!css.contains("#a#b"), s"invalid merged compound leaked: $css")
  }
}
