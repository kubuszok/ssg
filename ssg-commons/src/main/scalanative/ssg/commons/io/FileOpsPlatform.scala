/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native implementation of file operations.
 * TODO: Could use POSIX APIs for actual file I/O.
 * For now, throws to maintain JS/Native parity.
 */
package ssg
package commons
package io

private[io] object FileOpsPlatform {

  private def unsupported(op: String): Nothing =
    throw new UnsupportedOperationException(s"File $op is not yet implemented on Scala Native")

  def readAllBytes(path: FilePath): Array[Byte] =
    unsupported("read")

  def writeBytes(path: FilePath, bytes: Array[Byte]): Unit =
    unsupported("write")

  def exists(path: FilePath): Boolean =
    unsupported("exists")

  def isDirectory(path: FilePath): Boolean =
    unsupported("isDirectory")

  def isRegularFile(path: FilePath): Boolean =
    unsupported("isRegularFile")

  val isSupported: Boolean = false
}
