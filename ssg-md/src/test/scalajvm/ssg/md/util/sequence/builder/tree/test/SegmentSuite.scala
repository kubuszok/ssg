/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package sequence
package builder
package tree
package test

import java.util.Arrays

import scala.language.implicitConversions

final class SegmentSuite extends munit.FunSuite {

  private def loop(start: Int, end: Int, span: Int, param: Int, consumer: (Int, Int) => Unit): Unit = {
    val iMaxStart = start + span
    val iMinEnd   = end - span

    if (iMaxStart >= iMinEnd) {
      var i = start
      while (i < end) {
        consumer(param, i)
        i += 1
      }
    } else {
      var i = start
      while (i < iMaxStart) {
        consumer(param, i)
        i += 1
      }

      i = iMinEnd
      while (i < end) {
        consumer(param, i)
        i += 1
      }
    }
  }

  private def loopSizes(consumer: (Int, Int) => Unit): Unit = {
    loop(0, 16, 8, 0, consumer)
    loop(16, 256, 8, 1, consumer)
    loop(256, 65536, 8, 2, consumer)
    loop(65536, 65536 * 256, 8, 3, consumer)
    loop(65536 * 256, Int.MaxValue, 8, 4, consumer)
  }

  private def loopSizesShort(consumer: (Int, Int) => Unit): Unit = {
    loop(0, 16, 8, 0, consumer)
    loop(16, 256, 8, 1, consumer)
    loop(256, 65536, 8, 2, consumer)
    loop(65536, 65536 * 8, 8, 3, consumer)
  }

  private def loopEnd(startOffset: Int, consumer: (Int, Int) => Unit): Unit = {
    loop(startOffset + 0, startOffset + 16, 8, 0, consumer)
    loop(startOffset + 16, startOffset + 256, 8, 1, consumer)
    loop(startOffset + 256, startOffset + 65536, 8, 2, consumer)
    loop(startOffset + 65536, startOffset + 65536 * 256, 8, 3, consumer)
    loop(startOffset + 65536 * 256, Seg.MAX_TEXT_OFFSET, 8, 4, consumer)
  }

  private def loopEndShort(startOffset: Int, consumer: (Int, Int) => Unit): Unit = {
    loop(startOffset + 0, startOffset + 16, 8, 0, consumer)
    loop(startOffset + 16, startOffset + 256, 8, 1, consumer)
    loop(startOffset + 256, startOffset + 65536, 8, 2, consumer)
    loop(startOffset + 65536, startOffset + 65536 * 8, 8, 3, consumer)
  }

  /** A dummy CharSequence that returns the same char for all positions. */
  private class DummyCharSequence(myChar: Char, myLength: Int) extends CharSequence {
    override def length():                          Int          = myLength
    override def charAt(index:      Int):           Char         = myChar
    override def subSequence(start: Int, end: Int): CharSequence = new DummyCharSequence(myChar, end - start)
    override def toString:                          String       = {
      val sb = new StringBuilder(length())
      sb.append(this)
      sb.toString()
    }
  }

  test("test_SegAnchorSize") {
    loopSizes { (b, i) =>
      val seg = Seg.segOf(i, i)
//            System.out.println(i);
      assertEquals(Segment.getSegByteLength(seg, ""), b + 1, s"i: $i")
    }
  }

  test("test_SegBaseSize") {
    loopSizes { (bi, i) =>
      loopEnd(
        i,
        (bj, j) =>
          if (j > i) {
//                    System.out.println("i: " + i + " j: " + j);
            val seg = Seg.segOf(i, j)
            assertEquals(Segment.getSegByteLength(seg, ""), Math.max(1, bi) + Math.max(1, bj) + 1, s"i: $i j: $j")
          }
      )
    }
  }

  test("test_SegTextSpaceSize") {
    loopSizes { (bi, i) =>
      loopEnd(
        i,
        (bj, j) =>
          if (j > i) {
            val seg = Seg.textOf(i, j, true, true)
            assertEquals(Segment.getSegByteLength(seg, new DummyCharSequence(' ', j)), bj + 1, s"i: $i j: $j")
          }
      )
    }
  }

  test("test_SegTextEolSize") {
    loopSizes { (bi, i) =>
      loopEnd(
        i,
        (bj, j) =>
          if (j > i) {
            val seg = Seg.textOf(i, j, true, true)
            assertEquals(Segment.getSegByteLength(seg, new DummyCharSequence('\n', j)), bj + 1, s"i: $i j: $j")
          }
      )
    }
  }

