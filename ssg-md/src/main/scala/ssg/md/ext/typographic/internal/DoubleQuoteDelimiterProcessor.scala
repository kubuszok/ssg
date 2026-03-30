/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-typographic/src/main/java/com/vladsch/flexmark/ext/typographic/internal/DoubleQuoteDelimiterProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package typographic
package internal

class DoubleQuoteDelimiterProcessor(options: TypographicOptions)
    extends QuoteDelimiterProcessorBase(options, '"', '"', options.doubleQuoteOpen, options.doubleQuoteClose, options.doubleQuoteUnmatched)
