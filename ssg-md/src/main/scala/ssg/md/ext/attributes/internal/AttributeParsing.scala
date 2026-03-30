/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/internal/AttributeParsing.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package attributes
package internal

import ssg.md.ast.util.Parsing

import java.util.regex.Pattern
import scala.language.implicitConversions

class AttributeParsing(parsing: Parsing) {

  val ATTRIBUTES_TAG: Pattern = {
    val unquotedValue = parsing.UNQUOTEDVALUE
    if (AttributesExtension.USE_EMPTY_IMPLICIT_AS_SPAN_DELIMITER.get(parsing.options)) {
      Pattern.compile(
        "^\\{((?:[#.])|(?:" + "\\s*([#.]" + unquotedValue + "|" + parsing.ATTRIBUTENAME + ")\\s*(?:=\\s*(" + parsing.ATTRIBUTEVALUE + ")?" + ")?" + ")" +
          "(?:" + "\\s+([#.]" + unquotedValue + "|" + parsing.ATTRIBUTENAME + ")\\s*(?:=\\s*(" + parsing.ATTRIBUTEVALUE + ")?" + ")?" + ")*" + "\\s*)\\}"
      )
    } else {
      Pattern.compile(
        "^\\{((?:" + "\\s*([#.]" + unquotedValue + "|" + parsing.ATTRIBUTENAME + ")\\s*(?:=\\s*(" + parsing.ATTRIBUTEVALUE + ")?" + ")?" + ")" +
          "(?:" + "\\s+([#.]" + unquotedValue + "|" + parsing.ATTRIBUTENAME + ")\\s*(?:=\\s*(" + parsing.ATTRIBUTEVALUE + ")?" + ")?" + ")*" + "\\s*)\\}"
      )
    }
  }

  val ATTRIBUTE: Pattern = {
    val unquotedValue = parsing.UNQUOTEDVALUE
    Pattern.compile("\\s*([#.]" + unquotedValue + "|" + parsing.ATTRIBUTENAME + ")\\s*(?:=\\s*(" + parsing.ATTRIBUTEVALUE + ")?" + ")?")
  }
}
