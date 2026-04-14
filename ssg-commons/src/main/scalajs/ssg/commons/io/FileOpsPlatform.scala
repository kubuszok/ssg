/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Scala.js implementation of file operations.
 * File I/O is not available on JS - all operations throw.
 */
package ssg
package commons
package io

private[io] object FileOpsPlatform {

  private def unsupported(op: String): Nothing =
    throw new UnsupportedOperationException(s"File $op is not supported on Scala.js")

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
