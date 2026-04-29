/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/tree/Segment.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-sequence/src/main/java/com/vladsch/flexmark/util/sequence/builder/tree/Segment.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package sequence
package builder
package tree

import ssg.md.Nullable
import ssg.md.util.misc.Utils.escapeJavaString
import ssg.md.util.sequence.BasedSequence

/** SegmentedSequence Segment stored in byte[] in serialized format
  */
abstract class Segment(
  val pos:        Int, // position of segment in aggr length table
  val bytes:      Array[Byte],
  val byteOffset: Int,
  val startIndex: Int
) {

  // @formatter:off
  import Segment.*
  // @formatter:on

  def hasAll(flags: Int, mask: Int): Boolean =
    (flags & mask) == mask

  final def endIndex: Int = startIndex + length

  def notInSegment(index: Int): Boolean =
    index < startIndex || index >= startIndex + length

  def offsetNotInSegment(offset: Int): Boolean =
    offset < getStartOffset || offset >= getEndOffset

  final def getType: SegType =
    SegType.fromTypeMask(bytes(byteOffset))

  final def getByteLength: Int =
    Segment.getSegByteLength(getType, getStartOffset, length)

  def length:            Int
  def isBase:            Boolean
  def isAnchor:          Boolean
  def isText:            Boolean
  def isFirst256Start:   Boolean
  def isRepeatedTextEnd: Boolean
  def getStartOffset:    Int
  def getEndOffset:      Int
  def getCharSequence:   CharSequence

  /** get char at index
    *
    * @param index
    *   index in segmented sequence coordinates. index offset must be subtracted to convert to segment coordinates
    * @return
    *   character at given index in segmented sequence
    */
  def charAt(index: Int): Char

  override def toString: String =
    if (isBase) {
      if (isAnchor) {
        "[" + getStartOffset + ")"
      } else {
        "[" + getStartOffset + ", " + getEndOffset + ")"
      }
    } else {
      val charSequence = getCharSequence
      if (isRepeatedTextEnd && length > 1) {
        if (isFirst256Start) {
          "a:" + (length + "x'" + escapeJavaString(Nullable(charSequence.subSequence(0, 1))) + "'")
        } else {
          "" + (length + "x'" + escapeJavaString(Nullable(charSequence.subSequence(0, 1))) + "'")
        }
      } else {
        val len   = charSequence.length()
        val chars =
          if (len <= 20) charSequence.toString
          else charSequence.subSequence(0, 10).toString + "\u2026" + charSequence.subSequence(len - 10, len).toString
        if (isFirst256Start) {
          "a:'" + escapeJavaString(Nullable(chars: CharSequence)) + "'"
        } else {
          "'" + escapeJavaString(Nullable(chars: CharSequence)) + "'"
        }
      }
    }
}

object Segment {

  // @formatter:off
  final val TYPE_MASK              = 0x000000e0 // 0b0000_0000_1110_0000
  final val TYPE_NO_SIZE_BYTES     = 0x00000010 // 0b0000_0000_0001_0000
  final val TYPE_START_BYTES       = 0x00000003 // 0b0000_0000_0000_0011
  final val TYPE_LENGTH_BYTES      = 0x0000000c // 0b0000_0000_0000_1100

  final val TYPE_ANCHOR            = 0x00000000 // 0b0000_0000_0000_0000
  final val TYPE_BASE              = 0x00000020 // 0b0000_0000_0010_0000
  final val TYPE_TEXT              = 0x00000040 // 0b0000_0000_0100_0000
  final val TYPE_REPEATED_TEXT     = 0x00000060 // 0b0000_0000_0110_0000
  final val TYPE_TEXT_ASCII        = 0x00000080 // 0b0000_0000_1000_0000
  final val TYPE_REPEATED_ASCII    = 0x000000a0 // 0b0000_0000_1010_0000
  final val TYPE_REPEATED_SPACE    = 0x000000c0 // 0b0000_0000_1100_0000
  final val TYPE_REPEATED_EOL      = 0x000000e0 // 0b0000_0000_1110_0000

