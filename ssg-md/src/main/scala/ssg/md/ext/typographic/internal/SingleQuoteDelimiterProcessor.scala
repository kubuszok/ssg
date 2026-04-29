/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-typographic/src/main/java/com/vladsch/flexmark/ext/typographic/internal/SingleQuoteDelimiterProcessor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-typographic/src/main/java/com/vladsch/flexmark/ext/typographic/internal/SingleQuoteDelimiterProcessor.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package typographic
package internal

import ssg.md.Nullable

class SingleQuoteDelimiterProcessor(options: TypographicOptions)
    extends QuoteDelimiterProcessorBase(options, '\'', '\'', options.singleQuoteOpen, options.singleQuoteClose, Nullable(options.singleQuoteUnmatched))
