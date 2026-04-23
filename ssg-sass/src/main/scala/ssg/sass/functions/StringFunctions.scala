/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: lib/src/functions/string.dart
 * Original: Copyright (c) 2019 Google Inc.
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: string.dart -> StringFunctions.scala
 *   Convention: faithful port of dart-sass sass:string module. The module
 *               functions use the unprefixed names (length/insert/index/
 *               slice); the global definitions use the str-prefixed names.
 *   Idiom: ASCII-only case conversion via CharCode.toUpperCase/toLowerCase
 *          (matches dart util/character.dart, NOT Java's locale-sensitive
 *          String.toUpperCase which mangles non-ASCII letters).
 *
 * Covenant: full-port
 * Covenant-baseline-spec-pass: 122
 * Covenant-baseline-loc: 320
 * Covenant-baseline-methods: unquoteFn,quoteFn,lengthFn,insertFn,indexFn,sliceFn,toUpperCaseFn,toLowerCaseFn,uniqueIdFn,splitFn,strLengthFn,strInsertFn,strIndexFn,strSliceFn,withName,global,module,codepointForIndex,StringFunctions
 * Covenant-dart-reference: lib/src/functions/string.dart
 * Covenant-verified: 2026-04-08
 *
 * T001 — Phase 2 forcing-function reference port. Status:
 *   - core_functions/string sass-spec subdir: 47/155 (30.3%) → 122/155 (78.7%)
 *   - global sass-spec count: 4,157 → 4,237 (+80 net)
 *   - The remaining 33 string-subdir failures are blocked on 7 cross-
 *     cutting issues handled in their own micro-tasks (B001-B007). The
 *     string functions themselves are faithful per dart-sass.
 */
package ssg
package sass
package functions

import scala.language.implicitConversions

import ssg.sass.{ BuiltInCallable, Callable, SassScriptException }
import ssg.sass.util.CharCode
import ssg.sass.value.{ ListSeparator, SassList, SassNull, SassNumber, SassString, Value }

/** Built-in `sass:string` functions.
  *
  * Faithful port of `lib/src/functions/string.dart`. The module is exposed via [[Functions.modules]] and the global members via [[Functions.global]].
  */
object StringFunctions {

  // ---------------------------------------------------------------------------
  // Base callables (unprefixed names — these are what `string.X` resolves to).
  // ---------------------------------------------------------------------------

  private val unquoteFn: BuiltInCallable =
    BuiltInCallable.function(
      "unquote",
      "$string",
      { args =>
        val s = args(0).assertString("string")
        if (!s.hasQuotes) s
        else SassString(s.text, hasQuotes = false)
      }
    )

  private val quoteFn: BuiltInCallable =
    BuiltInCallable.function(
      "quote",
      "$string",
      { args =>
        val s = args(0).assertString("string")
        if (s.hasQuotes) s
        else SassString(s.text, hasQuotes = true)
      }
    )

  private val lengthFn: BuiltInCallable =
    BuiltInCallable.function(
      "length",
      "$string",
      { args =>
        val s = args(0).assertString("string")
        SassNumber(s.sassLength.toDouble)
      }
    )

  private val insertFn: BuiltInCallable =
    BuiltInCallable.function(
      "insert",
      "$string, $insert, $index",
      { args =>
        val s        = args(0).assertString("string")
        val insert   = args(1).assertString("insert")
        val indexNum = args(2).assertNumber("index")
        indexNum.assertNoUnits("index")

        var indexInt = indexNum.assertInt("index")

        // dart-sass: str-insert has unusual behavior for negative inputs.
        // It guarantees that $insert appears AT $index in the result, so
        // for negatives we insert AFTER that index.
        if (indexInt < 0) {
          // +1 because negative indexes start counting from -1 rather than
          // 0, and another +1 because we want to insert AFTER that index.
          indexInt = math.max(s.sassLength + indexInt + 2, 0)
        }

        val codepointIndex = codepointForIndex(indexInt, s.sassLength)
        val codeUnitIndex  = ssg.sass.Utils.codepointIndexToCodeUnitIndex(s.text, codepointIndex)
        SassString(
          s.text.substring(0, codeUnitIndex) + insert.text + s.text.substring(codeUnitIndex),
          hasQuotes = s.hasQuotes
        )
      }
    )

