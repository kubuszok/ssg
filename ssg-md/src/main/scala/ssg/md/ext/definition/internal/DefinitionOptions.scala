/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/internal/DefinitionOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/internal/DefinitionOptions.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package definition
package internal

import ssg.md.parser.{ Parser, ParserEmulationProfile }
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class DefinitionOptions(options: DataHolder) {

  val markerSpaces:                       Int                    = DefinitionExtension.MARKER_SPACES.get(options)
  val tildeMarker:                        Boolean                = DefinitionExtension.TILDE_MARKER.get(options)
  val colonMarker:                        Boolean                = DefinitionExtension.COLON_MARKER.get(options)
  val myParserEmulationProfile:           ParserEmulationProfile = Parser.PARSER_EMULATION_PROFILE.get(options)
  val autoLoose:                          Boolean                = Parser.LISTS_AUTO_LOOSE.get(options)
  val autoLooseOneLevelLists:             Boolean                = Parser.LISTS_AUTO_LOOSE_ONE_LEVEL_LISTS.get(options)
  val looseOnPrevLooseItem:               Boolean                = Parser.LISTS_LOOSE_WHEN_PREV_HAS_TRAILING_BLANK_LINE.get(options)
  val looseWhenBlankFollowsItemParagraph: Boolean                = Parser.LISTS_LOOSE_WHEN_BLANK_LINE_FOLLOWS_ITEM_PARAGRAPH.get(options)
  val looseWhenHasLooseSubItem:           Boolean                = Parser.LISTS_LOOSE_WHEN_HAS_LOOSE_SUB_ITEM.get(options)
  val looseWhenHasTrailingBlankLine:      Boolean                = Parser.LISTS_LOOSE_WHEN_HAS_TRAILING_BLANK_LINE.get(options)
  val codeIndent:                         Int                    = Parser.LISTS_CODE_INDENT.get(options)
  val itemIndent:                         Int                    = Parser.LISTS_ITEM_INDENT.get(options)
  val newItemCodeIndent:                  Int                    = Parser.LISTS_NEW_ITEM_CODE_INDENT.get(options)
  val doubleBlankLineBreaksList:          Boolean                = DefinitionExtension.DOUBLE_BLANK_LINE_BREAKS_LIST.get(options)
}
