/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/internal/TocOptionsParser.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package toc
package internal

import ssg.md.util.options.OptionsParser

/** Parses TOC option strings like "levels=2 numbered hierarchy" */
class TocOptionsParser extends OptionsParser[TocOptions]("TocOptions", TocOptionTypes.OPTIONS, ' ', '=')

object TocOptionsParser
