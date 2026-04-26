/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/TocBlock.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/TocBlock.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package toc

import ssg.md.util.sequence.BasedSequence

/** A TOC node */
class TocBlock(chars: BasedSequence, styleChars: BasedSequence, closingSimToc: Boolean) extends TocBlockBase(chars, styleChars, closingSimToc) {

  def this(chars: BasedSequence) = this(chars, null.asInstanceOf[BasedSequence], false) // @nowarn - overloaded ctor
  def this(chars: BasedSequence, closingSimToc: Boolean) = this(chars, null.asInstanceOf[BasedSequence], closingSimToc) // @nowarn - overloaded ctor
  def this(chars: BasedSequence, styleChars:    BasedSequence) = this(chars, styleChars, false)
}
