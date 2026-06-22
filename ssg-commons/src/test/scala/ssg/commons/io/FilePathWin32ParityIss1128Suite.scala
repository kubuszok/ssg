/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package commons
package io

/** ISS-1128 [R0610]: cross-platform FilePath parity for colon and backslash inputs.
  *
  * The JS FilePathPlatform had Win32-flavored heuristics that diverge from posix JVM and Native:
  *   - isAbsolute and resolve treated a second-char ':' as a drive-letter (absolute), but on posix JVM/Native, ':' is a legal filename char: `Paths.get("a:b").isAbsolute == false`.
  *   - of/renderPath converted backslash to '/', but on posix JVM/Native, backslash is a legal filename char: `Paths.get("a\\b").toString == "a\\b"` (preserved, not a separator).
  *
  * This suite runs on ALL THREE platforms (JVM, JS, Native) and asserts the SAME posix result everywhere. Every expected value is the java.nio.file.Paths result on posix JVM (anti-cheat C11).
  */
final class FilePathWin32ParityIss1128Suite extends munit.FunSuite {

  // ===== colon inputs: ':' is a legal filename char on posix, not a drive-letter indicator ======

  test("ISS-1128: of(\"a:b\").isAbsolute is false — colon is not a drive-letter on posix") {
    // JVM-evaluated (posix): Paths.get("a:b").isAbsolute == false.
    // Was true on JS before ISS-1128 fix (Win32 drive-letter heuristic).
    assert(!FilePath.of("a:b").isAbsolute)
  }

  test("ISS-1128: of(\"/x\").isAbsolute is true — control for slash-prefixed absolute") {
    // JVM-evaluated: Paths.get("/x").isAbsolute == true.
    assert(FilePath.of("/x").isAbsolute)
  }

  test("ISS-1128: resolve treats a:b as relative child, not absolute replacement") {
    // JVM-evaluated (posix): Paths.get("/x").resolve("a:b").toString == "/x/a:b".
    // On posix, "a:b" is relative (no leading '/'), so resolve appends it.
    // Was treated as absolute on JS before ISS-1128 fix (Win32 drive-letter heuristic).
    assertEquals(FilePath.of("/x").resolve("a:b").pathString, "/x/a:b")
  }

  test("ISS-1128: of(\"a:b\").pathString preserves the colon") {
    // JVM-evaluated: Paths.get("a:b").toString == "a:b".
    assertEquals(FilePath.of("a:b").pathString, "a:b")
  }

  // ===== backslash inputs: '\' is a legal filename char on posix, not a separator ================

  test("ISS-1128: of(\"a\\\\b\").pathString preserves the backslash — not converted to /") {
    // JVM-evaluated (posix): Paths.get("a\\b").toString == "a\\b".
    // The string literal "a\\b" is a 3-char string: 'a', '\\', 'b'.
    // Was "a/b" on JS before ISS-1128 fix (backslash-to-slash conversion in renderPath).
    assertEquals(FilePath.of("a\\b").pathString, "a\\b")
  }

  test("ISS-1128: of(\"a\\\\b\").isAbsolute is false — backslash does not make a path absolute") {
    // JVM-evaluated (posix): Paths.get("a\\b").isAbsolute == false.
    assert(!FilePath.of("a\\b").isAbsolute)
  }

  // ===== control cases: normal posix paths are unaffected ========================================

  test("ISS-1128: of(\"/a/b\").pathString is unchanged — control") {
    // JVM-evaluated: Paths.get("/a/b").toString == "/a/b".
    assertEquals(FilePath.of("/a/b").pathString, "/a/b")
  }

  test("ISS-1128: of(\"a/b\").pathString is unchanged — control") {
    // JVM-evaluated: Paths.get("a/b").toString == "a/b".
    assertEquals(FilePath.of("a/b").pathString, "a/b")
  }

  test("ISS-1128: of(\"a/../b\").pathString preserves .. — control") {
    // JVM-evaluated: Paths.get("a/../b").toString == "a/../b".
    assertEquals(FilePath.of("a/../b").pathString, "a/../b")
  }

  test("ISS-1128: of(\"/a/b\").isAbsolute is true — control") {
    assert(FilePath.of("/a/b").isAbsolute)
  }

  test("ISS-1128: of(\"a/b\").isAbsolute is false — control") {
    assert(!FilePath.of("a/b").isAbsolute)
  }
}
