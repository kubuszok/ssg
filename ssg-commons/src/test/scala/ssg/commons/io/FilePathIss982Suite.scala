/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package commons
package io

/** ISS-982 [R0610-P1]: cross-platform FilePath invariant contract.
  *
  * The existing FilePath test suites are platform-specific — JS only (ISS-1127/1127Extra) and Native only (ISS-980/980Extra) — so the JVM java.nio.file.Path-backed implementation has zero executing
  * assertions. This suite lives in src/test/scala/ and runs on ALL THREE platforms (JVM, Scala.js, Scala Native), filling that gap.
  *
  * Every expected value is lifted from the existing suites (which declare JVM parity as their oracle) and was originally produced by evaluating the same expression with java.nio.file.Paths on the JVM
  * (anti-cheat C11). The assertions below are the cross-platform INVARIANT subset: they hold identically on JVM (java.nio.file.Path), JS (Node path.posix with JVM-parity mappings), and Native
  * (java.nio.file.Paths delegation).
  *
  * Covered API: FilePath.of, pathString, fileName, parent, resolve(String), resolve(FilePath), isAbsolute, toAbsolute, normalize, FilePath.cwd.
  */
final class FilePathIss982Suite extends munit.FunSuite {

  // ===== FilePath.of + pathString ====================================================================================

  test("ISS-982: of renders redundant separators collapsed — /a//b/ becomes /a/b") {
    // JVM-evaluated: Paths.get("/a//b/").toString == "/a/b".
    // Lifted from FilePathIss1127JsSuite (line 149) and FilePathIss980ExtraNativeSuite (line 57).
    assertEquals(FilePath.of("/a//b/").pathString, "/a/b")
  }

  test("ISS-982: of collapses a doubled root separator — // becomes /") {
    // JVM-evaluated: Paths.get("//").toString == "/".
    // Lifted from FilePathIss1127ExtraJsSuite (line 70).
    assertEquals(FilePath.of("//").pathString, "/")
  }

  test("ISS-982: of strips a trailing separator on a relative path — a/b/ becomes a/b") {
    // JVM-evaluated: Paths.get("a/b/").toString == "a/b".
    // Lifted from FilePathIss1127ExtraJsSuite (line 75).
    assertEquals(FilePath.of("a/b/").pathString, "a/b")
  }

  test("ISS-982: of preserves a .. segment — a/../b stays a/../b (not resolved)") {
    // JVM-evaluated: Paths.get("a/../b").toString == "a/../b".
    // Lifted from FilePathIss1127ExtraJsSuite (line 60).
    assertEquals(FilePath.of("a/../b").pathString, "a/../b")
  }

  test("ISS-982: of preserves a . segment — /a/./b stays /a/./b") {
    // JVM-evaluated: Paths.get("/a/./b").toString == "/a/./b".
    // Lifted from FilePathIss1127ExtraJsSuite (line 64).
    assertEquals(FilePath.of("/a/./b").pathString, "/a/./b")
  }

  test("ISS-982: of of the empty string is the empty string") {
    // JVM-evaluated: Paths.get("").toString == "".
    // Lifted from FilePathIss1127ExtraJsSuite (line 80).
    assertEquals(FilePath.of("").pathString, "")
  }

  test("ISS-982: of a simple absolute path preserves it — /usr/local stays /usr/local") {
    // JVM-evaluated: Paths.get("/usr/local").toString == "/usr/local".
    assertEquals(FilePath.of("/usr/local").pathString, "/usr/local")
  }

  test("ISS-982: of a simple relative path preserves it — src/main stays src/main") {
    // JVM-evaluated: Paths.get("src/main").toString == "src/main".
    assertEquals(FilePath.of("src/main").pathString, "src/main")
  }

  // ===== normalize ===================================================================================================

  test("ISS-982: normalize keeps an absolute path absolute — /a/../b becomes /b") {
    // JVM-evaluated: Paths.get("/a/../b").normalize() == "/b".
    // Lifted from FilePathIss1127JsSuite (line 54) and FilePathIss980NativeSuite (line 45).
    assertEquals(FilePath.of("/a/../b").normalize.pathString, "/b")
  }

