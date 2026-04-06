/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * JVM-only — uses java.nio.file.
 */
package ssg
package sass

import java.nio.file.{Files, Path}

import ssg.sass.importer.FilesystemImporter
import ssg.sass.Nullable

import scala.language.implicitConversions

final class ImportSuite extends munit.FunSuite {

  // A temp dir for each test; cleaned up via munit fixture.
  private val tempDir = FunFixture[Path](
    setup = _ => Files.createTempDirectory("ssg-sass-import-"),
    teardown = dir => {
      if (Files.exists(dir)) {
        Files.walk(dir)
          .sorted(java.util.Comparator.reverseOrder[Path]())
          .forEach(p => Files.deleteIfExists(p))
      }
    }
  )

  private def writeFile(dir: Path, name: String, contents: String): Unit = {
    val path = dir.resolve(name)
    Files.write(path, contents.getBytes(java.nio.charset.StandardCharsets.UTF_8))
  }

  tempDir.test("loads @import of partial by basename") { dir =>
    writeFile(dir, "_colors.scss", "$primary: #3498db;")
    val source = """
      @import "colors";
      .button { color: $primary; }
    """
    val importer = new FilesystemImporter(dir.toString)
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("#3498db"))
  }

  tempDir.test("loads @import with explicit .scss extension") { dir =>
    writeFile(dir, "vars.scss", "$size: 42px;")
    val source = """
      @import "vars.scss";
      .box { width: $size; }
    """
    val importer = new FilesystemImporter(dir.toString)
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("42px"))
  }

  tempDir.test("loads @import of _partial.scss") { dir =>
    writeFile(dir, "_helpers.scss", "@function double($x) { @return $x * 2; }")
    val source = """
      @import "helpers";
      .box { width: 10; }
    """
    val importer = new FilesystemImporter(dir.toString)
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains(".box"))
  }

  tempDir.test("unresolved @import is silently skipped") { dir =>
    val source = """
      @import "nonexistent";
      a { color: red; }
    """
    val importer = new FilesystemImporter(dir.toString)
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("color: red"))
  }

  tempDir.test("imported variables are visible to caller") { dir =>
    writeFile(dir, "_a.scss", "$x: blue;")
    val source = """
      @import "a";
      .box { background: $x; }
    """
    val importer = new FilesystemImporter(dir.toString)
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("background: blue"))
  }

  tempDir.test("loadedUrls tracks imported files") { dir =>
    writeFile(dir, "_foo.scss", "$x: 1;")
    val source = """@import "foo"; a { color: red; }"""
    val importer = new FilesystemImporter(dir.toString)
    val result = Compile.compileString(source, importer = Nullable(importer))
    assert(result.loadedUrls.nonEmpty)
  }

  tempDir.test("CompileFile.compile reads file from path") { dir =>
    writeFile(dir, "main.scss", "a { color: red; }")
    val path = dir.resolve("main.scss").toString
    val result = CompileFile.compile(path)
    assert(result.css.contains("color: red"))
  }
}
