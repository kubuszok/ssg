/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/internal/TocLevelsOptionParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package toc
package internal

import ssg.md.Nullable
import ssg.md.util.misc.{ DelimitedBuilder, Pair, Utils }
import ssg.md.util.options.*
import ssg.md.util.sequence.{ BasedSequence, SequenceUtils }

import java.util.Collections

class TocLevelsOptionParser(val optionName: String) extends OptionParser[TocOptions] {

  override def parseOption(optionText: BasedSequence, options: TocOptions, provider: Nullable[MessageProvider]): Pair[TocOptions, java.util.List[ParsedOption[TocOptions]]] = {
    var result            = options
    val levelsOptionValue = optionText.split(",")
    val parserParams      = new ParserParams()
    val useProvider       = if (provider.isDefined) provider.get else MessageProvider.DEFAULT

    var newLevels = 0

    for (option <- levelsOptionValue) {
      val optionRange = option.split("-", 2, SequenceUtils.SPLIT_TRIM_PARTS | SequenceUtils.SPLIT_INCLUDE_DELIM_PARTS, Nullable.empty)
      var skip        = false
      var rangeStart: Nullable[Int] = Nullable.empty
      var rangeEnd:   Nullable[Int] = Nullable.empty

      def convertWithMessage(opt: BasedSequence): Nullable[Int] =
        if (opt.isEmpty) Nullable.empty
        else {
          try
            Nullable(Integer.parseInt(opt.toString))
          catch {
            case _: Exception =>
              parserParams.add(
                new ParserMessage(
                  opt,
                  ParsedOptionStatus.ERROR,
                  useProvider.message(
                    TocLevelsOptionParser.KEY_OPTION_0_VALUE_1_NOT_INTEGER,
                    TocLevelsOptionParser.OPTION_0_VALUE_1_NOT_INTEGER,
                    optionName.asInstanceOf[AnyRef],
                    opt.asInstanceOf[AnyRef]
                  )
                )
              )
              skip = true
              Nullable.empty
          }
        }

      if (optionRange.length >= 2) {
        rangeStart = convertWithMessage(optionRange(0))
        rangeEnd = if (optionRange.length >= 3) convertWithMessage(optionRange(2)) else Nullable.empty
        if (rangeStart.isEmpty) rangeStart = Nullable(1)
        if (rangeEnd.isEmpty) rangeEnd = Nullable(6)
      } else {
        // NOTE: 1 means heading level 1 only, 2 means 2 only, rest mean 2-x
        val optionValue = convertWithMessage(optionRange(0))
        if (optionValue.isDefined && optionValue.get <= 2) {
          rangeStart = optionValue
          rangeEnd = rangeStart
        } else {
          rangeStart = if (optionValue.isEmpty) Nullable.empty else Nullable(2)
          rangeEnd = optionValue
        }
      }

      if (!skip) {
        if (rangeStart.isEmpty) {
          parserParams.add(
            new ParserMessage(
              option,
              ParsedOptionStatus.IGNORED,
              useProvider.message(
                TocLevelsOptionParser.KEY_OPTION_0_VALUE_1_TRUNCATED_TO_EMPTY_RANGE,
                TocLevelsOptionParser.OPTION_0_VALUE_1_TRUNCATED_TO_EMPTY_RANGE,
                optionName.asInstanceOf[AnyRef],
                option.asInstanceOf[AnyRef]
              )
            )
          )
        } else {
          var rs = rangeStart.get
          var re = rangeEnd.get
          if (re < rs) {
            val tmp = rs
            rs = re
            re = tmp
          }

          if (re < 1 || rs > 6) {
            if (rs == re) {
              parserParams.add(
                new ParserMessage(
                  option,
                  ParsedOptionStatus.IGNORED,
                  useProvider.message(
                    TocLevelsOptionParser.KEY_OPTION_0_VALUE_1_NOT_IN_RANGE,
                    TocLevelsOptionParser.OPTION_0_VALUE_1_NOT_IN_RANGE,
                    optionName.asInstanceOf[AnyRef],
                    option.asInstanceOf[AnyRef]
                  )
                )
              )
            } else {
              parserParams.add(
                new ParserMessage(
                  option,
                  ParsedOptionStatus.WARNING,
                  useProvider.message(
                    TocLevelsOptionParser.KEY_OPTION_0_VALUE_1_TRUNCATED_TO_EMPTY_RANGE,
                    TocLevelsOptionParser.OPTION_0_VALUE_1_TRUNCATED_TO_EMPTY_RANGE,
                    optionName.asInstanceOf[AnyRef],
                    option.asInstanceOf[AnyRef]
                  )
                )
              )
            }
          } else {
            val wasStart = rs
            val wasEnd   = re
            rs = Utils.minLimit(rs, 1)
            re = Utils.maxLimit(re, 6)
            if (wasStart != rs || wasEnd != re) {
              parserParams.add(
                new ParserMessage(
                  option,
                  ParsedOptionStatus.WEAK_WARNING,
                  useProvider.message(
                    TocLevelsOptionParser.KEY_OPTION_0_VALUE_1_TRUNCATED_TO_RANGE_2,
                    TocLevelsOptionParser.OPTION_0_VALUE_1_TRUNCATED_TO_RANGE_2,
                    optionName.asInstanceOf[AnyRef],
                    option.asInstanceOf[AnyRef],
                    s"$rs, $re".asInstanceOf[AnyRef]
                  )
                )
              )
            }
            var b = rs
            while (b <= re) {
              newLevels = newLevels | (1 << b)
              b += 1
            }
          }
        }
      }
    }

    if (newLevels != 0) result = result.withLevels(newLevels)

    new Pair(
      Nullable(result),
      Nullable(
        Collections
          .singletonList(new ParsedOption[TocOptions](optionText, this, parserParams.status, parserParams.messages.map(_.toList), Nullable.empty))
          .asInstanceOf[java.util.List[ParsedOption[TocOptions]]]
      )
    )
  }

