/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/SimTocOptionList.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/SimTocOptionList.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package toc

import ssg.md.util.ast.{ DoNotDecorate, Node }
import ssg.md.util.sequence.BasedSequence

/** A sim toc option list node */
class SimTocOptionList() extends Node, DoNotDecorate {

  override def segments: Array[BasedSequence] = Node.EMPTY_SEGMENTS

  def this(chars: BasedSequence) = { this(); this.chars = chars }
}
