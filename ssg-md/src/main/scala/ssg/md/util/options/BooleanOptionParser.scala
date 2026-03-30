/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-options/src/main/java/com/vladsch/flexmark/util/options/BooleanOptionParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package options

import ssg.md.Nullable
import ssg.md.util.misc.Pair
import ssg.md.util.sequence.BasedSequence

import java.util.Collections

abstract class BooleanOptionParser[T](val optionName: String) extends OptionParser[T] {

  protected def setOptions(options:  T): T
  protected def isOptionSet(options: T): Boolean

  override def parseOption(optionText: BasedSequence, options: T, provider: Nullable[MessageProvider]): Pair[T, java.util.List[ParsedOption[T]]] =
    if (optionText.isEmpty) {
      new Pair(
        Nullable(setOptions(options)),
        Nullable(Collections.singletonList(new ParsedOption[T](optionText, this, ParsedOptionStatus.VALID)))
      )
    } else {
      val useProvider = if (provider.isDefined) provider.get else MessageProvider.DEFAULT
      val message     = useProvider.message(
        BooleanOptionParser.KEY_OPTION_0_PARAMETERS_1_IGNORED,
        BooleanOptionParser.OPTION_0_PARAMETERS_1_IGNORED,
        optionName.asInstanceOf[AnyRef],
        optionText.asInstanceOf[AnyRef]
      )
      new Pair(
        Nullable(setOptions(options)),
        Nullable(
          Collections.singletonList(
            new ParsedOption[T](optionText, this, ParsedOptionStatus.IGNORED, new ParserMessage(optionText, ParsedOptionStatus.IGNORED, message))
          )
        )
      )
    }

  override def getOptionText(options: T, defaultOptions: Nullable[T]): String =
    if (isOptionSet(options) && (defaultOptions.isEmpty || !isOptionSet(defaultOptions.get))) optionName
    else ""
}

object BooleanOptionParser {
  val OPTION_0_PARAMETERS_1_IGNORED:     String = "Option {0} does not have any parameters. {1} was ignored"
  val KEY_OPTION_0_PARAMETERS_1_IGNORED: String = "options.parser.boolean-option.ignored"
}