  override def getOptionText(options: TocOptions, defaultOptions: Nullable[TocOptions]): String =
    if (defaultOptions.isEmpty || options.levels != defaultOptions.get.levels) {
      val out = new DelimitedBuilder()
      out.append("levels=")

      val fixedLevels = TocLevelsOptionParser.TOC_LEVELS_MAP.get(options.levels)
      if (fixedLevels.isDefined) {
        out.append(fixedLevels.get).mark()
      } else {
        out.push(",")

        var firstBit = 0
        var lastBit  = 0
        var i        = 1
        while (i <= 6) {
          if (options.isLevelIncluded(i)) {
            if (firstBit == 0) {
              firstBit = i
              lastBit = i
            } else {
              if (lastBit + 1 != i) {
                if (firstBit != lastBit) {
                  if (firstBit + 1 == lastBit) { out.append(firstBit); out.mark(); out.append(lastBit); out.mark() }
                  else { out.append(firstBit); out.append('-'); out.append(lastBit); out.mark() }
                } else {
                  out.append(firstBit); out.mark()
                }
                firstBit = i
                lastBit = i
              } else {
                lastBit = i
              }
            }
          }
          i += 1
        }

        if (firstBit != 0) {
          if (firstBit != lastBit) {
            if (firstBit == 2) { out.append(lastBit); out.mark() }
            else if (firstBit + 1 == lastBit) { out.append(firstBit); out.mark(); out.append(lastBit); out.mark() }
            else { out.append(firstBit); out.append('-'); out.append(lastBit); out.mark() }
          } else {
            out.append(firstBit); out.mark()
          }
        }

        out.pop(); out.mark()
      }
      out.toString
    } else ""
}

object TocLevelsOptionParser {
  val OPTION_0_VALUE_1_NOT_IN_RANGE:                 String = "{0} option value {1} is not an integer in the range [1, 6]"
  val KEY_OPTION_0_VALUE_1_NOT_IN_RANGE:             String = "options.parser.toc-levels-option.not-in-range"
  val OPTION_0_VALUE_1_NOT_INTEGER:                  String = "{0} option value {1} is not an integer"
  val KEY_OPTION_0_VALUE_1_NOT_INTEGER:              String = "options.parser.toc-levels-option.not-integer"
  val OPTION_0_VALUE_1_TRUNCATED_TO_RANGE_2:         String = "{0} option value {1} truncated to range [{2}]"
  val KEY_OPTION_0_VALUE_1_TRUNCATED_TO_RANGE_2:     String = "options.parser.toc-levels-option.truncated-to-range"
  val OPTION_0_VALUE_1_TRUNCATED_TO_EMPTY_RANGE:     String = "{0} option value {1} truncated to empty range []"
  val KEY_OPTION_0_VALUE_1_TRUNCATED_TO_EMPTY_RANGE: String = "options.parser.toc-levels-option.truncated-to-empty"

  private val TOC_LEVELS_MAP: Map[Int, String] = Map(
    0x04 -> "2",
    0x0c -> "3",
    0x1c -> "4",
    0x3c -> "5",
    0x7c -> "6",
    (1 << 1) -> "1",
    (1 << 3) -> "3-3",
    (1 << 4) -> "4-4",
    (1 << 5) -> "5-5",
    (1 << 6) -> "6-6"
  )
}
