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

  /** Positional parameter names and their optional default-expression text, derived from the textual [[signature]] (e.g. `"$color, $amount: 1"` → `List(("color", None), ("amount", Some("1")))`).
    * Underscores are normalized to hyphens to match Sass name conventions. Rest parameters (`$args...`) are ignored here. Returns an empty list when the signature is a rest-only form such as
    * `"$args..."`.
    */
  lazy val parameterEntries: List[(String, Option[String])] = {
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
        // Split default value: `$x: expr`
        val (nameRaw, defaultOpt) = raw.indexOf(':') match {
          case -1  => (raw, None)
          case idx => (raw.substring(0, idx).trim, Some(raw.substring(idx + 1).trim))
        }
        // Skip rest parameters `$args...` — they don't bind a fixed name.
        if (nameRaw.endsWith("...")) None
        else if (nameRaw.startsWith("$")) Some((nameRaw.substring(1).replace('_', '-'), defaultOpt))
        else None
      }
    }
  }

  /** Positional parameter names. */
  lazy val parameterNames: List[String] = parameterEntries.map(_._1)

  /** Raw default-expression text for each declared parameter (None if required). */
  lazy val parameterDefaults: List[Option[String]] = parameterEntries.map(_._2)

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

  /** Builds a built-in callable that dispatches by arity.
    *
    * Each entry of `overloads` maps a textual signature (e.g. `"$color"`, `"$color, $amount"`, `"$args..."`) to its callback. At call time the overload whose signature accepts the runtime argument
    * count is selected:
    *
    *   - First, an exact match (positional count == declared parameter count) wins.
    *   - Otherwise, an overload that has more declared parameters (the rest are assumed to have defaults) wins.
    *   - Otherwise, the rest-parameter overload (`$args...`) wins.
    *   - If nothing matches, an `IllegalArgumentException` is thrown.
    */
  def overloadedFunction(
    name:      String,
    overloads: Map[String, List[Value] => Value]
  ): BuiltInCallable = {
    // Pre-compute (declaredCount, hasRest, callback) for each overload.
    final case class _Entry(arity: Int, hasRest: Boolean, callback: List[Value] => Value)
    val entries: List[_Entry] = overloads.toList.map { case (signature, cb) =>
      val trimmed = signature.trim
      val parts   =
        if (trimmed.isEmpty) Nil
        else {
          val buf     = scala.collection.mutable.ListBuffer.empty[String]
          val current = new StringBuilder()
          var depth   = 0
          var i       = 0
          while (i < trimmed.length) {
            val c = trimmed.charAt(i)
            if (c == '(' || c == '[') { depth += 1; current.append(c) }
            else if (c == ')' || c == ']') { depth -= 1; current.append(c) }
            else if (c == ',' && depth == 0) {
              buf += current.toString().trim
              current.setLength(0)
            } else current.append(c)
            i += 1
          }
          if (current.nonEmpty) buf += current.toString().trim
          buf.toList
        }
      val hasRest = parts.lastOption.exists(_.endsWith("..."))
      val arity   = if (hasRest) parts.length - 1 else parts.length
      _Entry(arity, hasRest, cb)
    }

    val dispatch: List[Value] => Value = args => {
      val n = args.length
      // 1. Exact arity match (non-rest preferred).
      val exact = entries.find(e => !e.hasRest && e.arity == n)
      // 2. Non-rest overload with more params (defaulted tail).
      val widened = exact.orElse(entries.filter(e => !e.hasRest && e.arity > n).sortBy(_.arity).headOption)
      // 3. Rest-parameter overload accepting at least `n` positional args.
      val resty = widened.orElse(entries.find(e => e.hasRest && e.arity <= n))
      resty match {
        case Some(e) => e.callback(args)
        case None    =>
          throw new IllegalArgumentException(
            s"No overload of $name matches ${args.length} argument(s)"
          )
      }
    }

    BuiltInCallable(name, Nullable.empty, dispatch)
  }
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

/** A callable defined in user Sass source via `@function` or `@mixin`.
  *
  * `declaration` is typed as the loose [[Statement]] base for backwards compatibility, but in practice it is always a [[CallableDeclaration]] (FunctionRule, MixinRule, or ContentBlock). The [[name]]
  * is taken from the declaration when possible.
  */
final class UserDefinedCallable[E](
  val declaration:  Statement, // The Sass AST node (FunctionRule or MixinRule)
  val environment:  E,
  val inDependency: Boolean = false
) extends Callable {

  val name: String = declaration match {
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
