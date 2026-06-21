/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala Native implementation of platform-specific filesystem access.
 * Mirrors java.io.File.isFile()/exists() and FileUtil.getFileContentBytesWithExceptions
 * (java.nio.file.Files.readAllBytes). java.io.File and java.nio.file.Files/Paths are
 * supported on Scala Native (javalib), as already relied upon by
 * ssg-commons/src/main/scalanative/ssg/commons/io/FileOpsPlatform.scala.
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
