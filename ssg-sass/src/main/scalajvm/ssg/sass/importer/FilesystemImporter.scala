/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/importer/filesystem.dart Original: Copyright (c) 2017 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 * Renames: filesystem.dart -> FilesystemImporter.scala (JVM-only) Convention: Uses ssg-commons FileOps/FilePath for cross-platform I/O.
 * Idiom: Resolves imports via ImporterFileUtils.resolveImportPath, which tries exact, partial, extended, and index variants. */
package ssg
package sass
package importer

import ssg.commons.io.{ FileOps, FilePath }
import ssg.sass.Nullable.*

import scala.language.implicitConversions

/** An importer that loads files from a load path on the filesystem, either relative to the path passed to [[FilesystemImporter]] or absolute `file:` URLs.
  *
  * Use [[FilesystemImporter.noLoadPath]] to _only_ load absolute `file:` URLs and URLs relative to the current file.
  *
  * JVM-only.
  */
final class FilesystemImporter private (
  private val _loadPath:           Nullable[String],
  private val _loadPathDeprecated: Boolean
) extends Importer {

  /** Creates an importer that loads files relative to [[loadPath]]. */
  def this(loadPath: String) =
    this(Nullable(FilePath.of(loadPath).toAbsolute.normalize.pathString), false)

  /** The load path as a string, for backward compatibility. */
  def loadPath: String = _loadPath.getOrElse("")

  def canonicalize(url: String): Nullable[String] = {
    var resolved: Nullable[String] = Nullable.empty
    if (url.startsWith("file:")) {
      // file: URL — resolve from the filesystem path
      val path = try {
        val uri = new java.net.URI(url)
        uri.getPath
      } catch {
        case _: Throwable => url.stripPrefix("file:")
      }
      resolved = ImporterFileUtils.resolveImportPath(path)
    } else if (url.contains(":")) {
      // Non-file scheme — not our business
      Nullable.empty
    } else {
      _loadPath.toOption match {
        case Some(lp) =>
          val joined = {
            val lpPath = FilePath.of(lp)
            lpPath.resolve(url).pathString
          }
          resolved = ImporterFileUtils.resolveImportPath(joined)

          if (resolved.isDefined && _loadPathDeprecated) {
            EvaluationContext.warnForDeprecation(
              Deprecation.FsImporterCwd,
              "Using the current working directory as an implicit load path is " +
                "deprecated. Either add it as an explicit load path or importer, or " +
                "load this stylesheet from a different URL."
            )
          }
        case scala.None =>
          Nullable.empty
      }
    }

    resolved.map { r =>
      // Canonicalize the resolved path
      val fp = FilePath.of(r).toAbsolute.normalize
      fp.pathString
    }
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
        val syntax   = Syntax.forPath(pathStr)
        Nullable(ImporterResult(contents, syntax, sourceMapUrl = Nullable(url)))
      }
    } catch {
      case _: Throwable => Nullable.empty
    }

  /** Returns the modification time of the file at [[url]]. */
  override def modificationTime(url: String): Long =
    try {
      val path: FilePath = {
        val uri = java.net.URI.create(url)
        if (uri.getScheme == "file") FilePath.of(uri.getPath) else FilePath.of(url)
      }
      java.nio.file.Files
        .getLastModifiedTime(
          java.nio.file.Paths.get(path.pathString)
        )
        .toMillis
    } catch {
      case _: Throwable => System.currentTimeMillis()
    }

  /** Quick check if this importer could potentially canonicalize [[url]] to [[canonicalUrl]].
    *
    * This avoids full canonicalization when possible, checking only basename compatibility.
    */
  override def couldCanonicalize(url: String, canonicalUrl: String): Boolean = {
    // In the original: url.scheme must be 'file' or '' and canonicalUrl.scheme must be 'file'
    // Since we model URLs as strings, we check for file: prefix or no scheme
    val urlHasNonFileScheme = url.contains(":") && !url.startsWith("file:")
    if (urlHasNonFileScheme) false
    else {
      // canonicalUrl must be a file-like path
      val urlBasename       = urlBasenameOf(url)
      var canonicalBasename = urlBasenameOf(canonicalUrl)

      if (!urlBasename.startsWith("_") && canonicalBasename.startsWith("_")) {
        canonicalBasename = canonicalBasename.substring(1)
      }

      urlBasename == canonicalBasename ||
      urlBasename == withoutExtensionBasename(canonicalBasename)
    }
  }

  /** Extracts the basename (last path component) from a URL/path string. */
  private def urlBasenameOf(url: String): String = {
    val cleaned = if (url.startsWith("file:")) {
      try new java.net.URI(url).getPath
      catch { case _: Throwable => url.stripPrefix("file:") }
    } else url
    val sep = math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'))
    if (sep < 0) cleaned else cleaned.substring(sep + 1)
  }

  /** Removes the extension from a basename. */
  private def withoutExtensionBasename(name: String): String = {
    val dot = name.lastIndexOf('.')
    if (dot <= 0) name else name.substring(0, dot)
  }

  override def toString: String = _loadPath.getOrElse("<absolute file importer>")
}

object FilesystemImporter {

  /** A [[FilesystemImporter]] that loads files relative to the current working directory.
    *
    * @deprecated
    *   Use [[FilesystemImporter.noLoadPath]] or `new FilesystemImporter(".")` instead.
    */
  @deprecated("Use FilesystemImporter.noLoadPath or FilesystemImporter(\".\") instead.", "1.73.0")
  val cwd: FilesystemImporter = new FilesystemImporter(Nullable(FilePath.of(".").toAbsolute.normalize.pathString), true)

  /** Creates an importer that _only_ loads absolute `file:` URLs and URLs relative to the current file.
    */
  val noLoadPath: FilesystemImporter = new FilesystemImporter(Nullable.empty, false)
}
