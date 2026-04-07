/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Cross-platform import tests using the in-memory MapImporter.
 * Runs on JVM, JS, and Native (no java.nio dependency).
 */
package ssg
package sass

import ssg.sass.importer.MapImporter
import ssg.sass.Nullable

import scala.language.implicitConversions

final class ImportMapSuite extends munit.FunSuite {

  private def importerOf(files: (String, String)*): MapImporter =
    new MapImporter(files.toMap)

  test("loads @import of partial by basename") {
    val importer = importerOf("_colors.scss" -> "$primary: #3498db;")
    val source   = """
      @import "colors";
      .button { color: $primary; }
    """
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("#3498db"))
  }

  test("loads @import with explicit .scss extension") {
    val importer = importerOf("vars.scss" -> "$size: 42px;")
    val source   = """
      @import "vars.scss";
      .box { width: $size; }
    """
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("42px"))
  }

  test("@import of .css URL passes through as a plain CSS @import") {
    // dart-sass: @import "foo.css" is never loaded as Sass. It is always
    // emitted verbatim as a plain CSS `@import url(foo.css)` so that the
    // browser handles the fetch at runtime.
    val importer = importerOf("foo.css" -> "/* should not be loaded */")
    val source   = """
      @import "foo.css";
      a { color: red; }
    """
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("@import"), s"missing @import passthrough in: ${result.css}")
    assert(result.css.contains("foo.css"), s"missing foo.css in: ${result.css}")
    assert(!result.css.contains("should not be loaded"), s"file was evaluated in: ${result.css}")
  }

  test("unresolved @import is silently skipped") {
    val importer = importerOf()
    val source   = """
      @import "nonexistent";
      a { color: red; }
    """
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("color: red"))
  }

  test("@use loads module with default namespace") {
    val importer = importerOf("_colors.scss" -> "$primary: #3498db;")
    val source   = """
      @use "colors";
      a { color: colors.$primary; }
    """
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("#3498db"))
  }

  test("@use with `as *` merges members flat") {
    val importer = importerOf("_vars.scss" -> "$size: 7px;")
    val source   = """
      @use "vars" as *;
      .box { width: $size; }
    """
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("7px"))
  }

  test("@use with explicit namespace") {
    val importer = importerOf("_t.scss" -> "$c: #abcdef;")
    val source   = """
      @use "t" as th;
      a { color: th.$c; }
    """
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("#abcdef"))
  }

  test("@forward re-exports variables to caller of @use") {
    val importer = importerOf(
      "_inner.scss" -> "$primary: #abcdef;",
      "_mid.scss" -> """@forward "inner";"""
    )
    val source = """
      @use "mid";
      a { color: mid.$primary; }
    """
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("#abcdef"), result.css)
  }

  test("@use with config overrides !default variable") {
    val importer = importerOf(
      "_theme.scss" -> "$primary: red !default; .a { color: $primary; }"
    )
    val source = """
      @use "theme" with ($primary: blue);
    """
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("blue"), result.css)
    assert(!result.css.contains("red"), result.css)
  }

  test("@use without config uses !default value") {
    val importer = importerOf(
      "_theme.scss" -> "$primary: red !default; .a { color: $primary; }"
    )
    val source = """
      @use "theme";
    """
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("red"), result.css)
  }

  // --- Module system tightening: private hiding, transitive forward ------

  test("private variable ($-name) hidden from @use namespace") {
    val importer = importerOf(
      "_m.scss" -> "$-private: red; $public: blue;"
    )
    val source = """
      @use "m";
      a { color: m.$public; }
    """
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("blue"), result.css)
    // Accessing m.$-private must error out at compile time.
    val failing = """
      @use "m";
      a { color: m.$-private; }
    """
    intercept[Exception] {
      val _ = Compile.compileString(failing, importer = Nullable(importer))
    }
  }

  test("underscore-private ($_name) hidden the same as dash-private") {
    val importer = importerOf(
      "_m.scss" -> "$_private: red; $public: blue;"
    )
    val failing = """
      @use "m";
      a { color: m.$_private; }
    """
    intercept[Exception] {
      val _ = Compile.compileString(failing, importer = Nullable(importer))
    }
  }

  test("private variable hidden via `as *` flat merge") {
    val importer = importerOf(
      "_m.scss" -> "$-private: red; $public: blue;"
    )
    val source = """
      @use "m" as *;
      a { color: $public; }
    """
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("blue"), result.css)
    val failing = """
      @use "m" as *;
      a { color: $-private; }
    """
    intercept[Exception] {
      val _ = Compile.compileString(failing, importer = Nullable(importer))
    }
  }

  test("dashes mid-name are NOT private (e.g. $theme-color)") {
    val importer = importerOf(
      "_m.scss" -> "$theme-color: #3498db;"
    )
    val source = """
      @use "m";
      a { color: m.$theme-color; }
    """
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("#3498db"), result.css)
  }

  test("transitive @forward chain (A → B → C)") {
    val importer = importerOf(
      "_c.scss" -> "$x: 1px;",
      "_b.scss" -> """@forward "c";""",
      "_a.scss" -> """@forward "b";"""
    )
    val source = """
      @use "a";
      .x { foo: a.$x; }
    """
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("foo: 1px"), result.css)
  }

  test("@forward does not re-export private members") {
    val importer = importerOf(
      "_inner.scss" -> "$-secret: red; $public: blue;",
      "_mid.scss" -> """@forward "inner";"""
    )
    val source = """
      @use "mid";
      a { color: mid.$public; }
    """
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("blue"), result.css)
    val failing = """
      @use "mid";
      a { color: mid.$-secret; }
    """
    intercept[Exception] {
      val _ = Compile.compileString(failing, importer = Nullable(importer))
    }
  }

  test("@forward show only filters the named variable kind") {
    // `show $bar` exposes the variable `$bar` without hiding a mixin
    // or function that happens to share the name.
    val importer = importerOf(
      "_inner.scss" ->
        """
          |$bar: #123456;
          |@function bar() { @return #abcdef; }
          |$baz: #deadbe;
          |""".stripMargin,
      "_mid.scss" -> """@forward "inner" show $bar, bar;"""
    )
    val source = """
      @use "mid";
      .a { c: mid.$bar; d: mid.bar(); }
    """
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("#123456"), result.css)
    assert(result.css.contains("#abcdef"), result.css)
    // $baz should NOT be forwarded.
    val failing = """
      @use "mid";
      .a { c: mid.$baz; }
    """
    intercept[Exception] {
      val _ = Compile.compileString(failing, importer = Nullable(importer))
    }
  }

  test("@forward with (...) configures through chain") {
    val importer = importerOf(
      "_inner.scss" -> "$primary: red !default; .x { color: $primary; }",
      "_mid.scss" -> """@forward "inner";"""
    )
    val source = """
      @use "mid" with ($primary: blue);
    """
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("blue"), result.css)
    assert(!result.css.contains("red"), result.css)
  }

  test("loadedUrls tracks imported files") {
    val importer = importerOf("_foo.scss" -> "$x: 1;")
    val source   = """@import "foo"; a { color: red; }"""
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.loadedUrls.nonEmpty)
  }
}
