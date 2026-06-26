/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package commons
package io

/** ISS-980 (extra): Native FilePathPlatform operations beyond the red suite's coverage.
  *
  * After ISS-1346 the Native impl uses OS-independent POSIX string logic (not raw nio delegation), so path operations are no longer sensitive to the host OS's path conventions. The expected values
  * below are POSIX literals matching the JVM-evaluated result of the equivalent java.nio.file.Path expression (anti-cheat C11). The raw Paths.get(...) comparisons that were originally present have
  * been removed because they assert host-OS nio parity, which is intentionally NOT the model after the POSIX-string rewrite: on Windows, nio returns backslash-separated paths while the OS-independent
  * model uses forward slashes.
  */
final class FilePathIss980ExtraNativeSuite extends munit.FunSuite {

  test("ISS-980 extra: fileName of an absolute path is the last component") {
    // JVM parity: scalajvm/ssg/commons/io/FilePathPlatform.scala:31-34 (Path.getFileName).
    // JVM-evaluated: Paths.get("/a/b/c.txt").getFileName == "c.txt".
    assertEquals(FilePath.of("/a/b/c.txt").fileName, "c.txt")
  }

  test("ISS-980 extra: fileName of the root / is the empty string") {
    // JVM parity: scalajvm/ssg/commons/io/FilePathPlatform.scala:31-34 — getFileName is null for the root,
    // mapped to "". JVM-evaluated: Paths.get("/").getFileName == null.
    assertEquals(FilePath.of("/").fileName, "")
  }

  test("ISS-980 extra: resolve(FilePath) with a relative child appends, mirroring Path.resolve") {
    // JVM parity: scalajvm/ssg/commons/io/FilePathPlatform.scala:26-29 (Path.resolve).
    // JVM-evaluated: Paths.get("/a/b").resolve(Paths.get("c/d")) == "/a/b/c/d".
    assertEquals(FilePath.of("/a/b").resolve(FilePath.of("c/d")).pathString, "/a/b/c/d")
  }

  test("ISS-980 extra: resolve(FilePath) with an absolute child replaces, mirroring Path.resolve") {
    // JVM parity: scalajvm/ssg/commons/io/FilePathPlatform.scala:26-29 (Path.resolve) — resolving an
    // absolute path returns that absolute path. JVM-evaluated: Paths.get("/a/b").resolve(Paths.get("/x/y")) == "/x/y".
    assertEquals(FilePath.of("/a/b").resolve(FilePath.of("/x/y")).pathString, "/x/y")
  }

  test("ISS-980 extra: of renders the path the way java.nio.file.Path does (redundant separators collapsed)") {
    // JVM parity: scalajvm/ssg/commons/io/FilePathPlatform.scala:54-55 (Paths.get(path).toString).
    // JVM-evaluated: Paths.get("/a//b/").toString == "/a/b".
    assertEquals(FilePath.of("/a//b/").pathString, "/a/b")
  }
}
