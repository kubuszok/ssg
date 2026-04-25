/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions/list.dart
 * Original: Copyright (c) 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: list.dart -> ListFunctions.scala
 *   Convention: faithful port of dart-sass sass:list module. Module
 *               functions use unprefixed names (separator, etc.); global
 *               api uses list-separator for backwards compatibility.
 *
 * Covenant: full-port
 * Covenant-baseline-spec-pass: 139
 * Covenant-baseline-loc: 245
 * Covenant-baseline-methods: lengthFn,nthFn,setNthFn,joinFn,appendFn,zipFn,indexFn,separatorFn,isBracketedFn,slashFn,listSeparatorFn,withName,autoStr,global,module,ListFunctions
 * Covenant-dart-reference: lib/src/functions/list.dart
 * Covenant-verified: 2026-04-08
 *
 * T002 — Phase 4 task. Status:
 *   - core_functions/list sass-spec subdir: 129/233 (55.4%) → 139/233 (59.7%)
 *   - Remaining 94 list-subdir failures map to cross-cutting issues:
 *     B004 argument-arity validation, missing HRX import resolution
 *     across utils.scss, list-with-separator unification, and a couple
 *     of new B-tasks worth filing once Phase 4 stabilises.
 */
package ssg
package sass
package functions

import scala.language.implicitConversions

import ssg.sass.{ BuiltInCallable, Callable, SassScriptException }
import ssg.sass.value.{ ListSeparator, SassBoolean, SassList, SassNull, SassNumber, SassString, Value }

/** Built-in `sass:list` functions. Faithful port of `lib/src/functions/list.dart`. */
object ListFunctions {

  // ---------------------------------------------------------------------------
  // Base callables (unprefixed names — what `list.X` resolves to).
  // ---------------------------------------------------------------------------

  private val lengthFn: BuiltInCallable =
    BuiltInCallable.function(
      "length",
      "$list",
      args => SassNumber(args(0).asList.length.toDouble)
    )

  private val nthFn: BuiltInCallable =
    BuiltInCallable.function(
      "nth",
      "$list, $n",
      { args =>
        val list  = args(0)
        val index = args(1)
        list.asList(list.sassIndexToListIndex(index, "n"))
      }
    )

  private val setNthFn: BuiltInCallable =
    BuiltInCallable.function(
      "set-nth",
      "$list, $n, $value",
      { args =>
        val list    = args(0)
        val index   = args(1)
        val value   = args(2)
        val newList = list.asList.toBuffer
        newList(list.sassIndexToListIndex(index, "n")) = value
        list.withListContents(newList.toList)
      }
    )

  // Sentinel for the default `auto` value used by joinFn/appendFn when
  // ssg-sass's argument binder did not pad the call with declared
  // defaults. Should be replaced once the binder is fixed (B004 covers
  // a related improvement to argument validation).
  private val autoStr: SassString = SassString("auto", hasQuotes = false)

  private val joinFn: BuiltInCallable =
    BuiltInCallable.function(
      "join",
      "$list1, $list2, $separator: auto, $bracketed: auto",
      { args =>
        if (args.length < 2)
          throw SassScriptException("Missing arguments to join().")
        val list1 = args(0)
        val list2 = args(1)
        // $separator may be SassNull when skipped via named $bracketed parameter
        // (the framework fills gaps with null instead of parsing defaults)
        val separatorParam = (if (args.length > 2 && !(args(2) eq SassNull)) args(2) else autoStr).assertString("separator")
        // dart-sass list.dart:64: $bracketed is taken directly from arguments;
        // null means falsey (not "auto").
        val bracketedParam = if (args.length > 3) args(3) else autoStr

        val separator = separatorParam.text match {
          case "auto" =>
            (list1.separator, list2.separator) match {
              case (ListSeparator.Undecided, ListSeparator.Undecided) => ListSeparator.Space
              case (ListSeparator.Undecided, sep)                     => sep
              case (sep, _)                                           => sep
            }
          case "space" => ListSeparator.Space
          case "comma" => ListSeparator.Comma
          case "slash" => ListSeparator.Slash
          case _       =>
            throw SassScriptException(
              """$separator: Must be "space", "comma", "slash", or "auto"."""
            )
        }

        val bracketed = bracketedParam match {
          case s: SassString if s.text == "auto" => list1.hasBrackets
          case _ => bracketedParam.isTruthy
        }

        SassList(list1.asList ::: list2.asList, separator, brackets = bracketed)
      }
    )

