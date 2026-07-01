/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM-only — the fixtures use java.nio.file directly. Since ISS-1154 FilesystemImporter itself is cross-platform; this suite is retained for its java.nio-based fixtures and JVM env assertions. */
package ssg
package sass

import java.nio.file.{ Files, Path }

import scala.language.implicitConversions

import ssg.sass.importer.{ FilesystemImporter, LoadPathImporterPlatform, MapImporter }

/** Differential red tests for the constructor + SASS_PATH surface of ISS-991 ([R0610-P1] api-noop).
  *
  * Two distinct surfaces the shared CompileWiringIss990Suite does NOT cover:
  *
  *   1. The public 3-arg ImportCache constructor `this(importers, loadPaths, logger)` (ImportCache.scala). Before this fix it called `ImportCache.toImporters(importers, loadPaths)` with no
  *      `loadPathImporter`, falling into the hidden `_ => Importer.noOp` default — so every load path silently resolved nothing. dart-sass maps each load path to `FilesystemImporter(path)`
  *      (import_cache.dart:128-129); the fix routes the ctor through the platform seam (LoadPathImporterPlatform.loadPathImporter) and deletes the noOp default outright.
  *   2. The implicit SASS_PATH environment variable. dart-sass reads `getEnvironmentVariable('SASS_PATH')` and turns each entry into a `FilesystemImporter` (import_cache.dart:124/130-132), behind the
  *      `if (isBrowser) return [...?importers];` guard (import_cache.dart:125): honored on the VM, skipped in a browser. The port mirrors this with LoadPathImporterPlatform.sassPathImporters()
  *      (JVM-populated, JS/Native-empty). Env vars cannot be set from a JVM test, so the parsing + FilesystemImporter wiring is exercised through the package-private pure core
  *      `sassPathImportersFrom(env, sep)`.
  */
final class ImportCacheCtorSassPathIss991JvmSuite extends munit.FunSuite {

  // A temp dir for each test; cleaned up via munit fixture (same pattern as
  // CompileLoadPathsIss991JvmSuite / the JVM ImportSuite).
  private val tempDir = FunFixture[Path](
    setup = _ => Files.createTempDirectory("ssg-sass-iss991-ctor-"),
    teardown = dir =>
      if (Files.exists(dir)) {
        Files.walk(dir).sorted(java.util.Comparator.reverseOrder[Path]()).forEach(p => Files.deleteIfExists(p))
      }
  )

  private def writeFile(dir: Path, name: String, contents: String): Unit = {
    val path = dir.resolve(name)
    val _    = Files.write(path, contents.getBytes(java.nio.charset.StandardCharsets.UTF_8))
  }

  // ---------------------------------------------------------------------------
  // (a) The 3-arg ImportCache constructor must resolve a load path.
  // ---------------------------------------------------------------------------

  tempDir.test("ISS-991 RED: 3-arg ImportCache(importers, loadPaths, logger) resolves a load path") { dir =>
    writeFile(dir, "_dep.scss", "c {\n  d: e;\n}\n")
    // The exact surface ISS-991 cites: the public 3-arg ctor with an empty
    // importers list and a single load path. Before the fix this hits the
    // hidden noOp default and `@use "dep"` finds nothing.
    val cache  = new ImportCache(Nil, List(dir.toString), Nullable.empty[Logger])
    val result = Compile.compileString(
      "@use \"dep\";\na { b: f; }",
      importCache = Nullable(cache)
    )
    assert(
      result.css.contains("c {\n  d: e;\n}"),
      s"3-arg ctor load path must resolve @use \"dep\":\n${result.css}"
    )
  }

  tempDir.test("ISS-991 control: a FilesystemImporter in the 1-arg ctor resolves the same @use") { dir =>
    writeFile(dir, "_dep.scss", "c {\n  d: e;\n}\n")
    val cache  = new ImportCache(importers = List(new FilesystemImporter(dir.toString): ssg.sass.importer.Importer))
    val result = Compile.compileString(
      "@use \"dep\";\na { b: f; }",
      importCache = Nullable(cache)
    )
    assert(
      result.css.contains("c {\n  d: e;\n}"),
      s"FilesystemImporter must resolve @use \"dep\":\n${result.css}"
    )
  }

  tempDir.test("ISS-991 control: 3-arg ctor with a MapImporter (no load path) still works") { dir =>
    val _     = dir // unused: this control is filesystem-free, but shares the fixture
    val cache = new ImportCache(
      List(new MapImporter(Map("_dep.scss" -> "c {\n  d: e;\n}\n")): ssg.sass.importer.Importer),
      Nil,
      Nullable.empty[Logger]
    )
    val result = Compile.compileString(
      "@use \"dep\";\na { b: f; }",
      importCache = Nullable(cache)
    )
    assert(
      result.css.contains("c {\n  d: e;\n}"),
      s"3-arg ctor with MapImporter must resolve @use \"dep\":\n${result.css}"
    )
  }

  // ---------------------------------------------------------------------------
  // (b) SASS_PATH must be honored on the JVM (dart-sass import_cache.dart:124,
  //     130-132). Exercised through the pure seam core with a fixed env value.
  // ---------------------------------------------------------------------------

  tempDir.test("ISS-991 RED: SASS_PATH entries become load-path importers that resolve @use (via the JVM seam)") { dir =>
    writeFile(dir, "_dep.scss", "c {\n  d: e;\n}\n")
    // Feed a fixed SASS_PATH value to the pure seam core (no env mutation); on
    // the JVM each entry must become a FilesystemImporter
    // (import_cache.dart:130-132).
    val sassPathImporters =
      LoadPathImporterPlatform.sassPathImportersFrom(Some(dir.toString), java.io.File.pathSeparator)
    assertEquals(sassPathImporters.length, 1, "one SASS_PATH entry -> one importer")
    val cache  = new ImportCache(importers = sassPathImporters)
    val result = Compile.compileString(
      "@use \"dep\";\na { b: f; }",
      importCache = Nullable(cache)
    )
    assert(
      result.css.contains("c {\n  d: e;\n}"),
      s"SASS_PATH importer must resolve @use \"dep\":\n${result.css}"
    )
  }

  test("ISS-991 control: an empty/absent SASS_PATH contributes no importers (JVM seam)") {
    assertEquals(LoadPathImporterPlatform.sassPathImportersFrom(scala.None, java.io.File.pathSeparator), Nil)
    assertEquals(LoadPathImporterPlatform.sassPathImportersFrom(Some(""), java.io.File.pathSeparator), Nil)
  }

  tempDir.test("ISS-991 control: multiple SASS_PATH entries split on the separator (JVM seam)") { dir =>
    val a         = dir.resolve("a")
    val b         = dir.resolve("b")
    val _         = Files.createDirectory(a)
    val _         = Files.createDirectory(b)
    val sep       = java.io.File.pathSeparator
    val importers =
      LoadPathImporterPlatform.sassPathImportersFrom(Some(s"${a.toString}${sep}${b.toString}"), sep)
    assertEquals(importers.length, 2, "two colon-separated SASS_PATH entries -> two importers")
    assert(importers.forall(_.isInstanceOf[FilesystemImporter]), "SASS_PATH entries become FilesystemImporters")
  }
}
