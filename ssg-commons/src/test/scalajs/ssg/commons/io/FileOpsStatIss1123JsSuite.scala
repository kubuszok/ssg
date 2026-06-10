/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package commons
package io

import scala.scalajs.js

/** ISS-1123 [R0610-P0]: Scala.js isDirectory/isRegularFile single-stat, JVM-parity false on unreadable attrs.
  *
  * The JS implementation queries the type of an entry with a single statSync (no existsSync pre-check), so there is
  * no window in which the entry can be removed between two calls. When the entry is absent or its attributes cannot
  * be read, the answer is false — the JVM-parity semantic of java.nio.file.Files.isDirectory/isRegularFile, which
  * "return false if the file does not exist or it cannot be determined whether the file is a directory [/regular
  * file]" (ssg-commons/src/main/scalajvm/ssg/commons/io/FileOpsPlatform.scala:24-28).
  *
  * The two missing-path tests exercise the catch path: the single statSync fails because the entry is absent and the
  * result must be false. Mutating the catch to re-raise instead of returning false turns these two red (anti-cheat
  * C8). The remaining two are wrong-type sanity checks matching the JVM reference.
  */
final class FileOpsStatIss1123JsSuite extends munit.FunSuite {

  /** Node built-in modules — used only for test setup/teardown, never for the behavior under test. */
  private val nodeOs:   js.Dynamic = js.Dynamic.global.require("os")
  private val nodeFs:   js.Dynamic = js.Dynamic.global.require("fs")
  private val nodePath: js.Dynamic = js.Dynamic.global.require("path")

  /** Fresh temp directory for the current test, created in beforeEach via Node (independent of FileOps). */
  private var tempDir: String = ""

  override def beforeEach(context: BeforeEach): Unit = {
    val prefix = nodePath.join(nodeOs.tmpdir(), "iss1123-").asInstanceOf[String]
    tempDir = nodeFs.mkdtempSync(prefix).asInstanceOf[String]
  }

  override def afterEach(context: AfterEach): Unit = {
    // Recursive cleanup with Node fs; force=true tolerates entries a test never managed to create.
    nodeFs.rmSync(tempDir, js.Dynamic.literal(recursive = true, force = true)): Unit
  }

  /** Joins a child name onto the current temp directory using Node's path module. */
  private def child(name: String): String =
    nodePath.join(tempDir, name).asInstanceOf[String]

  test("ISS-1123: isDirectory on a missing path is false, not a raised error") {
    // JVM parity: scalajvm/ssg/commons/io/FileOpsPlatform.scala:24-25 — Files.isDirectory returns false when the
    // file does not exist. The single statSync fails for the absent entry; the catch path must yield false.
    assert(!FileOps.isDirectory(FilePath.of(child("absent-dir"))))
  }

  test("ISS-1123: isRegularFile on a missing path is false, not a raised error") {
    // JVM parity: scalajvm/ssg/commons/io/FileOpsPlatform.scala:27-28 — Files.isRegularFile returns false when the
    // file does not exist. The single statSync fails for the absent entry; the catch path must yield false.
    assert(!FileOps.isRegularFile(FilePath.of(child("absent-file.txt"))))
  }

  test("ISS-1123: isDirectory on a regular file is false") {
    // JVM parity: scalajvm/ssg/commons/io/FileOpsPlatform.scala:24-25 — a regular file is not a directory.
    val file = child("file.txt")
    nodeFs.writeFileSync(file, "x"): Unit
    assert(!FileOps.isDirectory(FilePath.of(file)))
  }

  test("ISS-1123: isRegularFile on a directory is false") {
    // JVM parity: scalajvm/ssg/commons/io/FileOpsPlatform.scala:27-28 — a directory is not a regular file.
    assert(!FileOps.isRegularFile(FilePath.of(tempDir)))
  }
}
