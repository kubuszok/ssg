/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/importer/utils.dart (filesystem-dependent portion) Original: Copyright (c) 2017 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 * Renames: utils.dart -> ImporterFileUtils.scala Convention: Uses ssg-commons FileOps/FilePath for cross-platform I/O.
 * Idiom: resolveImportPath, tryPath, tryPathWithExtensions, tryPathAsDirectory, exactlyOne — filesystem operations that require actual file existence checks.
 * Cross-platform: FileOps/FilePath are supported on JVM, Scala Native, and Scala.js (under Node), so this helper — and the FilesystemImporter that uses it — is now cross-platform (ISS-1154). */
package ssg
package sass
package importer

import ssg.commons.io.{ FileOps, FilePath }
import ssg.sass.Nullable.*

import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions

/** Filesystem resolution helpers for the importer infrastructure.
  *
  * These functions implement the same logic as `resolveImportPath` and its helpers in the original Dart `importer/utils.dart`, using `FileOps` for actual filesystem access.
  */
object ImporterFileUtils {

  /** Resolves an imported path using the same logic as the filesystem importer.
    *
    * This tries to fill in extensions and partial prefixes and check for a directory default. If no file can be found, it returns empty.
    */
  def resolveImportPath(path: String): Nullable[String] = {
    val ext = extension(path)
    if (ext == ".sass" || ext == ".scss" || ext == ".css") {
      val importOnly = ImporterUtils.ifInImport { () =>
        exactlyOne(tryPath(withoutExtension(path) + ".import" + ext))
      }.flatten
      importOnly.orElse(exactlyOne(tryPath(path)))
    } else {
      val importOnly = ImporterUtils.ifInImport { () =>
        exactlyOne(tryPathWithExtensions(path + ".import"))
      }.flatten
      importOnly.orElse(exactlyOne(tryPathWithExtensions(path))).orElse(tryPathAsDirectory(path))
    }
  }

  /** Like [[tryPath]], but checks `.sass`, `.scss`, and `.css` extensions. */
  private def tryPathWithExtensions(path: String): List[String] = {
    val result = tryPath(path + ".sass") ++ tryPath(path + ".scss")
    if (result.nonEmpty) result else tryPath(path + ".css")
  }

  /** Returns the [[path]] and/or the partial with the same name, if either or both exists.
    *
    * If neither exists, returns an empty list.
    */
  private def tryPath(path: String): List[String] = {
    val dir     = dirname(path)
    val base    = basename(path)
    val partial = if (dir.isEmpty) s"_$base" else s"$dir/_$base"
    val result  = ArrayBuffer.empty[String]
    if (fileExists(partial)) result += partial
    if (fileExists(path)) result += path
    result.toList
  }

  /** Returns the resolved index file for [[path]] if [[path]] is a directory and the index file exists.
    *
    * Otherwise, returns empty.
    */
  private def tryPathAsDirectory(path: String): Nullable[String] =
    if (!dirExists(path)) Nullable.empty
    else {
      val importOnly = ImporterUtils.ifInImport { () =>
        exactlyOne(tryPathWithExtensions(joinPath(path, "index.import")))
      }.flatten
      importOnly.orElse(exactlyOne(tryPathWithExtensions(joinPath(path, "index"))))
    }

  /** If [[paths]] contains exactly one path, returns that path.
    *
    * If it contains no paths, returns empty. If it contains more than one, throws an exception.
    */
  private def exactlyOne(paths: List[String]): Nullable[String] = paths match {
    case Nil         => Nullable.empty
    case head :: Nil => Nullable(head)
    case _           =>
      throw new IllegalStateException(
        "It's not clear which file to import. Found:\n" +
          paths.map(p => "  " + p).mkString("\n")
      )
  }

  // -- Path helpers --
  // These replicate `p.extension`, `p.withoutExtension`, `p.dirname`,
  // `p.basename`, `p.join` from the Dart `path` package.

  /** Returns the file extension of [[path]], including the leading `.`. */
  private def extension(path: String): String = {
    val name = basename(path)
    val dot  = name.lastIndexOf('.')
    if (dot <= 0) "" else name.substring(dot)
  }

  /** Returns [[path]] without its extension. */
  private def withoutExtension(path: String): String = {
    val ext = extension(path)
    if (ext.isEmpty) path else path.substring(0, path.length - ext.length)
  }

  /** Returns the directory portion of [[path]]. */
  private def dirname(path: String): String = {
    val sep = math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'))
    if (sep < 0) "" else path.substring(0, sep)
  }

