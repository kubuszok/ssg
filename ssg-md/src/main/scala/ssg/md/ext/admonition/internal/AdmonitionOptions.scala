/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-admonition/src/main/java/com/vladsch/flexmark/ext/admonition/internal/AdmonitionOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-admonition/src/main/java/com/vladsch/flexmark/ext/admonition/internal/AdmonitionOptions.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package ext
package admonition
package internal

import ssg.md.util.data.DataHolder

import java.{ util => ju }

import scala.language.implicitConversions

class AdmonitionOptions(options: DataHolder) {

  val contentIndent:                     Int                    = AdmonitionExtension.CONTENT_INDENT.get(options)
  val allowLeadingSpace:                 Boolean                = AdmonitionExtension.ALLOW_LEADING_SPACE.get(options)
  val interruptsParagraph:               Boolean                = AdmonitionExtension.INTERRUPTS_PARAGRAPH.get(options)
  val interruptsItemParagraph:           Boolean                = AdmonitionExtension.INTERRUPTS_ITEM_PARAGRAPH.get(options)
  val withSpacesInterruptsItemParagraph: Boolean                = AdmonitionExtension.WITH_SPACES_INTERRUPTS_ITEM_PARAGRAPH.get(options)
  val allowLazyContinuation:             Boolean                = AdmonitionExtension.ALLOW_LAZY_CONTINUATION.get(options)
  val unresolvedQualifier:               String                 = AdmonitionExtension.UNRESOLVED_QUALIFIER.get(options)
  val qualifierTypeMap:                  ju.Map[String, String] = AdmonitionExtension.QUALIFIER_TYPE_MAP.get(options)
  val qualifierTitleMap:                 ju.Map[String, String] = AdmonitionExtension.QUALIFIER_TITLE_MAP.get(options)
  val typeSvgMap:                        ju.Map[String, String] = AdmonitionExtension.TYPE_SVG_MAP.get(options)
}
