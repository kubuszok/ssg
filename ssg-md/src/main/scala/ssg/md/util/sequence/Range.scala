/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/Range.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/Range.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package util
package sequence

/** Immutable range with start/end, factory methods, combine/intersect/etc.
  *
  * NOTE: Methods that reference BasedSequence/RichSequence are omitted here because those types have not yet been ported. They will be added as forward references become available.
  */
class Range protected (val start: Int, val end: Int) {

  protected def this(other: Range) =
    this(other.start, other.end)

  // Kotlin compatibility getters
  def component1(): Int = start
  def component2(): Int = end

  // compatibility getters with JetBrains API TextRange
  def startOffset: Int = start
  def endOffset:   Int = end

  def withStart(start:  Int):           Range = if (start == this.start) this else Range.of(start, end)
  def withEnd(end:      Int):           Range = if (end == this.end) this else Range.of(start, end)
  def endMinus(delta:   Int):           Range = if (delta == 0) this else Range.of(start, end - delta)
  def endPlus(delta:    Int):           Range = if (delta == 0) this else Range.of(start, end + delta)
  def startMinus(delta: Int):           Range = if (delta == 0) this else Range.of(start - delta, end)
  def startPlus(delta:  Int):           Range = if (delta == 0) this else Range.of(start + delta, end)
  def withRange(start:  Int, end: Int): Range = if (start == this.start && end == this.end) this else Range.of(start, end)
  def shiftLeft(delta:  Int):           Range = if (delta == 0) this else Range.of(start - delta, end - delta)
  def shiftRight(delta: Int):           Range = if (delta == 0) this else Range.of(start + delta, end + delta)

  def span: Int = if (isNull) 0 else end - start

  // NOTE: change to equal NULL instead of instance otherwise inheriting makes null equality impossible
  def isNull:     Boolean = this.start == Range.NULL.start && this.end == Range.NULL.end
  def isNotNull:  Boolean = !isNull
  def isEmpty:    Boolean = start >= end
  def isNotEmpty: Boolean = start < end

  def contains(other:    Range): Boolean = end >= other.end && start <= other.start
  def doesContain(other: Range): Boolean = end >= other.end && start <= other.start

  def contains(index:    Int): Boolean = start <= index && index < end
  def doesContain(index: Int): Boolean = start <= index && index < end

  def contains(start:    Int, end: Int): Boolean = this.start <= start && end <= this.end
  def doesContain(start: Int, end: Int): Boolean = this.start <= start && end <= this.end

  def overlaps(other:       Range): Boolean = !(other.end <= start || other.start >= end)
  def doesOverlap(other:    Range): Boolean = !(other.end <= start || other.start >= end)
  def doesNotOverlap(other: Range): Boolean = other.end <= start || other.start >= end

  def overlapsOrAdjacent(other:        Range): Boolean = !(other.end < start || other.start > end)
  def doesOverlapOrAdjacent(other:     Range): Boolean = !(other.end < start || other.start > end)
  def doesNotOverlapOrAdjacent(other:  Range): Boolean = other.end < start || other.start > end
  def doesNotOverlapNorAdjacent(other: Range): Boolean = other.end < start || other.start > end

  def properlyContains(other:    Range): Boolean = end > other.end && start < other.start
  def doesProperlyContain(other: Range): Boolean = end > other.end && start < other.start

  def isAdjacent(index:       Int):   Boolean = index == start - 1 || index == end
  def isAdjacentAfter(index:  Int):   Boolean = start - 1 == index
  def isAdjacentBefore(index: Int):   Boolean = end == index
  def isAdjacent(other:       Range): Boolean = start == other.end || end == other.start
  def isAdjacentBefore(other: Range): Boolean = end == other.start
  def isAdjacentAfter(other:  Range): Boolean = start == other.end

  def isContainedBy(other:         Range):         Boolean = other.end >= end && other.start <= start
  def isContainedBy(start:         Int, end: Int): Boolean = end >= this.end && start <= this.start
  def isProperlyContainedBy(other: Range):         Boolean = other.end > end && other.start < start
  def isProperlyContainedBy(start: Int, end: Int): Boolean = end > this.end && start < this.start

  def isEqual(other: Range): Boolean = end == other.end && start == other.start

  def isValidIndex(index: Int): Boolean = index >= start && index <= end
  def isStart(index:      Int): Boolean = index == start
  def isEnd(index:        Int): Boolean = index == end
  def isLast(index:       Int): Boolean = index >= start && index == end - 1

  def leadBy(index:    Int): Boolean = index <= start
  def leads(index:     Int): Boolean = end <= index
  def trailedBy(index: Int): Boolean = end <= index
  def trails(index:    Int): Boolean = index <= start

  def intersect(other: Range): Range = {
    var thisStart = Math.max(start, other.start)
    val thisEnd   = Math.min(end, other.end)
    if (thisStart >= thisEnd) thisStart = thisEnd
    withRange(thisStart, thisEnd)
  }

  def exclude(other: Range): Range = {
    var thisStart = start
    if (thisStart >= other.start && thisStart < other.end) thisStart = other.end

    var thisEnd = end
    if (thisEnd <= other.end && thisEnd > other.start) thisEnd = other.start

    if (thisStart >= thisEnd) {
      thisStart = 0
      thisEnd = 0
    }
    withRange(thisStart, thisEnd)
  }

  def compare(other: Range): Int =
    if (start < other.start) -1
    else if (start > other.start) 1
    else if (end > other.end) -1
    else if (end < other.end) 1
    else 0

  def include(other: Range): Range =
    if (other.isNull) if (this.isNull) Range.NULL else this
    else expandToInclude(other)

  def include(pos: Int): Range = include(pos, pos)

  def include(start: Int, end: Int): Range =
    if (this.isNull) Range.of(start, end)
    else expandToInclude(start, end)

  def expandToInclude(other: Range): Range = expandToInclude(other.start, other.end)

  def expandToInclude(start: Int, end: Int): Range =
    withRange(Math.min(this.start, start), Math.max(this.end, end))

  def charSubSequence(charSequence: CharSequence): CharSequence =
    charSequence.subSequence(start, end)

  def safeSubSequence(charSequence: CharSequence): CharSequence = {
    val safeEnd = Math.min(charSequence.length(), this.end)
    if (isNull) charSequence.subSequence(0, 0)
    else charSequence.subSequence(Math.min(safeEnd, Math.max(0, start)), safeEnd)
  }

  override def toString: String = s"[$start, $end)"

  override def equals(o: Any): Boolean =
    if (this.asInstanceOf[AnyRef] eq o.asInstanceOf[AnyRef]) true
    else
      o match {
        case range: Range => start == range.start && end == range.end
        case _ => false
      }

  override def hashCode(): Int = {
    var result = start
    result = 31 * result + end
    result
  }
}

object Range {
  val NULL:  Range = new Range(Integer.MAX_VALUE, Integer.MIN_VALUE)
  val EMPTY: Range = new Range(0, 0)

  def of(start: Int, end: Int): Range =
    if (start == NULL.start && end == NULL.end) NULL else new Range(start, end)

  def emptyOf(position: Int): Range = new Range(position, position)

  def ofLength(start: Int, length: Int): Range = new Range(start, start + length)
}
