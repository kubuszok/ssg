/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-footnotes/src/main/java/com/vladsch/flexmark/ext/footnotes/internal/FootnoteOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package footnotes
package internal

import ssg.md.parser.Parser
import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class FootnoteOptions(options: DataHolder) {

  val footnoteRefPrefix:        String = FootnoteExtension.FOOTNOTE_REF_PREFIX.get(options)
  val footnoteRefSuffix:        String = FootnoteExtension.FOOTNOTE_REF_SUFFIX.get(options)
  val footnoteBackRefString:    String = FootnoteExtension.FOOTNOTE_BACK_REF_STRING.get(options)
  val footnoteLinkRefClass:     String = FootnoteExtension.FOOTNOTE_LINK_REF_CLASS.get(options)
  val footnoteBackLinkRefClass: String = FootnoteExtension.FOOTNOTE_BACK_LINK_REF_CLASS.get(options)
  val contentIndent:            Int    = Parser.LISTS_ITEM_INDENT.get(options)
}
