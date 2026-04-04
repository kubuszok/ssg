/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-typographic/src/main/java/com/vladsch/flexmark/ext/typographic/internal/TypographicOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package typographic
package internal

import ssg.md.Nullable
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class TypographicOptions(options: DataHolder) {

  val typographicQuotes:    Boolean          = TypographicExtension.ENABLE_QUOTES.get(options)
  val typographicSmarts:    Boolean          = TypographicExtension.ENABLE_SMARTS.get(options)
  val ellipsis:             String           = TypographicExtension.ELLIPSIS.get(options)
  val ellipsisSpaced:       String           = TypographicExtension.ELLIPSIS_SPACED.get(options)
  val enDash:               String           = TypographicExtension.EN_DASH.get(options)
  val emDash:               String           = TypographicExtension.EM_DASH.get(options)
  val singleQuoteOpen:      String           = TypographicExtension.SINGLE_QUOTE_OPEN.get(options)
  val singleQuoteClose:     String           = TypographicExtension.SINGLE_QUOTE_CLOSE.get(options)
  val singleQuoteUnmatched: String           = TypographicExtension.SINGLE_QUOTE_UNMATCHED.get(options)
  val doubleQuoteOpen:      String           = TypographicExtension.DOUBLE_QUOTE_OPEN.get(options)
  val doubleQuoteClose:     String           = TypographicExtension.DOUBLE_QUOTE_CLOSE.get(options)
  val doubleQuoteUnmatched: Nullable[String] = Nullable(TypographicExtension.DOUBLE_QUOTE_UNMATCHED.get(options))
  val angleQuoteOpen:       String           = TypographicExtension.ANGLE_QUOTE_OPEN.get(options)
  val angleQuoteClose:      String           = TypographicExtension.ANGLE_QUOTE_CLOSE.get(options)
  val angleQuoteUnmatched:  Nullable[String] = Nullable(TypographicExtension.ANGLE_QUOTE_UNMATCHED.get(options))
}