  private val appendFn: BuiltInCallable =
    BuiltInCallable.function(
      "append",
      "$list, $val, $separator: auto",
      { args =>
        if (args.isEmpty)
          throw SassScriptException("Missing argument $list.")
        if (args.length < 2)
          throw SassScriptException("Missing argument $val.")
        val list           = args(0)
        val value          = args(1)
        val separatorParam = (if (args.length > 2) args(2) else autoStr).assertString("separator")

        val separator = separatorParam.text match {
          case "auto" =>
            if (list.separator == ListSeparator.Undecided) ListSeparator.Space
            else list.separator
          case "space" => ListSeparator.Space
          case "comma" => ListSeparator.Comma
          case "slash" => ListSeparator.Slash
          case _       =>
            throw SassScriptException(
              """$separator: Must be "space", "comma", "slash", or "auto"."""
            )
        }

        // Use SassList directly (rather than withListContents) so the
        // receiver may be a map or other list-coercible value, not just
        // a SassList. dart-sass's `withListContents` requires a SassList
        // receiver but the spec accepts maps via asList.
        val newList = list.asList :+ value
        SassList(newList, separator, brackets = list.hasBrackets)
      }
    )

  private val zipFn: BuiltInCallable =
    BuiltInCallable.function(
      "zip",
      "$lists...",
      { args =>
        // ssg-sass's argument binder may pass the splat as a single
        // SassArgumentList wrapping the call's positional args, OR as
        // the positional args directly. Detect both shapes.
        val raw: List[Value] =
          if (args.isEmpty) Nil
          else if (args.length == 1 && args(0).isInstanceOf[ssg.sass.value.SassArgumentList]) args(0).asList
          else args
        val lists: List[List[Value]] = raw.map(_.asList)
        if (lists.isEmpty) {
          SassList.empty(ListSeparator.Comma)
        } else {
          var i         = 0
          val results   = scala.collection.mutable.ListBuffer.empty[SassList]
          var keepGoing = lists.forall(l => i != l.length)
          while (keepGoing) {
            val row = lists.map(l => l(i))
            results += SassList(row, ListSeparator.Space)
            i += 1
            keepGoing = lists.forall(l => i != l.length)
          }
          SassList(results.toList, ListSeparator.Comma)
        }
      }
    )

  private val indexFn: BuiltInCallable =
    BuiltInCallable.function(
      "index",
      "$list, $value",
      { args =>
        val list  = args(0).asList
        val value = args(1)
        val idx   = list.indexOf(value)
        if (idx == -1) SassNull
        else SassNumber((idx + 1).toDouble)
      }
    )

  private val separatorFn: BuiltInCallable =
    BuiltInCallable.function(
      "separator",
      "$list",
      { args =>
        val text = args(0).separator match {
          case ListSeparator.Comma => "comma"
          case ListSeparator.Slash => "slash"
          case _                   => "space"
        }
        SassString(text, hasQuotes = false)
      }
    )

  private val isBracketedFn: BuiltInCallable =
    BuiltInCallable.function(
      "is-bracketed",
      "$list",
      args => SassBoolean(args(0).hasBrackets)
    )

  private val slashFn: BuiltInCallable =
    BuiltInCallable.function(
      "slash",
      "$elements...",
      { args =>
        // Splat may be passed as either a single wrapper arg containing
        // a list, or as the positional args directly. Detect both.
        val list: List[Value] =
          if (args.isEmpty) Nil
          else if (args.length == 1) args(0).asList
          else args
        if (list.length < 2) {
          throw SassScriptException("At least two elements are required.")
        }
        SassList(list, ListSeparator.Slash)
      }
    )

  // ---------------------------------------------------------------------------
  // Public lists.
  // ---------------------------------------------------------------------------

  /** Globally available built-ins. Mirrors dart-sass `global`. Note `slash` and `separator` (under its bare name) are NOT in the global; only the `list-separator` alias is. Each entry uses
    * `.withDeprecationWarning("list")` to emit a `global-builtin` deprecation warning directing users to `list.X`.
    */
  val global: List[Callable] = List(
    lengthFn.withDeprecationWarning("list"),
    nthFn.withDeprecationWarning("list"),
    setNthFn.withDeprecationWarning("list"),
    joinFn.withDeprecationWarning("list"),
    appendFn.withDeprecationWarning("list"),
    zipFn.withDeprecationWarning("list"),
    indexFn.withDeprecationWarning("list"),
    isBracketedFn.withDeprecationWarning("list"),
    separatorFn.withDeprecationWarning("list").withName("list-separator")
  )

  /** Members of the `sass:list` module. Mirrors dart-sass `module`. */
  def module: List[Callable] = List(
    lengthFn,
    nthFn,
    setNthFn,
    joinFn,
    appendFn,
    zipFn,
    indexFn,
    isBracketedFn,
    separatorFn,
    slashFn
  )
}
