/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/parser/InlineParserOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/parser/InlineParserOptions.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package parser

import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class InlineParserOptions(options: DataHolder) {

  val matchLookaheadFirst:                    Boolean = Parser.MATCH_NESTED_LINK_REFS_FIRST.get(options)
  val parseMultiLineImageUrls:                Boolean = Parser.PARSE_MULTI_LINE_IMAGE_URLS.get(options)
  val hardLineBreakLimit:                     Boolean = Parser.HARD_LINE_BREAK_LIMIT.get(options)
  val spaceInLinkUrls:                        Boolean = Parser.SPACE_IN_LINK_URLS.get(options)
  val spaceInLinkElements:                    Boolean = Parser.SPACE_IN_LINK_ELEMENTS.get(options)
  val codeSoftLineBreaks:                     Boolean = Parser.CODE_SOFT_LINE_BREAKS.get(options)
  val inlineDelimiterDirectionalPunctuations: Boolean = Parser.INLINE_DELIMITER_DIRECTIONAL_PUNCTUATIONS.get(options)
  val linksAllowMatchedParentheses:           Boolean = Parser.LINKS_ALLOW_MATCHED_PARENTHESES.get(options)
  val wwwAutoLinkElement:                     Boolean = Parser.WWW_AUTO_LINK_ELEMENT.get(options)
  val intellijDummyIdentifier:                Boolean = Parser.INTELLIJ_DUMMY_IDENTIFIER.get(options)
  val parseJekyllMacrosInUrls:                Boolean = Parser.PARSE_JEKYLL_MACROS_IN_URLS.get(options)
  val useHardcodedLinkAddressParser:          Boolean = Parser.USE_HARDCODED_LINK_ADDRESS_PARSER.get(options)
  val linkTextPriorityOverLinkRef:            Boolean = Parser.LINK_TEXT_PRIORITY_OVER_LINK_REF.get(options)
}
