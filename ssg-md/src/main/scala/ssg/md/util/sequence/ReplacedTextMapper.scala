/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/ReplacedTextMapper.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/ReplacedTextMapper.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package sequence

import ssg.md.Nullable

import java.util as ju
import scala.util.boundary
import scala.util.boundary.break

/** Class which tracks text replacements to provide original offset from modified offset.
  *
  * This is needed when the original based sequence needs to be un-escaped but offsets to original escaped text are needed.
  *
  * These replacements can be nested so that you can track replacements of replaced text. To add nested replacements use startNestedReplacement()
  *
  * when isModified returns true then the text mapper is already used and nested replacements need to be applied
  */
// REFACTOR: to use segment builder with ISegmentBuilder.F_INCLUDE_ANCHORS and use segment information to find original offsets
class ReplacedTextMapper(private var original: BasedSequence) {

  private var parent:            Nullable[ReplacedTextMapper]     = Nullable.empty[ReplacedTextMapper]
  private var regions:           ju.ArrayList[ReplacedTextRegion] = new ju.ArrayList[ReplacedTextRegion]()
  private var replacedSegments:  ju.ArrayList[BasedSequence]      = new ju.ArrayList[BasedSequence]()
  private var _replacedLength:   Int                              = 0
  private var _replacedSequence: Nullable[BasedSequence]          = Nullable.empty[BasedSequence]

  // Copy constructor for nesting
  private def this(other: ReplacedTextMapper, dummy: Boolean) = {
    this(other.original)
    this.parent = other.parent
    this.regions = other.regions
    this.replacedSegments = other.replacedSegments
    this._replacedLength = other._replacedLength
    this._replacedSequence = Nullable(other.replacedSequence)
  }

  def startNestedReplacement(sequence: BasedSequence): Unit = {
    assert(sequence.equals(this.replacedSequence))

    // create parent from our data and re-initialize
    this.parent = Nullable(new ReplacedTextMapper(this, true))
    this.original = sequence
    this.regions = new ju.ArrayList[ReplacedTextRegion]()
    this.replacedSegments = new ju.ArrayList[BasedSequence]()
    this._replacedLength = 0
    this._replacedSequence = Nullable.empty[BasedSequence]
  }

  def isModified: Boolean = _replacedLength > 0

  def isFinalized: Boolean = _replacedSequence.isDefined

  private def finalizeMods(): Unit =
    if (!_replacedSequence.isDefined) {
      _replacedSequence = Nullable(
        if (replacedSegments.isEmpty) BasedSequence.NULL
        else SegmentedSequence.create(original, replacedSegments)
      )
    }

  def getParent: Nullable[ReplacedTextMapper] = parent

  def addReplacedText(startIndex: Int, endIndex: Int, replacedSequence: BasedSequence): Unit = {
    if (isFinalized) throw new IllegalStateException("Cannot modify finalized ReplacedTextMapper")

    regions.add(
      new ReplacedTextRegion(
        original.subSequence(startIndex, endIndex).getSourceRange,
        Range.of(startIndex, endIndex),
        Range.of(_replacedLength, _replacedLength + replacedSequence.length())
      )
    )
    _replacedLength += replacedSequence.length()
    replacedSegments.add(replacedSequence)
  }

  def addOriginalText(startIndex: Int, endIndex: Int): Unit = {
    if (isFinalized) throw new IllegalStateException("Cannot modify finalized ReplacedTextMapper")

    if (startIndex < endIndex) {
      val originalSegment = original.subSequence(startIndex, endIndex)
      regions.add(
        new ReplacedTextRegion(
          originalSegment.getSourceRange,
          Range.of(startIndex, endIndex),
          Range.of(_replacedLength, _replacedLength + originalSegment.length())
        )
      )
      _replacedLength += originalSegment.length()
      replacedSegments.add(originalSegment)
    }
  }

  def getRegions: ju.ArrayList[ReplacedTextRegion] = {
    finalizeMods()
    regions
  }

  def getReplacedSegments: ju.ArrayList[BasedSequence] = {
    finalizeMods()
    replacedSegments
  }

  def replacedSequence: BasedSequence = {
    finalizeMods()
    _replacedSequence.get
  }

  def replacedLength: Int = {
    finalizeMods()
    _replacedLength
  }

  private def parentOriginalOffset(originalIndex: Int): Int =
    if (parent.isDefined) parent.get.originalOffset(originalIndex) else originalIndex

  def originalOffset(replacedIndex: Int): Int = {
    finalizeMods()

    if (regions.isEmpty) parentOriginalOffset(replacedIndex)
    else if (replacedIndex == _replacedLength) parentOriginalOffset(original.length())
    else {
      boundary[Int] {
        var originalIndex = replacedIndex
        val iter          = regions.iterator()
        while (iter.hasNext) {
          val region = iter.next()
          if (region.containsReplacedIndex(replacedIndex)) {
            originalIndex = region.originalRange.start + replacedIndex - region.replacedRange.start
            if (originalIndex > region.originalRange.end) {
              originalIndex = region.originalRange.end
            }
            break(parentOriginalOffset(originalIndex))
          }
        }
        parentOriginalOffset(originalIndex)
      }
    }
  }
}