  test("test_SegTextRepeatedSize") {
    loopSizes { (bi, i) =>
      loopEnd(
        i,
        (bj, j) =>
          if (j > i) {
            val seg = Seg.textOf(i, j, true, true)
            assertEquals(Segment.getSegByteLength(seg, new DummyCharSequence('a', j)), bj + 2, s"i: $i j: $j")
          }
      )
    }
  }

  test("test_SegTextAsciiSize") {
    loopSizes { (bi, i) =>
      loopEnd(
        i,
        (bj, j) =>
          if (j > i) {
            val seg = Seg.textOf(i, j, true, false)
            assertEquals(Segment.getSegByteLength(seg, new DummyCharSequence('a', j)), bj + 1 + j - i, s"i: $i j: $j")
          }
      )
    }
  }

  test("test_SegTextSize") {
    loopSizes { (bi, i) =>
      loopEnd(
        i,
        (bj, j) =>
          if (j > i) {
            val seg = Seg.textOf(i, j, false, false)
            assertEquals(Segment.getSegByteLength(seg, new DummyCharSequence('a', j)), bj + 1 + (j - i) * 2, s"i: $i j: $j")
          }
      )
    }
  }

  test("test_addIntBytes_getInt") {
    val bytes = new Array[Byte](10)

    loopSizes { (bi, i) =>
      var j = 0
      while (j < 10 - 4) {
        Arrays.fill(bytes, 0xff.toByte)
        val b      = Math.max(1, bi)
        val offset = Segment.addIntBytes(bytes, j, i, b)
        assertEquals(offset, b + j, s"i: $i j: $j")

        val value = Segment.getInt(bytes, j, b)
        assertEquals(value, i, s"i: $i j: $j")
        j += 1
      }
    }
  }

  test("test_addChars_getChar") {
    val bytes = new Array[Byte](10)

    var c: Char = 0
    var continue = true
    while (continue) {
      val chars: CharSequence = new DummyCharSequence(c, 10)
      var j = 0
      while (j < 10 - 4) {
        Arrays.fill(bytes, 0xff.toByte)
        val offset = Segment.addChars(bytes, j, chars, j, j + 1)
        assertEquals(offset, j + 2, s"c: $c j: $j")

        val value = Segment.getChar(bytes, j)
        assertEquals(value, c, s"c: $c j: $j")
        j += 1
      }

      if (c == 0xffff.toChar) continue = false
      else c = (c + 1).toChar
    }
  }

  test("test_addChar_getChar") {
    val bytes = new Array[Byte](10)

    var c: Char = 0
    var continue = true
    while (continue) {
      var j = 0
      while (j < 10 - 4) {
        Arrays.fill(bytes, 0xff.toByte)
        val offset = Segment.addChar(bytes, j, c)
        assertEquals(offset, j + 2, s"c: $c j: $j")

        val value = Segment.getChar(bytes, j)
        assertEquals(value, c, s"c: $c j: $j")
        j += 1
      }

      if (c == 0xffff.toChar) continue = false
      else c = (c + 1).toChar
    }
  }

  test("test_addCharsAscii_getCharAscii") {
    val bytes = new Array[Byte](10)

    var c: Char = 0
    while (c < 256) {
      val chars: CharSequence = new DummyCharSequence(c, 10)
      var j = 0
      while (j < 10 - 4) {
        Arrays.fill(bytes, 0xff.toByte)
        val offset = Segment.addCharsAscii(bytes, j, chars, j, j + 1)
        assertEquals(offset, j + 1, s"c: $c j: $j")

        val value = Segment.getCharAscii(bytes, j)
        assertEquals(value, c, s"c: $c j: $j")
        j += 1
      }
      c = (c + 1).toChar
    }
  }

  test("test_addCharAscii_getCharAscii") {
    val bytes = new Array[Byte](10)

    var c: Char = 0
    while (c < 256) {
      var j = 0
      while (j < 10 - 4) {
        val offset = Segment.addCharAscii(bytes, j, c)
        assertEquals(offset, j + 1, s"c: $c j: $j")

        val value = Segment.getCharAscii(bytes, j)
        assertEquals(value, c, s"c: $c j: $j")
        j += 1
      }
      c = (c + 1).toChar
    }
  }

