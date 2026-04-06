/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Cross-platform tests for the strict plain-CSS parser (ISS-019).
 */
package ssg
package sass

import ssg.sass.importer.MapImporter
import ssg.sass.Nullable

import scala.language.implicitConversions

final class CssParserSuite extends munit.FunSuite {

  private def compileCss(source: String): CompileResult =
    Compile.compileString(source, syntax = Syntax.Css)

  test("plain CSS compiles unchanged through Syntax.Css") {
    val source =
      """|.button {
         |  color: red;
         |  font-size: 14px;
         |}""".stripMargin
    val result = compileCss(source)
    assert(result.css.contains(".button"))
    assert(result.css.contains("color: red"))
    assert(result.css.contains("font-size: 14px"))
  }

  test("Sass variable declaration in plain CSS is rejected") {
    val source = "$primary: #3498db; .a { color: $primary; }"
    intercept[SassFormatException](compileCss(source))
  }

  test("@mixin in plain CSS is rejected") {
    val source = "@mixin box { padding: 10px; } .a { @include box; }"
    intercept[SassFormatException](compileCss(source))
  }

  test("@include in plain CSS is rejected") {
    // Just an @include with no prior @mixin so the @mixin-before-@include
    // ordering doesn't matter; the first forbidden rule reached wins.
    val source = ".a { @include some-mixin; }"
    intercept[SassFormatException](compileCss(source))
  }

  test("#{...} interpolation in selectors is rejected") {
    val source = ".foo-#{bar} { color: red; }"
    intercept[SassFormatException](compileCss(source))
  }

  test("nested style rules are rejected") {
    val source = ".outer { .inner { color: red; } }"
    intercept[SassFormatException](compileCss(source))
  }

  test("parent selector & is rejected") {
    // `&:hover` wrapped in a synthetic outer so it appears at top level.
    // CssParser validates selectors textually for `&`.
    val source = "& { color: red; }"
    intercept[SassFormatException](compileCss(source))
  }

  test("@if / @for / @function / @return all rejected") {
    intercept[SassFormatException](compileCss("@if true { a { color: red; } }"))
    intercept[SassFormatException](compileCss("@for $i from 1 through 3 { a { color: red; } }"))
    intercept[SassFormatException](compileCss("@function f() { @return 1; }"))
  }

  test("custom properties (--var) are allowed in plain CSS") {
    val source =
      """|:root {
         |  --brand: #3498db;
         |}""".stripMargin
    val result = compileCss(source)
    assert(result.css.contains("--brand"))
    assert(result.css.contains("#3498db"))
  }

  test("@media is allowed in plain CSS") {
    val source =
      """|@media (min-width: 600px) {
         |  .a { color: red; }
         |}""".stripMargin
    val result = compileCss(source)
    assert(result.css.contains("@media"))
    assert(result.css.contains("color: red"))
  }

  test("@use of a .css file routes through the strict CssParser") {
    // The MapImporter assigns Syntax.forPath, so `evil.css` loads as
    // Syntax.Css, and ImportCache picks CssParser — which rejects the
    // Sass variable inside it.
    val importer = new MapImporter(
      Map("evil.css" -> "$oops: red; .a { color: $oops; }")
    )
    val source = """@use "evil.css";"""
    intercept[SassFormatException] {
      Compile.compileString(source, importer = Nullable(importer))
    }
  }

  test("@use of a .scss file still routes through ScssParser") {
    // Same layout, but with a .scss extension, so Sass variables are OK.
    val importer = new MapImporter(
      Map("ok.scss" -> "$brand: #123456; .a { color: $brand; }")
    )
    val source = """@use "ok.scss";"""
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("#123456"))
  }
}
