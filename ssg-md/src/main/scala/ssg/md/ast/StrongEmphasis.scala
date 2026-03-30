/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/StrongEmphasis.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ast

import ssg.md.util.sequence.BasedSequence

class StrongEmphasis extends DelimitedNodeImpl {

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(openingMarker: BasedSequence, content: BasedSequence, closingMarker: BasedSequence) = {
    this()
    this.chars = openingMarker.baseSubSequence(openingMarker.startOffset, closingMarker.endOffset)
    this.openingMarker = openingMarker
    this.text = content
    this.closingMarker = closingMarker
  }
}
