/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions/meta.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: meta.dart -> MetaFunctions.scala
 *   Convention: Phase 9 — implementations of basic meta built-ins.
 */
package ssg
package sass
package functions

import ssg.sass.{ BuiltInCallable, Callable, CurrentEnvironment, Environment, Nullable }
import ssg.sass.value.{ SassArgumentList, SassBoolean, SassColor, SassFunction, SassList, SassMap, SassMixin, SassNull, SassNumber, SassString, Value }

import scala.collection.immutable.ListMap

/** Built-in meta functions. */
object MetaFunctions {

  private def typeName(value: Value): String = value match {
    case _: SassArgumentList => "arglist"
    case _: SassNumber       => "number"
    case _: SassString       => "string"
    case _: SassColor        => "color"
    case _: SassMap          => "map"
    case _: SassList         => "list"
    case _: SassBoolean      => "bool"
    case SassNull => "null"
    case _: SassFunction => "function"
    case _: SassMixin    => "mixin"
    case _ => "unknown"
  }

  /** Extracts a string-typed argument's text. */
  private def argName(v: Value): String = v match {
    case s: SassString => s.text
    case other => other.toString
  }

  /** Resolves the env to introspect for an optional `$module` argument: `null` -> the active environment, otherwise the namespaced module registered under that name (or empty if none).
    */
  private def envFor(moduleArg: Value): Nullable[Environment] =
    CurrentEnvironment.get.flatMap { env =>
      moduleArg match {
        case SassNull => Nullable(env)
        case other    => env.getNamespace(argName(other))
      }
    }

  private val ifFn: BuiltInCallable =
    BuiltInCallable.function(
      "if",
      "$condition, $if-true, $if-false",
      args => if (args.head.isTruthy) args(1) else args(2)
    )

  private val typeOfFn: BuiltInCallable =
    BuiltInCallable.function("type-of", "$value", args => SassString(typeName(args.head), hasQuotes = false))

  private val inspectFn: BuiltInCallable =
    BuiltInCallable.function("inspect", "$value", args => SassString(args.head.toString, hasQuotes = false))

  private val featureExistsFn: BuiltInCallable =
    BuiltInCallable.function("feature-exists",
                             "$feature",
                             _ =>
                               // Deprecated; we don't claim to support any features.
                               SassBoolean.sassFalse
    )

  private val variableExistsFn: BuiltInCallable =
    BuiltInCallable.function(
      "variable-exists",
      "$name",
      args =>
        SassBoolean(
          CurrentEnvironment.get.fold(false)(_.variableExists(argName(args.head)))
        )
    )

  private val functionExistsFn: BuiltInCallable =
    BuiltInCallable.function(
      "function-exists",
      "$name, $module: null",
      { args =>
        val name      = argName(args.head)
        val moduleArg = if (args.length > 1) args(1) else SassNull
        val found     = envFor(moduleArg).fold(false)(_.functionExists(name)) ||
          (moduleArg == SassNull && Functions.lookupGlobal(name).isDefined)
        SassBoolean(found)
      }
    )

  private val keywordsFn: BuiltInCallable =
    BuiltInCallable.function(
      "keywords",
      "$args",
      args =>
        // When the arglist carries a keyword-rest map (set by
        // `_bindParameters` for `$kwargs...`), surface it as a SassMap with
        // quoted-string keys. Otherwise return an empty map.
        args.head match {
          case al: SassArgumentList =>
            val entries = al.keywords.iterator.map { case (k, v) =>
              (SassString(k, hasQuotes = true): Value) -> v
            }.toList
            SassMap(ListMap.from(entries))
          case _ => SassMap.empty
        }
    )

  private val mixinExistsFn: BuiltInCallable =
    BuiltInCallable.function(
      "mixin-exists",
      "$name, $module: null",
      { args =>
        val name      = argName(args.head)
        val moduleArg = if (args.length > 1) args(1) else SassNull
        SassBoolean(envFor(moduleArg).fold(false)(_.mixinExists(name)))
      }
    )

  private val globalVariableExistsFn: BuiltInCallable =
    BuiltInCallable.function(
      "global-variable-exists",
      "$name, $module: null",
      { args =>
        val name      = argName(args.head)
        val moduleArg = if (args.length > 1) args(1) else SassNull
        SassBoolean(envFor(moduleArg).fold(false)(_.variableExists(name)))
      }
    )

  private val contentExistsFn: BuiltInCallable =
    BuiltInCallable.function(
      "content-exists",
      "",
      _ =>
        // TODO: requires mixin call stack tracking; deferred.
        SassBoolean.sassFalse
    )

  private val moduleVariablesFn: BuiltInCallable =
    BuiltInCallable.function(
      "module-variables",
      "$module",
      { args =>
        val name = argName(args.head)
        // First try a built-in `sass:` module — none of those expose
        // variables today, so this is effectively the user-defined path.
        CurrentEnvironment.get.flatMap(_.getNamespace(name)).fold(SassMap.empty) { env =>
          val entries = env.variableEntries.map { case (k, v) =>
            (SassString(k, hasQuotes = true): Value) -> v
          }.toList
          SassMap(ListMap.from(entries))
        }
      }
    )

  private val moduleFunctionsFn: BuiltInCallable =
    BuiltInCallable.function(
      "module-functions",
      "$module",
      { args =>
        val name = argName(args.head)
        // Surface the namespace's function members as a name->name map.
        // First try the active env (covers `@use "vars" as v` and
        // `@use "sass:color"` since both register namespaces). Fall back
        // to the static built-in module table for `sass:` modules even
        // when no `@use` is in scope.
        val nsEntries = CurrentEnvironment.get.flatMap(_.getNamespace(name)).fold(List.empty[(Value, Value)]) { env =>
          env.functionValues.map { fn =>
            (SassString(fn.name, hasQuotes = true): Value) ->
              (SassString(fn.name, hasQuotes = true): Value)
          }.toList
        }
        val entries =
          if (nsEntries.nonEmpty) nsEntries
          else
            Functions.modules.get(name).fold(List.empty[(Value, Value)]) { fns =>
              fns.collect { case b: BuiltInCallable =>
                (SassString(b.name, hasQuotes = true): Value) ->
                  (SassString(b.name, hasQuotes = true): Value)
              }
            }
        SassMap(ListMap.from(entries))
      }
    )

  val global: List[Callable] = List(
    ifFn,
    typeOfFn,
    inspectFn,
    featureExistsFn,
    variableExistsFn,
    functionExistsFn,
    keywordsFn,
    mixinExistsFn,
    globalVariableExistsFn,
    contentExistsFn,
    moduleVariablesFn,
    moduleFunctionsFn
  )

  def module: List[Callable] = global
}
