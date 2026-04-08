/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/sass/import.dart,
 *              lib/src/ast/sass/import/dynamic.dart,
 *              lib/src/ast/sass/import/static.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: import.dart + dynamic.dart + static.dart -> Import.scala
 *   Convention: Dart abstract interface class -> Scala trait;
 *               Dart final class -> Scala final case class
 *   Idiom: Nullable for optional fields
 */
package ssg
package sass
package ast
package sass

import java.net.URI

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.util.FileSpan

/** An abstract superclass for different types of import. */
trait Import extends SassNode

/** An import that will load a Sass file at runtime.
  *
  * @param urlString
  *   the URL of the file to import, as a string
  * @param span
  *   the source span
  */
final case class DynamicImport(
  urlString: String,
  span:      FileSpan
) extends Import
    with SassDependency {

  /** The URL of the file to import. If this is relative, it's relative to the containing file.
    */
  def url: URI = URI.create(urlString)

  def urlSpan: FileSpan = span

  override def toString: String =
    StringExpression.quoteText(urlString)
}

/** An import that produces a plain CSS `@import` rule.
  *
  * @param url
  *   the URL for this import (already contains quotes)
  * @param span
  *   the source span
  * @param modifiers
  *   the modifiers (such as media or supports queries), or empty
  */
final case class StaticImport(
  url:       Interpolation,
  span:      FileSpan,
  modifiers: Nullable[Interpolation] = Nullable.empty
) extends Import {

  override def toString: String =
    modifiers.fold(url.toString)(m => s"$url $m")
}