  test("test_SegBase") {
    val bytes = new Array[Byte](65536 * 256 + 32)

    loopSizesShort { (bi, i) =>
      loopEndShort(
        i,
        (bj, j) =>
          if (j > i) {
//                    System.out.println("i: " + i + " j: " + j);
            Arrays.fill(bytes, Math.max(0, j - 1000), Math.min(bytes.length, j + 1000), 0xff.toByte)
            val dummy: CharSequence = new DummyCharSequence('a', j)
            val basedSequence = BasedSequence.of(dummy)
            val seg           = Seg.segOf(i, j)

            val offset        = Segment.addSegBytes(bytes, j, seg, dummy)
            val segByteLength = Segment.getSegByteLength(seg, dummy)
            assertEquals(offset, j + segByteLength, s"i: $i j: $j")

            val value = Segment.getSegment(bytes, j, 0, 0, basedSequence)
            assertEquals(value.getByteLength, segByteLength)
            assertEquals(value.toString, seg.toString(dummy), s"i: $i j: $j")
          }
      )
    }
  }

  test("test_SegAnchor") {
    val bytes = new Array[Byte](65536 * 256 + 32)

    loopSizesShort { (bi, i) =>
      var j = 0
      while (j < 10) {
//                    System.out.println("i: " + i + " j: " + j);
        Arrays.fill(bytes, Math.max(0, j - 1000), Math.min(bytes.length, j + 1000), 0xff.toByte)
        val dummy: CharSequence = new DummyCharSequence('a', j)
        val basedSequence = BasedSequence.of(dummy)
        val seg           = Seg.segOf(i, i)

        val offset        = Segment.addSegBytes(bytes, j, seg, dummy)
        val segByteLength = Segment.getSegByteLength(seg, dummy)
        assertEquals(offset, j + segByteLength, s"i: $i j: $j")

        val value = Segment.getSegment(bytes, j, 0, 0, basedSequence)
        assertEquals(value.getByteLength, segByteLength)
        assertEquals(value.toString, seg.toString(dummy), s"i: $i j: $j")
        j += 1
      }
    }
  }

  test("test_SegText") {
    val bytes = new Array[Byte](65536 * 256 + 32)

    loopSizesShort { (bi, i) =>
      loopEndShort(
        i,
        (bj, j) =>
          if (j > i) {
//                    System.out.println("i: " + i + " j: " + j);
            Arrays.fill(bytes, Math.max(0, j - 1000), Math.min(bytes.length, j + 1000), 0xff.toByte)
            val dummy: CharSequence = new DummyCharSequence('a', j)
            val basedSequence = BasedSequence.of(dummy)
            val seg           = Seg.textOf(i, j, false, false)

            val offset        = Segment.addSegBytes(bytes, j, seg, dummy)
            val segByteLength = Segment.getSegByteLength(seg, dummy)
            assertEquals(offset, j + segByteLength, s"i: $i j: $j")

            val value = Segment.getSegment(bytes, j, 0, 0, basedSequence)
            assertEquals(value.getByteLength, segByteLength)
            assertEquals(value.toString, seg.toString(dummy), s"i: $i j: $j")
          }
      )
    }
  }

  test("test_SegTextAscii") {
    val bytes = new Array[Byte](65536 * 256 + 32)

    loopSizesShort { (bi, i) =>
      loopEndShort(
        i,
        (bj, j) =>
          if (j > i) {
//                    System.out.println("i: " + i + " j: " + j);
            Arrays.fill(bytes, Math.max(0, j - 1000), Math.min(bytes.length, j + 1000), 0xff.toByte)
            val dummy: CharSequence = new DummyCharSequence('a', j)
            val basedSequence = BasedSequence.of(dummy)
            val seg           = Seg.textOf(i, j, true, false)

            val offset        = Segment.addSegBytes(bytes, j, seg, dummy)
            val segByteLength = Segment.getSegByteLength(seg, dummy)
            assertEquals(offset, j + segByteLength, s"i: $i j: $j")

            val value = Segment.getSegment(bytes, j, 0, 0, basedSequence)
            assertEquals(value.getByteLength, segByteLength)
            assertEquals(value.toString, seg.toString(dummy), s"i: $i j: $j")
          }
      )
    }
  }

