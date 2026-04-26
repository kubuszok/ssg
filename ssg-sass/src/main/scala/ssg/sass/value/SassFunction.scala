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
 *
 * Covenant: full-port
 * Covenant-dart-reference: lib/src/value/function.dart
 * Covenant-verified: 2026-04-26
 */
package ssg
package sass
package value

import ssg.sass.{ Callable, Nullable, SassScriptException }
import ssg.sass.Nullable.*
import ssg.sass.visitor.ValueVisitor

/** A SassScript function reference.
  *
  * A function reference captures a function from the local environment so that it may be passed between modules.
  */
final class SassFunction(
  val callable: Callable,
  /** The unique compile context for tracking if this [[SassFunction]] belongs to the current compilation or not.
    *
    * This is `Nullable.Null` for functions defined in plugins' Scala code.
    */
  private val compileContext: Nullable[AnyRef] = Nullable.Null
) extends Value {

  override def accept[T](visitor: ValueVisitor[T]): T = visitor.visitFunction(this)

  override def assertFunction(name: Nullable[String]): SassFunction = this

  /** Asserts that this SassFunction belongs to [compileContext] and returns it.
    *
    * It's checked before evaluating a SassFunction to prevent execution of SassFunction across different compilations.
    */
  def assertCompileContext(ctx: AnyRef): SassFunction = {
    if (compileContext.isDefined && (compileContext.get ne ctx)) {
      throw SassScriptException(
        s"$this does not belong to current compilation."
      )
    }
    this
  }

  override def hashCode(): Int = callable.hashCode()

  override def equals(other: Any): Boolean = other match {
    case that: SassFunction => this.callable == that.callable
    case _ => false
  }

  override def toString: String = s"""get-function("${callable.name}")"""
}

object SassFunction {

  /** Creates a SassFunction without a compile context (for plugin-defined functions). */
  def apply(callable: Callable): SassFunction =
    new SassFunction(callable)

  /** Creates a SassFunction with a compile context (for user-defined functions). */
  def withCompileContext(callable: Callable, compileContext: AnyRef): SassFunction =
    new SassFunction(callable, Nullable(compileContext))
}
