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

import ssg.sass.{ BuiltInCallable, Callable }
import ssg.sass.value.{ SassArgumentList, SassBoolean, SassColor, SassFunction, SassList, SassMap, SassMixin, SassNull, SassNumber, SassString }

import scala.collection.immutable.ListMap

/** Built-in meta functions. */
object MetaFunctions {

  private def typeName(value: ssg.sass.value.Value): String = value match {
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
    BuiltInCallable.function("variable-exists",
                             "$name",
                             _ =>
                               // TODO: requires Environment access; deferred.
                               SassBoolean.sassFalse
    )

  private val functionExistsFn: BuiltInCallable =
    BuiltInCallable.function(
      "function-exists",
      "$name",
      { args =>
        // TODO: requires Environment access; for now scan global built-ins.
        val name = args.head match {
          case s: SassString => s.text
          case other => other.toString
        }
        SassBoolean(Functions.lookupGlobal(name).isDefined)
      }
    )

  private val keywordsFn: BuiltInCallable =
    BuiltInCallable.function(
      "keywords",
      "$args",
      _ =>
        // Placeholder: the keyword-argument map is not yet tracked on SassArgumentList.
        SassMap.empty
    )

  private val mixinExistsFn: BuiltInCallable =
    BuiltInCallable.function(
      "mixin-exists",
      "$name, $module: null",
      _ =>
        // TODO: requires Environment access; deferred.
        SassBoolean.sassFalse
    )

  private val globalVariableExistsFn: BuiltInCallable =
    BuiltInCallable.function(
      "global-variable-exists",
      "$name, $module: null",
      _ =>
        // TODO: requires Environment access; deferred.
        SassBoolean.sassFalse
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
      _ =>
        // TODO: requires module introspection; returns empty map for now.
        SassMap(ListMap.empty)
    )

  private val moduleFunctionsFn: BuiltInCallable =
    BuiltInCallable.function(
      "module-functions",
      "$module",
      { args =>
        val name = args.head match {
          case s: SassString => s.text
          case other => other.toString
        }
        // Placeholder: surface built-in module function names as string-keyed map values.
        Functions.modules.get(name) match {
          case Some(fns) =>
            val entries = fns.collect { case b: BuiltInCallable =>
              SassString(b.name, hasQuotes = true) -> (SassString(b.name, hasQuotes = true): ssg.sass.value.Value)
            }
            SassMap(ListMap.from(entries))
          case None => SassMap(ListMap.empty)
        }
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