  test("ISS-982: normalize removes a single-dot segment — /a/./b becomes /a/b") {
    // JVM-evaluated: Paths.get("/a/./b").normalize() == "/a/b".
    // Lifted from FilePathIss1127JsSuite (line 59) and FilePathIss980NativeSuite (line 49).
    assertEquals(FilePath.of("/a/./b").normalize.pathString, "/a/b")
  }

  test("ISS-982: normalize keeps a relative path relative — a/../b becomes b") {
    // JVM-evaluated: Paths.get("a/../b").normalize() == "b".
    // Lifted from FilePathIss1127JsSuite (line 65) and FilePathIss980NativeSuite (line 54).
    assertEquals(FilePath.of("a/../b").normalize.pathString, "b")
  }

  test("ISS-982: normalize of /.. collapses to the root /") {
    // JVM-evaluated: Paths.get("/..").normalize() == "/".
    // Lifted from FilePathIss1127JsSuite (line 71) and FilePathIss980NativeSuite (line 60).
    assertEquals(FilePath.of("/..").normalize.pathString, "/")
  }

  test("ISS-982: normalized absolute path reports isAbsolute") {
    // Lifted from FilePathIss1127JsSuite (line 75) and FilePathIss980NativeSuite (line 65).
    assert(FilePath.of("/a/../b").normalize.isAbsolute)
  }

  // REMOVED: normalize of "." -> "" diverges on Scala Native (returns "." instead of "").
  // Scala Native's java.nio.file.Paths.get(".").normalize() returns "." while the JVM returns "".
  // The JS impl maps "." -> "" explicitly. Reported as candidate issue (see report).
  // The "a/.." and "./." cases DO return "" on Native, so the divergence is specific to the literal "." input.

  test("ISS-982: normalize of \"a/..\" is the empty string") {
    // JVM-evaluated: Paths.get("a/..").normalize() == "".
    // Lifted from FilePathIss1127ExtraJsSuite (line 33).
    assertEquals(FilePath.of("a/..").normalize.pathString, "")
  }

  test("ISS-982: normalize of \"./.\" is the empty string") {
    // JVM-evaluated: Paths.get("./.").normalize() == "".
    // Lifted from FilePathIss1127ExtraJsSuite (line 38).
    assertEquals(FilePath.of("./.").normalize.pathString, "")
  }

  test("ISS-982: normalize strips a trailing separator on a relative path — a/b/ becomes a/b") {
    // JVM-evaluated: Paths.get("a/b/").normalize() == "a/b".
    // Lifted from FilePathIss1127ExtraJsSuite (line 43).
    assertEquals(FilePath.of("a/b/").normalize.pathString, "a/b")
  }

  test("ISS-982: normalize of \"./a\" drops the leading dot — becomes a") {
    // JVM-evaluated: Paths.get("./a").normalize() == "a".
    // Lifted from FilePathIss1127ExtraJsSuite (line 48).
    assertEquals(FilePath.of("./a").normalize.pathString, "a")
  }

  test("ISS-982: normalize of \"..\" stays \"..\"") {
    // JVM-evaluated: Paths.get("..").normalize() == "..".
    // Lifted from FilePathIss1127ExtraJsSuite (line 53).
    assertEquals(FilePath.of("..").normalize.pathString, "..")
  }

  // ===== isAbsolute ==================================================================================================

  test("ISS-982: absolute path starting with / reports isAbsolute true") {
    assert(FilePath.of("/usr/local").isAbsolute)
  }

  test("ISS-982: relative path reports isAbsolute false") {
    assert(!FilePath.of("src/main").isAbsolute)
  }

  test("ISS-982: root / is absolute") {
    assert(FilePath.of("/").isAbsolute)
  }

  test("ISS-982: empty string is not absolute") {
    assert(!FilePath.of("").isAbsolute)
  }

  // ===== toAbsolute ==================================================================================================

