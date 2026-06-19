/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Output writer for the site pipeline.
 *
 * This is an SSG-native module (not a port of an external library).
 * See docs/architecture/site-pipeline-design.md section 3 for design.
 *
 * Writes rendered page content to the destination directory using
 * FileOps.writeString and FileOps.createDirectories.
 */
package ssg
package site

import ssg.commons.io.FileOps
import ssg.commons.io.FilePath

/** Writes rendered content to the destination directory.
  *
  * Creates parent directories as needed via `FileOps.createDirectories`, then writes the content string via `FileOps.writeString` (UTF-8).
  */
object OutputWriter {

  /** Writes the rendered content for a page to the destination directory.
    *
    * The output path is `config.destination` / `relativePath`, where `relativePath` is the source-relative path with any extension transformation applied (e.g. `.md` becomes `.html`).
    *
    * @param destination
    *   the output directory root (e.g. `_site`)
    * @param relativePath
    *   the relative path within the output directory (e.g. `index.html`)
    * @param content
    *   the rendered content to write
    */
  def write(destination: FilePath, relativePath: String, content: String): Unit = {
    val outputPath = destination.resolve(relativePath)
    // Ensure parent directories exist.
    outputPath.parent match {
      case Some(parentDir) => FileOps.createDirectories(parentDir)
      case _               => ()
    }
    FileOps.writeString(outputPath, content)
  }

  /** Copies a static file verbatim from source to destination.
    *
    * @param destination
    *   the output directory root
    * @param relativePath
    *   the relative path within the output directory
    * @param sourcePath
    *   the absolute path of the source file to copy
    */
  def copyStatic(destination: FilePath, relativePath: String, sourcePath: FilePath): Unit = {
    val outputPath = destination.resolve(relativePath)
    // Ensure parent directories exist.
    outputPath.parent match {
      case Some(parentDir) => FileOps.createDirectories(parentDir)
      case _               => ()
    }
    FileOps.copy(sourcePath, outputPath)
  }
}
