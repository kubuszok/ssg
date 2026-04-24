/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * JVM-only — uses java.nio.file.
 */
package ssg
package sass

import java.nio.file.{ Files, Path }

import ssg.sass.importer.FilesystemImporter
import ssg.sass.Nullable

import scala.language.implicitConversions

final class ImportSuite extends munit.FunSuite {

  // A temp dir for each test; cleaned up via munit fixture.
  private val tempDir = FunFixture[Path](
    setup = _ => Files.createTempDirectory("ssg-sass-import-"),
    teardown = dir =>
      if (Files.exists(dir)) {
        Files.walk(dir).sorted(java.util.Comparator.reverseOrder[Path]()).forEach(p => Files.deleteIfExists(p))
      }
  )

  private def writeFile(dir: Path, name: String, contents: String): Unit = {
    val path = dir.resolve(name)
    Files.write(path, contents.getBytes(java.nio.charset.StandardCharsets.UTF_8))
  }

  tempDir.test("loads @import of partial by basename") { dir =>
    writeFile(dir, "_colors.scss", "$primary: #3498db;")
    val source   = """
      @import "colors";
      .button { color: $primary; }
    """
    val importer = new FilesystemImporter(dir.toString)
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("#3498db"))
  }

  tempDir.test("loads @import with explicit .scss extension") { dir =>
    writeFile(dir, "vars.scss", "$size: 42px;")
    val source   = """
      @import "vars.scss";
      .box { width: $size; }
    """
    val importer = new FilesystemImporter(dir.toString)
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("42px"))
  }

  tempDir.test("loads @import of _partial.scss") { dir =>
    writeFile(dir, "_helpers.scss", "@function double($x) { @return $x * 2; }")
    val source   = """
      @import "helpers";
      .box { width: 10; }
    """
    val importer = new FilesystemImporter(dir.toString)
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains(".box"))
  }

  tempDir.test("unresolved @import raises error (matches dart-sass)") { dir =>
    val source   = """
      @import "nonexistent";
      a { color: red; }
    """
    val importer = new FilesystemImporter(dir.toString)
    // dart-sass: unresolved @import throws "Can't find stylesheet to import."
    val ex = intercept[SassException] {
      Compile.compileString(source, importer = Nullable(importer))
    }
    assert(ex.getMessage.contains("Can't find stylesheet to import"), s"Unexpected message: ${ex.getMessage}")
  }

  tempDir.test("imported variables are visible to caller") { dir =>
    writeFile(dir, "_a.scss", "$x: blue;")
    val source   = """
      @import "a";
      .box { background: $x; }
    """
    val importer = new FilesystemImporter(dir.toString)
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("background: blue"))
  }

  tempDir.test("loadedUrls tracks imported files") { dir =>
    writeFile(dir, "_foo.scss", "$x: 1;")
    val source   = """@import "foo"; a { color: red; }"""
    val importer = new FilesystemImporter(dir.toString)
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.loadedUrls.nonEmpty)
  }

  tempDir.test("@use loads module with default namespace") { dir =>
    writeFile(dir, "_colors.scss", "$primary: #3498db;")
    val source   = """
      @use "colors";
      a { color: colors.$primary; }
    """
    val importer = new FilesystemImporter(dir.toString)
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("#3498db"))
  }

  tempDir.test("@use with `as *` merges members flat") { dir =>
    writeFile(dir, "_vars.scss", "$size: 7px;")
    val source   = """
      @use "vars" as *;
      .box { width: $size; }
    """
    val importer = new FilesystemImporter(dir.toString)
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("7px"))
  }

  tempDir.test("@use with explicit namespace") { dir =>
    writeFile(dir, "_t.scss", "$c: #abcdef;")
    val source   = """
      @use "t" as th;
      a { color: th.$c; }
    """
    val importer = new FilesystemImporter(dir.toString)
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("#abcdef"))
  }

  tempDir.test("@forward re-exports variables to caller of @use") { dir =>
    writeFile(dir, "_inner.scss", "$primary: #abcdef;")
    writeFile(dir, "_mid.scss", """@forward "inner";""")
    val source   = """
      @use "mid";
      a { color: mid.$primary; }
    """
    val importer = new FilesystemImporter(dir.toString)
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("#abcdef"), result.css)
  }

  tempDir.test("@forward with show only re-exports listed names") { dir =>
    writeFile(dir, "_inner.scss", "$a: red; $b: blue;")
    writeFile(dir, "_mid.scss", """@forward "inner" show $a;""")
    val source   = """
      @use "mid";
      a { x: mid.$a; }
    """
    val importer = new FilesystemImporter(dir.toString)
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("red"), result.css)
  }

  tempDir.test("@forward with hide skips listed names") { dir =>
    writeFile(dir, "_inner.scss", "$a: red; $b: blue;")
    writeFile(dir, "_mid.scss", """@forward "inner" hide $a;""")
    val source   = """
      @use "mid";
      a { x: mid.$b; }
    """
    val importer = new FilesystemImporter(dir.toString)
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("blue"), result.css)
  }

  tempDir.test("@forward with `as prefix-*` adds prefix to forwarded variables") { dir =>
    writeFile(dir, "_inner.scss", "$color: green;")
    writeFile(dir, "_mid.scss", """@forward "inner" as ix-*;""")
    val source   = """
      @use "mid";
      a { x: mid.$ix-color; }
    """
    val importer = new FilesystemImporter(dir.toString)
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("green"), result.css)
  }

  tempDir.test("@use with config overrides !default variable") { dir =>
    writeFile(dir, "_theme.scss", "$primary: red !default; .a { color: $primary; }")
    val source   = """
      @use "theme" with ($primary: blue);
    """
    val importer = new FilesystemImporter(dir.toString)
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("blue"), result.css)
    assert(!result.css.contains("red"), result.css)
  }

  tempDir.test("@use with config overrides multiple !default variables") { dir =>
    writeFile(dir, "_theme.scss", "$primary: red !default; $secondary: green !default; .a { c1: $primary; c2: $secondary; }")
    val source   = """
      @use "theme" with ($primary: blue, $secondary: yellow);
    """
    val importer = new FilesystemImporter(dir.toString)
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("blue"), result.css)
    assert(result.css.contains("yellow") || result.css.contains("#ff0"), result.css)
  }

  tempDir.test("@use without config uses !default value") { dir =>
    writeFile(dir, "_theme.scss", "$primary: red !default; .a { color: $primary; }")
    val source   = """
      @use "theme";
    """
    val importer = new FilesystemImporter(dir.toString)
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("red"), result.css)
  }

  tempDir.test("CompileFile.compile reads file from path") { dir =>
    writeFile(dir, "main.scss", "a { color: red; }")
    val path   = dir.resolve("main.scss").toString
    val result = CompileFile.compile(path)
    assert(result.css.contains("color: red"))
  }
}
