/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/AttributeValueQuotes.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package attributes

import ssg.md.util.misc.CharPredicate
import ssg.md.util.sequence.SequenceUtils

enum AttributeValueQuotes extends java.lang.Enum[AttributeValueQuotes] {
  case AS_IS, NO_QUOTES_SINGLE_PREFERRED, NO_QUOTES_DOUBLE_PREFERRED, SINGLE_PREFERRED, DOUBLE_PREFERRED, SINGLE_QUOTES, DOUBLE_QUOTES

  def quotesFor(text: CharSequence, defaultQuotes: CharSequence): String = {
    this match {
      case NO_QUOTES_SINGLE_PREFERRED =>
        if (!SequenceUtils.containsAny(text, AttributeValueQuotes.P_SPACES_OR_QUOTES)) ""
        else if (!SequenceUtils.containsAny(text, AttributeValueQuotes.P_SINGLE_QUOTES) || SequenceUtils.containsAny(text, AttributeValueQuotes.P_DOUBLE_QUOTES)) "'"
        else "\""
      case NO_QUOTES_DOUBLE_PREFERRED =>
        if (!SequenceUtils.containsAny(text, AttributeValueQuotes.P_SPACES_OR_QUOTES)) ""
        else if (!SequenceUtils.containsAny(text, AttributeValueQuotes.P_DOUBLE_QUOTES) || SequenceUtils.containsAny(text, AttributeValueQuotes.P_SINGLE_QUOTES)) "\""
        else "'"
      case SINGLE_PREFERRED =>
        if (!SequenceUtils.containsAny(text, AttributeValueQuotes.P_SINGLE_QUOTES) || SequenceUtils.containsAny(text, AttributeValueQuotes.P_DOUBLE_QUOTES)) "'"
        else "\""
      case DOUBLE_PREFERRED =>
        if (!SequenceUtils.containsAny(text, AttributeValueQuotes.P_DOUBLE_QUOTES) || SequenceUtils.containsAny(text, AttributeValueQuotes.P_SINGLE_QUOTES)) "\""
        else "'"
      case SINGLE_QUOTES => "'"
      case DOUBLE_QUOTES => "\""
      case AS_IS => defaultQuotes.toString
    }
  }
}

object AttributeValueQuotes {
  val P_SPACES_OR_QUOTES: CharPredicate = CharPredicate.anyOf(" \t\n'\"")
  val P_SINGLE_QUOTES: CharPredicate = CharPredicate.anyOf("'")
  val P_DOUBLE_QUOTES: CharPredicate = CharPredicate.anyOf("\"")
}
