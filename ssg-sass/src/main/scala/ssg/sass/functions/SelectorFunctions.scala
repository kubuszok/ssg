/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions/selector.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: selector.dart -> SelectorFunctions.scala
 *   Convention: Phase 9 — minimal text-based selector functions, no AST.
 */
package ssg
package sass
package functions

import ssg.sass.{BuiltInCallable, Callable}
import ssg.sass.value.{SassNull, SassString, Value}

/** Built-in selector functions. Minimal text-based implementation — no
  * selector AST parsing. Accepts only [[SassString]] arguments; returns
  * [[SassNull]] when given a list or other non-string value.
  */
object SelectorFunctions {

  /** Extracts the text of a [[SassString]], or `None` if [v] isn't a string. */
  private def asText(v: Value): Option[String] = v match {
    case s: SassString => Some(s.text)
    case _             => scala.None
  }

  private val selectorAppendFn: BuiltInCallable =
    BuiltInCallable.function("selector-append", "$selectors...", { args =>
      val parts = args.flatMap(asText)
      if (parts.length != args.length) SassNull
      else SassString(parts.mkString(""), hasQuotes = false)
    })

  private val selectorNestFn: BuiltInCallable =
    BuiltInCallable.function("selector-nest", "$selectors...", { args =>
      val parts = args.flatMap(asText)
      if (parts.length != args.length) SassNull
      else SassString(parts.mkString(" "), hasQuotes = false)
    })

  private val selectorUnifyFn: BuiltInCallable =
    BuiltInCallable.function("selector-unify", "$selector1, $selector2", { _ =>
      // Stub — full unification requires selector AST. Return null.
      SassNull
    })

  private val selectorExtendFn: BuiltInCallable =
    BuiltInCallable.function("selector-extend", "$selector, $extendee, $extender", { args =>
      if (args.length < 3) SassNull
      else (asText(args(0)), asText(args(1)), asText(args(2))) match {
        case (Some(sel), Some(extendee), Some(extender)) =>
          val replacement = s"$extendee, $extender"
          SassString(sel.replace(extendee, replacement), hasQuotes = false)
        case _ => SassNull
      }
    })

  val global: List[Callable] = List(
    selectorAppendFn, selectorNestFn, selectorUnifyFn, selectorExtendFn
  )

  def module: List[Callable] = global
}
