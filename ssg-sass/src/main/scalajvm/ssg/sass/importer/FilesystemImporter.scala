/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/importer/filesystem.dart
 * Original: Copyright (c) 2017 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: filesystem.dart -> FilesystemImporter.scala (JVM-only)
 *   Convention: Uses ssg-commons FileOps/FilePath for cross-platform I/O.
 *   Idiom: Resolves imports by trying exact, partial, extended, and index
 *          variants in order.
 */
package ssg
package sass
package importer

import ssg.commons.io.{ FileOps, FilePath }
import ssg.sass.Nullable.*

import scala.language.implicitConversions

/** A filesystem importer rooted at a load path.
  *
  * Resolves imports by trying several candidate paths in order:
  *   1. exact `basename.scss`/`.sass`/`.css`
  *   2. `_basename.scss`/`.sass`/`.css` (partial)
  *   3. `path/_index.scss` / `path/index.scss`
  *
  * JVM-only.
  */
final class FilesystemImporter(val loadPath: String) extends Importer {

  private val rootPath: FilePath = FilePath.of(loadPath).toAbsolute.normalize

  /** Candidate file names to try for the given import target. */
  private def candidates(relative: String): List[FilePath] = {
    val target   = FilePath.of(relative)
    val fName    = target.fileName
    val par      = target.parent.getOrElse(FilePath.of(""))

    val hasExtension = fName.indexOf('.') >= 0
    val basenames: List[String] =
      if (hasExtension) {
        // Exact match only (plus partial form)
        List(fName, s"_$fName").distinct
      } else {
        // Try each syntax, including partial form
        List(
          s"$fName.scss",
          s"_$fName.scss",
          s"$fName.sass",
          s"_$fName.sass",
          s"$fName.css",
          s"_$fName.css"
        )
      }

    val directCandidates = basenames.map(n => par.resolve(n))

    // Also try index files if the relative path is a directory
    val indexCandidates: List[FilePath] =
      if (hasExtension) Nil
      else
        List(
          target.resolve("_index.scss"),
          target.resolve("index.scss"),
          target.resolve("_index.sass"),
          target.resolve("index.sass")
        )

    (directCandidates ++ indexCandidates).map(p => rootPath.resolve(p).normalize)
  }

  def canonicalize(url: String): Nullable[String] = {
    val cleaned = if (url.startsWith("file:")) url.stripPrefix("file:") else url
    val cands   = candidates(cleaned)
    var result: Nullable[String] = Nullable.empty
    var i = 0
    while (result.isEmpty && i < cands.length) {
      val c      = cands(i)
      val exists =
        try FileOps.exists(c) && FileOps.isRegularFile(c)
        catch { case _: Throwable => false }
      if (exists) {
        result = Nullable(c.toAbsolute.pathString)
      }
      i += 1
    }
    result
  }

  def load(url: String): Nullable[ImporterResult] =
    try {
      val path: FilePath = {
        val uri = java.net.URI.create(url)
        if (uri.getScheme == "file") FilePath.of(uri.getPath) else FilePath.of(url)
      }
      if (!FileOps.exists(path) || !FileOps.isRegularFile(path)) {
        Nullable.empty
      } else {
        val contents = FileOps.readString(path)
        val pathStr  = path.pathString
        val syntax   =
          if (pathStr.endsWith(".sass")) Syntax.Sass
          else if (pathStr.endsWith(".css")) Syntax.Css
          else Syntax.Scss
        Nullable(ImporterResult(contents, syntax))
      }
    } catch {
      case _: Throwable => Nullable.empty
    }

  override def toString: String = loadPath
}
