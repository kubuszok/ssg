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
 *   Convention: In-memory cache of canonicalization + parsed stylesheets
 *   Idiom: URIs modeled as Strings; parses via ScssParser/CssParser based
 *     on the importer's declared syntax (or the canonical URL's extension)
 */
package ssg
package sass

import scala.collection.mutable
import scala.language.implicitConversions
import ssg.sass.ast.sass.Stylesheet
import ssg.sass.importer.{ Importer, ImporterResult }
import ssg.sass.parse.{ CssParser, ScssParser, StylesheetParser }

/** An in-memory cache of parsed stylesheets, used by the evaluator to dedupe parses across multiple `@use`/`@forward`/`@import` of the same URL.
  */
final class ImportCache(
  val importers: List[Importer] = Nil,
  val loadPaths: List[String] = Nil,
  val logger:    Nullable[Logger] = Nullable.empty
) {

  private val canonicalizedCache: mutable.Map[String, (Importer, String)] = mutable.Map.empty
  private val importCache:        mutable.Map[String, Stylesheet]         = mutable.Map.empty
  private val resultsCache:       mutable.Map[String, ImporterResult]     = mutable.Map.empty

  /** Canonicalizes [url] by walking [importers] and returning the first one that resolves it. Returns the resolving importer and the canonical URL.
    */
  def canonicalize(
    url:          String,
    baseImporter: Nullable[Importer] = Nullable.empty,
    baseUrl:      Nullable[String] = Nullable.empty,
    forImport:    Boolean = false
  ): Nullable[(Importer, String, String)] = {
    val _        = (baseUrl, forImport)
    val cacheKey = url
    canonicalizedCache.get(cacheKey) match {
      case Some((imp, canonical)) =>
        Nullable((imp, canonical, url))
      case scala.None =>
        val all:   List[Importer]                       = baseImporter.toOption.toList ++ importers
        var found: Nullable[(Importer, String, String)] = Nullable.empty
        val it = all.iterator
        while (it.hasNext && found.isEmpty) {
          val imp       = it.next()
          val canonical = imp.canonicalize(url)
          canonical.foreach { c =>
            canonicalizedCache.update(cacheKey, (imp, c))
            found = Nullable((imp, c, url))
          }
        }
        found
    }
  }

  /** Imports the stylesheet at [canonicalUrl] through [importer], parsing and caching the result. Subsequent calls for the same canonical URL return the cached [[Stylesheet]] without re-parsing or
    * re-invoking the importer.
    */
  def importCanonical(
    importer:     Importer,
    canonicalUrl: String,
    originalUrl:  Nullable[String] = Nullable.empty
  ): Nullable[Stylesheet] = {
    val _ = originalUrl
    importCache.get(canonicalUrl) match {
      case Some(s)    => Nullable(s)
      case scala.None =>
        val loaded = importer.load(canonicalUrl)
        loaded.fold[Nullable[Stylesheet]](Nullable.empty) { result =>
          resultsCache.update(canonicalUrl, result)
          // Pick the parser based on the importer's declared syntax
          // (falling back to the canonical URL's extension when the
          // importer returned the SCSS default). A `.css` import goes
          // through the strict `CssParser`, so Sass-only syntax in a
          // plain-CSS file raises a `SassFormatException` instead of
          // being silently evaluated.
          val effectiveSyntax: Syntax =
            if (result.syntax == Syntax.Scss) Syntax.forPath(canonicalUrl)
            else result.syntax
          val parser: StylesheetParser = effectiveSyntax match {
            case Syntax.Css  => new CssParser(result.contents, Nullable(canonicalUrl))
            case Syntax.Sass =>
              // The indented-syntax parser isn't currently wired through
              // the import cache — fall back to SCSS so existing tests
              // are unaffected.
              new ScssParser(result.contents, Nullable(canonicalUrl))
            case Syntax.Scss => new ScssParser(result.contents, Nullable(canonicalUrl))
          }
          val sheet = parser.parse()
          importCache.update(canonicalUrl, sheet)
          Nullable(sheet)
        }
    }
  }

  /** Returns the cached [[ImporterResult]] for [canonicalUrl], if any. */
  def resultFor(canonicalUrl: String): Nullable[ImporterResult] =
    resultsCache.get(canonicalUrl) match {
      case Some(r)    => Nullable(r)
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
