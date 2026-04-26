/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/importer/utils.dart
 * Original: Copyright (c) 2017 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: utils.dart -> ImporterUtils.scala (cross-platform portion)
 *   Convention: Dart Zone-based context replaced by shared var (single-threaded)
 *   Idiom: _ifInImport, fromImport, canonicalizeContext, inImportRule,
 *          withCanonicalizeContext, isValidUrlScheme
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/importer/utils.dart
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass
package importer

import scala.language.implicitConversions
import scala.util.matching.Regex

/** Cross-platform importer utility functions.
  *
  * The Dart original uses `Zone.current[#_canonicalizeContext]` to store the current canonicalization context. Since Sass evaluation is single-threaded, we use a simple shared `var` (consistent with
  * [[ssg.sass.EvaluationContext]]).
  *
  * Filesystem-dependent resolution helpers (`resolveImportPath`, `_tryPath`, etc.) are in the JVM-only `ImporterFileUtils` since they require real filesystem access.
  */
object ImporterUtils {

  /** The current canonicalize context, or empty if none is active.
    *
    * This replaces Dart's `Zone.current[#_canonicalizeContext]`.
    */
  private var _canonicalizeContext: Nullable[CanonicalizeContext] = Nullable.empty

  /** Whether the Sass compiler is currently evaluating an `@import` rule.
    *
    * When evaluating `@import` rules, URLs should canonicalize to an import-only file if one exists for the URL being canonicalized. Otherwise, canonicalization should be identical for `@import` and
    * `@use` rules. It's admittedly hacky to set this globally, but `@import` will eventually be removed, at which point we can delete this and have one consistent behavior.
    */
  def fromImport: Boolean =
    _canonicalizeContext.fold(false)(_.fromImport)

  /** The CanonicalizeContext of the current load. */
  def canonicalizeContext: CanonicalizeContext =
    _canonicalizeContext.fold {
      throw new IllegalStateException(
        "canonicalizeContext may only be accessed within a call to canonicalize()."
      )
    }(identity)

  /** Runs [[callback]] in a context where [[fromImport]] returns `true` and `resolveImportPath` uses `@import` semantics rather than `@use` semantics.
    */
  def inImportRule[T](callback: () => T): T =
    _canonicalizeContext.toOption match {
      case scala.None =>
        withCanonicalizeContext(Nullable(new CanonicalizeContext(Nullable.empty, true)), callback)
      case Some(context) =>
        context.withFromImport(true, callback)
      // Dart has a third branch for unexpected values; since our var is typed
      // Nullable[CanonicalizeContext], that cannot happen here.
    }

  /** Runs [[callback]] in the given context. */
  def withCanonicalizeContext[T](context: Nullable[CanonicalizeContext], callback: () => T): T = {
    val prev = _canonicalizeContext
    _canonicalizeContext = context
    try
      callback()
    finally
      _canonicalizeContext = prev
  }

  /** If [[fromImport]] is `true`, invokes callback and returns the result.
    *
    * Otherwise, returns empty.
    */
  def ifInImport[T](callback: () => T): Nullable[T] =
    if (fromImport) Nullable(callback()) else Nullable.empty

  /** A regular expression matching valid URL schemes. */
  private val _urlSchemeRegExp: Regex = "^[a-z0-9+.-]+$".r

  /** Returns whether [[scheme]] is a valid URL scheme. */
  def isValidUrlScheme(scheme: String): Boolean =
    _urlSchemeRegExp.findFirstIn(scheme).isDefined
}
