/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/importer.dart, lib/src/importer/filesystem.dart,
 *              lib/src/importer/no_op.dart, lib/src/importer/package.dart,
 *              lib/src/importer/node_package.dart
 * Original: Copyright (c) 2017 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: importer.dart -> Importer.scala (merged family)
 *   Convention: Uri modeled as String
 *   Idiom: FilesystemImporter is in src/main/scala-jvm (JVM only) since it
 *          requires java.nio.file. Cross-platform code should use NoOpImporter
 *          or construct their own implementation.
 */
package ssg
package sass
package importer

/** An interface for importers that resolve URLs in `@import`/`@use`/`@forward` to stylesheet contents.
  */
trait Importer {

  /** Canonicalizes [url] to an absolute URL, or returns `Nullable.empty` if this importer doesn't recognize it.
    */
  def canonicalize(url: String): Nullable[String]

  /** Loads the contents of the stylesheet at the canonical [url]. */
  def load(url: String): Nullable[ImporterResult]

  /** Returns the modification time of the stylesheet at [url]. */
  def modificationTime(url: String): Long = System.currentTimeMillis()

  /** Whether this importer could potentially canonicalize [url] to [canonicalUrl].
    */
  def couldCanonicalize(url: String, canonicalUrl: String): Boolean = true

  /** Whether [scheme] is known to this importer as non-canonical. */
  def isNonCanonicalScheme(scheme: String): Boolean = false
}

object Importer {

  /** An importer that never imports any stylesheets. */
  val noOp: Importer = new NoOpImporter()
}

/** An importer that never imports any stylesheets. */
final class NoOpImporter extends Importer {

  def canonicalize(url: String): Nullable[String]         = Nullable.empty
  def load(url:         String): Nullable[ImporterResult] = Nullable.empty

  override def toString: String = "(unknown)"
}

// FilesystemImporter is JVM-only and lives in src/main/scala-jvm/

/** An importer resolving `pkg:` URLs via a map of package name -> root path.
  *
  * A URL of the form `pkg:name/rest/of/path` is rewritten to `<root>/rest/of/path` (where `<root>` is looked up in [[packages]]) and delegated to [[delegate]] for actual canonicalization and loading.
  *
  * This lets callers wire in package-style imports without requiring a real `node_modules` traversal (that job belongs to [[NodePackageImporter]]).
  */
final class PackageImporter(
  val packages: Map[String, String],
  val delegate: Importer = Importer.noOp
) extends Importer {

  private val Prefix = "pkg:"

  /** Rewrites a `pkg:name/rest` URL into a delegate URL, or empty if either the prefix is missing or the package name isn't registered.
    */
  private def rewrite(url: String): Nullable[String] =
    if (!url.startsWith(Prefix)) Nullable.empty
    else {
      val rest           = url.substring(Prefix.length)
      val slashIdx       = rest.indexOf('/')
      val (name, suffix) =
        if (slashIdx < 0) (rest, "")
        else (rest.substring(0, slashIdx), rest.substring(slashIdx + 1))
      packages.get(name) match {
        case Some(root) =>
          val sep = if (root.endsWith("/") || suffix.isEmpty) "" else "/"
          Nullable(root + sep + suffix)
        case scala.None => Nullable.empty
      }
    }

  def canonicalize(url: String): Nullable[String] =
    rewrite(url).flatMap(delegate.canonicalize)

  def load(url: String): Nullable[ImporterResult] =
    delegate.load(url)
}

// NodePackageImporter is JVM-only and lives in src/main/scala-jvm/

/** A cross-platform, in-memory importer backed by a map of URL -> SCSS source.
  *
  * Useful for tests and any environment without a filesystem. Resolves imports using the same order as [[FilesystemImporter]]:
  *   1. exact `basename.scss`
  *   2. `_basename.scss` (partial)
  *   3. `basename/_index.scss` / `basename/index.scss`
  *
  * Keys in [[sources]] should be the canonical form (what [[canonicalize]] returns) — typically the resolved file name including extension, e.g. `_colors.scss` or `vars.scss`.
  */
