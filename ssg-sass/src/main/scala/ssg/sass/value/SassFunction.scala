/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/function.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: function.dart → SassFunction.scala
 *   Convention: Callable type placeholder (Any) until Phase 8
 */
package ssg
package sass
package value

import ssg.sass.{Callable, Nullable}
import ssg.sass.visitor.ValueVisitor

/** A SassScript function reference. */
final class SassFunction(
  val callable: Callable
) extends Value {

  override def accept[T](visitor: ValueVisitor[T]): T = visitor.visitFunction(this)

  override def assertFunction(name: Nullable[String]): SassFunction = this

  override def hashCode(): Int = callable.hashCode()

  override def equals(other: Any): Boolean = other match {
    case that: SassFunction => this.callable == that.callable
    case _ => false
  }

  override def toString: String = s"get-function(\"$callable\")"
}