  final val TYPE_HAS_OFFSET        = 0x00000100 // 0b0000_0001_0000_0000
  final val TYPE_HAS_LENGTH        = 0x00000200 // 0b0000_0010_0000_0000
  final val TYPE_HAS_BOTH          = 0x00000300 // 0b0000_0011_0000_0000
  final val TYPE_HAS_CHAR          = 0x00000400 // 0b0000_0100_0000_0000
  final val TYPE_HAS_CHARS         = 0x00000800 // 0b0000_1000_0000_0000
  final val TYPE_HAS_BYTE          = 0x00001000 // 0b0001_0000_0000_0000
  final val TYPE_HAS_BYTES         = 0x00002000 // 0b0010_0000_0000_0000
  // @formatter:on

  enum SegType(val flags: Int) extends java.lang.Enum[SegType] {
    case ANCHOR extends SegType(TYPE_ANCHOR | TYPE_HAS_OFFSET)
    case BASE extends SegType(TYPE_BASE | TYPE_HAS_BOTH)
    case TEXT extends SegType(TYPE_TEXT | TYPE_HAS_LENGTH | TYPE_HAS_CHARS)
    case REPEATED_TEXT extends SegType(TYPE_REPEATED_TEXT | TYPE_HAS_LENGTH | TYPE_HAS_CHAR)
    case TEXT_ASCII extends SegType(TYPE_TEXT_ASCII | TYPE_HAS_LENGTH | TYPE_HAS_BYTES)
    case REPEATED_ASCII extends SegType(TYPE_REPEATED_ASCII | TYPE_HAS_LENGTH | TYPE_HAS_BYTE)
    case REPEATED_SPACE extends SegType(TYPE_REPEATED_SPACE | TYPE_HAS_LENGTH)
    case REPEATED_EOL extends SegType(TYPE_REPEATED_EOL | TYPE_HAS_LENGTH)

    def hasAll(flags: Int): Boolean = (this.flags & flags) == flags
    def hasLength:          Boolean = hasAll(TYPE_HAS_LENGTH)
    def hasOffset:          Boolean = hasAll(TYPE_HAS_OFFSET)
    def hasBoth:            Boolean = hasAll(TYPE_HAS_BOTH)
    def hasChar:            Boolean = hasAll(TYPE_HAS_CHAR)
    def hasChars:           Boolean = hasAll(TYPE_HAS_CHARS)
    def hasByte:            Boolean = hasAll(TYPE_HAS_BYTE)
    def hasBytes:           Boolean = hasAll(TYPE_HAS_BYTES)
  }

  object SegType {
    def fromTypeMask(segTypeMask: Int): SegType =
      (segTypeMask & TYPE_MASK) match {
        // @formatter:off
        case TYPE_ANCHOR         => SegType.ANCHOR
        case TYPE_BASE           => SegType.BASE
        case TYPE_TEXT           => SegType.TEXT
        case TYPE_TEXT_ASCII     => SegType.TEXT_ASCII
        case TYPE_REPEATED_TEXT  => SegType.REPEATED_TEXT
        case TYPE_REPEATED_ASCII => SegType.REPEATED_ASCII
        case TYPE_REPEATED_SPACE => SegType.REPEATED_SPACE
        case TYPE_REPEATED_EOL   => SegType.REPEATED_EOL
        // @formatter:on
        case _ =>
          throw new IllegalStateException(String.format("Invalid text type %02x", segTypeMask: Integer))
      }
  }

  // ---- Inner segment types ----

