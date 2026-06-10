/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package commons
package io

import java.nio.file.Paths

/** ISS-980 (extra): Native FilePathPlatform operations beyond the red suite's coverage.
  *
  * While fixing ISS-980 the Native impl
  * (ssg-commons/src/main/scalanative/ssg/commons/io/FilePathPlatform.scala) was changed to delegate ALL operations to
  * java.nio.file.Paths/Path, mirroring the JVM reference
  * (ssg-commons/src/main/scalajvm/ssg/commons/io/FilePathPlatform.scala) operation-for-operation. This pins the
  * operations the red suite (FilePathIss980NativeSuite) does not cover — `fileName`, `resolve(FilePath)`, and `of`'s
  * rendering — to JVM parity, so they do not silently drift back to the previous hand-rolled string logic.
  *
  * Every expected value is the JVM-evaluated result of the same java.nio.file.Path expression (anti-cheat C11),
  * computed here independently of the API under test via java.nio.file.Paths (available on Scala Native, as proven by
  * FileOpsIss977NativeSuite).
  */
final class FilePathIss980ExtraNativeSuite extends munit.FunSuite {

  test("ISS-980 extra: fileName of an absolute path is the last component") {
    // JVM parity: scalajvm/ssg/commons/io/FilePathPlatform.scala:31-34 (Path.getFileName).
    // JVM-evaluated: Paths.get("/a/b/c.txt").getFileName == "c.txt".
    assertEquals(FilePath.of("/a/b/c.txt").fileName, Paths.get("/a/b/c.txt").getFileName.toString)
    assertEquals(FilePath.of("/a/b/c.txt").fileName, "c.txt")
  }

  test("ISS-980 extra: fileName of the root / is the empty string") {
    // JVM parity: scalajvm/ssg/commons/io/FilePathPlatform.scala:31-34 — getFileName is null for the root,
    // mapped to "". JVM-evaluated: Paths.get("/").getFileName == null.
    assert(Paths.get("/").getFileName eq null)
    assertEquals(FilePath.of("/").fileName, "")
  }

  test("ISS-980 extra: resolve(FilePath) with a relative child appends, mirroring Path.resolve") {
    // JVM parity: scalajvm/ssg/commons/io/FilePathPlatform.scala:26-29 (Path.resolve).
    // JVM-evaluated: Paths.get("/a/b").resolve(Paths.get("c/d")) == "/a/b/c/d".
    val expected = Paths.get("/a/b").resolve(Paths.get("c/d")).toString
    assertEquals(FilePath.of("/a/b").resolve(FilePath.of("c/d")).pathString, expected)
    assertEquals(FilePath.of("/a/b").resolve(FilePath.of("c/d")).pathString, "/a/b/c/d")
  }

  test("ISS-980 extra: resolve(FilePath) with an absolute child replaces, mirroring Path.resolve") {
    // JVM parity: scalajvm/ssg/commons/io/FilePathPlatform.scala:26-29 (Path.resolve) — resolving an
    // absolute path returns that absolute path. JVM-evaluated: Paths.get("/a/b").resolve(Paths.get("/x/y")) == "/x/y".
    val expected = Paths.get("/a/b").resolve(Paths.get("/x/y")).toString
    assertEquals(FilePath.of("/a/b").resolve(FilePath.of("/x/y")).pathString, expected)
    assertEquals(FilePath.of("/a/b").resolve(FilePath.of("/x/y")).pathString, "/x/y")
  }

  test("ISS-980 extra: of renders the path the way java.nio.file.Path does (redundant separators collapsed)") {
    // JVM parity: scalajvm/ssg/commons/io/FilePathPlatform.scala:54-55 (Paths.get(path).toString).
    // JVM-evaluated: Paths.get("/a//b/").toString == "/a/b".
    assertEquals(FilePath.of("/a//b/").pathString, Paths.get("/a//b/").toString)
    assertEquals(FilePath.of("/a//b/").pathString, "/a/b")
  }
}
