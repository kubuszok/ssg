/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Source-file selection for the site pipeline.
 *
 * This is an SSG-native module (not a port of an external library).
 * See docs/architecture/site-pipeline-design.md section 3.2 for design.
 *
 * Walks config.source, partitions every entry into one of three buckets:
 * processed (front-matter pages), static (copied verbatim), or excluded.
 * Follows Jekyll's source-file selection rules per the design.
 */
package ssg
package site

import ssg.commons.io.FileOps
import ssg.commons.io.FilePath

/** Result of scanning the source directory. Each file is assigned to exactly one bucket.
  *
  * @param processed
  *   files with front matter (pages that will be rendered through the pipeline)
  * @param static
  *   files without front matter (copied verbatim to output)
  * @param excluded
  *   files that should not appear in output (destination dir, underscore dirs, dotfiles, excluded paths)
  */
final case class ScanResult(
  processed: Vector[FilePath],
  static:    Vector[FilePath],
  excluded:  Vector[FilePath]
)

/** Walks `config.source` and partitions every entry into processed / static / excluded buckets.
  *
  * The selection rules match Jekyll's source-file selection (design section 3.2):
  *
  *   1. Skip the destination directory (prevents reading previous output). 2. Skip underscore-prefixed top-level directories (`_layouts/`, `_includes/`, `_sass/`, etc.). 3. Skip dotfiles and
  *      dot-directories (`.git/`, `.gitignore`, `.DS_Store`, etc.). 4. Honor `exclude:` and `include:` config keys (plain relative paths and directory prefixes). 5. Files with front matter are
  *      processed; files without are static.
  */
object SourceScan {

  /** Scans the source directory and returns a [[ScanResult]] with files bucketed into processed, static, or excluded.
    *
    * @param config
    *   the site configuration (provides source path, destination path, and raw config for exclude/include lists)
    */
  def scan(config: SiteConfig): ScanResult = {
    val sourceAbs = config.source.toAbsolute.normalize
    val destAbs   = config.destination.toAbsolute.normalize

    val excludeList = extractStringList(config.raw, "exclude")
    val includeList = extractStringList(config.raw, "include")

    val allEntries = FileOps.walkTree(sourceAbs)

    val processedBuilder = Vector.newBuilder[FilePath]
    val staticBuilder    = Vector.newBuilder[FilePath]
    val excludedBuilder  = Vector.newBuilder[FilePath]

    allEntries.foreach { entry =>
      if (!FileOps.isDirectory(entry)) {
        val relativePath = relativize(sourceAbs, entry)
        if (isExcluded(relativePath, destAbs, sourceAbs, excludeList, includeList)) {
          excludedBuilder += entry
        } else {
          // Read the file to check for front matter.
          val content = FileOps.readString(entry)
          if (FrontMatterBridge.hasFrontMatter(content)) {
            processedBuilder += entry
          } else {
            staticBuilder += entry
          }
        }
      }
    }

    ScanResult(
      processed = processedBuilder.result(),
      static = staticBuilder.result(),
      excluded = excludedBuilder.result()
    )
  }

  /** Extracts a list of strings from a DataView mapping by key.
    *
    * The value is expected to be a YAML sequence of strings (e.g. `exclude: [".git", "vendor"]`). Returns an empty vector if the key is absent or the value is not a sequence.
    */
  private def extractStringList(raw: ssg.data.DataView, key: String): Vector[String] =
    raw.asMap.toOption match {
      case Some(map) =>
        map.get(key) match {
          case Some(dv) =>
            dv.asVector.toOption match {
              case Some(vec) => vec.flatMap(_.asString.toOption)
              case _         => Vector.empty
            }
          case _ => Vector.empty
        }
      case _ => Vector.empty
    }

  /** Computes the relative path of `entry` under `base` by stripping the base prefix from the path string.
    *
    * FilePath does not provide a `relativize` method, so this uses string manipulation on the normalized absolute path strings.
    */
  def relativize(base: FilePath, entry: FilePath): String = {
    val basePath  = base.pathString
    val entryPath = entry.pathString
    if (entryPath.startsWith(basePath)) {
      val rel = entryPath.substring(basePath.length)
      // Strip leading separator.
      if (rel.startsWith("/")) rel.substring(1)
      else if (rel.startsWith("\\")) rel.substring(1)
      else rel
    } else {
      entryPath
    }
  }

  /** Determines whether a file (given as a relative path from source) should be excluded from the output.
    *
    * Applies the Jekyll selection rules in order (design section 3.2):
    *
    *   1. Skip files inside the destination directory. 2. Skip files inside top-level underscore-prefixed directories. 3. Skip dotfiles and files inside dot-directories. 4. Honor exclude:/include:
    *      config keys.
    *
    * The `include:` list can re-add paths that rules 2 or 3 would skip.
    */
  private def isExcluded(
    relativePath: String,
    destAbs:      FilePath,
    sourceAbs:    FilePath,
    excludeList:  Vector[String],
    includeList:  Vector[String]
  ): Boolean = {
    // Split into path segments for analysis.
    val segments = relativePath.split("[/\\\\]").toVector.filter(_.nonEmpty)
    if (segments.isEmpty) {
      // Should not happen for regular files.
      false
    } else {
      val entryAbsPath = sourceAbs.resolve(relativePath).toAbsolute.normalize.pathString
      val destPath     = destAbs.pathString

      // Rule 1: Skip files inside the destination directory.
      val insideDest = entryAbsPath.startsWith(destPath + "/") ||
        entryAbsPath.startsWith(destPath + "\\") ||
        entryAbsPath == destPath

      if (insideDest) {
        true
      } else if (isIncluded(relativePath, segments, includeList)) {
        // The include list re-adds this path, overriding rules 2-4.
        false
      } else {
        // Rule 2: Skip top-level underscore-prefixed directories.
        val topLevelUnderscore = segments.head.startsWith("_")

        // Rule 3: Skip dotfiles and dot-directories.
        val hasDotSegment = segments.exists(_.startsWith("."))

        // Rule 4: Honor exclude: config key.
        val matchesExclude = excludeList.exists(pattern => matchesExcludePattern(relativePath, pattern))

        topLevelUnderscore || hasDotSegment || matchesExclude
      }
    }
  }

  /** Checks whether a path is explicitly included via the `include:` config key. */
  private def isIncluded(relativePath: String, segments: Vector[String], includeList: Vector[String]): Boolean =
    includeList.exists { pattern =>
      // Plain relative path match or directory prefix match.
      relativePath == pattern ||
      relativePath.startsWith(pattern + "/") ||
      relativePath.startsWith(pattern + "\\") ||
      // Single segment match (e.g. include: [".htaccess"]).
      segments.head == pattern
    }

  /** Checks whether a relative path matches an exclude pattern.
    *
    * v1 matches plain relative paths and directory prefixes (not globs).
    */
  private def matchesExcludePattern(relativePath: String, pattern: String): Boolean =
    relativePath == pattern ||
      relativePath.startsWith(pattern + "/") ||
      relativePath.startsWith(pattern + "\\")
}
