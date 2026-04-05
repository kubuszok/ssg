/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/sass/interpolation.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: interpolation.dart -> Interpolation.scala
 *   Convention: Dart final class -> Scala final class
 *   Idiom: contents is List[String | Expression] -> List[Any]; validated at construction
 */
package ssg
package sass
package ast
package sass

import ssg.sass.Nullable
import ssg.sass.Nullable.*
import ssg.sass.util.FileSpan

/** Plain text interpolated with Sass expressions.
  *
  * @param contents the contents of this interpolation: [String]s and [Expression]s,
  *                 never containing two adjacent [String]s
  * @param spans    source spans for each [Expression] in [contents]; must be the
  *                 same length as [contents]
  * @param span     the source span covering the entire interpolation
  */
final class Interpolation(
  val contents: List[Any /* String | Expression */],
  val spans: List[Nullable[FileSpan]],
  val span: FileSpan
) extends SassNode {

  require(
    spans.length == contents.length,
    s"spans must be the same length as contents (${spans.length} != ${contents.length})."
  )

  // Validate contents
  {
    var i = 0
    while (i < contents.length) {
      val v = contents(i)
      val isString = v.isInstanceOf[String]
      if (!isString && !v.isInstanceOf[Expression]) {
        throw new IllegalArgumentException(
          "contents may only contain Strings or Expressions."
        )
      } else if (isString) {
        if (i != 0 && contents(i - 1).isInstanceOf[String]) {
          throw new IllegalArgumentException(
            "contents may not contain adjacent Strings."
          )
        }
      }
      i += 1
    }
  }

  /** Returns whether this contains no interpolated expressions. */
  def isPlain: Boolean = asPlain.isDefined

  /** If this contains no interpolated expressions, returns its text contents.
    * Otherwise, returns empty.
    */
  def asPlain: Nullable[String] = contents match {
    case Nil                    => Nullable("")
    case (s: String) :: Nil     => Nullable(s)
    case _                      => Nullable.empty
  }

  /** Returns the plain text before the interpolation, or the empty string. */
  def initialPlain: String = contents match {
    case (s: String) :: _ => s
    case _                => ""
  }

  /** Returns the [FileSpan] covering the element of the interpolation at [index]. */
  def spanForElement(index: Int): FileSpan = contents(index) match {
    case _: String =>
      val start =
        if (index == 0) span.start
        else spans(index - 1).get.end
      val end =
        if (index + 1 == spans.length) span.end
        else spans(index + 1).get.start
      span.file.span(start.offset, end.offset)
    case _ =>
      spans(index).get
  }

  override def toString: String =
    contents
      .map {
        case s: String => s
        case e         => s"#{$e}"
      }
      .mkString
}

object Interpolation {

  /** Creates an interpolation containing only plain text. */
  def plain(text: String, span: FileSpan): Interpolation =
    new Interpolation(List(text), List(Nullable.empty), span)
}
