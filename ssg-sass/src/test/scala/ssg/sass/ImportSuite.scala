/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass

import scala.collection.mutable
import scala.language.implicitConversions

import ssg.sass.importer.{ Importer, ImporterResult, PackageImporter }

// Moved to src/test/scala-jvm/ssg/sass/ImportSuite.scala — filesystem-backed tests.
// These in-memory tests exercise ImportCache, circular detection and PackageImporter.
final class ImportSuitePlaceholder extends munit.FunSuite

/** A tiny cross-platform importer backed by an in-memory `Map[String, String]` (canonical URL -> SCSS source) that counts how many times `load` is called.
  */
final class CountingMemoryImporter(val files: Map[String, String]) extends Importer {
  val loadCount: mutable.Map[String, Int] = mutable.Map.empty.withDefaultValue(0)

  def canonicalize(url: String): Nullable[String] =
    if (files.contains(url)) Nullable(url)
    else if (files.contains(url + ".scss")) Nullable(url + ".scss")
    else Nullable.empty

  def load(url: String): Nullable[ImporterResult] =
    files.get(url) match {
      case Some(src) =>
        loadCount(url) = loadCount(url) + 1
        Nullable(ImporterResult(src))
      case scala.None => Nullable.empty
    }
}

final class ImportCacheSuite extends munit.FunSuite {

  test("ImportCache parses a canonical URL only once across repeated imports") {
    val imp = new CountingMemoryImporter(
      Map("colors.scss" -> "$primary: #3498db;")
    )
    val cache = new ImportCache(importers = List(imp))
    val c1    = cache.canonicalize("colors")
    val c2    = cache.canonicalize("colors")
    assert(c1.isDefined)
    assert(c2.isDefined)
    val url = c1.get._2
    val s1  = cache.importCanonical(imp, url)
    val s2  = cache.importCanonical(imp, url)
    assert(s1.isDefined)
    assert(s2.isDefined)
    // load() must have been invoked exactly once for the canonical URL.
    assertEquals(imp.loadCount(url), 1)
  }

  test("Evaluator-level @use dedupes parses via ImportCache") {
    val imp = new CountingMemoryImporter(
      Map("vars.scss" -> "$size: 42px;")
    )
    // Two @use statements referencing the same URL should only load once.
    val source = """
      @use "vars";
      @use "vars" as other;
      .a { x: vars.$size; }
    """
    val result = Compile.compileString(source, importer = Nullable(imp))
    assert(result.css.contains("42px"))
    assertEquals(imp.loadCount("vars.scss"), 1)
  }

  test("Circular @import is broken silently without stack overflow") {
    val files = Map(
      "a.scss" -> """@import "b"; .a { color: red; }""",
      "b.scss" -> """@import "a"; .b { color: blue; }"""
    )
    val imp    = new CountingMemoryImporter(files)
    val source = """@import "a"; .root { x: 1; }"""
    val result = Compile.compileString(source, importer = Nullable(imp))
    // Must finish, emit at least the root rule, and not blow up.
    assert(result.css.contains(".root"))
  }

  test("StylesheetGraph.addEdge rejects direct self-cycle") {
    val graph = new StylesheetGraph(ImportCache.none)
    assert(graph.addEdge("a", "b"))
    assert(graph.addEdge("b", "c"))
    // c -> a would make a -> b -> c -> a -> ... a cycle.
    assertEquals(graph.addEdge("c", "a"), false)
    assertEquals(graph.addEdge("a", "a"), false)
  }

  test("PackageImporter rewrites pkg: URLs via the package map and delegate") {
    val delegate = new CountingMemoryImporter(
      Map("libs/colors/index.scss" -> "$primary: green;")
    )
    val pkg = new PackageImporter(
      packages = Map("lib" -> "libs/colors"),
      delegate = delegate
    )
    val canonical = pkg.canonicalize("pkg:lib/index.scss")
    assert(canonical.isDefined, "pkg: URL should resolve via delegate")
    assertEquals(canonical.get, "libs/colors/index.scss")
    val loaded = pkg.load(canonical.get)
    assert(loaded.isDefined)
  }

  test("PackageImporter returns empty for unknown packages and non-pkg URLs") {
    val pkg = new PackageImporter(packages = Map("lib" -> "root"))
    assert(pkg.canonicalize("pkg:other/x").isEmpty)
    assert(pkg.canonicalize("foo").isEmpty)
  }

  test("@import via pkg: scheme resolves through the package map") {
    val delegate = new CountingMemoryImporter(
      Map("vendor/lib/colors.scss" -> "$primary: #abcdef;")
    )
    val pkg = new PackageImporter(
      packages = Map("lib" -> "vendor/lib"),
      delegate = delegate
    )
    val source = """
      @import "pkg:lib/colors";
      .a { color: $primary; }
    """
    val result = Compile.compileString(source, importer = Nullable(pkg))
    assert(result.css.contains("#abcdef"), result.css)
  }
}
