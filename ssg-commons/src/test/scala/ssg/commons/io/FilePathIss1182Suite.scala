/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package commons
package io

/** ISS-1182 [R0610]: cross-platform FilePath normalize contract for the literal "." input.
  *
  * `FilePath.of(".").normalize.pathString` must be the empty string on every platform, but it currently diverges on Scala Native (yields "." instead of ""). This suite lives in src/test/scala/ and
  * runs on ALL THREE platforms (JVM, Scala.js, Scala Native), encoding the uniform contract that holds on JVM/JS and SHOULD hold on Native.
  *
  * Oracle (anti-cheat C11) — the expected value "" is the real java.nio result on the JVM, mirrored by JS and expected of Native:
  *   - JVM: java.nio.file.Paths.get(".").normalize().toString == "".
  *   - JS: ssg-commons/src/main/scalajs/ssg/commons/io/FilePathPlatform.scala:108 maps "." -> "" explicitly (`if (stripped == ".") ""`).
  *   - Native: ssg-commons/src/main/scalanative/ssg/commons/io/FilePathPlatform.scala:65-66 is the divergence site — `new NativeFilePath(underlying.normalize().toString)` leaks "." because Scala
  *     Native's java.nio emulation returns "." for the single-segment dot.
  *
  * The multi-segment guard cases ("a/.." and "./.") already return "" on all three platforms, so the divergence is specific to the literal "." input.
  */
final class FilePathIss1182Suite extends munit.FunSuite {

  test("ISS-1182: normalize of the literal \".\" is the empty string") {
    // JVM-evaluated: Paths.get(".").normalize().toString == "".
    // Passes on JVM and JS; currently fails on Scala Native (yields ".") — the divergence under test.
    assertEquals(FilePath.of(".").normalize.pathString, "")
  }

  test("ISS-1182: normalize of \"a/..\" is the empty string (guard — consistent on all platforms)") {
    // JVM-evaluated: Paths.get("a/..").normalize().toString == "".
    assertEquals(FilePath.of("a/..").normalize.pathString, "")
  }

  test("ISS-1182: normalize of \"./.\" is the empty string (guard — consistent on all platforms)") {
    // JVM-evaluated: Paths.get("./.").normalize().toString == "".
    assertEquals(FilePath.of("./.").normalize.pathString, "")
  }

  test("ISS-1182: normalize of a non-reducing relative path is unchanged (control)") {
    // JVM-evaluated: Paths.get("a/b").normalize().toString == "a/b".
    assertEquals(FilePath.of("a/b").normalize.pathString, "a/b")
  }
}
