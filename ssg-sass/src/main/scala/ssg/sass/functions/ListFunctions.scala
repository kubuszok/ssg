/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions/list.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: list.dart -> ListFunctions.scala
 *   Convention: Phase 9 — implementations of basic list built-ins.
 */
package ssg
package sass
package functions

import ssg.sass.{BuiltInCallable, Callable, SassScriptException}
import ssg.sass.value.{ListSeparator, SassBoolean, SassList, SassNull, SassNumber, SassString, Value}

/** Built-in list functions. */
object ListFunctions {

  private def parseSeparator(value: Value, paramName: String): Option[ListSeparator] = {
    value match {
      case s: SassString =>
        s.text match {
          case "auto" => None
          case "space" => Some(ListSeparator.Space)
          case "comma" => Some(ListSeparator.Comma)
          case "slash" => Some(ListSeparator.Slash)
          case _ =>
            throw SassScriptException(
              s"""$$$paramName: Must be "space", "comma", "slash", or "auto".""",
              Some(paramName)
            )
        }
      case _ =>
        throw SassScriptException(
          s"""$$$paramName: Must be "space", "comma", "slash", or "auto".""",
          Some(paramName)
        )
    }
  }

  private def parseBracketed(value: Value): Option[Boolean] = value match {
    case s: SassString if !s.hasQuotes && s.text == "auto" => None
    case _ => Some(value.isTruthy)
  }

  private val lengthFn: BuiltInCallable =
    BuiltInCallable.function("length", "$list", { args =>
      SassNumber(args.head.lengthAsList.toDouble)
    })

  private val nthFn: BuiltInCallable =
    BuiltInCallable.function("nth", "$list, $n", { args =>
      val list = args.head
      val idx = list.sassIndexToListIndex(args(1))
      list.asList(idx)
    })

  private val setNthFn: BuiltInCallable =
    BuiltInCallable.function("set-nth", "$list, $n, $value", { args =>
      val list = args.head
      val idx = list.sassIndexToListIndex(args(1))
      val newValue = args(2)
      val newContents = list.asList.updated(idx, newValue)
      list.withListContents(newContents)
    })

  private val joinFn: BuiltInCallable =
    BuiltInCallable.function("join", "$list1, $list2, $separator: auto, $bracketed: auto", { args =>
      val list1 = args.head
      val list2 = args(1)
      val sepArg = parseSeparator(args(2), "separator")
      val brArg = parseBracketed(args(3))
      val separator = sepArg.getOrElse {
        if (list1.separator != ListSeparator.Undecided) list1.separator
        else if (list2.separator != ListSeparator.Undecided) list2.separator
        else ListSeparator.Space
      }
      val bracketed = brArg.getOrElse(list1.hasBrackets)
      SassList(list1.asList ::: list2.asList, separator, brackets = bracketed)
    })

  private val appendFn: BuiltInCallable =
    BuiltInCallable.function("append", "$list, $val, $separator: auto", { args =>
      val list = args.head
      val sepArg = parseSeparator(args(2), "separator")
      val separator = sepArg.getOrElse {
        if (list.separator != ListSeparator.Undecided) list.separator
        else ListSeparator.Space
      }
      SassList(list.asList :+ args(1), separator, brackets = list.hasBrackets)
    })

  private val zipFn: BuiltInCallable =
    BuiltInCallable.function("zip", "$lists...", { args =>
      val raw = if (args.length == 1) args.head.asList else args
      val lists = raw.map(_.asList)
      if (lists.isEmpty || lists.exists(_.isEmpty)) {
        SassList.empty(ListSeparator.Comma)
      } else {
        val minLen = lists.map(_.length).min
        val rows = (0 until minLen).map { i =>
          SassList(lists.map(_(i)), ListSeparator.Space)
        }.toList
        SassList(rows, ListSeparator.Comma)
      }
    })

  private val indexFn: BuiltInCallable =
    BuiltInCallable.function("index", "$list, $value", { args =>
      val list = args.head.asList
      val target = args(1)
      val idx = list.indexWhere(_ == target)
      if (idx < 0) SassNull
      else SassNumber((idx + 1).toDouble)
    })

  private val listSeparatorFn: BuiltInCallable =
    BuiltInCallable.function("list-separator", "$list", { args =>
      val sep = args.head.separator match {
        case ListSeparator.Comma => "comma"
        case ListSeparator.Slash => "slash"
        case _ => "space"
      }
      SassString(sep, hasQuotes = false)
    })

  private val isBracketedFn: BuiltInCallable =
    BuiltInCallable.function("is-bracketed", "$list", { args =>
      SassBoolean(args.head.hasBrackets)
    })

  val global: List[Callable] = List(
    lengthFn, nthFn, setNthFn, joinFn, appendFn,
    zipFn, indexFn, listSeparatorFn, isBracketedFn
  )

  def module: List[Callable] = global
}
