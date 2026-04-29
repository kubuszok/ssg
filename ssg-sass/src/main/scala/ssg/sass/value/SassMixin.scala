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
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/value/mixin.dart
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: ec85871864ca16f8045e66ad329bd462e791bfa1
 */
package ssg
package sass
package value

import ssg.sass.{ Callable, Nullable, SassScriptException }
import ssg.sass.Nullable.*
import ssg.sass.visitor.ValueVisitor

/** A SassScript mixin reference.
  *
  * A mixin reference captures a mixin from the local environment so that it may be passed between modules.
  */
final class SassMixin(
  val callable: Callable,
  /** The unique compile context for tracking if this [[SassMixin]] belongs to the current compilation or not.
    */
  private val compileContext: Nullable[AnyRef] = Nullable.Null
) extends Value {

  override def accept[T](visitor: ValueVisitor[T]): T = visitor.visitMixin(this)

  override def assertMixin(name: Nullable[String]): SassMixin = this

  /** Asserts that this SassMixin belongs to [compileContext] and returns it.
    *
    * It's checked before evaluating a SassMixin to prevent execution of SassMixin across different compilations.
    */
  def assertCompileContext(ctx: AnyRef): SassMixin = {
    if (compileContext.isDefined && (compileContext.get ne ctx)) {
      throw SassScriptException(
        s"$this does not belong to current compilation."
      )
    }
    this
  }

  override def hashCode(): Int = callable.hashCode()

  override def equals(other: Any): Boolean = other match {
    case that: SassMixin => this.callable == that.callable
    case _ => false
  }

  override def toString: String = s"""get-mixin("${callable.name}")"""
}

object SassMixin {

  /** Creates a SassMixin without a compile context (for plugin-defined mixins). */
  def apply(callable: Callable): SassMixin =
    new SassMixin(callable)

  /** Creates a SassMixin with a compile context (for user-defined mixins). */
  def withCompileContext(callable: Callable, compileContext: AnyRef): SassMixin =
    new SassMixin(callable, Nullable(compileContext))
}
