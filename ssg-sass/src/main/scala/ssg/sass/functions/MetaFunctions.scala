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

  val global: List[Callable] = List(
    typeOfFn,
    inspectFn,
    featureExistsFn,
    variableExistsFn,
    functionExistsFn
  )

  def module: List[Callable] = global
}
