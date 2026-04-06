/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/callable.dart, lib/src/callable/built_in.dart,
 *              lib/src/callable/plain_css.dart, lib/src/callable/user_defined.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: callable.dart -> Callable.scala (merged family)
 *   Convention: Skeleton — public API surface only
 *   Idiom: Scala 3 trait + concrete subclasses
 */
package ssg
package sass

import ssg.sass.ast.sass.{ParameterList, Statement}
import ssg.sass.value.Value

/** An interface for functions and mixins that can be invoked from Sass by
  * passing in arguments.
  */
trait Callable {

  /** The name of this callable. */
  def name: String
}

object Callable {

  /** Creates a function [[Callable]] with the given name, arguments signature
    * and callback. TODO: parse arguments into a [[ParameterList]].
    */
  def function(name: String, arguments: String, callback: List[Value] => Value): Callable =
    BuiltInCallable.function(name, arguments, callback)
}

/** A callable defined in Dart/Scala code, wrapping a native function. */
final class BuiltInCallable(
  val name: String,
  val parameters: Nullable[ParameterList],
  val callback: List[Value] => Value,
  val acceptsContent: Boolean = false
) extends Callable {

  override def toString: String = s"BuiltInCallable($name)"
}

object BuiltInCallable {

  def function(name: String, arguments: String, callback: List[Value] => Value): BuiltInCallable =
    // TODO: parse `arguments` into a ParameterList
    BuiltInCallable(name, Nullable.empty, callback)

  def mixin(
    name: String,
    arguments: String,
    callback: List[Value] => Value,
    acceptsContent: Boolean = false
  ): BuiltInCallable =
    // TODO: parse `arguments` into a ParameterList
    BuiltInCallable(name, Nullable.empty, callback, acceptsContent)

  def overloadedFunction(
    name: String,
    overloads: Map[String, List[Value] => Value]
  ): BuiltInCallable =
    // TODO: overload dispatch
    BuiltInCallable(name, Nullable.empty, args => overloads.head._2(args))
}

/** A callable that emits a plain-CSS function call. */
final class PlainCssCallable(val name: String) extends Callable {

  override def toString: String = s"PlainCssCallable($name)"

  override def equals(other: Any): Boolean = other match {
    case that: PlainCssCallable => this.name == that.name
    case _ => false
  }

  override def hashCode(): Int = name.hashCode
}

/** A callable defined in user Sass source via `@function` or `@mixin`. */
final class UserDefinedCallable[E](
  val declaration: Statement, // The Sass AST node (FunctionRule or MixinRule)
  val environment: E,
  val inDependency: Boolean = false
) extends Callable {

  // TODO: extract name from declaration
  def name: String = "user-defined"

  override def toString: String = s"UserDefinedCallable($name)"
}

/** Context utilities for [[UserDefinedCallable]]. */
object UserDefinedCallable {

  def apply[E](
    declaration: Statement,
    environment: E,
    inDependency: Boolean = false
  ): UserDefinedCallable[E] = new UserDefinedCallable(declaration, environment, inDependency)
}

