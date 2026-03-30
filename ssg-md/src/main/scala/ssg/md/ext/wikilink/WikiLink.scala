/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-wikilink/src/main/java/com/vladsch/flexmark/ext/wikilink/WikiLink.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package wikilink

import ssg.md.ast.LinkRendered
import ssg.md.util.sequence.BasedSequence

class WikiLink(linkIsFirst: Boolean) extends WikiNode(linkIsFirst) with LinkRendered {

  def this(chars: BasedSequence, linkIsFirst: Boolean, allowAnchors: Boolean, canEscapePipe: Boolean, canEscapeAnchor: Boolean) = {
    this(linkIsFirst)
    this.chars = chars
    setLinkChars(chars, allowAnchors, canEscapePipe, canEscapeAnchor)
  }
}
