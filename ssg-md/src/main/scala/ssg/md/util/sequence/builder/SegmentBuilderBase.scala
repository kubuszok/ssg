/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/SegmentBuilderBase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package util
package sequence
package builder

import ssg.md.Nullable
import ssg.md.util.misc.DelimitedBuilder
import ssg.md.util.sequence.{ Range, SequenceUtils }
import ssg.md.util.sequence.builder.ISegmentBuilder.{ F_INCLUDE_ANCHORS, F_TRACK_FIRST256 }

import java.util.Arrays

abstract class SegmentBuilderBase[S <: SegmentBuilderBase[S]] protected (
  _options: Int
) extends ISegmentBuilder[S] {

  val options: Int = _options & (F_INCLUDE_ANCHORS | F_TRACK_FIRST256)

  protected def this() = {
    this(F_INCLUDE_ANCHORS /*| F_TRACK_FIRST256*/ )
  }

  protected var parts:       Array[Int] = SegmentBuilderBase.EMPTY_PARTS
  protected var partsSize:   Int        = 0
  protected var anchorsSize: Int        = 0

  protected var _startOffset: Int          = Range.NULL.start
  protected var _endOffset:   Int          = Range.NULL.end
  protected var _length:      Int          = 0
  protected val stats:        SegmentStats = new SegmentStats((options & F_TRACK_FIRST256) != 0) // committed and dangling text stats
  protected val textStats:    SegmentStats = new SegmentStats((options & F_TRACK_FIRST256) != 0) // dangling text stats

  // NOTE: all text accumulation is in the string builder, dangling text segment is between immutableOffset and text.length()
  protected val text:            StringBuilder = new StringBuilder() // text segment ranges come from this CharSequence
  protected var immutableOffset: Int           = 0 // text offset for all committed text segments

  private def ensureCapacity(size: Int): Unit =
    parts = SegmentBuilderBase.ensureCapacity(parts, size + 1)

  def trimToSize(): Unit =
    if (parts.length > partsSize) {
      parts = Arrays.copyOf(parts, partsSize * 2)
    }

  override def startOffset: Int =
    if (_startOffset <= _endOffset) _startOffset else -1

  def needStartOffset: Boolean = startOffsetIfNeeded != -1

  def startOffsetIfNeeded: Int = {
    val so  = startOffset
    val seg = getSegOrNull(0)
    if (so != -1 && seg.isDefined && seg.get.isBase && so != seg.get.start) so else -1
  }

  override def endOffset: Int =
    if (_endOffset >= _startOffset) _endOffset else -1

  def needEndOffset: Boolean = endOffsetIfNeeded != -1

  def endOffsetIfNeeded: Int = {
    val eo  = endOffset
    val seg = getSegOrNull(partsSize - 1)
    if (eo != -1 && seg.isDefined && seg.get.isBase && eo != seg.get.end) eo else -1
  }

  override def isEmpty: Boolean = _length == 0

  override def isBaseSubSequenceRange: Boolean = getBaseSubSequenceRange.isDefined

  override def getBaseSubSequenceRange: Nullable[Range] =
    if (partsSize == 1 && !haveDanglingText()) {
      var seg = getSeg(partsSize - 1)
      if (seg.length != 0 && anchorsSize == 1) seg = getSeg(partsSize - 2)
      if (seg.isBase && seg.start == _startOffset && seg.end == _endOffset) {
        Nullable(seg.getRange)
      } else {
        Nullable.empty
      }
    } else {
      Nullable.empty
    }

  override def haveOffsets: Boolean = _startOffset <= _endOffset

  override def size: Int = partsSize + (if (haveDanglingText()) 1 else 0)

  override def getText: CharSequence = text

  override def noAnchorsSize: Int = size - anchorsSize

  private def computeLength(): Int = {
    var len = 0
    var i   = 0
    while (i < partsSize) {
      val part = getSeg(i)
      len += part.length
      i += 1
    }

    if (haveDanglingText()) {
      len += text.length() - immutableOffset
    }
    len
  }

  override def length: Int = {
    // RELEASE: remove assert for release
    assert(_length == computeLength(), s"length:${_length} != computeLength(): ${computeLength()}")
    _length
  }

  def getStats: SegmentStats = stats

  // @formatter:off
  override def isTrackTextFirst256: Boolean = stats.isTrackTextFirst256
  override def textLength: Int = stats.getTextLength
  override def textSegments: Int = stats.getTextSegments
  override def textSpaceLength: Int = stats.getTextSpaceLength
  override def textSpaceSegments: Int = stats.getTextSpaceSegments
  override def textFirst256Length: Int = stats.getTextFirst256Length
  override def textFirst256Segments: Int = stats.getTextFirst256Segments
  // @formatter:on

  override def iterator(): java.util.Iterator[Object] =
    new SegmentBuilderBase.PartsIterator(this)

  override def getSegments: java.lang.Iterable[Seg] =
    new SegmentBuilderBase.SegIterable(this)

  override def isIncludeAnchors: Boolean = (options & F_INCLUDE_ANCHORS) != 0

  /** Span for offsets of this list
    *
    * @return
    *   -ve if no information in the list, or span of offsets
    */
  override def span: Int =
    if (_startOffset > _endOffset) -1 else _endOffset - _startOffset

  private def getSegOrNull(index: Int): Nullable[Seg] = {
    val i = index * 2
    if (i + 1 >= parts.length) Nullable.empty else Nullable(Seg.segOf(parts(i), parts(i + 1)))
  }

  private def getSeg(index: Int): Seg = {
    val i = index * 2
    if (i + 1 >= parts.length) Seg.NULL else Seg.segOf(parts(i), parts(i + 1))
  }

  def getPart(index: Int): Object =
    if (index == partsSize && haveDanglingText()) {
      // return dangling text
      text.subSequence(immutableOffset, text.length()).asInstanceOf[Object]
    } else {
      val i   = index * 2
      val seg = if (i + 1 >= parts.length) Seg.NULL else Seg.segOf(parts(i), parts(i + 1))
      if (seg.isBase) seg.getRange.asInstanceOf[Object]
      else if (seg.isText) text.subSequence(seg.textStart, seg.textEnd).asInstanceOf[Object]
      else Range.NULL.asInstanceOf[Object]
    }

  private[builder] def getSegPart(index: Int): Seg =
    if (index == partsSize && haveDanglingText()) {
      // return dangling text
      Seg.textOf(immutableOffset, text.length(), textStats.isTextFirst256, textStats.isRepeatedText)
    } else {
      val i = index * 2
      if (i + 1 >= parts.length) Seg.NULL else Seg.segOf(parts(i), parts(i + 1))
    }

  private def setSegEnd(index: Int, endOff: Int): Unit = {
    val i = index * 2
    assert(i + 1 < parts.length)

    // adjust anchor count
    if (parts(i) == endOff) {
      if (parts(i) != parts(i + 1)) anchorsSize += 1
    } else if (parts(i) == parts(i + 1)) {
      anchorsSize -= 1
    }

    parts(i + 1) = endOff
  }

  private def addSeg(startOff: Int, endOff: Int): Unit = {
    ensureCapacity(partsSize)
    val i = partsSize * 2
    parts(i) = startOff
    parts(i + 1) = endOff
    partsSize += 1
    if (startOff == endOff) anchorsSize += 1
  }

  private def lastSegOrNull(): Nullable[Seg] =
    if (partsSize == 0) Nullable.empty else getSegOrNull(partsSize - 1)

  protected def haveDanglingText(): Boolean = text.length() > immutableOffset

  protected def optimizeText(parts: Array[Object]): Array[Object] = parts

  protected def handleOverlap(parts: Array[Object]): Array[Object] = {
    // range overlaps with last segment in the list
    val lastSeg = parts(0).asInstanceOf[Range]
    val text    = parts(1).asInstanceOf[CharSequence]
    val range   = parts(2).asInstanceOf[Range]

    assert(!lastSeg.isNull && lastSeg.end > range.start)

    if (lastSeg.end < range.end) {
      // there is a part of the overlap outside the last seg range
      if (text.length() > 0) {
        // append the chopped off base part
        parts(2) = Range.of(lastSeg.end, range.end)
      } else {
        // extend the last base seg to include this range
        parts(0) = lastSeg.withEnd(range.end)
        parts(2) = Range.NULL
      }
    } else {
      parts(2) = Range.NULL
    }
    parts
  }

  private def processParts(segStart: Int, segEnd: Int, resolveOverlap: Boolean, nullNextRange: Boolean, transform: Array[Object] => Array[Object]): Unit = {
    assert(segStart >= 0 && segEnd >= 0 && segStart <= segEnd)

    val danglingText: CharSequence = this.text.subSequence(immutableOffset, this.text.length())
    assert(resolveOverlap || danglingText.length() > 0)

    val lastSeg = lastSegOrNull()
    var prevRange: Range = if (lastSeg.isEmpty || !lastSeg.get.isBase) Range.NULL else lastSeg.get.getRange

    if (!isIncludeAnchors && haveOffsets) {
      // need to use max with endOffset since anchors are not stored
      if (prevRange.isNull || prevRange.end < _endOffset) prevRange = Range.emptyOf(_endOffset)
    }

    if (!haveOffsets) _startOffset = segStart

    // NOTE: cannot incorporate segEnd if overlap is being resolved
    if (!resolveOverlap) _endOffset = Math.max(_endOffset, segEnd)

    val partsArr: Array[Object] = Array(
      prevRange.asInstanceOf[Object],
      danglingText.asInstanceOf[Object],
      (if (nullNextRange) Range.NULL else Range.of(segStart, segEnd)).asInstanceOf[Object]
    )

    val originalParts = partsArr.clone()
    val optimizedText = transform(partsArr)
    assert(optimizedText.length > 0)

    if (Arrays.equals(optimizedText.asInstanceOf[Array[AnyRef]], originalParts.asInstanceOf[Array[AnyRef]])) {
      // nothing changed, make sure it was not called to resolve overlap
      assert(!resolveOverlap)

      if (segEnd > segStart || isIncludeAnchors) {
        // NOTE: only commit text if adding real range after it
        if (danglingText.length() > 0) {
          commitText()
        }

        _length += segEnd - segStart
        addSeg(segStart, segEnd)
      }
    } else {
      // remove dangling text information
      textStats.commitText()
      stats.commitText()
      stats.remove(textStats)
      textStats.clear()
      _length -= danglingText.length()
      this.text.delete(immutableOffset, this.text.length())

      if (lastSeg.isDefined && lastSeg.get.isBase) {
        // remove last seg from parts, it will be added on return
        _length -= lastSeg.get.length
        partsSize -= 1
        if (lastSeg.get.length == 0) anchorsSize -= 1
      }

      val iMax           = optimizedText.length
      var optStartOffset = Int.MaxValue
      var optEndOffset   = Int.MinValue

      var i = 0
      while (i < iMax) {
        val oPart = optimizedText(i)
        oPart match {
          case optText: CharSequence =>
            if (optText.length() > 0) {
              addTextInternal(optText)
            }
          case range: Range =>
            if (range.isNotNull) {
              val optRangeStart = range.start
              val optRangeEnd   = range.end
              assert(optRangeStart >= 0 && optRangeEnd >= 0 && optRangeStart <= optRangeEnd)

              if (optStartOffset == Int.MaxValue) optStartOffset = optRangeStart

              if (optRangeStart < optEndOffset) {
                throw new IllegalStateException(
                  String.format(
                    "Accumulated range [%d, %d) overlaps Transformed Range[%d]: [%d, %d)",
                    Integer.valueOf(optStartOffset),
                    Integer.valueOf(optEndOffset),
                    Integer.valueOf(i),
                    Integer.valueOf(optRangeStart),
                    Integer.valueOf(optRangeEnd)
                  )
                )
              }

              optEndOffset = Math.max(optEndOffset, optRangeEnd)

              val hasDangling = haveDanglingText()

              if (hasDangling && resolveOverlap) {
                processParts(optRangeStart, optRangeEnd, false, false, this.optimizeText)
              } else {
                // adjust offsets since they could have expanded
                _startOffset = Math.min(_startOffset, optRangeStart)
                _endOffset = Math.max(_endOffset, optRangeEnd)

                if (optRangeStart != optRangeEnd || isIncludeAnchors) {
                  if (hasDangling) {
                    commitText()
                  }

                  // add base segment
                  _length += optRangeEnd - optRangeStart
                  addSeg(optRangeStart, optRangeEnd)
                }
              }
            }
          case null  => // null entry ignored
          case other =>
            throw new IllegalStateException("Invalid optimized part type " + other.getClass)
        }
        i += 1
      }
    }
  }

  private def commitText(): Unit = {
    addSeg(Seg.getTextStart(immutableOffset, textStats.isTextFirst256), Seg.getTextEnd(text.length(), textStats.isRepeatedText))
    immutableOffset = text.length()
    stats.commitText()
    textStats.clear()
  }

  private def addTextInternal(optText: CharSequence): Unit = {
    _length += optText.length()
    text.append(optText)

    stats.addText(optText)
    textStats.addText(optText)
  }

  /** append anchor in original sequence coordinates, no checking is done other than overlap with tail range fast
    *
    * @param offset
    *   offset in original sequence
    * @return
    *   this
    */
  def appendAnchor(offset: Int): S = append(offset, offset)

  /** append range in original sequence coordinates, no checking is done other than overlap with tail range fast
    *
    * @param range
    *   range in original sequence
    * @return
    *   this
    */
  def append(range: Range): S = append(range.start, range.end)

  /** append range in original sequence coordinates, no checking is done other than overlap with tail range fast
    *
    * @param startOff
    *   start offset in original sequence
    * @param endOff
    *   end offset in original sequence
    * @return
    *   this
    */
  def append(startOff: Int, endOff: Int): S =
    if (endOff < 0 || startOff > endOff) {
      this.asInstanceOf[S]
    } else {
      val rangeSpan = endOff - startOff
      if (rangeSpan == 0 && (!isIncludeAnchors || startOff < this._endOffset)) {
        if (startOff >= this._endOffset) {
          // can optimize text
          if (haveDanglingText()) {
            processParts(startOff, endOff, false, false, this.optimizeText)
          } else {
            if (!haveOffsets) this._startOffset = startOff
            this._endOffset = startOff
          }
        }
        this.asInstanceOf[S]
      } else if (this._endOffset > startOff) {
        // overlap
        processParts(startOff, endOff, true, false, this.handleOverlap)
        this.asInstanceOf[S]
      } else if (this._endOffset == startOff) {
        // adjacent, merge the two if no text between them
        if (haveDanglingText()) {
          processParts(startOff, endOff, false, false, this.optimizeText)
        } else {
          this._endOffset = endOff
          _length += rangeSpan

          if (partsSize == 0) {
            // no last segment, add this one
            addSeg(startOff, endOff)
          } else {
            // combine this with the last segment
            setSegEnd(partsSize - 1, endOff)
          }
        }
        this.asInstanceOf[S]
      } else {
        // disjoint
        if (haveDanglingText()) {
          processParts(startOff, endOff, false, false, this.optimizeText)
        } else {
          if (!haveOffsets) this._startOffset = startOff
          this._endOffset = endOff
          _length += rangeSpan
          addSeg(startOff, endOff)
        }
        this.asInstanceOf[S]
      }
    }

  def append(text: CharSequence): S = {
    val len = text.length()
    if (len != 0) {
      stats.addText(text)
      textStats.addText(text)

      this.text.append(text)
      this._length += len
    }
    this.asInstanceOf[S]
  }

  def append(c: Char): S = {
    stats.addText(c)
    textStats.addText(c)

    text.append(c)
    _length += 1

    this.asInstanceOf[S]
  }

  def append(c: Char, repeat: Int): S = {
    if (repeat > 0) {
      stats.addText(c, repeat)
      textStats.addText(c, repeat)
      _length += repeat

      var r = repeat
      while (r > 0) {
        text.append(c)
        r -= 1
      }
    }
    this.asInstanceOf[S]
  }

  def toString(chars: CharSequence, rangePrefix: CharSequence, rangeSuffix: CharSequence, textMapper: CharSequence => CharSequence): String = {
    if (_endOffset > chars.length()) {
      throw new IllegalArgumentException("baseSequence length() must be at least " + _endOffset + ", got: " + chars.length())
    }

    if (haveDanglingText() && haveOffsets) {
      processParts(_endOffset, _endOffset, false, true, this.optimizeText)
    }

    val out = new StringBuilder()

    var i = 0
    while (i < partsSize) {
      val part = getSeg(i)

      if (!part.isBase) {
        out.append(textMapper(text.subSequence(part.textStart, part.textEnd)))
      } else {
        out.append(rangePrefix).append(textMapper(chars.subSequence(part.start, part.end))).append(rangeSuffix)
      }
      i += 1
    }

    if (haveDanglingText()) {
      out.append(textMapper(text.subSequence(immutableOffset, text.length())))
    }

    out.toString
  }

  def toStringWithRangesVisibleWhitespace(chars: CharSequence): String =
    toString(chars, "\u27e6", "\u27e7", SequenceUtils.toVisibleWhitespaceString)

  def toStringWithRanges(chars: CharSequence): String =
    toString(chars, "\u27e6", "\u27e7", identity)

  def toString(chars: CharSequence): String =
    toString(chars, "", "", identity)

  def toStringPrep: String = {
    if (haveDanglingText() && haveOffsets) {
      processParts(_endOffset, _endOffset, false, true, this.optimizeText)
    }
    toString
  }

  override def toString: String = {
    val sb = new DelimitedBuilder(", ")
    sb.append(this.getClass.getSimpleName).append("{")

    if (haveOffsets) {
      sb.append("[").append(_startOffset).mark().append(_endOffset).unmark().append(")").mark()
    } else {
      sb.append("NULL").mark()
    }

    val committedStats = stats.committedCopy()
    sb.append(committedStats.asInstanceOf[AnyRef]).mark().append("l=").append(_length).mark().append("sz=").append(size).mark().append("na=").append(noAnchorsSize)

    if (size > 0) sb.append(": ")

    var i = 0
    while (i < partsSize) {
      val part = getSeg(i)
      sb.append(Nullable(part.toString(text)))
      sb.mark()
      i += 1
    }

    if (haveDanglingText()) {
      val part = Seg.textOf(immutableOffset, text.length(), textStats.isTextFirst256, textStats.isRepeatedText)
      sb.append(Nullable(part.toString(text)))
      sb.mark()
    }

    sb.unmark().append(" }")
    sb.toString
  }
}

