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

  test("nested style rules are allowed (CSS nesting support, dart-sass compat)") {
    // Modern dart-sass supports CSS nesting in plain CSS files.
    // Nested rules are preserved as-is, not flattened or rejected.
    val source = ".outer { .inner { color: red; } }"
    val result = compileCss(source)
    assert(result.css.contains(".outer"), s"outer selector missing: ${result.css}")
    assert(result.css.contains(".inner"), s"inner selector missing: ${result.css}")
    assert(result.css.contains("color: red"), s"declaration missing: ${result.css}")
  }

  test("parent selector & is allowed in plain CSS (CSS Nesting spec)") {
    // dart-sass: `&` is allowed at the top level in plain CSS per CSS Nesting.
    // CssParser no longer rejects `&` selectors at the top level.
    val source = "& { color: red; }"
    val result = compileCss(source)
    assert(result.css.contains("color: red"), result.css.toString)
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
    // dart-sass: _addExceptionTrace wraps _execute, so the SassFormatException
    // from parsing gets converted to SassRuntimeException with a trace.
    // The outer run() catch then adds loadedUrls.
    val importer = new MapImporter(
      Map("evil.css" -> "$oops: red; .a { color: $oops; }")
    )
    val source = """@use "evil.css";"""
    val ex     = intercept[SassRuntimeException] {
      Compile.compileString(source, importer = Nullable(importer))
    }
    assert(ex.sassMessage.contains("Sass variables aren't allowed in plain CSS"))
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
