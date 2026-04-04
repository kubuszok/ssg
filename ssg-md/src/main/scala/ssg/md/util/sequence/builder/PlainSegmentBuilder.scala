/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/PlainSegmentBuilder.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence
package builder

class PlainSegmentBuilder private (options: Int) extends SegmentBuilderBase[PlainSegmentBuilder](options) {

  private def this() =
    this(ISegmentBuilder.F_INCLUDE_ANCHORS)
}

object PlainSegmentBuilder {

  def emptyBuilder(): PlainSegmentBuilder = new PlainSegmentBuilder()

  def emptyBuilder(options: Int): PlainSegmentBuilder = new PlainSegmentBuilder(options)
}
