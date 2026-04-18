/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/importer/utils.dart (filesystem-dependent portion)
 * Original: Copyright (c) 2017 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: utils.dart -> ImporterFileUtils.scala (JVM-only portion)
 *   Convention: Uses ssg-commons FileOps/FilePath for cross-platform I/O.
 *   Idiom: resolveImportPath, tryPath, tryPathWithExtensions,
 *          tryPathAsDirectory, exactlyOne — filesystem operations that
 *          require actual file existence checks.
 */
package ssg
package sass
package importer

import ssg.commons.io.{ FileOps, FilePath }
import ssg.sass.Nullable.*

import scala.collection.mutable.ArrayBuffer
import scala.language.implicitConversions

/** JVM-only filesystem resolution helpers for the importer infrastructure.
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
      importOnly
        .orElse(exactlyOne(tryPathWithExtensions(path)))
        .orElse(tryPathAsDirectory(path))
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
    val dir      = dirname(path)
    val base     = basename(path)
    val partial  = if (dir.isEmpty) s"_$base" else s"$dir/_$base"
    val result   = ArrayBuffer.empty[String]
    if (fileExists(partial)) result += partial
    if (fileExists(path)) result += path
    result.toList
  }

  /** Returns the resolved index file for [[path]] if [[path]] is a directory and the index file exists.
    *
    * Otherwise, returns empty.
    */
  private def tryPathAsDirectory(path: String): Nullable[String] = {
    if (!dirExists(path)) Nullable.empty
    else {
      val importOnly = ImporterUtils.ifInImport { () =>
        exactlyOne(tryPathWithExtensions(joinPath(path, "index.import")))
      }.flatten
      importOnly.orElse(exactlyOne(tryPathWithExtensions(joinPath(path, "index"))))
    }
  }

  /** If [[paths]] contains exactly one path, returns that path.
    *
    * If it contains no paths, returns empty. If it contains more than one, throws an exception.
    */
  private def exactlyOne(paths: List[String]): Nullable[String] = paths match {
    case Nil         => Nullable.empty
    case head :: Nil => Nullable(head)
    case _ =>
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
}
