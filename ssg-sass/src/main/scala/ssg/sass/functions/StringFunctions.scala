/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions/string.dart
 * Original: Copyright (c) 2016 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: string.dart -> StringFunctions.scala
 *   Convention: Phase 9 — implementations of basic string built-ins.
 */
package ssg
package sass
package functions

import ssg.sass.{ BuiltInCallable, Callable, SassScriptException }
import ssg.sass.value.{ SassNull, SassNumber, SassString }

/** Built-in string functions. */
object StringFunctions {

  private val unquoteFn: BuiltInCallable =
    BuiltInCallable.function("unquote",
                             "$string",
                             { args =>
                               val s = args.head.assertString()
                               if (!s.hasQuotes) s
                               else SassString(s.text, hasQuotes = false)
                             }
    )

  private val quoteFn: BuiltInCallable =
    BuiltInCallable.function("quote",
                             "$string",
                             { args =>
                               val s = args.head.assertString()
                               if (s.hasQuotes) s
                               else SassString(s.text, hasQuotes = true)
                             }
    )

  private val strLengthFn: BuiltInCallable =
    BuiltInCallable.function("str-length",
                             "$string",
                             { args =>
                               val s = args.head.assertString()
                               SassNumber(s.sassLength.toDouble)
                             }
    )

  private val toUpperCaseFn: BuiltInCallable =
    BuiltInCallable.function("to-upper-case",
                             "$string",
                             { args =>
                               val s = args.head.assertString()
                               SassString(s.text.toUpperCase, hasQuotes = s.hasQuotes)
                             }
    )

  private val toLowerCaseFn: BuiltInCallable =
    BuiltInCallable.function("to-lower-case",
                             "$string",
                             { args =>
                               val s = args.head.assertString()
                               SassString(s.text.toLowerCase, hasQuotes = s.hasQuotes)
                             }
    )

  private val strInsertFn: BuiltInCallable =
    BuiltInCallable.function(
      "str-insert",
      "$string, $insert, $index",
      { args =>
        val s        = args.head.assertString()
        val insert   = args(1).assertString()
        val indexNum = args(2).assertNumber()
        indexNum.assertNoUnits()
        val i = indexNum.assertInt()
        if (i == 0) throw SassScriptException("$index: String index may not be 0.")
        val len = s.sassLength
        // 1-based; negative counts from end; clamp.
        val insertionIndex =
          if (i > 0) math.min(i - 1, len)
          else math.max(len + i + 1, 0)
        val codeUnitIdx = ssg.sass.Utils.codepointIndexToCodeUnitIndex(s.text, insertionIndex)
        val newText     = s.text.substring(0, codeUnitIdx) + insert.text + s.text.substring(codeUnitIdx)
        SassString(newText, hasQuotes = s.hasQuotes)
      }
    )

  private val strIndexFn: BuiltInCallable =
    BuiltInCallable.function(
      "str-index",
      "$string, $substring",
      { args =>
        val s   = args.head.assertString()
        val sub = args(1).assertString()
        val idx = s.text.indexOf(sub.text)
        if (idx == -1) SassNull
        else {
          // Convert UTF-16 code unit index to 1-based codepoint index.
          val cpIndex = s.text.codePointCount(0, idx)
          SassNumber((cpIndex + 1).toDouble)
        }
      }
    )

  private val strSliceFn: BuiltInCallable =
    BuiltInCallable.function(
      "str-slice",
      "$string, $start-at, $end-at: -1",
      { args =>
        val s        = args.head.assertString()
        val startNum = args(1).assertNumber()
        startNum.assertNoUnits()
        val endNum = args(2).assertNumber()
        endNum.assertNoUnits()
        val len   = s.sassLength
        val start = startNum.assertInt()
        val end   = endNum.assertInt()
        if (start == 0) throw SassScriptException("$start-at: String index may not be 0.")
        // Sass slice is inclusive on both ends, 1-based.
        val normStart =
          if (start > 0) math.min(start - 1, len)
          else math.max(len + start, 0)
        val normEnd =
          if (end == 0) -1
          else if (end > 0) math.min(end, len)
          else math.max(len + end + 1, 0)
        if (normEnd <= normStart) {
          SassString("", hasQuotes = s.hasQuotes)
        } else {
          val startCu = ssg.sass.Utils.codepointIndexToCodeUnitIndex(s.text, normStart)
          val endCu   = ssg.sass.Utils.codepointIndexToCodeUnitIndex(s.text, normEnd)
          SassString(s.text.substring(startCu, endCu), hasQuotes = s.hasQuotes)
        }
      }
    )

  val global: List[Callable] = List(
    unquoteFn,
    quoteFn,
    strLengthFn,
    toUpperCaseFn,
    toLowerCaseFn,
    strInsertFn,
    strIndexFn,
    strSliceFn
  )

  def module: List[Callable] = global
}
