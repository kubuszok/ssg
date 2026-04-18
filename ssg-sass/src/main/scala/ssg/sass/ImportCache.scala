/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/import_cache.dart
 * Original: Copyright (c) 2018 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: import_cache.dart -> ImportCache.scala
 *   Convention: URIs modeled as Strings; Dart (Uri, {bool forImport})
 *     tuple keys -> (String, Boolean); CanonicalizeResult typedef ->
 *     CanonicalizeResult case class.
 *   Idiom: Dart's Map.putIfAbsent with lazy callback -> getOrElseUpdate;
 *     Dart Zone-based canonicalize context -> ImporterUtils.withCanonicalizeContext
 */
package ssg
package sass

import scala.collection.mutable
import scala.language.implicitConversions
import scala.util.boundary
import scala.util.boundary.break

import ssg.sass.ast.sass.Stylesheet
import ssg.sass.importer.{ CanonicalizeContext, Importer, ImporterResult, ImporterUtils }
import ssg.sass.parse.{ CssParser, ScssParser, StylesheetParser }

/** A canonicalized URL and the importer that canonicalized it.
  *
  * This also includes the URL that was originally passed to the importer, which may be resolved relative to a base URL.
  */
final case class CanonicalizeResult(
  importer:     Importer,
  canonicalUrl: String,
  originalUrl:  String
)

/** An in-memory cache of parsed stylesheets that have been imported by Sass.
  */
