/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/boolean.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: boolean.dart → SassBoolean.scala
 *   Convention: Singleton true/false via companion object
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/value/boolean.dart
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass
package value

import ssg.sass.Nullable
import ssg.sass.visitor.ValueVisitor

/** A SassScript boolean value. */
final class SassBoolean private (val value: Boolean) extends Value {

  override def isTruthy: Boolean = value

  override def accept[T](visitor: ValueVisitor[T]): T = visitor.visitBoolean(this)

  override def assertBoolean(name: Nullable[String]): SassBoolean = this

  override def unaryNot(): Value =
    if (value) SassBoolean.sassFalse else SassBoolean.sassTrue

  override def hashCode(): Int = value.hashCode()

  override def equals(other: Any): Boolean = other match {
    case that: SassBoolean => this.value == that.value
    case _ => false
  }

  override def toString: String = value.toString
}

object SassBoolean {
  val sassTrue:  SassBoolean = new SassBoolean(true)
  val sassFalse: SassBoolean = new SassBoolean(false)

  def apply(value: Boolean): SassBoolean =
    if (value) sassTrue else sassFalse
}