  private class Base(
    pos:         Int,
    bytes:       Array[Byte],
    byteOffset:  Int,
    indexOffset: Int,
    baseSeq:     BasedSequence
  ) extends Segment(pos, bytes, byteOffset, indexOffset) {

    val (startOffset: Int, endOffset: Int) = {
      var off      = byteOffset
      val typeByte = bytes(off) & 0x00ff
      off += 1

      if ((typeByte & TYPE_MASK) == TYPE_ANCHOR) {
        if (hasAll(typeByte, TYPE_NO_SIZE_BYTES)) {
          val v = typeByte & 0x000f
          (v, v)
        } else {
          val intBytes = typeByte & TYPE_START_BYTES
          val v        = getInt(bytes, off, intBytes + 1)
          (v, v)
        }
      } else {
        assert(!hasAll(typeByte, TYPE_NO_SIZE_BYTES))

        val intBytes = typeByte & TYPE_START_BYTES
        val s        = getInt(bytes, off, intBytes + 1)
        off += intBytes + 1

        val lengthBytes = (typeByte & TYPE_LENGTH_BYTES) >> 2
        val e           = s + getInt(bytes, off, lengthBytes + 1)
        (s, e)
      }
    }

    override def length:            Int     = endOffset - startOffset
    override def isBase:            Boolean = true
    override def isAnchor:          Boolean = startOffset == endOffset
    override def isText:            Boolean = false
    override def isFirst256Start:   Boolean = false
    override def isRepeatedTextEnd: Boolean = false
    override def getStartOffset:    Int     = startOffset
    override def getEndOffset:      Int     = endOffset

    override def charAt(index: Int): Char = {
      if (index < startIndex || index - startIndex >= length) {
        throw new IndexOutOfBoundsException("index " + index + " out of bounds [" + startIndex + ", " + startIndex + length + ")")
      }
      baseSeq.charAt(startOffset + index - startIndex)
    }

    override def getCharSequence: CharSequence =
      baseSeq.subSequence(startOffset, endOffset)
  }

  // ---- CharSequence implementations for text segments ----

  abstract private class TextCharSequenceBase(
    protected val bytes:          Array[Byte],
    protected val seqByteOffset:  Int, // byte offset of first byte of chars for original base sequence
    protected val seqStartOffset: Int,
    protected val seqLength:      Int
  ) extends CharSequence {

    override def length(): Int = seqLength

    override def charAt(index: Int): Char

    def create(startOff: Int, len: Int): CharSequence

    override def subSequence(startIdx: Int, endIdx: Int): CharSequence = {
      if (startIdx < 0 || startIdx > endIdx || endIdx > seqLength) {
        throw new IndexOutOfBoundsException("Invalid index range [" + startIdx + ", " + endIdx + "] out of bounds [0, " + length() + ")")
      }
      create(seqStartOffset + startIdx, endIdx - startIdx)
    }

    override def toString: String = {
      val sb = new StringBuilder()
      var i  = 0
      while (i < seqLength) {
        sb.append(charAt(i))
        i += 1
      }
      sb.toString()
    }
  }

  private class TextCharSequence(
    bytes:          Array[Byte],
    seqByteOffset:  Int,
    seqStartOffset: Int,
    seqLength:      Int
  ) extends TextCharSequenceBase(bytes, seqByteOffset, seqStartOffset, seqLength) {

    override def charAt(index: Int): Char = {
      if (index < 0 || index >= seqLength) {
        throw new IndexOutOfBoundsException("index " + index + " out of bounds [0, " + seqLength + ")")
      }
      getChar(bytes, seqByteOffset + (seqStartOffset + index) * 2)
    }

    override def create(startOff: Int, len: Int): CharSequence =
      new TextCharSequence(bytes, seqByteOffset, startOff, len)
  }

  private class TextAsciiCharSequence(
    bytes:          Array[Byte],
    seqByteOffset:  Int,
    seqStartOffset: Int,
    seqLength:      Int
  ) extends TextCharSequenceBase(bytes, seqByteOffset, seqStartOffset, seqLength) {

    override def charAt(index: Int): Char = {
      if (index < 0 || index >= seqLength) {
        throw new IndexOutOfBoundsException("index " + index + " out of bounds [0, " + seqLength + ")")
      }
      (0x00ff & bytes(seqByteOffset + seqStartOffset + index)).toChar
    }

    override def create(startOff: Int, len: Int): CharSequence =
      new TextAsciiCharSequence(bytes, seqByteOffset, startOff, len)
  }