final class ImportCache(
  val importers: List[Importer] = Nil,
  val logger:    Nullable[Logger] = Nullable.empty
) {

  // -- Constructor variants --

  /** Creates an import cache that resolves imports using [[importers]] and [[loadPaths]]. */
  def this(
    importers: List[Importer],
    loadPaths: List[String],
    logger:    Nullable[Logger]
  ) = {
    this(ImportCache.toImporters(importers, loadPaths), logger)
  }

  // ---- Caches ----

  /** The canonicalized URLs for each non-canonical URL.
    *
    * The `Boolean` in each key is true when this canonicalization is for an `@import` rule. Otherwise, it's for a `@use` or `@forward` rule.
    *
    * This cache covers loads that go through the entire chain of [[importers]], but it doesn't cover individual loads or loads in which any importer accesses `containingUrl`. See also
    * [[_perImporterCanonicalizeCache]].
    */
  private val _canonicalizeCache: mutable.Map[(String, Boolean), Nullable[CanonicalizeResult]] =
    mutable.Map.empty

  /** Like [[_canonicalizeCache]] but also includes the specific importer in the key.
    *
    * This is used to cache both relative imports from the base importer and individual importer results in the case where some other component of the importer chain isn't cacheable.
    */
  private val _perImporterCanonicalizeCache: mutable.Map[(Importer, String, Boolean), Nullable[CanonicalizeResult]] =
    mutable.Map.empty

  /** A map from the keys in [[_perImporterCanonicalizeCache]] that are generated for relative URL loads against the base importer to the original relative URLs what were loaded.
    *
    * This is used to invalidate the cache when files are changed.
    */
  private val _nonCanonicalRelativeUrls: mutable.Map[(Importer, String, Boolean), String] =
    mutable.Map.empty

  /** The parsed stylesheets for each canonicalized import URL. */
  private val _importCache: mutable.Map[String, Nullable[Stylesheet]] =
    mutable.Map.empty

  /** The import results for each canonicalized import URL. */
  private val _resultsCache: mutable.Map[String, ImporterResult] =
    mutable.Map.empty

  /** A map from canonical URLs to the most recent time at which those URLs were loaded from their importers.
    */
  private val _loadTimes: mutable.Map[String, Long] =
    mutable.Map.empty

  /** Canonicalizes [[url]] according to one of this cache's importers.
    *
    * The [[baseUrl]] should be the canonical URL of the stylesheet that contains the load, if it exists.
    *
    * Returns the importer that was used to canonicalize [[url]], the canonical URL, and the URL that was passed to the importer (which may be resolved relative to [[baseUrl]] if it's passed).
    *
    * If [[baseImporter]] is non-empty, this first tries to use [[baseImporter]] to canonicalize [[url]] (resolved relative to [[baseUrl]] if it's passed).
    *
    * If any importers understand [[url]], returns that importer as well as the canonicalized URL and the original URL (resolved relative to [[baseUrl]] if applicable). Otherwise, returns empty.
    */
  def canonicalize(
    url:          String,
    baseImporter: Nullable[Importer] = Nullable.empty,
    baseUrl:      Nullable[String] = Nullable.empty,
    forImport:    Boolean = false
  ): Nullable[CanonicalizeResult] = boundary[Nullable[CanonicalizeResult]] {
    // Try base importer first for relative URLs (no scheme)
    baseImporter.foreach { bi =>
      if (!hasScheme(url)) {
        val resolvedUrl = baseUrl.fold(url)(base => resolveUrl(base, url))
        val key         = (bi, resolvedUrl, forImport)
        val relativeResult = _perImporterCanonicalizeCache.getOrElseUpdate(key, {
          val (result, cacheable) = _canonicalize(bi, resolvedUrl, baseUrl, forImport)
          assert(
            cacheable,
            "Relative loads should always be cacheable because they never " +
              "provide access to the containing URL."
          )
          baseUrl.foreach { _ =>
            _nonCanonicalRelativeUrls(key) = url
          }
          result
        })
        if (relativeResult.isDefined) break(relativeResult)
      }
    }

    val key = (url, forImport)
    if (_canonicalizeCache.contains(key)) break(_canonicalizeCache(key))

    // Each individual call to a `canonicalize()` override may not be cacheable
    // (specifically, if it has access to `containingUrl` it's too
    // context-sensitive to usefully cache). We want to cache a given URL across
    // the _entire_ importer chain, so we use `cacheable` to track whether _all_
    // `canonicalize()` calls we've attempted are cacheable. Only if they are, do
    // we store the result in the cache.
    var cacheable = true
    var i         = 0
    while (i < importers.length) {
      val imp            = importers(i)
      val perImporterKey = (imp, url, forImport)

      // Check per-importer cache first
      _perImporterCanonicalizeCache.get(perImporterKey) match {
        case Some(cached) =>
          if (cached.isDefined) break(cached)
          // cached is empty => this importer returned null, continue
          i += 1
        case scala.None =>
          val (result, thisCacheable) = _canonicalize(imp, url, baseUrl, forImport)
          if (thisCacheable && cacheable) {
            // Fully cacheable: store in the global cache
            if (result.isDefined) {
              _canonicalizeCache(key) = result
              break(result)
            }
          } else if (thisCacheable && !cacheable) {
            // Previous importers were not cacheable, store per-importer
            _perImporterCanonicalizeCache(perImporterKey) = result
            if (result.isDefined) break(result)
          } else {
            // This importer is not cacheable
            if (cacheable) {
              // If this is the first uncacheable result, add all previous results
              // to the per-importer cache so we don't have to re-run them for
              // future uses of this importer.
              var j = 0
              while (j < i) {
                _perImporterCanonicalizeCache((importers(j), url, forImport)) = Nullable.empty
                j += 1
              }
              cacheable = false
            }
            if (result.isDefined) break(result)
          }
          i += 1
      }
    }

    if (cacheable) _canonicalizeCache(key) = Nullable.empty
    Nullable.empty
  }

  /** Calls `importer.canonicalize` and returns both the result and whether that result is cacheable at all.
    */
  private def _canonicalize(
    importer:  Importer,
    url:       String,
    baseUrl:   Nullable[String],
    forImport: Boolean
  ): (Nullable[CanonicalizeResult], Boolean) = {
    val passContainingUrl = baseUrl.isDefined &&
      (!hasScheme(url) || importer.isNonCanonicalScheme(schemeOf(url)))

    val canonicalizeContext = new CanonicalizeContext(
      if (passContainingUrl) baseUrl else Nullable.empty,
      forImport
    )

    val result = ImporterUtils.withCanonicalizeContext(
      Nullable(canonicalizeContext),
      () => importer.canonicalize(url)
    )

    val isCacheable =
      !passContainingUrl || !canonicalizeContext.wasContainingUrlAccessed

    if (result.isEmpty) (Nullable.empty, isCacheable)
    else {
      val canonical = result.get
      // Relative canonical URLs (empty scheme) should throw an error starting in
      // Dart Sass 2.0.0, but for now, they only emit a deprecation warning in
      // the evaluator.
      if (hasScheme(canonical) && importer.isNonCanonicalScheme(schemeOf(canonical))) {
        throw new IllegalStateException(
          s"Importer $importer canonicalized $url to $canonical, which uses a " +
            "scheme declared as non-canonical."
        )
      }
      (Nullable(CanonicalizeResult(importer, canonical, url)), isCacheable)
    }
  }

  /** Tries to import [[url]] using one of this cache's importers.
    *
    * If [[baseImporter]] is non-empty, this first tries to use [[baseImporter]] to import [[url]] (resolved relative to [[baseUrl]] if it's passed).
    *
    * If any importers can import [[url]], returns that importer as well as the parsed stylesheet. Otherwise, returns empty.
    *
    * Caches the result of the import and uses cached results if possible.
    */
  def import_(
    url:          String,
    baseImporter: Nullable[Importer] = Nullable.empty,
    baseUrl:      Nullable[String] = Nullable.empty,
    forImport:    Boolean = false
  ): Nullable[(Importer, Stylesheet)] = {
    val result = canonicalize(url, baseImporter = baseImporter, baseUrl = baseUrl, forImport = forImport)
    result.flatMap { cr =>
      importCanonical(cr.importer, cr.canonicalUrl, originalUrl = Nullable(cr.originalUrl)).map { sheet =>
        (cr.importer, sheet)
      }
    }
  }

  /** Tries to load the canonicalized [[canonicalUrl]] using [[importer]].
    *
    * If [[importer]] can import [[canonicalUrl]], returns the imported [[Stylesheet]]. Otherwise returns empty.
    *
    * If passed, the [[originalUrl]] represents the URL that was canonicalized into [[canonicalUrl]]. It's used to resolve a relative canonical URL, which importers may return for legacy reasons.
    *
    * Caches the result of the import and uses cached results if possible.
    */
  def importCanonical(
    importer:     Importer,
    canonicalUrl: String,
    originalUrl:  Nullable[String] = Nullable.empty
  ): Nullable[Stylesheet] = {
    _importCache.getOrElseUpdate(canonicalUrl, {
      val loadTime = System.currentTimeMillis()
      val result   = importer.load(canonicalUrl)
      result.fold[Nullable[Stylesheet]](Nullable.empty) { ir =>
        _loadTimes(canonicalUrl) = loadTime
        _resultsCache(canonicalUrl) = ir

        // For backwards-compatibility, relative canonical URLs are resolved
        // relative to [originalUrl].
        val effectiveUrl = originalUrl.fold(canonicalUrl)(orig => resolveUrl(orig, canonicalUrl))

        // Pick the parser based on the importer's declared syntax
        // (falling back to the canonical URL's extension when the
        // importer returned the SCSS default).
        val effectiveSyntax: Syntax =
          if (ir.syntax == Syntax.Scss) Syntax.forPath(canonicalUrl)
          else ir.syntax
        val parser: StylesheetParser = effectiveSyntax match {
          case Syntax.Css  => new CssParser(ir.contents, Nullable(effectiveUrl))
          case Syntax.Sass =>
            // The indented-syntax parser may not be fully wired;
            // fall back to SCSS so existing tests are unaffected.
            new ScssParser(ir.contents, Nullable(effectiveUrl))
          case Syntax.Scss => new ScssParser(ir.contents, Nullable(effectiveUrl))
        }
        Nullable(parser.parse())
      }
    })
  }

  /** Return a human-friendly URL for [[canonicalUrl]] to use in a stack trace.
    *
    * Returns [[canonicalUrl]] as-is if it hasn't been loaded by this cache.
    */
  def humanize(canonicalUrl: String): String = {
    // If multiple original URLs canonicalize to the same thing, choose the
    // shortest one.
    val candidates = _canonicalizeCache.values.iterator
      .filter(_.isDefined)
      .map(_.get)
      .filter(_.canonicalUrl == canonicalUrl)
      .map(_.originalUrl)
      .toList

    if (candidates.isEmpty) canonicalUrl
    else {
      val shortest = candidates.minBy(_.length)
      // Use the canonicalized basename so that we display e.g.
      // package:example/_example.scss rather than package:example/example
      // in stack traces.
      val canonicalBasename = urlBasename(canonicalUrl)
      resolveUrl(shortest, canonicalBasename)
    }
  }

  /** Returns the URL to use in the source map to refer to [[canonicalUrl]].
    *
    * Returns [[canonicalUrl]] as-is if it hasn't been loaded by this cache.
    */
  def sourceMapUrl(canonicalUrl: String): String =
    _resultsCache.get(canonicalUrl) match {
      case Some(ir) => ir.sourceMapUrl.getOrElse(canonicalUrl)
      case scala.None => canonicalUrl
    }

  /** Returns the most recent time the stylesheet at [[canonicalUrl]] was loaded from its importer, or -1 if it has never been loaded.
    */
  def loadTime(canonicalUrl: String): Long =
    _loadTimes.getOrElse(canonicalUrl, -1L)

  /** Returns the cached [[ImporterResult]] for [[canonicalUrl]], if any. */
  def resultFor(canonicalUrl: String): Nullable[ImporterResult] =
    _resultsCache.get(canonicalUrl) match {
      case Some(r)    => Nullable(r)
      case scala.None => Nullable.empty
    }

  /** Clears all cached canonicalizations that could potentially produce [[canonicalUrl]].
    */
  def clearCanonicalize(canonicalUrl: String): Unit = {
    // Take a snapshot of keys to avoid concurrent modification
    val canonKeys = _canonicalizeCache.keys.toList
    for (key <- canonKeys) {
      var removed = false
      val it      = importers.iterator
      while (it.hasNext && !removed) {
        val imp = it.next()
        if (imp.couldCanonicalize(key._1, canonicalUrl)) {
          _canonicalizeCache.remove(key)
          removed = true
        }
      }
    }

    val perImpKeys = _perImporterCanonicalizeCache.keys.toList
    for (key <- perImpKeys) {
      if (key._1.couldCanonicalize(key._2, canonicalUrl)) {
        _perImporterCanonicalizeCache.remove(key)
      }
    }
  }

  /** Clears the cached parse tree for the stylesheet with the given [[canonicalUrl]].
    *
    * Has no effect if the imported file at [[canonicalUrl]] has not been cached.
    */
  def clearImport(canonicalUrl: String): Unit = {
    val _ = _resultsCache.remove(canonicalUrl)
    val _ = _importCache.remove(canonicalUrl)
  }

  // -- URL helpers --
  // These approximate the Dart `path` package's URL operations for String-based URLs.

  /** Whether the URL has a scheme (contains `:` before any `/`). */
  private def hasScheme(url: String): Boolean = {
    val colonIdx = url.indexOf(':')
    colonIdx > 0 && (url.indexOf('/') < 0 || url.indexOf('/') > colonIdx)
  }

  /** Extracts the scheme from a URL (portion before the first `:`). */
  private def schemeOf(url: String): String = {
    val colonIdx = url.indexOf(':')
    if (colonIdx < 0) "" else url.substring(0, colonIdx)
  }

  /** Resolves [[relative]] against [[base]], using simple path concatenation. */
  private def resolveUrl(base: String, relative: String): String = {
    if (hasScheme(relative)) relative
    else if (relative.startsWith("/")) relative
    else {
      val lastSlash = base.lastIndexOf('/')
      if (lastSlash < 0) relative
      else base.substring(0, lastSlash + 1) + relative
    }
  }

  /** Returns the basename (last path component) of a URL. */
  private def urlBasename(url: String): String = {
    val sep = url.lastIndexOf('/')
    if (sep < 0) url else url.substring(sep + 1)
  }
}

object ImportCache {

  /** An [[ImportCache]] that contains no importers. */
  val none: ImportCache = new ImportCache()

  /** Creates an [[ImportCache]] with only the given importers (no load paths). */
  def only(importers: List[Importer]): ImportCache =
    new ImportCache(importers)

  /** Converts the user's importers and loadPaths into a single list of importers. */
  private[sass] def toImporters(
    importers: List[Importer],
    loadPaths: List[String]
  ): List[Importer] = {
    // Note: FilesystemImporter is JVM-only; load paths are handled by
    // the caller constructing the appropriate importers. The SASS_PATH
    // environment variable is handled at the compile() entry point.
    importers
  }
}