  private val indexFn: BuiltInCallable =
    BuiltInCallable.function(
      "index",
      "$string, $substring",
      { args =>
        val s           = args(0).assertString("string")
        val sub         = args(1).assertString("substring")
        val codeUnitIdx = s.text.indexOf(sub.text)
        if (codeUnitIdx == -1) SassNull
        else {
          val codepointIdx = ssg.sass.Utils.codeUnitIndexToCodepointIndex(s.text, codeUnitIdx)
          SassNumber((codepointIdx + 1).toDouble)
        }
      }
    )

  private val sliceFn: BuiltInCallable =
    BuiltInCallable.function(
      "slice",
      "$string, $start-at, $end-at: -1",
      { args =>
        val s     = args(0).assertString("string")
        val start = args(1).assertNumber("start-at")
        val end   = args(2).assertNumber("end-at")
        start.assertNoUnits("start-at")
        end.assertNoUnits("end-at")

        val lengthInCodepoints = s.sassLength

        // No matter what the start index is, an end index of 0 produces
        // an empty string.
        val endInt = end.assertInt()
        if (endInt == 0) SassString.empty(quotes = s.hasQuotes)
        else {
          val startCp = codepointForIndex(start.assertInt(), lengthInCodepoints)
          var endCp   = codepointForIndex(endInt, lengthInCodepoints, allowNegative = true)
          if (endCp == lengthInCodepoints) endCp -= 1
          if (endCp < startCp) {
            SassString.empty(quotes = s.hasQuotes)
          } else {
            val startCu = ssg.sass.Utils.codepointIndexToCodeUnitIndex(s.text, startCp)
            val endCu   = ssg.sass.Utils.codepointIndexToCodeUnitIndex(s.text, endCp + 1)
            SassString(s.text.substring(startCu, endCu), hasQuotes = s.hasQuotes)
          }
        }
      }
    )

  private val toUpperCaseFn: BuiltInCallable =
    BuiltInCallable.function(
      "to-upper-case",
      "$string",
      { args =>
        val s  = args(0).assertString("string")
        val sb = new StringBuilder(s.text.length)
        var i  = 0
        while (i < s.text.length) {
          sb.append(CharCode.toUpperCase(s.text.charAt(i).toInt).toChar)
          i += 1
        }
        SassString(sb.toString, hasQuotes = s.hasQuotes)
      }
    )

  private val toLowerCaseFn: BuiltInCallable =
    BuiltInCallable.function(
      "to-lower-case",
      "$string",
      { args =>
        val s  = args(0).assertString("string")
        val sb = new StringBuilder(s.text.length)
        var i  = 0
        while (i < s.text.length) {
          sb.append(CharCode.toLowerCase(s.text.charAt(i).toInt).toChar)
          i += 1
        }
        SassString(sb.toString, hasQuotes = s.hasQuotes)
      }
    )

  // We use base-36 so we can use the (26-character) alphabet and all digits.
  private val random = new java.util.Random()
  private var previousUniqueId: Long = {
    val max = math.pow(36, 6).toLong
    (math.abs(random.nextLong()) % max).max(0L)
  }

  private val uniqueIdFn: BuiltInCallable =
    BuiltInCallable.function(
      "unique-id",
      "",
      _ =>
        synchronized {
          // Make it difficult to guess the next ID by randomizing the increase.
          previousUniqueId += random.nextInt(36) + 1L
          val max = math.pow(36, 6).toLong
          if (previousUniqueId > max) previousUniqueId %= max

          // The leading "u" ensures that the result is a valid identifier.
          val padded  = java.lang.Long.toString(previousUniqueId, 36)
          val withPad = "0" * math.max(0, 6 - padded.length) + padded
          SassString("u" + withPad, hasQuotes = false)
        }
    )

