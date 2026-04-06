/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/import_cache.dart
 * Original: Copyright (c) 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: import_cache.dart -> ImportCache.scala
 *   Convention: Skeleton — caches empty, public API surface only
 */
package ssg
package sass

import scala.collection.mutable
import scala.language.implicitConversions
import ssg.sass.ast.sass.Stylesheet
import ssg.sass.importer.{Importer, ImporterResult}

/** An in-memory cache of parsed stylesheets, used by the evaluator. */
final class ImportCache(
  val importers: List[Importer] = Nil,
  val loadPaths: List[String] = Nil,
  val logger: Nullable[Logger] = Nullable.empty
) {

  private val canonicalizedCache: mutable.Map[String, (Importer, String)] = mutable.Map.empty
  private val importCache: mutable.Map[String, Stylesheet] = mutable.Map.empty
  private val resultsCache: mutable.Map[String, ImporterResult] = mutable.Map.empty

  /** Canonicalizes [url] against all importers and load paths. TODO. */
  def canonicalize(
    url: String,
    baseImporter: Nullable[Importer] = Nullable.empty,
    baseUrl: Nullable[String] = Nullable.empty,
    forImport: Boolean = false
  ): Nullable[(Importer, String, String)] = Nullable.empty

  /** Imports the stylesheet at [canonicalUrl] through [importer]. TODO. */
  def importCanonical(
    importer: Importer,
    canonicalUrl: String,
    originalUrl: Nullable[String] = Nullable.empty
  ): Nullable[Stylesheet] =
    importCache.get(canonicalUrl) match {
      case Some(s) => s
      case scala.None => Nullable.empty
    }

  /** Clears cached entries for [canonicalUrl]. */
  def clearImport(canonicalUrl: String): Unit = {
    val _ = importCache.remove(canonicalUrl)
    val _ = resultsCache.remove(canonicalUrl)
    val _ = canonicalizedCache.remove(canonicalUrl)
  }
}

object ImportCache {

  /** An [[ImportCache]] that contains no importers. */
  val none: ImportCache = new ImportCache()
}