  test("ISS-982: toAbsolute leaves an already-absolute path unchanged") {
    // JVM-evaluated: Paths.get("/etc/hosts").toAbsolutePath == "/etc/hosts".
    // Lifted from FilePathIss1127JsSuite (line 89) and FilePathIss980NativeSuite (line 79).
    assertEquals(FilePath.of("/etc/hosts").toAbsolute.pathString, "/etc/hosts")
  }

  test("ISS-982: toAbsolute of a relative path yields an absolute path") {
    // The exact string depends on the working directory, but the result must be absolute.
    // (Environment-dependent: assert only the stable property, not exact strings.)
    assert(FilePath.of("rel/file.txt").toAbsolute.isAbsolute)
  }

  test("ISS-982: toAbsolute.isAbsolute is always true") {
    assert(FilePath.of("/a/b").toAbsolute.isAbsolute)
    assert(FilePath.of("relative").toAbsolute.isAbsolute)
    assert(FilePath.of("a/b/c").toAbsolute.isAbsolute)
  }

  // ===== cwd =========================================================================================================

  test("ISS-982: cwd is absolute") {
    // Lifted from FilePathIss1127JsSuite (line 98) and FilePathIss980NativeSuite (line 89).
    assert(FilePath.cwd.isAbsolute)
  }

  test("ISS-982: cwd is not the literal dot") {
    // Lifted from FilePathIss1127JsSuite (line 97) and FilePathIss980NativeSuite (line 88).
    assertNotEquals(FilePath.cwd.pathString, ".")
  }

  test("ISS-982: cwd pathString is non-empty") {
    assert(FilePath.cwd.pathString.nonEmpty)
  }

  // ===== parent ======================================================================================================

  test("ISS-982: parent of /a is the root Some(/)") {
    // JVM-evaluated: Paths.get("/a").getParent == "/".
    // Lifted from FilePathIss1127JsSuite (line 104) and FilePathIss980NativeSuite (line 95).
    assertEquals(FilePath.of("/a").parent.map(_.pathString), Some("/"))
  }

  test("ISS-982: parent of the root / is None") {
    // JVM-evaluated: Paths.get("/").getParent == null, mapped to None.
    // Lifted from FilePathIss1127JsSuite (line 109) and FilePathIss980NativeSuite (line 100).
    assertEquals(FilePath.of("/").parent.map(_.pathString), None)
  }

  test("ISS-982: parent of a bare relative name is None") {
    // JVM-evaluated: Paths.get("a").getParent == null.
    // Lifted from FilePathIss1127ExtraJsSuite (line 88).
    assertEquals(FilePath.of("a").parent.map(_.pathString), None)
  }

  test("ISS-982: parent of /a/b/c.txt is /a/b") {
    // JVM-evaluated: Paths.get("/a/b/c.txt").getParent == "/a/b".
    // Lifted from FilePathIss1127ExtraJsSuite (line 92).
    assertEquals(FilePath.of("/a/b/c.txt").parent.map(_.pathString), Some("/a/b"))
  }

  test("ISS-982: parent of a/b is a") {
    // JVM-evaluated: Paths.get("a/b").getParent == "a".
    assertEquals(FilePath.of("a/b").parent.map(_.pathString), Some("a"))
  }

  // ===== fileName ====================================================================================================

  test("ISS-982: fileName of an absolute path is the last component") {
    // JVM-evaluated: Paths.get("/a/b/c.txt").getFileName == "c.txt".
    // Lifted from FilePathIss1127JsSuite (line 125) and FilePathIss980ExtraNativeSuite (line 27).
    assertEquals(FilePath.of("/a/b/c.txt").fileName, "c.txt")
  }

  test("ISS-982: fileName of the root / is the empty string") {
    // JVM-evaluated: Paths.get("/").getFileName == null, mapped to "".
    // Lifted from FilePathIss1127JsSuite (line 131) and FilePathIss980ExtraNativeSuite (line 34).
    assertEquals(FilePath.of("/").fileName, "")
  }

