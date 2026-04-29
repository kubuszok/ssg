/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-tag/src/main/java/com/vladsch/flexmark/ext/jekyll/tag/internal/JekyllTagParsing.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-jekyll-tag/src/main/java/com/vladsch/flexmark/ext/jekyll/tag/internal/JekyllTagParsing.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package jekyll
package tag
package internal

import ssg.md.ast.util.Parsing

import java.util.regex.Pattern

class JekyllTagParsing(val myParsing: Parsing) {

  val OPEN_MACROTAG: String  = "\\{%\\s+(" + myParsing.TAGNAME + ")(?:\\s+.+)?\\s+%\\}"
  val MACRO_OPEN:    Pattern = Pattern.compile('^' + OPEN_MACROTAG + "\\s*$", Pattern.CASE_INSENSITIVE)
  val MACRO_TAG:     Pattern = Pattern.compile(OPEN_MACROTAG)
}
