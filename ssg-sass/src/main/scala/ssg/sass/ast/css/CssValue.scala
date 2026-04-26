/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/ast/css/value.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: value.dart -> CssValue.scala
 *   Convention: Dart final class -> Scala final case class (without auto-generated copy)
 *   Idiom: Value equality on the wrapped value only (not span)
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/ast/css/value.dart
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass
package ast
package css

import ssg.sass.util.FileSpan

/** A value in a plain CSS tree.
  *
  * This is used to associate a span with a value that doesn't otherwise track its span. It has value equality semantics based solely on the wrapped value.
  *
  * @param value
  *   the wrapped value
  * @param span
  *   the span associated with the value
  */
final class CssValue[T](val value: T, val span: FileSpan) extends AstNode {

  override def equals(other: Any): Boolean = other match {
    case that: CssValue[_] => this.value == that.value
    case _ => false
  }

  override def hashCode(): Int = value.hashCode()

  override def toString: String = value.toString
}
