/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-wikilink/src/main/java/com/vladsch/flexmark/ext/wikilink/WikiImage.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-wikilink/src/main/java/com/vladsch/flexmark/ext/wikilink/WikiImage.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
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
