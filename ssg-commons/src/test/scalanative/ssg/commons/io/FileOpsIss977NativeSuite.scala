/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package commons
package io

import java.nio.charset.StandardCharsets
import java.nio.file.Files

/** ISS-977: Native FileOps unimplemented.
  *
  * Every operation in ssg-commons/src/main/scalanative/ssg/commons/io/FileOpsPlatform.scala throws unconditionally
  * (lines 15-16), even though java.nio.file.Files works on Scala Native (this suite itself uses
  * Files.createTempDirectory for setup, proving it).
  *
  * Expected semantics are defined by the JVM reference implementation
  * ssg-commons/src/main/scalajvm/ssg/commons/io/FileOpsPlatform.scala, which delegates to java.nio.file.Files. Each
  * test below cites the JVM file:line that backs its expected behavior (anti-cheat C11).
  *
  * Red until ISS-977 is fixed: each test currently fails with the exception thrown by the current Native
  * FileOpsPlatform.
  */
final class FileOpsIss977NativeSuite extends munit.FunSuite {

  /** Creates a fresh temp directory, runs the body, and attempts to clean up the listed files afterwards. */
  private def withTempDir[A](body: java.nio.file.Path => A): A = {
    val dir = Files.createTempDirectory("iss977")
    try {
      body(dir)
    } finally {
      // Attempted cleanup of anything the test created directly under the temp dir.
      val entries = Files.list(dir)
      try {
        entries.forEach(p => Files.deleteIfExists(p): Unit)
      } finally {
        entries.close()
      }
      Files.deleteIfExists(dir): Unit
    }
  }

  test("ISS-977: writeBytes then readAllBytes round-trips raw bytes") {
    // JVM parity: scalajvm/ssg/commons/io/FileOpsPlatform.scala:15-16 (readAllBytes = Files.readAllBytes)
    // and :18-19 (writeBytes = Files.write) — a write followed by a read returns the same bytes.
    withTempDir { dir =>
      val path  = FilePath.of(dir.resolve("bytes.bin").toString)
      val bytes = Array[Byte](0, 1, 2, 127, -128, 42)
      FileOps.writeBytes(path, bytes)
      assertEquals(FileOps.readAllBytes(path).toSeq, bytes.toSeq)
    }
  }

  test("ISS-977: writeString then readString round-trips UTF-8 content") {
    // JVM parity: scalajvm/ssg/commons/io/FileOpsPlatform.scala:15-19 via the facade
    // ssg-commons/src/main/scala/ssg/commons/io/FileOps.scala:29-47 (readString/writeString are
    // UTF-8 wrappers over readAllBytes/writeBytes).
    withTempDir { dir =>
      val path    = FilePath.of(dir.resolve("text.txt").toString)
      val content = "liquid include: zażółć gęślą jaźń\nsecond line"
      FileOps.writeString(path, content)
      assertEquals(FileOps.readString(path), content)
      assertEquals(FileOps.readString(path, StandardCharsets.UTF_8), content)
    }
  }

  test("ISS-977: exists is true for an existing file and false for a missing path") {
    // JVM parity: scalajvm/ssg/commons/io/FileOpsPlatform.scala:21-22 (exists = Files.exists).
    withTempDir { dir =>
      val present = dir.resolve("present.txt")
      Files.write(present, "x".getBytes(StandardCharsets.UTF_8)): Unit
      assert(FileOps.exists(FilePath.of(present.toString)))
      assert(!FileOps.exists(FilePath.of(dir.resolve("missing.txt").toString)))
    }
  }

  test("ISS-977: isDirectory is true for a directory and false for a regular file") {
    // JVM parity: scalajvm/ssg/commons/io/FileOpsPlatform.scala:24-25 (isDirectory = Files.isDirectory).
    withTempDir { dir =>
      val file = dir.resolve("file.txt")
      Files.write(file, "x".getBytes(StandardCharsets.UTF_8)): Unit
      assert(FileOps.isDirectory(FilePath.of(dir.toString)))
      assert(!FileOps.isDirectory(FilePath.of(file.toString)))
    }
  }

  test("ISS-977: isRegularFile is true for a regular file and false for a directory") {
    // JVM parity: scalajvm/ssg/commons/io/FileOpsPlatform.scala:27-28 (isRegularFile = Files.isRegularFile).
    withTempDir { dir =>
      val file = dir.resolve("file.txt")
      Files.write(file, "x".getBytes(StandardCharsets.UTF_8)): Unit
      assert(FileOps.isRegularFile(FilePath.of(file.toString)))
      assert(!FileOps.isRegularFile(FilePath.of(dir.toString)))
    }
  }

  test("ISS-977: isSupported reports true once Native file ops are implemented") {
    // JVM parity: scalajvm/ssg/commons/io/FileOpsPlatform.scala:30 (val isSupported = true).
    // The ISS-977 done-condition is a full java.nio.file-based Native impl, so Native must report true too.
    assert(FileOps.isSupported, "FileOps.isSupported must be true on Scala Native after ISS-977 is fixed")
  }
}
