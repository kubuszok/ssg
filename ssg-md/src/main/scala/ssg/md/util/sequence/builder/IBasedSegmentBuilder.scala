/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/IBasedSegmentBuilder.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/IBasedSegmentBuilder.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package sequence
package builder

import ssg.md.util.sequence.BasedSequence

trait IBasedSegmentBuilder[S <: IBasedSegmentBuilder[S]] extends ISegmentBuilder[S] {

  def baseSequence: BasedSequence

  def toStringWithRangesVisibleWhitespace(): String

  def toStringWithRanges(): String

  def toStringChars(): String
}