  private class TextRepeatedSequence(
    protected val c:         Char,
    protected val seqLength: Int
  ) extends CharSequence {

    override def length(): Int = seqLength

    override def charAt(index: Int): Char = {
      if (index < 0 || index >= seqLength) {
        throw new IndexOutOfBoundsException("index " + index + " out of bounds [0, " + seqLength + ")")
      }
      c
    }

    override def subSequence(startIdx: Int, endIdx: Int): CharSequence = {
      if (startIdx < 0 || startIdx > endIdx || endIdx > seqLength) {
        throw new IndexOutOfBoundsException("Invalid index range [" + startIdx + ", " + endIdx + "] out of bounds [0, " + length() + ")")
      }
      new TextRepeatedSequence(c, endIdx - startIdx)
    }

    override def toString: String = {
      val sb = new StringBuilder()
      var i  = 0
      while (i < seqLength) {
        sb.append(c)
        i += 1
      }
      sb.toString()
    }
  }

  private class Text(
    pos:         Int,
    bytes:       Array[Byte],
    byteOffset:  Int,
    indexOffset: Int
  ) extends Segment(pos, bytes, byteOffset, indexOffset) {

    val chars: CharSequence = {
      var off      = byteOffset
      val typeByte = bytes(off) & 0x00ff
      off += 1
      val segTypeMask = typeByte & TYPE_MASK

      val len: Int =
        if (hasAll(typeByte, TYPE_NO_SIZE_BYTES)) {
          typeByte & 0x000f
        } else {
          val lengthBytes = (typeByte & TYPE_LENGTH_BYTES) >> 2
          val v           = getInt(bytes, off, lengthBytes + 1)
          off += lengthBytes + 1
          v
        }

      segTypeMask match {
        case TYPE_TEXT =>
          new TextCharSequence(bytes, off, 0, len)
        case TYPE_TEXT_ASCII =>
          new TextAsciiCharSequence(bytes, off, 0, len)
        case TYPE_REPEATED_TEXT =>
          new TextRepeatedSequence(getChar(bytes, off), len)
        case TYPE_REPEATED_ASCII =>
          new TextRepeatedSequence((0x00ff & bytes(off)).toChar, len)
        case TYPE_REPEATED_SPACE =>
          new TextRepeatedSequence(' ', len)
        case TYPE_REPEATED_EOL =>
          new TextRepeatedSequence('\n', len)
        case _ =>
          throw new IllegalStateException("Invalid text type " + segTypeMask)
      }
    }

    override def length: Int = chars.length()

    override def charAt(index: Int): Char = {
      if (index < startIndex || index - startIndex >= chars.length()) {
        throw new IndexOutOfBoundsException("index " + index + " out of bounds [" + startIndex + ", " + startIndex + chars.length() + ")")
      }
      chars.charAt(index - startIndex)
    }

    override def isBase:   Boolean = false
    override def isAnchor: Boolean = false
    override def isText:   Boolean = true

    private def textType: Int = bytes(byteOffset) & TYPE_MASK

    override def isFirst256Start: Boolean = {
      val tt = textType
      tt == TYPE_TEXT_ASCII || tt == TYPE_REPEATED_ASCII || tt == TYPE_REPEATED_SPACE || tt == TYPE_REPEATED_EOL
    }

    override def isRepeatedTextEnd: Boolean = {
      val tt = textType
      tt == TYPE_REPEATED_TEXT || tt == TYPE_REPEATED_ASCII || tt == TYPE_REPEATED_SPACE || tt == TYPE_REPEATED_EOL
    }

    override def getStartOffset:  Int          = -1
    override def getEndOffset:    Int          = -1
    override def getCharSequence: CharSequence = chars
  }

  // ---- Static factory and utility methods ----

