/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Cross-platform (JVM / Scala Native / Scala.js-under-Node) load-path test for ISS-1154.
 *
 * ISS-1154 made FilesystemImporter cross-platform (backed by ssg-commons FileOps/FilePath) and flipped the Native
 * and JS LoadPathImporterPlatform from a loud UnsupportedOperationException to a real FilesystemImporter. This suite
 * exercises the real load-path resolution on every platform, using only FileOps/FilePath for its temporary-file
 * setup so it links and runs on all three (java.nio.file temp-dir helpers are unavailable on Scala.js).
 *
 * dart-sass reference: import_cache.dart:128-129 `for (var path in loadPaths) FilesystemImporter(path)`. */
package ssg
package sass

import ssg.commons.io.{ FileOps, FilePath }
import ssg.sass.importer.FilesystemImporter

import scala.language.implicitConversions

final class CompileLoadPathsIss1154Suite extends munit.FunSuite {

  // A unique temp dir per test, created and cleaned up via FileOps so the suite is cross-platform.
  private val tempDir = FunFixture[FilePath](
    setup = test => {
      val dir = FilePath.of(s"target/ssg-sass-iss1154-${test.name.replaceAll("[^A-Za-z0-9]", "_")}-${System.nanoTime()}")
      FileOps.createDirectories(dir)
      dir
    },
    teardown = dir =>
      if (FileOps.exists(dir)) FileOps.deleteRecursively(dir)
  )

  private def writeFile(dir: FilePath, name: String, contents: String): Unit =
    FileOps.writeString(dir.resolve(name), contents)

  tempDir.test("ISS-1154: loadPaths= resolves @use from the given directory on every platform") { dir =>
    writeFile(dir, "_dep.scss", "c {\n  d: e;\n}\n")
    val result = Compile.compileString(
      "@use \"dep\";\na { b: f; }",
      loadPaths = Nullable(List(dir.pathString))
    )
    // import_cache.dart:128-129: the load path acts as a FilesystemImporter, so
    // `@use "dep"` finds the partial `_dep.scss` under [dir].
    assert(
      result.css.contains("c {\n  d: e;\n}"),
      s"@use \"dep\" content missing from output:\n${result.css}"
    )
  }

  tempDir.test("ISS-1154: an explicit FilesystemImporter resolves @use on every platform") { dir =>
    writeFile(dir, "_dep.scss", "c {\n  d: e;\n}\n")
    val importer = new FilesystemImporter(dir.pathString)
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
