/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package sass

import ssg.sass.visitor.OutputStyle

final class MetaApplySuite extends munit.FunSuite {

  test("meta.apply runs a no-arg mixin") {
    val src =
      """@use "sass:meta";
        |@mixin greet { color: red; }
        |a { @include meta.apply(meta.get-mixin("greet")); }""".stripMargin
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assertEquals(css, "a{color:red}")
  }

  test("meta.apply passes positional args") {
    val src =
      """@use "sass:meta";
        |@mixin set-color($c) { color: $c; }
        |a { @include meta.apply(meta.get-mixin("set-color"), blue); }""".stripMargin
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assertEquals(css, "a{color:blue}")
  }

  test("meta.apply with SassMixin captured in a variable") {
    val src =
      """@use "sass:meta";
        |@mixin paint($c) { background: $c; }
        |$m: meta.get-mixin("paint");
        |a { @include meta.apply($m, green); }""".stripMargin
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assertEquals(css, "a{background:green}")
  }

  test("meta.apply accepts a legacy string mixin name") {
    val src =
      """@use "sass:meta";
        |@mixin hi { content: "hi"; }
        |a { @include meta.apply("hi"); }""".stripMargin
    val css = Compile.compileString(src, OutputStyle.Compressed).css
    assertEquals(css, """a{content:"hi"}""")
  }

  test("cross-media @extend raises error when target is at top level (dart-sass semantics)") {
    // dart-sass throws "You may not @extend selectors across media queries."
    // The legacy code path emitted a warning; the module-system-based
    // ExtensionStore correctly raises a hard error.
    val src =
      """.foo { color: red; }
        |@media screen {
        |  .bar { @extend .foo; }
        |}""".stripMargin
    val ex = intercept[SassException] {
      Compile.compileString(src, OutputStyle.Compressed)
    }
    assert(
      ex.getMessage.contains("@extend") || ex.getMessage.contains("media"),
      s"expected cross-media extend error, got: ${ex.getMessage}"
    )
  }

  test("undefined variable error reports a non-zero source line") {
    val src =
      """a {
        |  color: $missing;
        |}""".stripMargin
    val e = intercept[SassException] {
      Compile.compileString(src)
    }
    // Line 2 contains `$missing`; spans are 0-indexed so we expect line 1
    // (which highlights "color: $missing"). We only care that the span is
    // real, not synthetic: non-zero offset into the source.
    assert(e.span.start.line >= 1, s"line=${e.span.start.line} span=${e.span}")
    assert(e.getMessage.contains("Undefined variable"), e.getMessage)
  }
}
