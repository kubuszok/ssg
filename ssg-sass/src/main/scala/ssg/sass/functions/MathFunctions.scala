/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions/math.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: math.dart -> MathFunctions.scala
 *   Convention: Phase 9 — implementations of basic math built-ins.
 */
package ssg
package sass
package functions

import ssg.sass.{ BuiltInCallable, Callable, SassScriptException }
import ssg.sass.value.{ SassBoolean, SassNumber, SassString, Value }

/** Built-in math functions: abs, ceil, floor, round, max, min, percentage, div, unit, unitless, comparable.
  */
object MathFunctions {

  private def numericUnary(name: String, op: Double => Double): BuiltInCallable =
    BuiltInCallable.function(
      name,
      "$number",
      { args =>
        val n = args.head.assertNumber()
        SassNumber.withUnits(op(n.value), n.numeratorUnits, n.denominatorUnits)
      }
    )

  private val absFn:   BuiltInCallable = numericUnary("abs", math.abs)
  private val ceilFn:  BuiltInCallable = numericUnary("ceil", v => math.ceil(v))
  private val floorFn: BuiltInCallable = numericUnary("floor", v => math.floor(v))
  private val roundFn: BuiltInCallable = numericUnary("round", v => math.round(v).toDouble)

  private def collectNumbers(args: List[Value]): List[SassNumber] = {
    // varargs come in as a single SassList (or SassArgumentList) wrapping the rest
    val raw = if (args.length == 1) args.head.asList else args
    raw.map(_.assertNumber())
  }

  private val maxFn: BuiltInCallable =
    BuiltInCallable.function(
      "max",
      "$numbers...",
      { args =>
        val numbers = collectNumbers(args)
        if (numbers.isEmpty) throw SassScriptException("At least one argument must be passed.")
        var result: SassNumber = numbers.head
        for (n <- numbers.tail)
          if (n.greaterThan(result).value) result = n
        result
      }
    )

  private val minFn: BuiltInCallable =
    BuiltInCallable.function(
      "min",
      "$numbers...",
      { args =>
        val numbers = collectNumbers(args)
        if (numbers.isEmpty) throw SassScriptException("At least one argument must be passed.")
        var result: SassNumber = numbers.head
        for (n <- numbers.tail)
          if (n.lessThan(result).value) result = n
        result
      }
    )

  private val percentageFn: BuiltInCallable =
    BuiltInCallable.function("percentage",
                             "$number",
                             { args =>
                               val n = args.head.assertNumber()
                               n.assertNoUnits()
                               SassNumber(n.value * 100, "%")
                             }
    )

  private val divFn: BuiltInCallable =
    BuiltInCallable.function("div", "$number1, $number2", args => args.head.dividedBy(args(1)))

  private val unitFn: BuiltInCallable =
    BuiltInCallable.function("unit",
                             "$number",
                             { args =>
                               val n = args.head.assertNumber()
                               SassString(n.unitString, hasQuotes = true)
                             }
    )

  private val unitlessFn: BuiltInCallable =
    BuiltInCallable.function("unitless",
                             "$number",
                             { args =>
                               val n = args.head.assertNumber()
                               SassBoolean(!n.hasUnits)
                             }
    )

  private val comparableFn: BuiltInCallable =
    BuiltInCallable.function(
      "comparable",
      "$number1, $number2",
      { args =>
        val n1 = args.head.assertNumber()
        val n2 = args(1).assertNumber()
        SassBoolean(n1.isComparableTo(n2))
      }
    )

  val global: List[Callable] = List(
    absFn,
    ceilFn,
    floorFn,
    roundFn,
    maxFn,
    minFn,
    percentageFn,
    divFn,
    unitFn,
    unitlessFn,
    comparableFn
  )

  def module: List[Callable] = global
}