  private val splitFn: BuiltInCallable =
    BuiltInCallable.function(
      "split",
      "$string, $separator, $limit: null",
      { args =>
        val s   = args(0).assertString("string")
        val sep = args(1).assertString("separator")
        // Optional $limit (null means no limit).
        val limit: Option[Int] = args(2) match {
          case SassNull => None
          case other    =>
            val n = other.assertNumber("limit")
            val i = n.assertInt("limit")
            if (i < 1) {
              throw SassScriptException(s"$$limit: Must be 1 or greater, was $i.")
            }
            Some(i)
        }

        if (s.text.isEmpty) {
          // Empty string -> empty bracketed comma list.
          SassList(Nil, ListSeparator.Comma, brackets = true)
        } else if (sep.text.isEmpty) {
          // Empty separator -> split into individual codepoints (preserving
          // surrogate pairs). Each element preserves the input string's
          // hasQuotes flag.
          // Iterate codepoints manually because `String.codePoints()`
          // returns a `java.util.stream.IntStream`, which Scala.js does
          // not implement. The manual loop is portable across JVM, JS,
          // and Native.
          val parts = scala.collection.mutable.ListBuffer.empty[Value]
          var i     = 0
          while (i < s.text.length) {
            val cp = s.text.codePointAt(i)
            parts += SassString(new String(Character.toChars(cp)), hasQuotes = s.hasQuotes)
            i += Character.charCount(cp)
          }
          SassList(parts.toList, ListSeparator.Comma, brackets = true)
        } else {
          // General case: scan for separator occurrences, honoring $limit.
          val chunks  = scala.collection.mutable.ListBuffer.empty[String]
          var lastEnd = 0
          var i       = 0
          var found   = s.text.indexOf(sep.text)
          val cap     = limit.getOrElse(Int.MaxValue)
          while (found >= 0 && i < cap) {
            chunks += s.text.substring(lastEnd, found)
            lastEnd = found + sep.text.length
            i += 1
            found = s.text.indexOf(sep.text, lastEnd)
          }
          chunks += s.text.substring(lastEnd)
          val parts: List[Value] =
            chunks.toList.map(c => SassString(c, hasQuotes = s.hasQuotes))
          SassList(parts, ListSeparator.Comma, brackets = true)
        }
      }
    )

  // ---------------------------------------------------------------------------
  // Public lists.
  // ---------------------------------------------------------------------------

  /** The globally available built-ins. Mirrors dart-sass `global` (excluding `split`, which is module-only). Each entry uses `.withDeprecationWarning("string")` to emit a `global-builtin` deprecation
    * warning directing users to `string.X`.
    */
  val global: List[Callable] = List(
    unquoteFn.withDeprecationWarning("string"),
    quoteFn.withDeprecationWarning("string"),
    toUpperCaseFn.withDeprecationWarning("string"),
    toLowerCaseFn.withDeprecationWarning("string"),
    uniqueIdFn.withDeprecationWarning("string"),
    lengthFn.withDeprecationWarning("string").withName("str-length"),
    insertFn.withDeprecationWarning("string").withName("str-insert"),
    indexFn.withDeprecationWarning("string").withName("str-index"),
    sliceFn.withDeprecationWarning("string").withName("str-slice")
  )

  /** The members of the `sass:string` module. Mirrors dart-sass `module`. */
  def module: List[Callable] = List(
    unquoteFn,
    quoteFn,
    toUpperCaseFn,
    toLowerCaseFn,
    lengthFn,
    insertFn,
    indexFn,
    sliceFn,
    uniqueIdFn,
    splitFn
  )

  // ---------------------------------------------------------------------------
  // Helpers.
  // ---------------------------------------------------------------------------

  /** Converts a Sass string index into a codepoint index into a string whose codepoint length is [lengthInCodepoints].
    *
    * A Sass string index is one-based, and uses negative numbers to count backwards from the end of the string. A codepoint index is a 0-based offset into the codepoint sequence.
    *
    * If [index] is negative and points before the beginning of the string, this returns `0` if [allowNegative] is false and the index if it's true. Faithful port of `_codepointForIndex` in
    * `lib/src/functions/string.dart`.
    */
  private def codepointForIndex(
    index:              Int,
    lengthInCodepoints: Int,
    allowNegative:      Boolean = false
  ): Int =
    if (index == 0) 0
    else if (index > 0) math.min(index - 1, lengthInCodepoints)
    else {
      val result = lengthInCodepoints + index
      if (result < 0 && !allowNegative) 0
      else result
    }
}
