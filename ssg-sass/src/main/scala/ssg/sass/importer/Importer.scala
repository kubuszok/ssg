/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/importer.dart
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
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/importer.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
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
final class MapImporter(
  val sources: Map[String, String],
  val baseDir: Nullable[String] = Nullable.Null
) extends Importer {

  /** Normalizes a path by resolving `.` and `..` segments. */
  private def normalizePath(path: String): String = {
    val segments = path.split("/").toList
    val result   = scala.collection.mutable.ListBuffer.empty[String]
    for (seg <- segments)
      seg match {
        case "."  => () // skip
        case ".." =>
          if (result.nonEmpty && result.last != "..") result.remove(result.length - 1)
          else result += seg
        case _ => result += seg
      }
    result.mkString("/")
  }

  /** If [[paths]] contains exactly one path, returns it. If it contains no paths, returns empty. If it contains more than one, throws an exception.
    *
    * Mirrors ImporterFileUtils.exactlyOne.
    */
  private def exactlyOne(paths: List[String]): Nullable[String] = paths match {
    case Nil         => Nullable.empty
    case head :: Nil => Nullable(head)
    case ambiguous   =>
      throw new IllegalStateException(
        "It's not clear which file to import. Found:\n" +
          ambiguous.map(p => "  " + p).mkString("\n")
      )
  }

  /** Like [[tryPath]] but checks `.sass`, `.scss`, and `.css` extensions. Mirrors ImporterFileUtils.tryPathWithExtensions.
    */
  private def tryPathWithExtensions(path: String): List[String] = {
    val result = tryPath(path + ".sass") ++ tryPath(path + ".scss")
    if (result.nonEmpty) result else tryPath(path + ".css")
  }

  /** Returns the [[path]] and/or the partial with the same name, if either or both exists in [[sources]]. Mirrors ImporterFileUtils.tryPath.
    */
  private def tryPath(path: String): List[String] = {
    val slashIdx = path.lastIndexOf('/')
    val dir      = if (slashIdx < 0) "" else path.substring(0, slashIdx + 1)
    val base     = if (slashIdx < 0) path else path.substring(slashIdx + 1)
    val partial  = s"${dir}_$base"
    val result   = scala.collection.mutable.ArrayBuffer.empty[String]
    if (sources.contains(partial)) result += partial
    if (sources.contains(path)) result += path
    result.toList
  }

  def canonicalize(url: String): Nullable[String] = {
    val stripped = if (url.startsWith("file:")) url.stripPrefix("file:") else url
    // Normalize `..` and `.` segments so `../foo/bar` resolves correctly
    // when the key in the map is a clean relative path.
    val cleaned0 = if (stripped.contains("..") || stripped.contains("./")) normalizePath(stripped) else stripped

    // If the URL is absolute and we have a baseDir, try to produce a relative
    // path that matches our source keys. First try stripping the baseDir
    // prefix (for same-directory or child-directory files). If the URL is NOT
    // under baseDir (e.g. `@use "../utils"` resolved to a parent directory),
    // compute a relative path with `..` segments so it matches map keys built
    // by the test runner's archive-sibling logic.
    val cleaned =
      if (baseDir.isDefined && cleaned0.startsWith("/")) {
        val bd = baseDir.get
        if (cleaned0.startsWith(bd)) cleaned0.substring(bd.length)
        else {
          // Compute relative path from baseDir to the target URL.
          // Both are absolute paths; find the common prefix and add `..`
          // segments for each remaining component in baseDir.
          val bdParts  = bd.stripSuffix("/").split("/").toList
          val urlParts = cleaned0.split("/").toList
          var common   = 0
          while (common < bdParts.length && common < urlParts.length && bdParts(common) == urlParts(common)) common += 1
          val ups       = bdParts.length - common
          val remainder = urlParts.drop(common)
          if (ups > 0 && remainder.nonEmpty) {
            ("../" * ups) + remainder.mkString("/")
          } else {
            cleaned0 // fallback: keep the absolute URL (won't match map keys)
          }
        }
      } else cleaned0

    val slashIdx = cleaned.lastIndexOf('/')
    val fileName = if (slashIdx < 0) cleaned else cleaned.substring(slashIdx + 1)

    // Check if the file has a known Sass extension
    val hasKnownExtension = {
      val dot = fileName.lastIndexOf('.')
      dot > 0 && {
        val ext = fileName.substring(dot)
        ext == ".sass" || ext == ".scss" || ext == ".css"
      }
    }

    // dart-sass: only resolve an exact-match (no extension trial) when the URL
    // already has a known Sass extension. Files without extensions (e.g.
    // `other` with no `.scss`/`.sass`/`.css` suffix) are NOT valid import
    // targets.
    if (hasKnownExtension && sources.contains(cleaned)) return Nullable(cleaned)

    if (hasKnownExtension) {
      // With explicit extension: in @import context, try .import variant first
      val importOnly = ImporterUtils.ifInImport { () =>
        val dot  = cleaned.lastIndexOf('.')
        val ext  = cleaned.substring(dot)
        val base = cleaned.substring(0, dot)
        exactlyOne(tryPath(s"$base.import$ext"))
      }.flatten
      importOnly.orElse(exactlyOne(tryPath(cleaned)))
    } else {
      // Without extension: in @import context, try .import variant first
      val importOnly = ImporterUtils.ifInImport { () =>
        exactlyOne(tryPathWithExtensions(s"$cleaned.import"))
      }.flatten
      importOnly.orElse(exactlyOne(tryPathWithExtensions(cleaned))).orElse {
        // Try directory index resolution
        val importIndex = ImporterUtils.ifInImport { () =>
          exactlyOne(tryPathWithExtensions(s"$cleaned/index.import"))
        }.flatten
        importIndex.orElse(exactlyOne(tryPathWithExtensions(s"$cleaned/index")))
      }
    }
  }

  def load(url: String): Nullable[ImporterResult] =
    sources.get(url) match {
      case Some(src)  => Nullable(ImporterResult(src, Syntax.forPath(url)))
      case scala.None => Nullable.empty
    }

  override def toString: String = "(map)"
}