object SegmentBuilderBase {

  val MIN_PART_CAPACITY: Int = 8

  val EMPTY_PARTS: Array[Int] = Array.empty[Int]

  private def ensureCapacity(prev: Array[Int], size: Int): Array[Int] = {
    assert(size >= 0)

    val prevSize = prev.length / 2
    if (prevSize <= size) {
      val nextSize = Math.max(MIN_PART_CAPACITY, Math.max(prevSize + (prevSize >> 1), size))
      Arrays.copyOf(prev, nextSize * 2)
    } else {
      prev
    }
  }

  private class PartsIterator(builder: SegmentBuilderBase[?]) extends java.util.Iterator[Object] {
    private var nextIndex: Int = 0

    override def hasNext: Boolean = nextIndex < builder.size

    override def next(): Object = {
      val result = builder.getPart(nextIndex)
      nextIndex += 1
      result
    }
  }

  private class SegIterable(builder: SegmentBuilderBase[?]) extends java.lang.Iterable[Seg] {
    override def iterator(): java.util.Iterator[Seg] = new SegIterator(builder)
  }

  private class SegIterator(builder: SegmentBuilderBase[?]) extends java.util.Iterator[Seg] {
    private var nextIndex: Int = 0

    override def hasNext: Boolean = nextIndex < builder.size

    override def next(): Seg = {
      val result = builder.getSegPart(nextIndex)
      nextIndex += 1
      result
    }
  }
}
