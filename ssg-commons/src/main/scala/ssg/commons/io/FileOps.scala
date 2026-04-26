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

  /** Returns true if file operations are supported on this platform. */
  def isSupported: Boolean =
    FileOpsPlatform.isSupported
}
