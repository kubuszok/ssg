/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM-only — the fixtures use java.nio.file directly (Files.createTempDirectory). Since ISS-1154 FilesystemImporter itself is cross-platform; the cross-platform load-path coverage lives in
 * CompileLoadPathsIss1154Suite. This JVM suite is retained for its java.nio-based fixtures. */
package ssg
package sass

import java.nio.file.{ Files, Path }

import ssg.sass.importer.FilesystemImporter
import ssg.sass.Nullable

import scala.language.implicitConversions

/** Differential red test for the `loadPaths =` half of ISS-991 ([R0610-P1] api-noop, Compile.compileString).
  *
  * Upstream consumption in dart-sass (vendored at original-src/dart-sass): lib/sass.dart:219 (compileStringToResult param) -> lib/sass.dart:236-239 (`importCache: ImportCache(importers: importers,
  * ..., loadPaths: loadPaths)`) -> lib/src/import_cache.dart:100-103 (`_importers = _toImporters(importers, loadPaths, packageConfig)`) -> import_cache.dart:128-129: `for (var path in loadPaths)
  * FilesystemImporter(path)` — each load path becomes a filesystem importer consulted for `@use`/`@import` resolution (sass.dart:163-164: "this is a shorthand for adding [FilesystemImporter]s to
  * [importers]").
  *
  * The port drops `loadPaths` in Compile.compileString, and even the ImportCache route is inert: ImportCache.toImporters defaults `loadPathImporter = _ => Importer.noOp` (ImportCache.scala:446), so
  * load paths never resolve anything.
  *
  * This suite is JVM-scoped because its fixtures use java.nio.file directly (Files.createTempDirectory). The dart-sass reference maps each path to a FilesystemImporter; since ISS-1154 that importer
  * is cross-platform, and the cross-platform load-path coverage lives in CompileLoadPathsIss1154Suite.
  */
final class CompileLoadPathsIss991JvmSuite extends munit.FunSuite {

  // A temp dir for each test; cleaned up via munit fixture (same pattern as
  // the JVM ImportSuite).
  private val tempDir = FunFixture[Path](
    setup = _ => Files.createTempDirectory("ssg-sass-iss991-"),
    teardown = dir =>
      if (Files.exists(dir)) {
        Files.walk(dir).sorted(java.util.Comparator.reverseOrder[Path]()).forEach(p => Files.deleteIfExists(p))
      }
  )

  private def writeFile(dir: Path, name: String, contents: String): Unit = {
    val path = dir.resolve(name)
    Files.write(path, contents.getBytes(java.nio.charset.StandardCharsets.UTF_8))
  }

  tempDir.test("ISS-991 RED: loadPaths= resolves @use from the given directory") { dir =>
    writeFile(dir, "_dep.scss", "c {\n  d: e;\n}\n")
    val result = Compile.compileString(
      "@use \"dep\";\na { b: f; }",
      loadPaths = Nullable(List(dir.toString))
    )
    // import_cache.dart:128-129: the load path acts as a FilesystemImporter,
    // so `@use "dep"` finds the partial `_dep.scss` under [dir].
    assert(
      result.css.contains("c {\n  d: e;\n}"),
      s"@use \"dep\" content missing from output:\n${result.css}"
    )
  }

  tempDir.test("ISS-991 control: an explicit FilesystemImporter resolves the same @use today") { dir =>
    writeFile(dir, "_dep.scss", "c {\n  d: e;\n}\n")
    val importer = new FilesystemImporter(dir.toString)
    val result   = Compile.compileString(
      "@use \"dep\";\na { b: f; }",
      importer = Nullable(importer)
    )
    assert(
      result.css.contains("c {\n  d: e;\n}"),
      s"@use \"dep\" content missing from output:\n${result.css}"
    )
  }
}
