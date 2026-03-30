/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-wikilink/src/main/java/com/vladsch/flexmark/ext/wikilink/WikiImage.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package wikilink

import ssg.md.util.sequence.BasedSequence

class WikiImage(linkIsFirst: Boolean) extends WikiNode(linkIsFirst) {

  def this(chars: BasedSequence, linkIsFirst: Boolean, canEscapePipe: Boolean) = {
    this(linkIsFirst)
    this.chars = chars
    setLinkChars(chars, false, canEscapePipe, false)
  }
}