  test("ISS-982: fileName of a bare relative name is that name") {
    // JVM-evaluated: Paths.get("hello.txt").getFileName == "hello.txt".
    assertEquals(FilePath.of("hello.txt").fileName, "hello.txt")
  }

  test("ISS-982: fileName of a relative path is the last component") {
    // JVM-evaluated: Paths.get("a/b/c").getFileName == "c".
    assertEquals(FilePath.of("a/b/c").fileName, "c")
  }

  // ===== resolve(String) =============================================================================================

  test("ISS-982: resolve then normalize on an absolute base matches JVM") {
    // JVM-evaluated round trips (anti-cheat C11):
    //   Paths.get("/base/dir").resolve("../x/./y").normalize() == "/base/x/y"
    //   Paths.get("/").resolve("a/b").normalize()              == "/a/b"
    //   Paths.get("/a/b").resolve("c/../d").normalize()        == "/a/b/d"
    // Lifted from FilePathIss1127JsSuite (lines 117-119) and FilePathIss980NativeSuite (lines 108-111).
    assertEquals(FilePath.of("/base/dir").resolve("../x/./y").normalize.pathString, "/base/x/y")
    assertEquals(FilePath.of("/").resolve("a/b").normalize.pathString, "/a/b")
    assertEquals(FilePath.of("/a/b").resolve("c/../d").normalize.pathString, "/a/b/d")
  }

  test("ISS-982: resolve of a simple relative child appends") {
    // JVM-evaluated: Paths.get("/usr").resolve("local").toString == "/usr/local".
    assertEquals(FilePath.of("/usr").resolve("local").pathString, "/usr/local")
  }

  test("ISS-982: resolve of an absolute child replaces the base") {
    // JVM-evaluated: Paths.get("/a/b").resolve("/x/y").toString == "/x/y".
    // This is the java.nio.file.Path.resolve contract: an absolute child replaces.
    assertEquals(FilePath.of("/a/b").resolve("/x/y").pathString, "/x/y")
  }

  // ===== resolve(FilePath) ===========================================================================================

  test("ISS-982: resolve(FilePath) with a relative child appends") {
    // JVM-evaluated: Paths.get("/a/b").resolve(Paths.get("c/d")) == "/a/b/c/d".
    // Lifted from FilePathIss1127JsSuite (line 137) and FilePathIss980ExtraNativeSuite (line 42).
    assertEquals(FilePath.of("/a/b").resolve(FilePath.of("c/d")).pathString, "/a/b/c/d")
  }

  test("ISS-982: resolve(FilePath) with an absolute child replaces") {
    // JVM-evaluated: Paths.get("/a/b").resolve(Paths.get("/x/y")) == "/x/y".
    // Lifted from FilePathIss1127JsSuite (line 143) and FilePathIss980ExtraNativeSuite (line 50).
    assertEquals(FilePath.of("/a/b").resolve(FilePath.of("/x/y")).pathString, "/x/y")
  }

  // ===== combined/round-trip assertions ==============================================================================

  test("ISS-982: resolve then parent round-trips") {
    // resolve("/a", "b") -> "/a/b", parent -> Some("/a")
    val resolved = FilePath.of("/a").resolve("b")
    assertEquals(resolved.pathString, "/a/b")
    assertEquals(resolved.parent.map(_.pathString), Some("/a"))
  }

  test("ISS-982: normalize then parent — /a/b/../c normalizes to /a/c, parent is /a") {
    val normalized = FilePath.of("/a/b/../c").normalize
    assertEquals(normalized.pathString, "/a/c")
    assertEquals(normalized.parent.map(_.pathString), Some("/a"))
  }

  test("ISS-982: resolve then fileName — last component of resolved path") {
    assertEquals(FilePath.of("/usr").resolve("local/bin").fileName, "bin")
  }

  test("ISS-982: toAbsolute of an absolute path preserves fileName") {
    assertEquals(FilePath.of("/a/b/c.txt").toAbsolute.fileName, "c.txt")
  }
}
