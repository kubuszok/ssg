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
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("#3498db"))
  }

  test("loads @import with explicit .scss extension") {
    val importer = importerOf("vars.scss" -> "$size: 42px;")
    val source   = """
      @import "vars.scss";
      .box { width: $size; }
    """
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("42px"))
  }

  test("unresolved @import is silently skipped") {
    val importer = importerOf()
    val source   = """
      @import "nonexistent";
      a { color: red; }
    """
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("color: red"))
  }

  test("@use loads module with default namespace") {
    val importer = importerOf("_colors.scss" -> "$primary: #3498db;")
    val source   = """
      @use "colors";
      a { color: colors.$primary; }
    """
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("#3498db"))
  }

  test("@use with `as *` merges members flat") {
    val importer = importerOf("_vars.scss" -> "$size: 7px;")
    val source   = """
      @use "vars" as *;
      .box { width: $size; }
    """
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("7px"))
  }

  test("@use with explicit namespace") {
    val importer = importerOf("_t.scss" -> "$c: #abcdef;")
    val source   = """
      @use "t" as th;
      a { color: th.$c; }
    """
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("#abcdef"))
  }

  test("@forward re-exports variables to caller of @use") {
    val importer = importerOf(
      "_inner.scss" -> "$primary: #abcdef;",
      "_mid.scss"   -> """@forward "inner";"""
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

  test("loadedUrls tracks imported files") {
    val importer = importerOf("_foo.scss" -> "$x: 1;")
    val source   = """@import "foo"; a { color: red; }"""
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.loadedUrls.nonEmpty)
  }
}
