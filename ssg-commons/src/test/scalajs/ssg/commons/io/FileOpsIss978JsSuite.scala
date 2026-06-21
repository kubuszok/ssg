/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package commons
package io

import java.nio.charset.StandardCharsets

import scala.scalajs.js

/** ISS-978 [R0610-P0]: Scala.js FileOps unimplemented.
  *
  * Every operation in ssg-commons/src/main/scalajs/ssg/commons/io/FileOpsPlatform.scala throws unconditionally (line 15), even though the test suites run under Node (sbt `Test / jsEnv` is a
  * NodeJSEnv), where the `fs` module provides full file system access — this suite itself uses Node's os/fs/path modules for setup and teardown, proving it.
  *
  * Expected semantics are defined by the JVM reference implementation ssg-commons/src/main/scalajvm/ssg/commons/io/FileOpsPlatform.scala, which delegates to java.nio.file.Files. Each test below cites
  * the JVM file:line that backs its expected behavior (anti-cheat C11).
  *
  * Red until ISS-978 is fixed: each test currently fails with the exception thrown by the current JS FileOpsPlatform, except the isSupported test, which fails by assertion because the current JS
  * FileOpsPlatform hardcodes `false` (line 32).
  */
final class FileOpsIss978JsSuite extends munit.FunSuite {

  /** Node built-in modules — used only for test setup/teardown, never for the behavior under test. */
  private val nodeOs:   js.Dynamic = js.Dynamic.global.require("os")
  private val nodeFs:   js.Dynamic = js.Dynamic.global.require("fs")
  private val nodePath: js.Dynamic = js.Dynamic.global.require("path")

  /** Fresh temp directory for the current test, created in beforeEach via Node (independent of FileOps). */
  private var tempDir: String = ""

  override def beforeEach(context: BeforeEach): Unit = {
    val prefix = nodePath.join(nodeOs.tmpdir(), "iss978-").asInstanceOf[String]
    tempDir = nodeFs.mkdtempSync(prefix).asInstanceOf[String]
  }

  override def afterEach(context: AfterEach): Unit =
    // Recursive cleanup with Node fs; force=true tolerates files a red run never managed to create.
    nodeFs.rmSync(tempDir, js.Dynamic.literal(recursive = true, force = true)): Unit

  /** Joins a child name onto the current temp directory using Node's path module. */
  private def child(name: String): String =
    nodePath.join(tempDir, name).asInstanceOf[String]

  test("ISS-978: writeBytes then readAllBytes round-trips raw bytes") {
    // JVM parity: scalajvm/ssg/commons/io/FileOpsPlatform.scala:15-16 (readAllBytes = Files.readAllBytes)
    // and :18-19 (writeBytes = Files.write) — a write followed by a read returns the same bytes.
    val path  = FilePath.of(child("bytes.bin"))
    val bytes = Array[Byte](0, 1, 2, 127, -128, 42)
    FileOps.writeBytes(path, bytes)
    assertEquals(FileOps.readAllBytes(path).toSeq, bytes.toSeq)
  }

  test("ISS-978: writeString then readString round-trips UTF-8 content") {
    // JVM parity: scalajvm/ssg/commons/io/FileOpsPlatform.scala:15-19 via the facade
    // ssg-commons/src/main/scala/ssg/commons/io/FileOps.scala:29-47 (readString/writeString are
    // UTF-8 wrappers over readAllBytes/writeBytes).
    val path    = FilePath.of(child("text.txt"))
    val content = "liquid include: zażółć gęślą jaźń\nsecond line"
    FileOps.writeString(path, content)
    assertEquals(FileOps.readString(path), content)
    assertEquals(FileOps.readString(path, StandardCharsets.UTF_8), content)
  }

  test("ISS-978: exists is true for an existing file and false for a missing path") {
    // JVM parity: scalajvm/ssg/commons/io/FileOpsPlatform.scala:21-22 (exists = Files.exists).
    val present = child("present.txt")
    nodeFs.writeFileSync(present, "x"): Unit
    assert(FileOps.exists(FilePath.of(present)))
    assert(!FileOps.exists(FilePath.of(child("missing.txt"))))
  }

  test("ISS-978: isDirectory is true for a directory and false for a regular file") {
    // JVM parity: scalajvm/ssg/commons/io/FileOpsPlatform.scala:24-25 (isDirectory = Files.isDirectory).
    val file = child("file.txt")
    nodeFs.writeFileSync(file, "x"): Unit
    assert(FileOps.isDirectory(FilePath.of(tempDir)))
    assert(!FileOps.isDirectory(FilePath.of(file)))
  }

  test("ISS-978: isRegularFile is true for a regular file and false for a directory") {
    // JVM parity: scalajvm/ssg/commons/io/FileOpsPlatform.scala:27-28 (isRegularFile = Files.isRegularFile).
    val file = child("file.txt")
    nodeFs.writeFileSync(file, "x"): Unit
    assert(FileOps.isRegularFile(FilePath.of(file)))
    assert(!FileOps.isRegularFile(FilePath.of(tempDir)))
  }

  test("ISS-978: isSupported reports true once JS file ops are implemented") {
    // JVM parity: scalajvm/ssg/commons/io/FileOpsPlatform.scala:30 (val isSupported = true).
    // The ISS-978 done-condition is a Node fs-based JS impl, so JS under Node must report true too.
    assert(FileOps.isSupported, "FileOps.isSupported must be true on Scala.js under Node after ISS-978 is fixed")
  }
}
