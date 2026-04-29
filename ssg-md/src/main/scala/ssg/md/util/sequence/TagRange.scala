/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/TagRange.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/TagRange.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package sequence

class TagRange(val tag: String, startVal: Int, endVal: Int) extends Range(startVal, endVal) {

  def this(tag: CharSequence, range: Range) =
    this(String.valueOf(tag), range.start, range.end)

  def withTag(tag: CharSequence): TagRange =
    if (this.tag == String.valueOf(tag)) this
    else new TagRange(String.valueOf(tag), start, end)

  override def withStart(start: Int): TagRange =
    if (start == this.start) this else new TagRange(tag, start, end)

  override def withEnd(end: Int): TagRange =
    if (end == this.end) this else new TagRange(tag, start, end)

  override def withRange(start: Int, end: Int): TagRange =
    if (start == this.start && end == this.end) this else new TagRange(tag, start, end)
}

object TagRange {
  def of(tag: CharSequence, start: Int, end: Int): TagRange =
    new TagRange(String.valueOf(tag), start, end)
}