  def getSegment(bytes: Array[Byte], byteOffset: Int, pos: Int, indexOffset: Int, basedSequence: BasedSequence): Segment = {
    val typeByte = bytes(byteOffset) & TYPE_MASK

    typeByte match {
      case TYPE_ANCHOR | TYPE_BASE =>
        new Base(pos, bytes, byteOffset, indexOffset, basedSequence)
      case TYPE_TEXT | TYPE_REPEATED_TEXT | TYPE_TEXT_ASCII | TYPE_REPEATED_ASCII | TYPE_REPEATED_SPACE | TYPE_REPEATED_EOL =>
        new Text(pos, bytes, byteOffset, indexOffset)
      case _ =>
        throw new IllegalStateException("Invalid text type " + typeByte)
    }
  }

  def getSegType(seg: Seg, textChars: CharSequence): SegType =
    if (seg.isBase) {
      if (seg.isAnchor) SegType.ANCHOR else SegType.BASE
    } else if (seg.isText) {
      val first256Start   = seg.isFirst256Start
      val repeatedTextEnd = seg.isRepeatedTextEnd

      if (first256Start) {
        // ascii text
        if (repeatedTextEnd) {
          // repeated chars
          val c = textChars.charAt(seg.textStart)
          if (c == ' ') SegType.REPEATED_SPACE
          else if (c == '\n') SegType.REPEATED_EOL
          else SegType.REPEATED_ASCII
        } else {
          SegType.TEXT_ASCII
        }
      } else {
        if (repeatedTextEnd) SegType.REPEATED_TEXT else SegType.TEXT
      }
    } else {
      throw new IllegalStateException("Unknown seg type " + seg)
    }

  def getOffsetBytes(offset: Int): Int =
    if (offset < 16) 0
    else if (offset < 256) 1
    else if (offset < 65536) 2
    else if (offset < 65536 * 256) 3
    else 4

  def getLengthBytes(length: Int): Int =
    if (length < 16) 0
    else if (length < 256) 1
    else if (length < 65536) 2
    else if (length < 65536 * 256) 3
    else 4

  def getIntBytes(length: Int): Int =
    if (length < 256) 1
    else if (length < 65536) 2
    else if (length < 65536 * 256) 3
    else 4

  def getSegByteLength(segType: Segment.SegType, segStart: Int, segLength: Int): Int = {
    var len = 1

    if (segType.hasBoth) {
      len += getIntBytes(segStart) + getIntBytes(segLength)
    } else if (segType.hasOffset) {
      len += getOffsetBytes(segStart)
    } else if (segType.hasLength) {
      len += getLengthBytes(segLength)
    }

    if (segType.hasChar) len += 2
    else if (segType.hasChars) len += 2 * segLength
    else if (segType.hasByte) len += 1
    else if (segType.hasBytes) len += segLength

    len
  }

  def getSegByteLength(seg: Seg, textChars: CharSequence): Int = {
    val segType = getSegType(seg, textChars)
    getSegByteLength(segType, seg.segStart, seg.length)
  }

  /** Write int value to bytes with fall-through byte count. count=4 writes 4 bytes, count=3 writes 3 bytes, etc.
    */
  def addIntBytes(bytes: Array[Byte], offset: Int, value: Int, count: Int): Int = {
    var off = offset
    // Fall-through: for count=4 write all 4; count=3 write 3 MSB bytes; etc.
    if (count >= 4) {
      bytes(off) = ((value & 0xff000000) >> 24).toByte
      off += 1
    }
    if (count >= 3) {
      bytes(off) = ((value & 0x00ff0000) >> 16).toByte
      off += 1
    }
    if (count >= 2) {
      bytes(off) = ((value & 0x0000ff00) >> 8).toByte
      off += 1
    }
    if (count >= 1) {
      bytes(off) = (value & 0x000000ff).toByte
      off += 1
    }
    off
  }

  /** Read int value from bytes with fall-through byte count. count=4 reads 4 bytes, count=3 reads 3 bytes, etc.
    */
  def getInt(bytes: Array[Byte], offset: Int, count: Int): Int = {
    var value = 0
    var off   = offset
    // Fall-through: for count=4 read all 4; count=3 read 3 MSB bytes; etc.
    if (count >= 4) {
      value |= (0x00ff & bytes(off)) << 24
      off += 1
    }
    if (count >= 3) {
      value |= (0x00ff & bytes(off)) << 16
      off += 1
    }
    if (count >= 2) {
      value |= (0x00ff & bytes(off)) << 8
      off += 1
    }
    if (count >= 1) {
      value |= 0x00ff & bytes(off)
    }
    value
  }

