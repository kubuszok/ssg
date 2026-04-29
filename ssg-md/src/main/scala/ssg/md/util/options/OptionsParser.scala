/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-options/src/main/java/com/vladsch/flexmark/util/options/OptionsParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-options/src/main/java/com/vladsch/flexmark/util/options/OptionsParser.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package options

import ssg.md.Nullable
import ssg.md.util.misc.{ DelimitedBuilder, Pair }
import ssg.md.util.misc.CharPredicate
import ssg.md.util.sequence.{ BasedSequence, SequenceUtils }

import scala.collection.mutable.ArrayBuffer
import scala.util.boundary
import scala.util.boundary.break

class OptionsParser[T](
  val optionName:               String,
  private val parseableOptions: Array[OptionParser[T]],
  optionDelimiter:              Char,
  optionValueDelimiter:         Char
) extends OptionParser[T] {

  private val optionDelimiterStr:      String = optionDelimiter.toString
  private val optionValueDelimiterStr: String = optionValueDelimiter.toString

  override def parseOption(optionsText: BasedSequence, options: T, provider: Nullable[MessageProvider]): Pair[T, java.util.List[ParsedOption[T]]] = {
    val optionsList   = optionsText.split(optionDelimiterStr, 0, SequenceUtils.SPLIT_TRIM_SKIP_EMPTY, Nullable.empty[CharPredicate])
    var result        = options
    val useProvider   = if (provider.isDefined) provider.get else MessageProvider.DEFAULT
    val parsedOptions = new java.util.ArrayList[ParsedOption[T]](optionsList.length)

    for (optionText <- optionsList) {
      var matched: Nullable[OptionParser[T]]  = Nullable.empty
      var message: Nullable[DelimitedBuilder] = Nullable.empty

      val optionList = optionText.split(optionValueDelimiterStr, 2, SequenceUtils.SPLIT_SKIP_EMPTY, Nullable.empty[CharPredicate])
      if (optionList.length > 0) {
        val optName     = optionList(0)
        val optionValue =
          if (optionList.length > 1) optionList(1)
          else optName.subSequence(optName.length(), optName.length())

        boundary {
          for (optionParser <- parseableOptions) {
            if (optionParser.optionName == optName.toString) {
              matched = Nullable(optionParser)
              message = Nullable.empty
              break()
            }
            if (optionParser.optionName.startsWith(optName.toString)) {
              if (matched.isEmpty) {
                matched = Nullable(optionParser)
              } else {
                if (message.isEmpty) {
                  val msg = new DelimitedBuilder(", ")
                  msg.append(
                    useProvider.message(
                      OptionsParser.KEY_OPTION_0_IS_AMBIGUOUS,
                      OptionsParser.OPTION_0_IS_AMBIGUOUS,
                      optName.asInstanceOf[AnyRef]
                    )
                  )
                  msg.append(matched.get.optionName).mark()
                  message = Nullable(msg)
                }
                message.get.append(optionParser.optionName).mark()
              }
            }
          }
        }

        // have our match
        if (matched.isDefined) {
          if (message.isEmpty) {
            val pair = matched.get.parseOption(optionValue, result, Nullable(useProvider))
            result = pair.first.get
            parsedOptions.add(new ParsedOption[T](optionText, this, ParsedOptionStatus.VALID, Nullable.empty, Nullable(toScalaList(pair.second.get))))
          } else {
            parsedOptions.add(
              new ParsedOption[T](optionText, this, ParsedOptionStatus.ERROR, new ParserMessage(optName, ParsedOptionStatus.ERROR, message.get.toString))
            )
          }
        } else {
          val msg = new DelimitedBuilder(", ")
          msg.append(
            useProvider.message(
              OptionsParser.KEY_OPTION_0_DOES_NOT_MATCH,
              OptionsParser.OPTION_0_DOES_NOT_MATCH,
              optName.asInstanceOf[AnyRef]
            )
          )
          appendOptionNames(msg)
          parsedOptions.add(
            new ParsedOption[T](optionText, this, ParsedOptionStatus.ERROR, new ParserMessage(optName, ParsedOptionStatus.ERROR, msg.toString))
          )
        }
      }
    }
    new Pair(Nullable(result), Nullable(parsedOptions.asInstanceOf[java.util.List[ParsedOption[T]]]))
  }

  private def toScalaList[A](javaList: java.util.List[A]): List[A] = {
    val buf = ArrayBuffer.empty[A]
    val it  = javaList.iterator()
    while (it.hasNext)
      buf += it.next()
    buf.toList
  }

  def appendOptionNames(out: DelimitedBuilder): Unit =
    for (parsableOption <- parseableOptions)
      out.append(parsableOption.optionName).mark()

  override def getOptionText(options: T, defaultOptions: Nullable[T]): String = {
    val out = new DelimitedBuilder(optionDelimiterStr)
    for (parsableOption <- parseableOptions) {
      val text = parsableOption.getOptionText(options, defaultOptions).trim
      if (text.nonEmpty) out.append(text).mark()
    }
    out.toString
  }
}

object OptionsParser {
  val OPTION_0_IS_AMBIGUOUS:       String = "Option {0} matches: "
  val KEY_OPTION_0_IS_AMBIGUOUS:   String = "options.parser.option.ambiguous"
  val OPTION_0_DOES_NOT_MATCH:     String = "Option {0} does not match any of: "
  val KEY_OPTION_0_DOES_NOT_MATCH: String = "options.parser.option.unknown"
}
