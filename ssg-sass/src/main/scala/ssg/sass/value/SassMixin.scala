/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/value/mixin.dart
 * Original: Copyright (c) 2023 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: mixin.dart → SassMixin.scala
 *   Convention: Callable type placeholder (Any) until Phase 8
 */
package ssg
package sass
package value

import ssg.sass.{ Callable, Nullable }
import ssg.sass.visitor.ValueVisitor

/** A SassScript mixin reference. */
final class SassMixin(
  val callable: Callable
) extends Value {

  override def accept[T](visitor: ValueVisitor[T]): T = visitor.visitMixin(this)

  override def assertMixin(name: Nullable[String]): SassMixin = this

  override def hashCode(): Int = callable.hashCode()

  override def equals(other: Any): Boolean = other match {
    case that: SassMixin => this.callable == that.callable
    case _ => false
  }

  override def toString: String = s"""get-mixin("${callable.name}")"""
}
