/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * JVM implementation of platform-specific filesystem access.
 * Mirrors java.io.File.isFile()/exists() and FileUtil.getFileContentBytesWithExceptions
 * (java.nio.file.Files.readAllBytes), both fully supported on the JVM.
 *
 * Covenant: original */
package ssg
package md
package util
package misc

import java.io.File
import java.nio.file.Files

object PlatformFilesImpl {

  def isExistingFile(path: String): Boolean = {
    val file = new File(path)
    file.isFile && file.exists
  }

  def readAllBytes(path: String): Array[Byte] =
    Files.readAllBytes(new File(path).toPath)

  def separatorChar: Char = File.separatorChar
}
