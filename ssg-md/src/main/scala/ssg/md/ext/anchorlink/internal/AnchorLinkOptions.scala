/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-anchorlink/src/main/java/com/vladsch/flexmark/ext/anchorlink/internal/AnchorLinkOptions.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-anchorlink/src/main/java/com/vladsch/flexmark/ext/anchorlink/internal/AnchorLinkOptions.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package anchorlink
package internal

import ssg.md.util.data.DataHolder

import scala.language.implicitConversions

class AnchorLinkOptions(options: DataHolder) {
  val wrapText:      Boolean = AnchorLinkExtension.ANCHORLINKS_WRAP_TEXT.get(options)
  val textPrefix:    String  = AnchorLinkExtension.ANCHORLINKS_TEXT_PREFIX.get(options)
  val textSuffix:    String  = AnchorLinkExtension.ANCHORLINKS_TEXT_SUFFIX.get(options)
  val anchorClass:   String  = AnchorLinkExtension.ANCHORLINKS_ANCHOR_CLASS.get(options)
  val setName:       Boolean = AnchorLinkExtension.ANCHORLINKS_SET_NAME.get(options)
  val setId:         Boolean = AnchorLinkExtension.ANCHORLINKS_SET_ID.get(options)
  val noBlockQuotes: Boolean = AnchorLinkExtension.ANCHORLINKS_NO_BLOCK_QUOTE.get(options)
}