  /** Returns the filename portion of [[path]]. */
  private def basename(path: String): String = {
    val sep = math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'))
    if (sep < 0) path else path.substring(sep + 1)
  }

  /** Joins [[parent]] and [[child]] with a path separator. */
  private def joinPath(parent: String, child: String): String =
    if (parent.isEmpty) child
    else if (parent.endsWith("/") || parent.endsWith("\\")) parent + child
    else parent + "/" + child

  /** Returns whether a file exists at [[path]]. */
  private def fileExists(path: String): Boolean =
    try {
      val fp = FilePath.of(path)
      FileOps.exists(fp) && FileOps.isRegularFile(fp)
    } catch {
      case _: Throwable => false
    }

  /** Returns whether a directory exists at [[path]]. */
  private def dirExists(path: String): Boolean =
    try {
      val fp = FilePath.of(path)
      FileOps.exists(fp) && FileOps.isDirectory(fp)
    } catch {
      case _: Throwable => false
    }

  /** Converts a filesystem path to a `file:` URI string, OS-independently.
    *
    * The path is resolved to absolute, normalized, and forward-slash rendered so the resulting URI is identical on POSIX and Windows. On POSIX, this produces the same string as
    * `java.nio.file.Paths.get(path).toAbsolutePath.normalize.toUri.toString` (verified by construction: both yield `file:///absolute/path`).
    */
  def toFileUri(path: String): String = {
    val normalized = FilePath.of(path).toAbsolute.normalize.pathString.replace('\\', '/')
    "file://" + (if (normalized.startsWith("/")) normalized else "/" + normalized)
  }

  /** Converts a `file:` URI string to a filesystem path string, OS-independently — the inverse of [[toFileUri]] and the cross-platform equivalent of dart-sass `p.fromUri` (filesystem.dart:71/93/101).
    *
    * Mirrors `java.net.URI(url).getPath` for `file:` URLs (which the JVM-only port previously used): strips the `file:` scheme and any `//authority`, drops a query or fragment component, and
    * percent-decodes `%XX` octets as UTF-8. A `url` without the `file:` scheme is returned as-is (the pre-move fallback was `url.stripPrefix("file:")`; this pure helper never throws, so no fallback path
    * is needed).
    */
  def fileUriToPath(url: String): String = {
    val afterScheme =
      if (url.startsWith("file:")) url.substring("file:".length)
      else url
    val rawPath =
      if (afterScheme.startsWith("//")) {
        // file://[authority][/path] — skip the authority component up to the next '/'.
        val rest  = afterScheme.substring(2)
        val slash = rest.indexOf('/')
        if (slash < 0) "" else rest.substring(slash)
      } else afterScheme // file:/path or file:path
    // Drop a query (?) or fragment (#) component if present, mirroring URI.getPath.
    val q   = rawPath.indexOf('?')
    val h   = rawPath.indexOf('#')
    val cut =
      if (q < 0) h
      else if (h < 0) q
      else math.min(q, h)
    val pathPart = if (cut < 0) rawPath else rawPath.substring(0, cut)
    percentDecode(pathPart)
  }

  /** True if [[c]] is a hexadecimal digit (used by [[percentDecode]] to validate `%XX` escapes). */
  private def isHex(c: Char): Boolean =
    (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')

  /** Percent-decodes `%XX` octets in [[s]] as UTF-8, matching `java.net.URI.getPath`. A `%` not followed by two hex digits is left literal, and non-escaped characters are re-encoded as UTF-8 so
    * multi-byte characters round-trip. Returns [[s]] unchanged when it contains no `%`.
    */
  private def percentDecode(s: String): String =
    if (s.indexOf('%') < 0) s
    else {
      val bytes = ArrayBuffer.empty[Byte]
      var i     = 0
      while (i < s.length) {
        val c = s.charAt(i)
        if (c == '%' && i + 2 < s.length && isHex(s.charAt(i + 1)) && isHex(s.charAt(i + 2))) {
          val hi = Character.digit(s.charAt(i + 1), 16)
          val lo = Character.digit(s.charAt(i + 2), 16)
          bytes += (((hi << 4) | lo) & 0xff).toByte
          i += 3
        } else {
          val encoded = c.toString.getBytes(java.nio.charset.StandardCharsets.UTF_8)
          bytes ++= encoded
          i += 1
        }
      }
      new String(bytes.toArray, java.nio.charset.StandardCharsets.UTF_8)
    }
}
