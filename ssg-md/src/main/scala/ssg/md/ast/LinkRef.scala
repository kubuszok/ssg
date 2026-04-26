/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark/src/main/java/com/vladsch/flexmark/ast/LinkRef.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark/src/main/java/com/vladsch/flexmark/ast/LinkRef.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ast

import ssg.md.util.sequence.BasedSequence

class LinkRef extends RefNode, LinkRendered {

  def this(chars: BasedSequence) = {
    this()
    this.chars = chars
  }

  def this(
    textOpenMarker:       BasedSequence,
    text:                 BasedSequence,
    textCloseMarker:      BasedSequence,
    referenceOpenMarker:  BasedSequence,
    reference:            BasedSequence,
    referenceCloseMarker: BasedSequence
  ) = {
    this()
    this.chars = textOpenMarker.baseSubSequence(textOpenMarker.startOffset, referenceCloseMarker.endOffset)
    this.textOpeningMarker = textOpenMarker
    this.text = text
    this.textClosingMarker = textCloseMarker
    this.referenceOpeningMarker = referenceOpenMarker
    this.reference = reference
    this.referenceClosingMarker = referenceCloseMarker
  }

  def this(
    chars:                BasedSequence,
    textOpenMarker:       BasedSequence,
    text:                 BasedSequence,
    textCloseMarker:      BasedSequence,
    referenceOpenMarker:  BasedSequence,
    reference:            BasedSequence,
    referenceCloseMarker: BasedSequence
  ) = {
    this()
    this.chars = chars
    this.textOpeningMarker = textOpenMarker
    this.text = text
    this.textClosingMarker = textCloseMarker
    this.referenceOpeningMarker = referenceOpenMarker
    this.reference = reference
    this.referenceClosingMarker = referenceCloseMarker
  }

  def this(textOpenMarker: BasedSequence, text: BasedSequence, textCloseMarker: BasedSequence) = {
    this()
    this.chars = textOpenMarker.baseSubSequence(textOpenMarker.startOffset, textCloseMarker.endOffset)
    this.textOpeningMarker = textOpenMarker
    this.text = text
    this.textClosingMarker = textCloseMarker
  }

  def this(chars: BasedSequence, textOpenMarker: BasedSequence, text: BasedSequence, textCloseMarker: BasedSequence) = {
    this()
    this.chars = chars
    this.textOpeningMarker = textOpenMarker
    this.text = text
    this.textClosingMarker = textCloseMarker
  }

  def this(
    textOpenMarker:       BasedSequence,
    text:                 BasedSequence,
    textCloseMarker:      BasedSequence,
    referenceOpenMarker:  BasedSequence,
    referenceCloseMarker: BasedSequence
  ) = {
    this()
    this.chars = textOpenMarker.baseSubSequence(textOpenMarker.startOffset, referenceCloseMarker.endOffset)
    this.textOpeningMarker = textOpenMarker
    this.text = text
    this.textClosingMarker = textCloseMarker
    this.referenceOpeningMarker = referenceOpenMarker
    this.referenceClosingMarker = referenceCloseMarker
  }
}
