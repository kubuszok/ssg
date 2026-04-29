/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/SharedDataKeys.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-data/src/main/java/com/vladsch/flexmark/util/data/SharedDataKeys.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package data

import ssg.md.Nullable
import ssg.md.util.misc.Extension

import java.util.Collection

object SharedDataKeys {

  // BuilderBase
  val EXTENSIONS: DataKey[Collection[Extension]] =
    new DataKey[Collection[Extension]]("EXTENSIONS", java.util.Collections.emptyList[Extension]())

  // Parser
  val HEADING_NO_ATX_SPACE: DataKey[Boolean] =
    new DataKey[Boolean]("HEADING_NO_ATX_SPACE", false)
  // used to set escaping of # at start independent of HEADING_NO_ATX_SPACE setting if desired
  val ESCAPE_HEADING_NO_ATX_SPACE: DataKey[Boolean] =
    new DataKey[Boolean](
      "ESCAPE_HEADING_NO_ATX_SPACE",
      false,
      new DataNotNullValueFactory[Boolean] {
        def apply(dataHolder: DataHolder): Nullable[Boolean] = Nullable(HEADING_NO_ATX_SPACE.get(Nullable(dataHolder)))
      }
    )
  val HTML_FOR_TRANSLATOR: DataKey[Boolean] =
    new DataKey[Boolean]("HTML_FOR_TRANSLATOR", false)
  val INTELLIJ_DUMMY_IDENTIFIER: DataKey[Boolean] =
    new DataKey[Boolean]("INTELLIJ_DUMMY_IDENTIFIER", false)
  val PARSE_INNER_HTML_COMMENTS: DataKey[Boolean] =
    new DataKey[Boolean]("PARSE_INNER_HTML_COMMENTS", false)
  val BLANK_LINES_IN_AST: DataKey[Boolean] =
    new DataKey[Boolean]("BLANK_LINES_IN_AST", false)
  val TRANSLATION_HTML_BLOCK_TAG_PATTERN: DataKey[String] =
    new DataKey[String]("TRANSLATION_HTML_BLOCK_TAG_PATTERN", "___(?:\\d+)_")
  val TRANSLATION_HTML_INLINE_TAG_PATTERN: DataKey[String] =
    new DataKey[String]("TRANSLATION_HTML_INLINE_TAG_PATTERN", "__(?:\\d+)_")
  val TRANSLATION_AUTOLINK_TAG_PATTERN: DataKey[String] =
    new DataKey[String]("TRANSLATION_AUTOLINK_TAG_PATTERN", "____(?:\\d+)_")

  val RENDERER_MAX_TRAILING_BLANK_LINES: DataKey[Int] =
    new DataKey[Int]("RENDERER_MAX_TRAILING_BLANK_LINES", 1)
  val RENDERER_MAX_BLANK_LINES: DataKey[Int] =
    new DataKey[Int]("RENDERER_MAX_BLANK_LINES", 1)
  val INDENT_SIZE: DataKey[Int] =
    new DataKey[Int]("INDENT_SIZE", 0)
  val PERCENT_ENCODE_URLS: DataKey[Boolean] =
    new DataKey[Boolean]("PERCENT_ENCODE_URLS", false)
  val HEADER_ID_GENERATOR_RESOLVE_DUPES: DataKey[Boolean] =
    new DataKey[Boolean]("HEADER_ID_GENERATOR_RESOLVE_DUPES", true)
  val HEADER_ID_GENERATOR_TO_DASH_CHARS: DataKey[String] =
    new DataKey[String]("HEADER_ID_GENERATOR_TO_DASH_CHARS", " -_")
  val HEADER_ID_GENERATOR_NON_DASH_CHARS: DataKey[String] =
    new DataKey[String]("HEADER_ID_GENERATOR_NON_DASH_CHARS", "")
  val HEADER_ID_GENERATOR_NO_DUPED_DASHES: DataKey[Boolean] =
    new DataKey[Boolean]("HEADER_ID_GENERATOR_NO_DUPED_DASHES", false)
  val HEADER_ID_GENERATOR_NON_ASCII_TO_LOWERCASE: DataKey[Boolean] =
    new DataKey[Boolean]("HEADER_ID_GENERATOR_NON_ASCII_TO_LOWERCASE", true)
  val HEADER_ID_REF_TEXT_TRIM_LEADING_SPACES: DataKey[Boolean] =
    new DataKey[Boolean]("HEADER_ID_REF_TEXT_TRIM_LEADING_SPACES", true)
  val HEADER_ID_REF_TEXT_TRIM_TRAILING_SPACES: DataKey[Boolean] =
    new DataKey[Boolean]("HEADER_ID_REF_TEXT_TRIM_TRAILING_SPACES", true)
  val HEADER_ID_ADD_EMOJI_SHORTCUT: DataKey[Boolean] =
    new DataKey[Boolean]("HEADER_ID_ADD_EMOJI_SHORTCUT", false)
  val RENDER_HEADER_ID: DataKey[Boolean] =
    new DataKey[Boolean]("RENDER_HEADER_ID", false)
  val GENERATE_HEADER_ID: DataKey[Boolean] =
    new DataKey[Boolean]("GENERATE_HEADER_ID", true)
  val DO_NOT_RENDER_LINKS: DataKey[Boolean] =
    new DataKey[Boolean]("DO_NOT_RENDER_LINKS", false)

  // Formatter
  val FORMATTER_MAX_BLANK_LINES: DataKey[Int] =
    new DataKey[Int]("FORMATTER_MAX_BLANK_LINES", 2)
  val FORMATTER_MAX_TRAILING_BLANK_LINES: DataKey[Int] =
    new DataKey[Int]("FORMATTER_MAX_TRAILING_BLANK_LINES", 1)
  val BLOCK_QUOTE_BLANK_LINES: DataKey[Boolean] =
    new DataKey[Boolean]("BLOCK_QUOTE_BLANK_LINES", true)

  val APPLY_SPECIAL_LEAD_IN_HANDLERS: DataKey[Boolean] =
    new DataKey[Boolean]("APPLY_SPECIAL_LEAD_IN_HANDLERS", true)
  val ESCAPE_SPECIAL_CHARS: DataKey[Boolean] =
    new DataKey[Boolean](
      "ESCAPE_SPECIAL_CHARS",
      true,
      new DataNotNullValueFactory[Boolean] {
        def apply(dataHolder: DataHolder): Nullable[Boolean] = Nullable(APPLY_SPECIAL_LEAD_IN_HANDLERS.get(Nullable(dataHolder)))
      }
    )
  val ESCAPE_NUMBERED_LEAD_IN: DataKey[Boolean] =
    new DataKey[Boolean](
      "ESCAPE_NUMBERED_LEAD_IN",
      true,
      new DataNotNullValueFactory[Boolean] {
        def apply(dataHolder: DataHolder): Nullable[Boolean] = Nullable(APPLY_SPECIAL_LEAD_IN_HANDLERS.get(Nullable(dataHolder)))
      }
    )
  val UNESCAPE_SPECIAL_CHARS: DataKey[Boolean] =
    new DataKey[Boolean](
      "UNESCAPE_SPECIAL_CHARS",
      true,
      new DataNotNullValueFactory[Boolean] {
        def apply(dataHolder: DataHolder): Nullable[Boolean] = Nullable(APPLY_SPECIAL_LEAD_IN_HANDLERS.get(Nullable(dataHolder)))
      }
    )
  val RUNNING_TESTS: DataKey[Boolean] =
    new DataKey[Boolean]("RUNNING_TESTS", false)
}
