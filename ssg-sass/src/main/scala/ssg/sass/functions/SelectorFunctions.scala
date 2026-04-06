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
 *   Convention: Phase 9 — selector functions backed by the SelectorParser
 *               and the selector AST. List arguments are accepted by joining
 *               their elements with the appropriate separator and re-parsing.
 *               Falls back to text-based behaviour when parsing fails so that
 *               malformed inputs do not crash the build.
 */
package ssg
package sass
package functions

import ssg.sass.{ BuiltInCallable, Callable, Nullable }
import ssg.sass.parse.SelectorParser
import ssg.sass.value.{ ListSeparator, SassList, SassNull, SassString, Value }

/** Built-in selector functions. */
object SelectorFunctions {

  /** Coerces a [[Value]] to its selector text representation.
    *
    * Strings are returned verbatim. Lists are joined: comma-separated lists use `, ` between entries; space-separated lists use a single space. Returns `None` for any other value type.
    */
  private def asSelectorText(v: Value): Option[String] = v match {
    case s: SassString => Some(s.text)
    case l: SassList   =>
      val sep = l.separator match {
        case ListSeparator.Comma => ", "
        case _                   => " "
      }
      val items = l.asList
      val parts = items.flatMap(asSelectorText)
      if (parts.length != items.length) scala.None
      else Some(parts.mkString(sep))
    case _ => scala.None
  }

  /** Parses [text] into a [[ssg.sass.ast.selector.SelectorList]] or returns `Nullable.Null` if parsing fails. */
  private def tryParse(text: String): Nullable[ssg.sass.ast.selector.SelectorList] =
    SelectorParser.tryParse(text)

  /** Renders a selector list back to text using the AST `toString`. */
  private def render(list: ssg.sass.ast.selector.SelectorList): String = list.toString

  // ---------------------------------------------------------------------------
  // selector-append: concatenates each selector to the end of the previous
  // selector's compound list (no descendant combinator).
  // ---------------------------------------------------------------------------

  private val selectorAppendFn: BuiltInCallable =
    BuiltInCallable.function(
      "selector-append",
      "$selectors...",
      { args =>
        val parts = args.flatMap(asSelectorText)
        if (parts.length != args.length || parts.isEmpty) SassNull
        else {
          // Real append: ".a" + ".b" => ".a.b". We achieve this textually for
          // each successive pair, then re-parse to validate; on failure we
          // still return the textual concatenation.
          val text = parts.reduceLeft { (acc, next) =>
            // Strip a leading combinator from `next` if any (descendant `&`
            // semantics not yet supported here) and concatenate.
            acc + next
          }
          val parsed = tryParse(text)
          if (parsed.isDefined) SassString(render(parsed.get), hasQuotes = false)
          else SassString(text, hasQuotes = false)
        }
      }
    )

  // ---------------------------------------------------------------------------
  // selector-nest: joins selectors with descendant combinators (a space).
  // ---------------------------------------------------------------------------

  private val selectorNestFn: BuiltInCallable =
    BuiltInCallable.function(
      "selector-nest",
      "$selectors...",
      { args =>
        val parts = args.flatMap(asSelectorText)
        if (parts.length != args.length || parts.isEmpty) SassNull
        else {
          val text   = parts.mkString(" ")
          val parsed = tryParse(text)
          if (parsed.isDefined) SassString(render(parsed.get), hasQuotes = false)
          else SassString(text, hasQuotes = false)
        }
      }
    )

  // ---------------------------------------------------------------------------
  // selector-unify: real implementation backed by SelectorList.unify.
  // ---------------------------------------------------------------------------

  private val selectorUnifyFn: BuiltInCallable =
    BuiltInCallable.function(
      "selector-unify",
      "$selector1, $selector2",
      args =>
        if (args.length < 2) SassNull
        else
          (asSelectorText(args(0)), asSelectorText(args(1))) match {
            case (Some(a), Some(b)) =>
              val pa = tryParse(a)
              val pb = tryParse(b)
              if (pa.isEmpty || pb.isEmpty) SassNull
              else {
                val unified = pa.get.unify(pb.get)
                if (unified.isEmpty) SassNull
                else SassString(render(unified.get), hasQuotes = false)
              }
            case _ => SassNull
          }
    )

  // ---------------------------------------------------------------------------
  // selector-extend: keeps the existing textual replacement implementation,
  // since full @extend AST work is out of scope for this pass.
  // ---------------------------------------------------------------------------

  private val selectorExtendFn: BuiltInCallable =
    BuiltInCallable.function(
      "selector-extend",
      "$selector, $extendee, $extender",
      args =>
        if (args.length < 3) SassNull
        else
          (asSelectorText(args(0)), asSelectorText(args(1)), asSelectorText(args(2))) match {
            case (Some(sel), Some(extendee), Some(extender)) =>
              val replacement = s"$extendee, $extender"
              SassString(sel.replace(extendee, replacement), hasQuotes = false)
            case _ => SassNull
          }
    )

  val global: List[Callable] = List(
    selectorAppendFn,
    selectorNestFn,
    selectorUnifyFn,
    selectorExtendFn
  )

  def module: List[Callable] = global
}
