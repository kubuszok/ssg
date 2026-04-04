/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-aside/src/main/java/com/vladsch/flexmark/ext/aside/internal/AsideOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package aside
package internal

import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class AsideOptions(options: DataHolder) {
  val extendToBlankLine:                     Boolean = AsideExtension.EXTEND_TO_BLANK_LINE.get(options)
  val ignoreBlankLine:                       Boolean = AsideExtension.IGNORE_BLANK_LINE.get(options)
  val allowLeadingSpace:                     Boolean = AsideExtension.ALLOW_LEADING_SPACE.get(options)
  val interruptsParagraph:                   Boolean = AsideExtension.INTERRUPTS_PARAGRAPH.get(options)
  val interruptsItemParagraph:               Boolean = AsideExtension.INTERRUPTS_ITEM_PARAGRAPH.get(options)
  val withLeadSpacesInterruptsItemParagraph: Boolean = AsideExtension.WITH_LEAD_SPACES_INTERRUPTS_ITEM_PARAGRAPH.get(options)
}
