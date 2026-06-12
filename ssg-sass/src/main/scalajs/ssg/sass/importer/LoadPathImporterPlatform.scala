/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/import_cache.dart (_toImporters, import_cache.dart:119-135)
 * Original: Copyright (c) 2018 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: dart-sass maps each load path to `FilesystemImporter(path)`
 *     (import_cache.dart:128-129) and each SASS_PATH entry to a
 *     `FilesystemImporter` (import_cache.dart:130-132). The port's
 *     FilesystemImporter is JVM-only (src/main/scalajvm), so Scala.js has no
 *     load-path importer.
 *
 *     dart-sass short-circuits the whole block in a browser:
 *     `if (isBrowser) return [...?importers];` (import_cache.dart:125) — i.e.
 *     loadPaths and SASS_PATH are both ignored. Scala.js mirrors that for the
 *     IMPLICIT SASS_PATH path: `sassPathImporters` is empty (skipped, not an
 *     error), matching the browser's silent omission. An EXPLICIT load path,
 *     however, is a caller request that cannot be honored, so
 *     `loadPathImporter` fails loudly rather than dropping it silently. */
package ssg
package sass
package importer

/** Builds the importers that back load paths and the SASS_PATH environment variable, per platform.
  */
object LoadPathImporterPlatform {

  /** On Scala.js the filesystem-backed [[FilesystemImporter]] is unavailable, so an explicit `loadPaths` entry cannot be resolved. Fail loudly rather than dropping the load path silently.
    */
  def loadPathImporter(path: String): Importer =
    throw new UnsupportedOperationException(
      s"loadPaths are not supported on Scala.js (no FilesystemImporter): $path. " +
        "Pass an in-memory importer via `importers` instead."
    )

  /** dart-sass import_cache.dart:125 `if (isBrowser) return [...?importers];`: in a browser the SASS_PATH block is skipped entirely. Scala.js is the browser-equivalent target, so implicit SASS_PATH
    * resolution is skipped (empty) rather than failing — unlike an explicit `loadPathImporter`.
    */
  def sassPathImporters(): List[Importer] = Nil
}
