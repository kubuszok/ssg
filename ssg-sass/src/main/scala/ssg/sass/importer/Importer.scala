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
  * This is a simple stub that lets callers wire in package-style imports without requiring a real `node_modules` traversal (that job belongs to [[NodePackageImporter]]).
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

/** An importer resolving Node-style `pkg:` URLs. TODO. */
final class NodePackageImporter(val entryPoint: String) extends Importer {

  def canonicalize(url: String): Nullable[String]         = Nullable.empty
  def load(url:         String): Nullable[ImporterResult] = Nullable.empty
}
