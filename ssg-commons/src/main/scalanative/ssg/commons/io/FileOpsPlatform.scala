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

import java.nio.file.{ Files, LinkOption, Path, StandardCopyOption }

private[io] object FileOpsPlatform {

  def readAllBytes(path: FilePath): Array[Byte] =
    Files.readAllBytes(FilePathPlatform.toNioPath(path))

  def writeBytes(path: FilePath, bytes: Array[Byte]): Unit =
    Files.write(FilePathPlatform.toNioPath(path), bytes)

  def exists(path: FilePath): Boolean =
    Files.exists(FilePathPlatform.toNioPath(path))

  def isDirectory(path: FilePath): Boolean =
    Files.isDirectory(FilePathPlatform.toNioPath(path))

  def isRegularFile(path: FilePath): Boolean =
    Files.isRegularFile(FilePathPlatform.toNioPath(path))

  /** Immediate children of a directory, sorted by path string for deterministic output across platforms. Files.newDirectoryStream throws NotDirectoryException / NoSuchFileException for the non-dir /
    * missing cases, which propagate per the documented contract.
    */
  def list(path: FilePath): List[FilePath] = {
    val dir    = FilePathPlatform.toNioPath(path)
    val stream = Files.newDirectoryStream(dir)
    try {
      val builder = List.newBuilder[Path]
      stream.forEach(p => builder += p)
      builder.result().sortBy(_.toString).map(FilePathPlatform.fromNioPath)
    } finally
      stream.close()
  }

  /** Recursive pre-order descent. Each level is listed via [[list]] (already sorted), a directory entry is yielded before its contents, and directory symlinks are not descended into
    * (LinkOption.NOFOLLOW_LINKS keeps the is-directory test on the link itself, never its target).
    */
  def walkTree(path: FilePath): List[FilePath] = {
    val builder = List.newBuilder[FilePath]
    def descend(dir: FilePath): Unit =
      list(dir).foreach { child =>
        builder += child
        val nio = FilePathPlatform.toNioPath(child)
        if (Files.isDirectory(nio, LinkOption.NOFOLLOW_LINKS)) descend(child)
      }
    descend(path)
    builder.result()
  }

  /** Nested, idempotent directory creation (Files.createDirectories returns normally when the path already exists). */
  def createDirectories(path: FilePath): Unit =
    Files.createDirectories(FilePathPlatform.toNioPath(path)): Unit

  /** Byte-exact file copy; REPLACE_EXISTING overwrites a stale destination (static-asset rebuild, per the design). */
  def copy(from: FilePath, to: FilePath): Unit =
    Files.copy(
      FilePathPlatform.toNioPath(from),
      FilePathPlatform.toNioPath(to),
      StandardCopyOption.REPLACE_EXISTING
    ): Unit

  /** Removes a file or an entire tree; a missing path is a no-op. Symlinks are deleted as links — a directory symlink is removed directly (its target is never descended into), giving the clean-build
    * safety property (design Q13).
    *
    * ISS-1347 robustness: if a path vanishes between the exists check and the delete (e.g. a concurrent deletion, or the Native-Windows javalib following a directory symlink into a target that is
    * itself being cleaned up), NoSuchFileException is caught and treated as a no-op — consistent with the "a missing path is a no-op" contract.
    */
  def deleteRecursively(path: FilePath): Unit = {
    val nio = FilePathPlatform.toNioPath(path)
    if (Files.exists(nio, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.isDirectory(nio, LinkOption.NOFOLLOW_LINKS)) {
        list(path).foreach(deleteRecursively)
      }
      try Files.delete(nio)
      catch { case _: java.nio.file.NoSuchFileException => () }
    }
  }

  val isSupported: Boolean = true
}
