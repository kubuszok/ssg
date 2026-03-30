/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/ISegmentBuilder.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence
package builder

import ssg.md.Nullable
import ssg.md.util.misc.BitFieldSet
import ssg.md.util.sequence.Range

trait ISegmentBuilder[S <: ISegmentBuilder[S]] extends java.lang.Iterable[Object] {

  def options:          Int
  def isIncludeAnchors: Boolean

  def isEmpty:                 Boolean
  def isBaseSubSequenceRange:  Boolean
  def getBaseSubSequenceRange: Nullable[Range]
  def haveOffsets:             Boolean
  def span:                    Int
  def startOffset:             Int
  def endOffset:               Int

  def size:          Int
  def getText:       CharSequence
  def noAnchorsSize: Int
  def length:        Int

  def isTrackTextFirst256: Boolean
  def textLength:          Int
  def textSegments:        Int

  def textSpaceLength:   Int
  def textSpaceSegments: Int

  def textFirst256Length:   Int
  def textFirst256Segments: Int

  /** Return iterator over segment parts Range - BASE CharSequence - TEXT
    *
    * @return
    *   iterator over segment builder parts
    */
  override def iterator(): java.util.Iterator[Object]

  /** Return iterator over segments
    *
    * @return
    *   iterator over segment builder segments
    */
  def getSegments: java.lang.Iterable[Seg]

  def append(startOffset:                        Int, endOffset: Int): S
  def append(text:                               CharSequence):        S
  def appendAnchor(offset:                       Int):                 S
  def append(range:                              Range):               S
  def toStringWithRangesVisibleWhitespace(chars: CharSequence):        String
  def toStringWithRanges(chars:                  CharSequence):        String
  def toString(chars:                            CharSequence):        String
}

object ISegmentBuilder {

  enum Options extends java.lang.Enum[Options] {
    case INCLUDE_ANCHORS
    case TRACK_FIRST256
  }

  val O_INCLUDE_ANCHORS: Options = Options.INCLUDE_ANCHORS
  val O_TRACK_FIRST256:  Options = Options.TRACK_FIRST256

  val F_INCLUDE_ANCHORS: Int = BitFieldSet.intMask(O_INCLUDE_ANCHORS)
  val F_TRACK_FIRST256:  Int = BitFieldSet.intMask(O_TRACK_FIRST256)

  val F_DEFAULT: Int = F_INCLUDE_ANCHORS | F_TRACK_FIRST256
}