  test("test_SegSpaces") {
    val bytes = new Array[Byte](65536 * 256 + 32)

    loopSizesShort { (bi, i) =>
      loopEndShort(
        i,
        (bj, j) =>
          if (j > i) {
//                    System.out.println("i: " + i + " j: " + j);
            Arrays.fill(bytes, Math.max(0, j - 1000), Math.min(bytes.length, j + 1000), 0xff.toByte)
            val dummy: CharSequence = new DummyCharSequence(' ', j)
            val basedSequence = BasedSequence.of(dummy)
            val seg           = Seg.textOf(i, j, true, true)

            val offset        = Segment.addSegBytes(bytes, j, seg, dummy)
            val segByteLength = Segment.getSegByteLength(seg, dummy)
            assertEquals(offset, j + segByteLength, s"i: $i j: $j")

            val value = Segment.getSegment(bytes, j, 0, 0, basedSequence)
            assertEquals(value.getByteLength, segByteLength)
            assertEquals(value.toString, seg.toString(dummy), s"i: $i j: $j")
          }
      )
    }
  }

  test("test_SegEol") {
    val bytes = new Array[Byte](65536 * 256 + 32)

    loopSizesShort { (bi, i) =>
      loopEndShort(
        i,
        (bj, j) =>
          if (j > i) {
//                    System.out.println("i: " + i + " j: " + j);
            Arrays.fill(bytes, Math.max(0, j - 1000), Math.min(bytes.length, j + 1000), 0xff.toByte)
            val dummy: CharSequence = new DummyCharSequence('\n', j)
            val basedSequence = BasedSequence.of(dummy)
            val seg           = Seg.textOf(i, j, true, true)

            val offset        = Segment.addSegBytes(bytes, j, seg, dummy)
            val segByteLength = Segment.getSegByteLength(seg, dummy)
            assertEquals(offset, j + segByteLength, s"i: $i j: $j")

            val value = Segment.getSegment(bytes, j, 0, 0, basedSequence)
            assertEquals(value.getByteLength, segByteLength)
            assertEquals(value.toString, seg.toString(dummy), s"i: $i j: $j")
          }
      )
    }
  }

  test("test_SegRepAscii") {
    val bytes = new Array[Byte](65536 * 256 + 32)

    loopSizesShort { (bi, i) =>
      loopEndShort(
        i,
        (bj, j) =>
          if (j > i) {
//                    System.out.println("i: " + i + " j: " + j);
            Arrays.fill(bytes, Math.max(0, j - 1000), Math.min(bytes.length, j + 1000), 0xff.toByte)
            val dummy: CharSequence = new DummyCharSequence('a', j)
            val basedSequence = BasedSequence.of(dummy)
            val seg           = Seg.textOf(i, j, true, true)

            val offset        = Segment.addSegBytes(bytes, j, seg, dummy)
            val segByteLength = Segment.getSegByteLength(seg, dummy)
            assertEquals(offset, j + segByteLength, s"i: $i j: $j")

            val value = Segment.getSegment(bytes, j, 0, 0, basedSequence)
            assertEquals(value.getByteLength, segByteLength)
            assertEquals(value.toString, seg.toString(dummy), s"i: $i j: $j")
          }
      )
    }
  }

  test("test_SegRepText") {
    val bytes = new Array[Byte](65536 * 256 + 32)

    loopSizesShort { (bi, i) =>
      loopEndShort(
        i,
        (bj, j) =>
          if (j > i) {
//                    System.out.println("i: " + i + " j: " + j);
            Arrays.fill(bytes, Math.max(0, j - 1000), Math.min(bytes.length, j + 1000), 0xff.toByte)
            val dummy: CharSequence = new DummyCharSequence('\u2026', j)
            val basedSequence = BasedSequence.of(dummy)
            val seg           = Seg.textOf(i, j, false, true)

            val offset        = Segment.addSegBytes(bytes, j, seg, dummy)
            val segByteLength = Segment.getSegByteLength(seg, dummy)
            assertEquals(offset, j + segByteLength, s"i: $i j: $j")

            val value = Segment.getSegment(bytes, j, 0, 0, basedSequence)
            assertEquals(value.getByteLength, segByteLength)
            assertEquals(value.toString, seg.toString(dummy), s"i: $i j: $j")
          }
      )
    }
  }
}
