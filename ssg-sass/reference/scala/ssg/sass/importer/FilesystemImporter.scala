/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/importer/filesystem.dart Original: Copyright (c) 2017 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 * Renames: filesystem.dart -> FilesystemImporter.scala Convention: Uses ssg-commons FileOps/FilePath for cross-platform I/O.
 * Idiom: Resolves imports via ImporterFileUtils.resolveImportPath, which tries exact, partial, extended, and index variants.
 * Cross-platform (ISS-1154): every java.nio.file / java.net.URI site was replaced with FileOps/FilePath equivalents (path construction via FilePath.of, `file:` URL parsing via
 * ImporterFileUtils.fileUriToPath — the cross-platform analogue of dart's p.fromUri — and modification time via FileOps.lastModifiedTime), so this importer now compiles and runs on JVM, Scala Native,
 * and Scala.js (under Node). */
package ssg
package sass
package importer

import ssg.commons.io.{ FileOps, FilePath }
import ssg.sass.Nullable.*

import scala.language.implicitConversions

/** An importer that loads files from a load path on the filesystem, either relative to the path passed to [[FilesystemImporter]] or absolute `file:` URLs.
  *
  * Use [[FilesystemImporter.noLoadPath]] to _only_ load absolute `file:` URLs and URLs relative to the current file.
  */
final class FilesystemImporter private[importer] (
  private val _loadPath:           Nullable[String],
  private val _loadPathDeprecated: Boolean
) extends Importer {

  /** Creates an importer that loads files relative to [[loadPath]].
    *
    * dart-sass filesystem.dart:36-38 stores `p.absolute(loadPath)`; here the path string is resolved to absolute and normalized cross-platform via `FilePath.of(loadPath).toAbsolute.normalize`.
    */
  def this(loadPath: String) =
    this(Nullable(FilePath.of(loadPath).toAbsolute.normalize.pathString), false)

  /** The load path as a string, for backward compatibility. */
  def loadPath: String = _loadPath.getOrElse("")

  def canonicalize(url: String): Nullable[String] = {
    var resolved: Nullable[String] = Nullable.empty
    if (url.startsWith("file:")) {
      // file: URL — resolve from the filesystem path (dart-sass filesystem.dart:71 `p.fromUri(url)`).
      val path = ImporterFileUtils.fileUriToPath(url)
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
      ImporterFileUtils.toFileUri(r)
    }
  }

  private def urlToPath(url: String): FilePath =
    if (url.startsWith("file:")) {
      // dart-sass filesystem.dart:93 `p.fromUri(url)`.
      FilePath.of(ImporterFileUtils.fileUriToPath(url))
    } else {
      FilePath.of(url)
    }

  def load(url: String): Nullable[ImporterResult] =
    try {
      val path = urlToPath(url)
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

  /** Returns the modification time of the file at [[url]] (dart-sass filesystem.dart:101 `io.modificationTime(p.fromUri(url))`). */
  override def modificationTime(url: String): Long =
    try {
      val path = urlToPath(url)
      FileOps.lastModifiedTime(path)
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
    val cleaned = if (url.startsWith("file:")) ImporterFileUtils.fileUriToPath(url) else url
    val sep     = math.max(cleaned.lastIndexOf('/'), cleaned.lastIndexOf('\\'))
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

  /** Creates an importer that _only_ loads absolute `file:` URLs and URLs relative to the current file.
    */
  val noLoadPath: FilesystemImporter = new FilesystemImporter(Nullable.empty, false)
}
