/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/css/comment.dart, lib/src/ast/css/modifiable/comment.dart
 * Original: Copyright (c) 2016, 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: comment.dart + modifiable/comment.dart -> CssComment.scala
 *   Convention: Dart abstract interface class -> Scala trait
 *   Idiom: isPreserved checks third character for '!' (index 2)
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/ast/css/comment.dart, lib/src/ast/css/modifiable/comment.dart
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass
package ast
package css

import ssg.sass.util.FileSpan
import ssg.sass.visitor.CssVisitor

/** A plain CSS comment.
  *
  * This is always a multi-line comment.
  */
trait CssComment extends CssNode {

  /** The contents of this comment, including slash-star and star-slash. */
  def text: String

  /** Whether this comment starts with slash-star-bang and so should be preserved even in compressed mode.
    */
  def isPreserved: Boolean
}

/** A modifiable version of CssComment for use in the evaluation step. */
final class ModifiableCssComment(
  val text: String,
  val span: FileSpan
) extends ModifiableCssNode
    with CssComment {

  def isPreserved: Boolean = text.charAt(2) == '!'

  def accept[T](visitor: CssVisitor[T]): T =
    visitor.visitCssComment(this)
}
