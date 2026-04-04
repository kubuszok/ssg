/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-footnotes/src/main/java/com/vladsch/flexmark/ext/footnotes/internal/FootnoteFormatOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package footnotes
package internal

import ssg.md.util.data.DataHolder
import ssg.md.util.format.options.{ ElementPlacement, ElementPlacementSort }

import scala.language.implicitConversions

class FootnoteFormatOptions(options: DataHolder) {

  val footnotePlacement: ElementPlacement     = FootnoteExtension.FOOTNOTE_PLACEMENT.get(options)
  val footnoteSort:      ElementPlacementSort = FootnoteExtension.FOOTNOTE_SORT.get(options)
}
