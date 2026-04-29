/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/importer/result.dart
 *              lib/src/importer/canonicalize_context.dart
 * Original: Copyright (c) 2017 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: result.dart -> ImporterResult.scala (merged)
 *   Convention: Skeleton — Uri modeled as String
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/importer/result.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package importer

/** The result of importing a Sass stylesheet, as returned by [[Importer.load]].
  */
final class ImporterResult(
  val contents:     String,
  val sourceMapUrl: Nullable[String],
  val syntax:       Syntax
) {

  override def toString: String = s"ImporterResult(syntax=$syntax, ${contents.length} chars)"
}

object ImporterResult {

  def apply(
    contents:     String,
    syntax:       Syntax = Syntax.Scss,
    sourceMapUrl: Nullable[String] = Nullable.empty
  ): ImporterResult = new ImporterResult(contents, sourceMapUrl, syntax)
}

/** Contextual information used by importers' `canonicalize` method.
  *
  * Tracks whether the [[containingUrl]] has been accessed, which determines whether the canonicalization result is cacheable.
  */
final class CanonicalizeContext(
  private val _containingUrl: Nullable[String],
  private var _fromImport:    Boolean
) {

  /** Whether the Sass compiler is currently evaluating an `@import` rule. */
  def fromImport: Boolean = _fromImport

  /** The URL of the stylesheet that contains the current load.
    *
    * Accessing this marks the result as non-cacheable.
    */
  def containingUrl: Nullable[String] = {
    _wasContainingUrlAccessed = true
    _containingUrl
  }

  /** Returns the same value as [[containingUrl]], but doesn't mark it accessed. */
  def containingUrlWithoutMarking: Nullable[String] = _containingUrl

  /** Whether [[containingUrl]] has been accessed.
    *
    * This is used to determine whether canonicalize result is cacheable.
    */
  def wasContainingUrlAccessed:          Boolean = _wasContainingUrlAccessed
  private var _wasContainingUrlAccessed: Boolean = false

  /** Runs [[callback]] in a context with specified [[fromImport]]. */
  def withFromImport[T](fromImport: Boolean, callback: () => T): T = {
    val oldFromImport = _fromImport
    _fromImport = fromImport
    try
      callback()
    finally
      _fromImport = oldFromImport
  }
}
