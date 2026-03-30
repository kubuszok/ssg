/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/AttributesDelimiter.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package attributes

import ssg.md.util.sequence.BasedSequence

/** A empty implicit AttributesNode used to mark attribute span start */
class AttributesDelimiter() extends AttributesNode {

  def this(chars: BasedSequence) = { this(); this.chars = chars }

  def this(openingMarker: BasedSequence, text: BasedSequence, closingMarker: BasedSequence) = {
    this()
    this.chars = openingMarker.baseSubSequence(openingMarker.startOffset, closingMarker.endOffset)
    this.openingMarker = openingMarker
    this.text = text
    this.closingMarker = closingMarker
  }

  def this(chars: BasedSequence, attributesBlockText: String) = { this(); this.chars = chars }
}
