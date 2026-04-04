/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-options/src/main/java/com/vladsch/flexmark/util/options/ParsedOption.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package options

import ssg.md.Nullable
import ssg.md.util.sequence.BasedSequence

class ParsedOption[T](
  val source:       BasedSequence,
  val optionParser: OptionParser[T],
  optionResultIn:   ParsedOptionStatus,
  messagesIn:       Nullable[List[ParserMessage]],
  parsedOptions:    Nullable[List[ParsedOption[T]]]
) {

  val (optionResult: ParsedOptionStatus, messages: Nullable[List[ParserMessage]]) =
    if (parsedOptions.isDefined) {
      var result = optionResultIn
      var mergedMessages: Nullable[List[ParserMessage]] = messagesIn

      for (parsedOption <- parsedOptions.get) {
        result = result.escalate(parsedOption.optionResult)
        if (parsedOption.messages.isDefined) {
          mergedMessages =
            if (mergedMessages.isEmpty) Nullable(parsedOption.messages.get)
            else Nullable(mergedMessages.get ++ parsedOption.messages.get)
        }
      }
      (result, mergedMessages)
    } else {
      (optionResultIn, messagesIn)
    }

  def this(source: BasedSequence, optionParser: OptionParser[T], optionResult: ParsedOptionStatus) =
    this(source, optionParser, optionResult, Nullable.empty, Nullable.empty)

  def this(source: BasedSequence, optionParser: OptionParser[T], optionResult: ParsedOptionStatus, message: ParserMessage) =
    this(source, optionParser, optionResult, Nullable(List(message)), Nullable.empty)

  def this(source: BasedSequence, optionParser: OptionParser[T], optionResult: ParsedOptionStatus, messages: List[ParserMessage]) =
    this(source, optionParser, optionResult, Nullable(messages), Nullable.empty)
}
