/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Cross-platform file operations.
 *
 * On JVM: delegates to java.nio.file.Files
 * On JS/Native: throws UnsupportedOperationException (no file system)
 *
 * Covenant: full-port
 * Covenant-verified: 2026-04-26
 */
package ssg
package commons
package io

import java.nio.charset.{ Charset, StandardCharsets }

/** Cross-platform file operations.
  *
  * File I/O is only available on JVM. JS and Native implementations throw UnsupportedOperationException.
  */
object FileOps {

  /** Reads all bytes from a file. JVM-only. */
  def readAllBytes(path: FilePath): Array[Byte] =
    FileOpsPlatform.readAllBytes(path)

  /** Reads a file as a string using UTF-8. JVM-only. */
  def readString(path: FilePath): String =
    readString(path, StandardCharsets.UTF_8)

  /** Reads a file as a string. JVM-only. */
  def readString(path: FilePath, charset: Charset): String =
    new String(readAllBytes(path), charset)

  /** Writes bytes to a file. JVM-only. */
  def writeBytes(path: FilePath, bytes: Array[Byte]): Unit =
    FileOpsPlatform.writeBytes(path, bytes)

  /** Writes a string to a file using UTF-8. JVM-only. */
  def writeString(path: FilePath, content: String): Unit =
    writeString(path, content, StandardCharsets.UTF_8)

  /** Writes a string to a file. JVM-only. */
  def writeString(path: FilePath, content: String, charset: Charset): Unit =
    writeBytes(path, content.getBytes(charset))

  /** Checks if a file exists. JVM-only. */
  def exists(path: FilePath): Boolean =
    FileOpsPlatform.exists(path)

  /** Checks if the path is a directory. JVM-only. */
  def isDirectory(path: FilePath): Boolean =
    FileOpsPlatform.isDirectory(path)

  /** Checks if the path is a regular file. JVM-only. */
  def isRegularFile(path: FilePath): Boolean =
    FileOpsPlatform.isRegularFile(path)

  /** Lists the immediate children of a directory (one level deep, non-recursive).
    *
    * Supported on JVM, Scala Native, and Scala.js (under Node).
    *
    * The returned list contains the full child paths (the directory path resolved against each entry name), sorted by their string representation ascending. Sorting is explicit and
    * platform-independent so the result is deterministic across platforms and suitable for golden-output comparison; the underlying directory-iteration order of the platform is never relied upon.
    *
    * If `path` does not exist, or exists but is not a directory, this method throws (mirroring `java.nio.file.Files.list`, which signals a missing path and a non-directory path as distinct error
    * conditions). Callers that want to tolerate those cases must guard with [[exists]] / [[isDirectory]] first.
    */
  def list(path: FilePath): List[FilePath] =
    FileOpsPlatform.list(path)

  /** Recursively descends a directory and returns every entry found beneath it — both regular files and subdirectories — including nested entries at any depth.
    *
    * Supported on JVM, Scala Native, and Scala.js (under Node).
    *
    * The starting `path` itself is not included; only its descendants are. The result is returned in a deterministic, platform-independent order: each directory level is sorted by path string
    * ascending, and a directory is listed before the entries it contains (pre-order). This ordering is established by explicit sorting rather than by the platform's directory-iteration order, so it
    * is stable across JVM, Native, and JS.
    *
    * Directory symbolic links are not followed: a symlink that points at a directory is returned as an entry but its target's contents are not descended into. This keeps the traversal bounded and
    * prevents escaping the subtree under `path`.
    *
    * If `path` does not exist, or exists but is not a directory, this method throws (same condition class as [[list]]).
    */
  def walkTree(path: FilePath): List[FilePath] =
    FileOpsPlatform.walkTree(path)

  /** Creates a directory at `path`, creating any missing parent directories along the way.
    *
    * Supported on JVM, Scala Native, and Scala.js (under Node).
    *
    * Idempotent: if the directory (and all parents) already exist, the call succeeds and does nothing. This matches `java.nio.file.Files.createDirectories` semantics — an already-present directory is
    * not an error.
    */
  def createDirectories(path: FilePath): Unit =
    FileOpsPlatform.createDirectories(path)

  /** Copies the regular file at `from` to `to`, byte for byte.
    *
    * Supported on JVM, Scala Native, and Scala.js (under Node).
    *
    * The copy is byte-exact: the destination's contents are identical to the source's, including bytes with the high bit set (binary content). If `from` does not exist, this method throws.
    *
    * If `to` already exists, it is replaced. The static-asset rebuild described in the site-pipeline design (site-pipeline-design.md, static passthrough) re-copies assets into a destination that may
    * still hold a previous build's files, so an existing destination must be overwritten rather than rejected. The destination's parent directory is expected to exist already (create it via
    * [[createDirectories]] first if needed).
    */
  def copy(from: FilePath, to: FilePath): Unit =
    FileOpsPlatform.copy(from, to)

  /** Removes `path`: if it is a regular file it is deleted; if it is a directory its entire subtree is removed and then the directory itself.
    *
    * Supported on JVM, Scala Native, and Scala.js (under Node).
    *
    * If `path` does not exist, this is a no-op (it returns normally without throwing), so a clean-build of an output directory that has not been produced yet is safe.
    *
    * Symbolic links are never followed during removal: a symlink encountered in the tree (or a symlink passed directly as `path`) is itself deleted, but the link target and the target's contents are
    * left untouched. This is the clean-build safety property required by the site-pipeline design (site-pipeline-design.md Q13): deleting the destination directory must not reach outside it through a
    * link.
    */
  def deleteRecursively(path: FilePath): Unit =
    FileOpsPlatform.deleteRecursively(path)

  /** Returns true if file operations are supported on this platform. */
  def isSupported: Boolean =
    FileOpsPlatform.isSupported
}
