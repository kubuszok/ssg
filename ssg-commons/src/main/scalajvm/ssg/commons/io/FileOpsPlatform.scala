/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * JVM implementation of file operations using java.nio.file.Files.
 */
package ssg
package commons
package io

import java.nio.file.Files

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

  val isSupported: Boolean = true
}
