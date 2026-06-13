/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Cross-platform filesystem access abstraction.
 * Delegates to PlatformFilesImpl which has platform-specific implementations
 * in scalajvm/, scalajs/, and scalanative/ source directories.
 *
 * Supports FileUriContentResolver (flexmark/src/main/java/com/vladsch/flexmark/html/renderer/FileUriContentResolver.java),
 * which on the JVM reads file bytes via java.nio.file.Files.readAllBytes and checks
 * java.io.File.isFile/exists. Those NIO APIs are unavailable / unreliable on Scala.js
 * (no synchronous filesystem on the JVM model) so the read is funneled through this
 * facade exactly like ssg.md.util.misc.PlatformResources funnels resource loading.
 */
package ssg
package md
package util
package misc

object PlatformFiles {

  /** True iff the path denotes an existing regular file (mirrors File.isFile() && File.exists()).
    */
  def isExistingFile(path: String): Boolean =
    PlatformFilesImpl.isExistingFile(path)

  /** Read all bytes of the file at path. Throws java.io.IOException on failure, mirroring
    * FileUtil.getFileContentBytesWithExceptions (Files.readAllBytes).
    */
  def readAllBytes(path: String): Array[Byte] =
    PlatformFilesImpl.readAllBytes(path)
}
