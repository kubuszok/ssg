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

import java.nio.file.{ Files, Paths }

private[io] object FileOpsPlatform {

  /** Native FilePath is string-based, so route every operation through java.nio.file.Paths,
    * matching the JVM FilePathPlatform.toNioPath fallback (`Paths.get(fp.pathString)`).
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

  val isSupported: Boolean = true
}
