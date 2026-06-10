/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package commons
package io

/** ISS-1127 [R0610-P1]: extra JS FilePath parity cases beyond the red suite (FilePathIss1127JsSuite).
  *
  * The ISS-1127 fix delegates the JS FilePathPlatform to Node's `path.posix` + `process.cwd()`. Node's `path`
  * differs from java.nio.file.Path in two ways the red suite does not pin, which the fix maps deliberately; this
  * suite locks those mappings to JVM-evaluated values (anti-cheat C11) so a future revert of the mapping is caught:
  *
  *   1. `path.normalize` returns "." for a path that reduces to nothing (".", "a/..", "", "./.") whereas
  *      java.nio.file.Path.normalize returns the empty string "". The fix maps "." -> "".
  *   2. `of` (= java.nio.file.Paths.get(path).toString) collapses duplicate separators and strips a trailing
  *      separator but does NOT resolve "." / ".." segments — Paths.get("a/../b").toString == "a/../b". The fix uses a
  *      separator-collapse render, not path.normalize (which WOULD resolve them).
  *
  * Every expected value below was produced by evaluating the same expression with java.nio.file.Paths on the JVM
  * (Scala 3.8.3, JVM 25), matching the JVM reference impl scalajvm/ssg/commons/io/FilePathPlatform.scala.
  */
final class FilePathIss1127ExtraJsSuite extends munit.FunSuite {

  // --- normalize: java.nio renders a vanished path as "" (Node would say ".") ---

  test("ISS-1127: normalize of \".\" is the empty string, matching java.nio") {
    // JVM parity: scalajvm/ssg/commons/io/FilePathPlatform.scala:41-42 (Path.normalize).
    // JVM-evaluated: Paths.get(".").normalize() == "".
    assertEquals(FilePath.of(".").normalize.pathString, "")
  }

  test("ISS-1127: normalize of \"a/..\" is the empty string, matching java.nio") {
    // JVM-evaluated: Paths.get("a/..").normalize() == "".
    assertEquals(FilePath.of("a/..").normalize.pathString, "")
  }

  test("ISS-1127: normalize of \"./.\" is the empty string, matching java.nio") {
    // JVM-evaluated: Paths.get("./.").normalize() == "".
    assertEquals(FilePath.of("./.").normalize.pathString, "")
  }

  test("ISS-1127: normalize strips a trailing separator on a relative path — a/b/ becomes a/b") {
    // JVM-evaluated: Paths.get("a/b/").normalize() == "a/b".
    assertEquals(FilePath.of("a/b/").normalize.pathString, "a/b")
  }

  test("ISS-1127: normalize of \"./a\" drops the leading dot — becomes a") {
    // JVM-evaluated: Paths.get("./a").normalize() == "a".
    assertEquals(FilePath.of("./a").normalize.pathString, "a")
  }

  test("ISS-1127: normalize of \"..\" stays \"..\"") {
    // JVM-evaluated: Paths.get("..").normalize() == "..".
    assertEquals(FilePath.of("..").normalize.pathString, "..")
  }

  // --- of: separator collapse without resolving . / .. ---

  test("ISS-1127: of preserves a .. segment — a/../b stays a/../b (not resolved like Node normalize)") {
    // JVM-evaluated: Paths.get("a/../b").toString == "a/../b".
    assertEquals(FilePath.of("a/../b").pathString, "a/../b")
  }

  test("ISS-1127: of preserves a . segment — /a/./b stays /a/./b") {
    // JVM-evaluated: Paths.get("/a/./b").toString == "/a/./b".
    assertEquals(FilePath.of("/a/./b").pathString, "/a/./b")
  }

  test("ISS-1127: of collapses a doubled root separator — // becomes /") {
    // JVM-evaluated: Paths.get("//").toString == "/".
    assertEquals(FilePath.of("//").pathString, "/")
  }

  test("ISS-1127: of strips a trailing separator on a relative path — a/b/ becomes a/b") {
    // JVM-evaluated: Paths.get("a/b/").toString == "a/b".
    assertEquals(FilePath.of("a/b/").pathString, "a/b")
  }

  test("ISS-1127: of of the empty string is the empty string") {
    // JVM-evaluated: Paths.get("").toString == "".
    assertEquals(FilePath.of("").pathString, "")
  }

  // --- parent: Node dirname -> JVM getParent (null => None) ---

  test("ISS-1127: parent of a bare relative name is None, matching java.nio getParent == null") {
    // JVM parity: scalajvm/ssg/commons/io/FilePathPlatform.scala:18-21.
    // JVM-evaluated: Paths.get("a").getParent == null.
    assertEquals(FilePath.of("a").parent.map(_.pathString), None)
  }

  test("ISS-1127: parent of /a/b/c.txt is /a/b") {
    // JVM-evaluated: Paths.get("/a/b/c.txt").getParent == "/a/b".
    assertEquals(FilePath.of("/a/b/c.txt").parent.map(_.pathString), Some("/a/b"))
  }

  test("ISS-1127: toAbsolute does not normalize a relative path — a/../b keeps the ..") {
    // JVM parity: scalajvm/ssg/commons/io/FilePathPlatform.scala:38-39 (toAbsolutePath does NOT normalize).
    // JVM-evaluated: Paths.get("a/../b").toAbsolutePath == <cwd> + "/a/../b".
    val cwd = scala.scalajs.js.Dynamic.global.require("process").cwd().asInstanceOf[String]
    assertEquals(FilePath.of("a/../b").toAbsolute.pathString, cwd + "/a/../b")
  }
}
