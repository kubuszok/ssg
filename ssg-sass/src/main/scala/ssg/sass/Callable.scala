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

import ssg.sass.ast.sass.{ CallableDeclaration, ParameterList, Statement }
import ssg.sass.value.Value

/** An interface for functions and mixins that can be invoked from Sass by passing in arguments.
  */
trait Callable {

  /** The name of this callable. */
  def name: String
}

object Callable {

  /** Creates a function [[Callable]] with the given name, arguments signature and callback. TODO: parse arguments into a [[ParameterList]].
    */
  def function(name: String, arguments: String, callback: List[Value] => Value): Callable =
    BuiltInCallable.function(name, arguments, callback)
}

/** A callable defined in Dart/Scala code, wrapping a native function. */
final class BuiltInCallable(
  val name:           String,
  val parameters:     Nullable[ParameterList],
  val callback:       List[Value] => Value,
  val acceptsContent: Boolean = false,
  val signature:      String = ""
) extends Callable {

  /** Positional parameter names derived from the textual [[signature]] (e.g. `"$color, $amount"` → `List("color", "amount")`). Underscores are normalized to hyphens to match Sass name conventions.
    * Rest parameters (`$args...`) and trailing defaults are ignored — only the leading name is extracted. Returns an empty list when the signature is a rest-only form such as `"$args..."`.
    */
  lazy val parameterNames: List[String] = {
    val trimmed = signature.trim
    if (trimmed.isEmpty) Nil
    else {
      val parts = scala.collection.mutable.ListBuffer.empty[String]
      val buf   = new StringBuilder()
      var depth = 0
      var i     = 0
      while (i < trimmed.length) {
        val c = trimmed.charAt(i)
        if (c == '(' || c == '[') { depth += 1; buf.append(c) }
        else if (c == ')' || c == ']') { depth -= 1; buf.append(c) }
        else if (c == ',' && depth == 0) {
          parts += buf.toString().trim
          buf.setLength(0)
        } else buf.append(c)
        i += 1
      }
      if (buf.nonEmpty) parts += buf.toString().trim
      parts.toList.flatMap { raw =>
        // Strip default value: `$x: expr`
        val noDefault = raw.indexOf(':') match {
          case -1  => raw
          case idx => raw.substring(0, idx).trim
        }
        // Skip rest parameters `$args...` — they don't bind a fixed name.
        if (noDefault.endsWith("...")) None
        else if (noDefault.startsWith("$")) Some(noDefault.substring(1).replace('_', '-'))
        else None
      }
    }
  }

  override def toString: String = s"BuiltInCallable($name)"
}

object BuiltInCallable {

  def function(name: String, arguments: String, callback: List[Value] => Value): BuiltInCallable =
    BuiltInCallable(name, Nullable.empty, callback, signature = arguments)

  def mixin(
    name:           String,
    arguments:      String,
    callback:       List[Value] => Value,
    acceptsContent: Boolean = false
  ): BuiltInCallable =
    BuiltInCallable(name, Nullable.empty, callback, acceptsContent, signature = arguments)

  def overloadedFunction(
    name:      String,
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
  val declaration:  Statement, // The Sass AST node (FunctionRule or MixinRule)
  val environment:  E,
  val inDependency: Boolean = false
) extends Callable {

  def name: String = declaration match {
    case cd: CallableDeclaration => cd.name
    case _ => "user-defined"
  }

  override def toString: String = s"UserDefinedCallable($name)"
}

/** Context utilities for [[UserDefinedCallable]]. */
object UserDefinedCallable {

  def apply[E](
    declaration:  Statement,
    environment:  E,
    inDependency: Boolean = false
  ): UserDefinedCallable[E] = new UserDefinedCallable(declaration, environment, inDependency)
}
