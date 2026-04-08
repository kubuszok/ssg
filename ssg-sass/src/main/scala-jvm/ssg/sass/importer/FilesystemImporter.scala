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
 *   Convention: Uses java.nio.file, so only compiled for the JVM target.
 *   Idiom: Resolves imports by trying exact, partial, extended, and index
 *          variants in order.
 */
package ssg
package sass
package importer

import java.nio.file.{ Files, Path, Paths }

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

  private val rootPath: Path = Paths.get(loadPath).toAbsolutePath.normalize()

  /** Candidate file names to try for the given import target. */
  private def candidates(relative: String): List[Path] = {
    val target   = Paths.get(relative)
    val fileName = target.getFileName.toString
    val parent   = Option(target.getParent).getOrElse(Paths.get(""))

    val hasExtension = fileName.indexOf('.') >= 0
    val basenames: List[String] =
      if (hasExtension) {
        // Exact match only (plus partial form)
        List(fileName, s"_$fileName").distinct
      } else {
        // Try each syntax, including partial form
        List(
          s"$fileName.scss",
          s"_$fileName.scss",
          s"$fileName.sass",
          s"_$fileName.sass",
          s"$fileName.css",
          s"_$fileName.css"
        )
      }

    val directCandidates = basenames.map(n => parent.resolve(n))

    // Also try index files if the relative path is a directory
    val indexCandidates: List[Path] =
      if (hasExtension) Nil
      else
        List(
          target.resolve("_index.scss"),
          target.resolve("index.scss"),
          target.resolve("_index.sass"),
          target.resolve("index.sass")
        )

    (directCandidates ++ indexCandidates).map((p: Path) => rootPath.resolve(p).normalize())
  }

  def canonicalize(url: String): Nullable[String] = {
    val cleaned = if (url.startsWith("file:")) url.stripPrefix("file:") else url
    val cands   = candidates(cleaned)
    var result: Nullable[String] = Nullable.empty
    var i = 0
    while (result.isEmpty && i < cands.length) {
      val c      = cands(i)
      val exists =
        try Files.exists(c) && Files.isRegularFile(c)
        catch { case _: Throwable => false }
      if (exists) {
        result = Nullable(c.toUri.toString)
      }
      i += 1
    }
    result
  }

  def load(url: String): Nullable[ImporterResult] =
    try {
      val path: Path = {
        val uri = java.net.URI.create(url)
        if (uri.getScheme == "file") Paths.get(uri) else Paths.get(url)
      }
      if (!Files.exists(path) || !Files.isRegularFile(path)) {
        Nullable.empty
      } else {
        val contents = new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8)
        val syntax   =
          if (path.toString.endsWith(".sass")) Syntax.Sass
          else if (path.toString.endsWith(".css")) Syntax.Css
          else Syntax.Scss
        Nullable(ImporterResult(contents, syntax))
      }
    } catch {
      case _: Throwable => Nullable.empty
    }

  override def toString: String = loadPath
}