final class MapImporter(val sources: Map[String, String]) extends Importer {

  /** Normalizes a path by resolving `.` and `..` segments. */
  private def normalizePath(path: String): String = {
    val segments = path.split("/").toList
    val result = scala.collection.mutable.ListBuffer.empty[String]
    for (seg <- segments) {
      seg match {
        case "."  => () // skip
        case ".." =>
          if (result.nonEmpty && result.last != "..") result.remove(result.length - 1)
          else result += seg
        case _ => result += seg
      }
    }
    result.mkString("/")
  }

  /** Generates candidate paths for a given relative URL.
    *
    * Follows the same resolution order as [[ImporterFileUtils.resolveImportPath]]:
    *   1. If the path has an explicit `.sass`/`.scss`/`.css` extension,
    *      try the exact path and the partial (`_`-prefixed) variant.
    *   2. If no extension, try `.sass`, `.scss` extensions (and their
    *      partial variants), then `.css` as a fallback.
    *   3. For extensionless paths, also try directory-index resolution:
    *      `<path>/_index.scss`, `<path>/index.scss`,
    *      `<path>/_index.sass`, `<path>/index.sass`.
    */
  private def candidates(relative: String): List[String] = {
    val slashIdx = relative.lastIndexOf('/')
    val parent   = if (slashIdx < 0) "" else relative.substring(0, slashIdx + 1)
    val fileName = if (slashIdx < 0) relative else relative.substring(slashIdx + 1)

    // Only treat `.sass`, `.scss`, `.css` as known extensions, matching
    // ImporterFileUtils.resolveImportPath. Other dots (e.g. `dir.foo`)
    // are directory names, not extensions.
    val hasKnownExtension = {
      val dot = fileName.lastIndexOf('.')
      dot > 0 && {
        val ext = fileName.substring(dot)
        ext == ".sass" || ext == ".scss" || ext == ".css"
      }
    }
    val basenames: List[String] =
      if (hasKnownExtension) List(fileName, s"_$fileName").distinct
      else
        List(
          // .sass before .scss matches ImporterFileUtils.tryPathWithExtensions order
          s"$fileName.sass",
          s"_$fileName.sass",
          s"$fileName.scss",
          s"_$fileName.scss",
          // .css as fallback (same as ImporterFileUtils: only if .sass/.scss not found)
          s"$fileName.css",
          s"_$fileName.css"
        )

    val direct = basenames.map(parent + _)

    val index: List[String] =
      if (hasKnownExtension) Nil
      else
        List(
          s"$parent$fileName/_index.scss",
          s"$parent$fileName/index.scss",
          s"$parent$fileName/_index.sass",
          s"$parent$fileName/index.sass"
        )

    direct ++ index
  }

  def canonicalize(url: String): Nullable[String] = {
    val stripped  = if (url.startsWith("file:")) url.stripPrefix("file:") else url
    // Normalize `..` and `.` segments so `../foo/bar` resolves correctly
    // when the key in the map is a clean relative path.
    val cleaned = if (stripped.contains("..") || stripped.contains("./")) normalizePath(stripped) else stripped
    if (sources.contains(cleaned)) Nullable(cleaned)
    else {
      val cands = candidates(cleaned)
      var result: Nullable[String] = Nullable.empty
      var i = 0
      while (result.isEmpty && i < cands.length) {
        val c = cands(i)
        if (sources.contains(c)) result = Nullable(c)
        i += 1
      }
      result
    }
  }

  def load(url: String): Nullable[ImporterResult] =
    sources.get(url) match {
      case Some(src)  => Nullable(ImporterResult(src, Syntax.forPath(url)))
      case scala.None => Nullable.empty
    }

  override def toString: String = "(map)"
}