  def addChar(bytes: Array[Byte], offset: Int, c: Char): Int = {
    var off = offset
    bytes(off) = ((c & 0x0000ff00) >> 8).toByte
    off += 1
    bytes(off) = (c & 0x000000ff).toByte
    off += 1
    off
  }

  def getChar(bytes: Array[Byte], offset: Int): Char = {
    var c: Char = ((0x00ff & bytes(offset)) << 8).toChar
    c = (c | (0x00ff & bytes(offset + 1))).toChar
    c
  }

  def addChars(bytes: Array[Byte], offset: Int, chars: CharSequence, start: Int, end: Int): Int = {
    var off = offset
    var i   = start
    while (i < end) {
      val c = chars.charAt(i)
      bytes(off) = ((c & 0x0000ff00) >> 8).toByte
      off += 1
      bytes(off) = (c & 0x000000ff).toByte
      off += 1
      i += 1
    }
    off
  }

  def addCharAscii(bytes: Array[Byte], offset: Int, c: Char): Int = {
    assert(c < 256)
    bytes(offset) = (c & 0x000000ff).toByte
    offset + 1
  }

  def addCharsAscii(bytes: Array[Byte], offset: Int, chars: CharSequence, start: Int, end: Int): Int = {
    var off = offset
    var i   = start
    while (i < end) {
      val c = chars.charAt(i)
      assert(c < 256)
      bytes(off) = (c & 0x000000ff).toByte
      off += 1
      i += 1
    }
    off
  }

  def getCharAscii(bytes: Array[Byte], offset: Int): Char =
    (0x00ff & bytes(offset)).toChar

  def addSegBytes(bytes: Array[Byte], offset: Int, seg: Seg, textChars: CharSequence): Int = {
    val segType   = getSegType(seg, textChars)
    val segLength = seg.length
    var off       = offset

    if (segType.hasOffset) {
      val segStart = seg.start

      if (segType.hasLength) {
        val offsetBytes = getIntBytes(segStart)
        val intBytes    = getIntBytes(segLength)

        bytes(off) = (segType.flags | (offsetBytes - 1) | ((intBytes - 1) << 2)).toByte
        off += 1
        off = addIntBytes(bytes, off, segStart, offsetBytes)
        off = addIntBytes(bytes, off, segLength, intBytes)
      } else {
        val offsetByteCount = getOffsetBytes(segStart)
        if (offsetByteCount == 0) {
          assert(segStart < 16)
          bytes(off) = (segType.flags | TYPE_NO_SIZE_BYTES | segStart).toByte
          off += 1
        } else {
          bytes(off) = (segType.flags | (offsetByteCount - 1)).toByte
          off += 1
          off = addIntBytes(bytes, off, segStart, offsetByteCount)
        }
      }
    } else if (segType.hasLength) {
      val lengthByteCount = getLengthBytes(segLength)
      if (lengthByteCount == 0) {
        assert(segLength < 16)
        bytes(off) = (segType.flags | TYPE_NO_SIZE_BYTES | segLength).toByte
        off += 1
      } else {
        bytes(off) = (segType.flags | ((lengthByteCount - 1) << 2)).toByte
        off += 1
        off = addIntBytes(bytes, off, segLength, lengthByteCount)
      }
    }

    if (segType.hasChar) off = addChar(bytes, off, textChars.charAt(seg.textStart))
    else if (segType.hasChars) off = addChars(bytes, off, textChars, seg.textStart, seg.textEnd)
    else if (segType.hasByte) off = addCharAscii(bytes, off, textChars.charAt(seg.textStart))
    else if (segType.hasBytes) off = addCharsAscii(bytes, off, textChars, seg.textStart, seg.textEnd)

    off
  }
}
