/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-typographic/src/main/java/com/vladsch/flexmark/ext/typographic/internal/AngleQuoteDelimiterProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-typographic/src/main/java/com/vladsch/flexmark/ext/typographic/internal/AngleQuoteDelimiterProcessor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package typographic
package internal

import scala.language.implicitConversions

class AngleQuoteDelimiterProcessor(options: TypographicOptions) extends QuoteDelimiterProcessorBase(options, '<', '>', options.angleQuoteOpen, options.angleQuoteClose, options.angleQuoteUnmatched) {

  override def minLength: Int = 2

  override def canBeOpener(
    before:              String,
    after:               String,
    leftFlanking:        Boolean,
    rightFlanking:       Boolean,
    beforeIsPunctuation: Boolean,
    afterIsPunctuation:  Boolean,
    beforeIsWhitespace:  Boolean,
    afterIsWhiteSpace:   Boolean
  ): Boolean = true

  override def canBeCloser(
    before:              String,
    after:               String,
    leftFlanking:        Boolean,
    rightFlanking:       Boolean,
    beforeIsPunctuation: Boolean,
    afterIsPunctuation:  Boolean,
    beforeIsWhitespace:  Boolean,
    afterIsWhiteSpace:   Boolean
  ): Boolean = true

  override protected def isAllowed(seq: CharSequence, index: Int): Boolean = true
}
