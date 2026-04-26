/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/internal/SimTocOptionsParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/internal/SimTocOptionsParser.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package toc
package internal

import ssg.md.util.options.OptionsParser

/** Parses SimTOC option strings */
class SimTocOptionsParser extends OptionsParser[TocOptions]("SimTocOptions", SimTocOptionTypes.OPTIONS, ' ', '=')

object SimTocOptionsParser
