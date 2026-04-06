/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/importer/result.dart,
 *              lib/src/importer/canonicalize_context.dart
 * Original: Copyright (c) 2017 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: result.dart -> ImporterResult.scala (merged)
 *   Convention: Skeleton — Uri modeled as String
 */
package ssg
package sass
package importer

/** The result of importing a Sass stylesheet, as returned by
  * [[Importer.load]].
  */
final class ImporterResult(
  val contents: String,
  val sourceMapUrl: Nullable[String],
  val syntax: Syntax
) {

  override def toString: String = s"ImporterResult(syntax=$syntax, ${contents.length} chars)"
}

object ImporterResult {

  def apply(
    contents: String,
    syntax: Syntax = Syntax.Scss,
    sourceMapUrl: Nullable[String] = Nullable.empty
  ): ImporterResult = new ImporterResult(contents, sourceMapUrl, syntax)
}

/** The context passed to [[Importer.canonicalize]] about the URL being
  * resolved — the containing URL and whether it's being imported from an
  * `@import` rule.
  */
final class CanonicalizeContext(
  val containingUrl: Nullable[String],
  val fromImport: Boolean
)
