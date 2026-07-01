/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/import_cache.dart (_toImporters, import_cache.dart:119-135)
 * Original: Copyright (c) 2018 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Convention: dart-sass turns each load path into `FilesystemImporter(path)`
 *     (import_cache.dart:128-129) and every SASS_PATH entry into a
 *     `FilesystemImporter` (import_cache.dart:130-132). Since ISS-1154 made
 *     FilesystemImporter cross-platform (backed by ssg-commons FileOps /
 *     FilePath, which run on Scala.js under Node), both factories are real here:
 *     `loadPathImporter` builds a real Node-backed FilesystemImporter, and
 *     `sassPathImporters` reads SASS_PATH from Node's `process.env` and builds
 *     one FilesystemImporter per entry, splitting on Node's `path.delimiter`
 *     (the JS analogue of java.io.File.pathSeparator, which does not link on
 *     Scala.js).
 *
 *     dart-sass guards the whole load-path/SASS_PATH block behind
 *     `if (isBrowser) return [...?importers];` (import_cache.dart:125). Scala.js
 *     is the browser-capable target: `process` / `path` are acquired via a lazy
 *     `require` (same discipline as ssg-commons FilePathPlatform scalajs), so in
 *     a browser — where `require` is absent — `sassPathImporters` catches the
 *     require failure and yields an empty list, mirroring dart's isBrowser skip
 *     of SASS_PATH. Under Node the filesystem is real, so both factories work. */
package ssg
package sass
package importer

import scala.scalajs.js

/** Builds the importers that back load paths and the SASS_PATH environment variable, per platform.
  */
object LoadPathImporterPlatform {

  /** Node's `process`, acquired lazily so a browser bundle (no `require`) does not crash at module init. */
  private lazy val process: js.Dynamic = js.Dynamic.global.require("process")

  /** Node's `path`, acquired lazily for the same reason; `path.delimiter` is the JS analogue of java.io.File.pathSeparator. */
  private lazy val nodePath: js.Dynamic = js.Dynamic.global.require("path")

  /** dart-sass import_cache.dart:128-129: `for (var path in loadPaths) FilesystemImporter(path)`. Since ISS-1154 FilesystemImporter is Node-backed on Scala.js, so this is a real importer.
    */
  def loadPathImporter(path: String): Importer = new FilesystemImporter(path)

  /** dart-sass import_cache.dart:124/130-132: reads the SASS_PATH environment variable and turns each entry into a `FilesystemImporter`. Under Node this is honored; in a browser (`require` absent)
    * the require failure is caught and an empty list is returned, mirroring dart's `if (isBrowser) return [...?importers]` skip of SASS_PATH (import_cache.dart:125).
    */
  def sassPathImporters(): List[Importer] =
    try {
      val sassPath  = process.env.SASS_PATH.asInstanceOf[js.UndefOr[String]].toOption
      val delimiter = nodePath.delimiter.asInstanceOf[String]
      val separator = if (delimiter.nonEmpty) delimiter else ":"
      sassPathImportersFrom(sassPath, separator)
    } catch {
      case _: js.JavaScriptException => Nil
    }

  /** The pure core of [[sassPathImporters]], parameterized over the raw SASS_PATH value and the path separator so it can be exercised without the Node environment. dart-sass splits the variable on
    * `;` (Windows) or `:` and builds a `FilesystemImporter` per non-empty entry (import_cache.dart:130-132).
    */
  private[sass] def sassPathImportersFrom(sassPath: Option[String], separator: String): List[Importer] =
    sassPath.toList.flatMap { paths =>
      paths.split(separator).toList.filter(_.nonEmpty).map(loadPathImporter)
    }
}
