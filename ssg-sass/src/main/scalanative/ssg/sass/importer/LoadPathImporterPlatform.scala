/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/import_cache.dart (_toImporters, import_cache.dart:119-135)
 * Original: Copyright (c) 2018 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: dart-sass turns each load path into `FilesystemImporter(path)`
 *     (import_cache.dart:128-129) and likewise turns every SASS_PATH entry into
 *     a `FilesystemImporter` (import_cache.dart:130-132). Since ISS-1154 made
 *     FilesystemImporter cross-platform (it is backed by ssg-commons FileOps /
 *     FilePath, supported on Scala Native), both factories are real here — a
 *     Scala Native binary has a filesystem, so an explicit load path resolves a
 *     real FilesystemImporter and SASS_PATH is honored entry-for-entry, exactly
 *     as on the JVM.
 *
 *     dart-sass guards the whole load-path/SASS_PATH block behind
 *     `if (isBrowser) return [...?importers];` (import_cache.dart:125): Scala
 *     Native is not a browser, so SASS_PATH is honored (matching dart's
 *     non-browser behavior). */
package ssg
package sass
package importer

/** Builds the importers that back load paths and the SASS_PATH environment variable, per platform.
  */
object LoadPathImporterPlatform {

  /** dart-sass import_cache.dart:128-129: `for (var path in loadPaths) FilesystemImporter(path)`.
    */
  def loadPathImporter(path: String): Importer = new FilesystemImporter(path)

  /** dart-sass import_cache.dart:124/130-132: reads the SASS_PATH environment variable and turns each entry into a `FilesystemImporter`. Scala Native is not a browser (import_cache.dart:125
    * `isBrowser` guard does not apply), so SASS_PATH is honored here.
    */
  def sassPathImporters(): List[Importer] =
    try
      sassPathImportersFrom(
        sys.env.get("SASS_PATH"),
        java.io.File.pathSeparator
      )
    catch {
      case _: SecurityException => Nil
    }

  /** The pure core of [[sassPathImporters]], parameterized over the raw SASS_PATH value and the path separator so it can be exercised without mutating the environment. dart-sass splits the variable
    * on `;` (Windows) or `:` and builds a `FilesystemImporter` per non-empty entry (import_cache.dart:130-132).
    */
  private[sass] def sassPathImportersFrom(sassPath: Option[String], separator: String): List[Importer] =
    sassPath.toList.flatMap { paths =>
      paths.split(separator).toList.filter(_.nonEmpty).map(loadPathImporter)
    }
}
