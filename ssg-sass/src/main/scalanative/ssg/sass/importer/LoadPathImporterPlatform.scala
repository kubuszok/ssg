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
 *     FilesystemImporter is JVM-only (src/main/scalajvm), so Scala Native has
 *     no load-path importer.
 *
 *     dart-sass short-circuits the whole block in a browser:
 *     `if (isBrowser) return [...?importers];` (import_cache.dart:125) — both
 *     loadPaths and SASS_PATH are ignored there. Scala Native is not a browser,
 *     but it has no FilesystemImporter yet, so it follows the same shape as the
 *     browser branch for the IMPLICIT SASS_PATH path: `sassPathImporters` is
 *     empty (skipped). This is a deliberate deviation from dart's non-browser
 *     behavior, taken until FilesystemImporter is cross-platform; an EXPLICIT
 *     load path is still a caller request, so `loadPathImporter` fails loudly
 *     rather than dropping it silently. */
package ssg
package sass
package importer

/** Builds the importers that back load paths and the SASS_PATH environment variable, per platform.
  */
object LoadPathImporterPlatform {

  /** On Scala Native the filesystem-backed [[FilesystemImporter]] is unavailable, so an explicit `loadPaths` entry cannot be resolved. Fail loudly rather than dropping the load path silently.
    */
  def loadPathImporter(path: String): Importer =
    throw new UnsupportedOperationException(
      s"loadPaths are not supported on Scala Native (no FilesystemImporter): $path. " +
        "Pass an in-memory importer via `importers` instead."
    )

  /** dart-sass import_cache.dart:125 `if (isBrowser) return [...?importers];` skips the SASS_PATH block in a browser. Scala Native has no FilesystemImporter yet, so implicit SASS_PATH resolution
    * follows the same shape and is skipped (empty) rather than failing — a deliberate deviation from dart's non-browser behavior, until FilesystemImporter is cross-platform. Differs from an explicit
    * `loadPathImporter`, which is loud.
    */
  def sassPathImporters(): List[Importer] = Nil
}
