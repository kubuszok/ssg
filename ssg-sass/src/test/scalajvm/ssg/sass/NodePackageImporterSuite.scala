/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass

import java.nio.file.{ Files, Path }

import ssg.sass.importer.NodePackageImporter
import ssg.sass.Nullable

import scala.language.implicitConversions

final class NodePackageImporterSuite extends munit.FunSuite {

  private val tempDir = FunFixture[Path](
    setup = _ => Files.createTempDirectory("ssg-sass-node-pkg-"),
    teardown = dir =>
      if (Files.exists(dir)) {
        Files.walk(dir).sorted(java.util.Comparator.reverseOrder[Path]()).forEach(p => Files.deleteIfExists(p))
      }
  )

  private def writeFile(path: Path, contents: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, contents.getBytes(java.nio.charset.StandardCharsets.UTF_8))
  }

  tempDir.test("@import \"pkg:foo\" resolves package main via _index.scss") { dir =>
    val pkg = dir.resolve("node_modules").resolve("foo")
    writeFile(pkg.resolve("package.json"), """{"sass": "_index.scss"}""")
    writeFile(pkg.resolve("_index.scss"), "$primary: #abcdef;")
    val source   = """
      @import "pkg:foo";
      a { color: $primary; }
    """
    val importer = new NodePackageImporter(dir.toString)
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("#abcdef"), result.css)
  }

  tempDir.test("pkg:foo/bar resolves node_modules/foo/bar.scss") { dir =>
    val pkg = dir.resolve("node_modules").resolve("foo")
    writeFile(pkg.resolve("package.json"), """{"main": "index.js"}""")
    writeFile(pkg.resolve("bar.scss"), "$size: 9px;")
    val source   = """
      @import "pkg:foo/bar";
      .b { width: $size; }
    """
    val importer = new NodePackageImporter(dir.toString)
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("9px"), result.css)
  }

  tempDir.test("scoped pkg:@scope/foo resolves node_modules/@scope/foo") { dir =>
    val pkg = dir.resolve("node_modules").resolve("@scope").resolve("foo")
    writeFile(pkg.resolve("package.json"), """{"sass": "_index.scss"}""")
    writeFile(pkg.resolve("_index.scss"), "$c: green;")
    val source   = """
      @import "pkg:@scope/foo";
      a { color: $c; }
    """
    val importer = new NodePackageImporter(dir.toString)
    val result   = Compile.compileString(source, importer = Nullable(importer))
    assert(result.css.contains("green"), result.css)
  }

  tempDir.test("missing package returns empty (canonicalize)") { dir =>
    val importer = new NodePackageImporter(dir.toString)
    val canon    = importer.canonicalize("pkg:nope")
    assert(canon.isEmpty)
  }
}
