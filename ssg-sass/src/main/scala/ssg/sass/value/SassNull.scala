/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/null.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: null.dart → SassNull.scala
 *   Convention: Singleton object
 */
package ssg
package sass
package value

import ssg.sass.Nullable
import ssg.sass.visitor.ValueVisitor

import scala.language.implicitConversions

/** The SassScript null value. Singleton. */
object SassNull extends Value {

  override def isTruthy: Boolean = false

  override def isBlank: Boolean = true

  override def realNull: Nullable[Value] = Nullable.Null

  override def accept[T](visitor: ValueVisitor[T]): T = visitor.visitNull()

  override def unaryNot(): Value = SassBoolean.sassTrue

  override def hashCode(): Int = 0

  override def equals(other: Any): Boolean = other match {
    case SassNull => true
    case _        => false
  }

  override def toString: String = "null"
}
