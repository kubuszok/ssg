/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/ArrayUtils.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Migration notes:
 *   Renames: com.vladsch.flexmark.util.misc → ssg.md.util.misc
 *   Convention: Scala 3, Nullable[A], no return
 *   Idiom: boundary/break for early exit
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-util-misc/src/main/java/com/vladsch/flexmark/util/misc/ArrayUtils.java
 * Covenant-verified: 2026-04-26
 *
 * upstream-commit: bcfe84a3ab6d23d04adce3e5a0bae45c6b791d14
 */
package ssg
package md
package util
package misc

import ssg.md.Nullable

import java.util.BitSet
import scala.reflect.ClassTag
import scala.util.boundary
import scala.util.boundary.break

object ArrayUtils {

  def contained[T](value: T, array: Array[T]): Boolean =
    indexOf(value, array) != -1

  def append[T: ClassTag](array: Array[T], values: T*): Array[T] =
    if (values.nonEmpty) {
      val newInstance = new Array[T](array.length + values.length)
      System.arraycopy(array, 0, newInstance, 0, array.length)
      var i = 0
      for (v <- values) {
        newInstance(array.length + i) = v
        i += 1
      }
      newInstance
    } else {
      array
    }

  def contained(value: Int, array: Array[Int]): Boolean = boundary {
    var i = 0
    while (i < array.length) {
      if (array(i) == value) break(true)
      i += 1
    }
    false
  }

  def firstOf[T](ts: Array[T], predicate: T => Boolean): Nullable[T] =
    firstOf(ts, 0, ts.length, predicate)

  def firstOf[T](ts: Array[T], fromIndex: Int, predicate: T => Boolean): Nullable[T] =
    firstOf(ts, fromIndex, ts.length, predicate)

  def firstOf[T](ts: Array[T], fromIndex: Int, endIndex: Int, predicate: T => Boolean): Nullable[T] = {
    val i = indexOf(ts, fromIndex, endIndex, predicate)
    if (i == -1) Nullable.empty else Nullable(ts(i))
  }

  def indexOf[T](t: T, ts: Array[T]): Int = indexOf(t, ts, 0, ts.length)

  def indexOf[T](t: T, ts: Array[T], fromIndex: Int): Int = indexOf(t, ts, fromIndex, ts.length)

  def indexOf[T](t: T, ts: Array[T], fromIndex: Int, endIndex: Int): Int =
    indexOf(ts, fromIndex, endIndex, (t1: T) => t == t1)

  def indexOf[T](ts: Array[T], predicate: T => Boolean): Int = indexOf(ts, 0, ts.length, predicate)

  def indexOf[T](ts: Array[T], fromIndex: Int, predicate: T => Boolean): Int = indexOf(ts, fromIndex, ts.length, predicate)

  def indexOf[T](ts: Array[T], fromIndex: Int, endIndex: Int, predicate: T => Boolean): Int = {
    val iMax = ts.length
    if (endIndex > 0) {
      val from = if (fromIndex < 0) 0 else fromIndex
      val end  = if (endIndex > iMax) iMax else endIndex
      if (from < end) {
        var i = from
        while (i < end) {
          if (predicate(ts(i))) return i // @nowarn - performance-critical loop
          i += 1
        }
      }
    }
    -1
  }

  def lastOf[T](ts: Array[T], predicate: T => Boolean): Nullable[T] =
    lastOf(ts, 0, ts.length, predicate)

  def lastOf[T](ts: Array[T], fromIndex: Int, predicate: T => Boolean): Nullable[T] =
    lastOf(ts, 0, fromIndex, predicate)

  def lastOf[T](ts: Array[T], startIndex: Int, fromIndex: Int, predicate: T => Boolean): Nullable[T] = {
    val i = lastIndexOf(ts, startIndex, fromIndex, predicate)
    if (i == -1) Nullable.empty else Nullable(ts(i))
  }

  def lastIndexOf[T](t: T, ts: Array[T]): Int = lastIndexOf(t, ts, 0, ts.length)

  def lastIndexOf[T](t: T, ts: Array[T], fromIndex: Int): Int = lastIndexOf(t, ts, 0, fromIndex)

  def lastIndexOf[T](t: T, ts: Array[T], startIndex: Int, fromIndex: Int): Int =
    lastIndexOf(ts, startIndex, fromIndex, (t1: T) => t == t1)

  def lastIndexOf[T](ts: Array[T], predicate: T => Boolean): Int = lastIndexOf(ts, 0, ts.length, predicate)

  def lastIndexOf[T](ts: Array[T], fromIndex: Int, predicate: T => Boolean): Int = lastIndexOf(ts, 0, fromIndex, predicate)

  def lastIndexOf[T](ts: Array[T], startIndex: Int, fromIndex: Int, predicate: T => Boolean): Int = {
    val iMax = ts.length
    if (fromIndex >= 0) {
      val start = if (startIndex < 0) 0 else startIndex
      val from  = if (fromIndex >= iMax) iMax - 1 else fromIndex
      if (start < from) {
        var i = from
        while (i >= start) {
          if (predicate(ts(i))) return i // @nowarn - performance-critical loop
          i -= 1
        }
      }
    }
    -1
  }

  def toArray(bitSet: BitSet): Array[Int] = {
    var count   = bitSet.cardinality()
    val bits    = new Array[Int](count)
    var lastSet = bitSet.length()
    while (lastSet >= 0) {
      lastSet = bitSet.previousSetBit(lastSet - 1)
      if (lastSet < 0) {
        // done
        lastSet = -1
      } else {
        count -= 1
        bits(count) = lastSet
      }
    }
    assert(count == 0)
    bits
  }
}
