/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native implementation of file operations using java.nio.file.Files.
 * java.nio.file (Files/Paths) is supported on Scala Native, so these operations
 * mirror the JVM implementation operation-for-operation.
 */
package ssg
package commons
package io

import java.nio.file.{ Files, LinkOption, Path, Paths, StandardCopyOption }

private[io] object FileOpsPlatform {

  /** Native FilePath is string-based, so route every operation through java.nio.file.Paths, matching the JVM FilePathPlatform.toNioPath fallback (`Paths.get(fp.pathString)`).
    */
  private def toNioPath(path: FilePath): java.nio.file.Path =
    Paths.get(path.pathString)

  def readAllBytes(path: FilePath): Array[Byte] =
    Files.readAllBytes(toNioPath(path))

  def writeBytes(path: FilePath, bytes: Array[Byte]): Unit =
    Files.write(toNioPath(path), bytes)

  def exists(path: FilePath): Boolean =
    Files.exists(toNioPath(path))

  def isDirectory(path: FilePath): Boolean =
    Files.isDirectory(toNioPath(path))

  def isRegularFile(path: FilePath): Boolean =
    Files.isRegularFile(toNioPath(path))

  /** Immediate children, sorted by path string for deterministic output. Files.newDirectoryStream signals the missing / non-directory cases the same way the JVM reference does, so those propagate per
    * the contract.
    */
  def list(path: FilePath): List[FilePath] = {
    val dir    = toNioPath(path)
    val stream = Files.newDirectoryStream(dir)
    try {
      val builder = List.newBuilder[Path]
      stream.forEach(p => builder += p)
      builder.result().sortBy(_.toString).map(p => FilePath.of(p.toString))
    } finally
      stream.close()
  }

  /** Recursive pre-order descent mirroring the JVM reference: each level via [[list]] (sorted), directory before its contents, directory symlinks not descended into (NOFOLLOW_LINKS tests the link,
    * not its target).
    */
  def walkTree(path: FilePath): List[FilePath] = {
    val builder = List.newBuilder[FilePath]
    def descend(dir: FilePath): Unit =
      list(dir).foreach { child =>
        builder += child
        val nio = toNioPath(child)
        if (Files.isDirectory(nio, LinkOption.NOFOLLOW_LINKS)) descend(child)
      }
    descend(path)
    builder.result()
  }

  /** Nested, idempotent directory creation (Files.createDirectories returns normally when the path already exists). */
  def createDirectories(path: FilePath): Unit =
    Files.createDirectories(toNioPath(path)): Unit

  /** Byte-exact file copy; REPLACE_EXISTING overwrites a stale destination (static-asset rebuild, per the design). */
  def copy(from: FilePath, to: FilePath): Unit =
    Files.copy(toNioPath(from), toNioPath(to), StandardCopyOption.REPLACE_EXISTING): Unit

  /** Removes a file or an entire tree; a missing path is a no-op. Symlinks are deleted as links — a directory symlink is removed directly (its target is never descended into), giving the clean-build
    * safety property (design Q13).
    */
  def deleteRecursively(path: FilePath): Unit = {
    val nio = toNioPath(path)
    if (Files.exists(nio, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.isDirectory(nio, LinkOption.NOFOLLOW_LINKS)) {
        list(path).foreach(deleteRecursively)
      }
      Files.delete(nio)
    }
  }

  val isSupported: Boolean = true
}
