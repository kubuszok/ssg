/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package collection
package test

import java.util.ConcurrentModificationException

import scala.language.implicitConversions

final class OrderedSetSuite extends munit.FunSuite {

  test("testAddRemove") {
    val orderedSet = new OrderedSet[String]()

    for (i <- 0 until 10) {
      assertEquals(orderedSet.add(String.valueOf(i)), true)
      assertEquals(orderedSet.add(String.valueOf(i)), false)
    }

    assertEquals(orderedSet.addAll(orderedSet), false)

    var i     = 0
    val iter1 = orderedSet.iterator()
    while (iter1.hasNext) {
      val it = iter1.next()
      assertEquals(it, String.valueOf(i))
      i += 1
    }

    for (j <- 0 until 10) {
      assertEquals(orderedSet.remove(String.valueOf(j)), true)

      assertEquals(orderedSet.getValueList.size(), if (j == 9) 0 else 10)

      var lastJ = j + 1
      val iter2 = orderedSet.iterator()
      while (iter2.hasNext) {
        val it = iter2.next()
        assertEquals(it, String.valueOf(lastJ))
        lastJ += 1
      }
    }
  }

  test("testAddRemoveReversed") {
    val orderedSet = new OrderedSet[String]()

    for (i <- 0 until 10) {
      assertEquals(orderedSet.add(String.valueOf(i)), true)
      assertEquals(orderedSet.add(String.valueOf(i)), false)
    }

    assertEquals(orderedSet.addAll(orderedSet), false)

    var i     = 0
    val iter1 = orderedSet.iterator()
    while (iter1.hasNext) {
      val it = iter1.next()
      assertEquals(it, String.valueOf(i))
      i += 1
    }

    var j = 10
    while ({ j -= 1; j >= 0 }) {
      assertEquals(orderedSet.remove(String.valueOf(j)), true)

      assertEquals(orderedSet.getValueList.size(), j)

      var lastJ = 0
      val iter2 = orderedSet.iterator()
      while (iter2.hasNext) {
        val it = iter2.next()
        assertEquals(it, String.valueOf(lastJ))
        lastJ += 1
      }

      assertEquals(lastJ, j)
    }
  }

  test("testRetainAll") {
    val orderedSet = new OrderedSet[String]()
    val retainSet  = new OrderedSet[String]()

    for (i <- 0 until 10) {
      assertEquals(orderedSet.add(String.valueOf(i)), true)
      assertEquals(orderedSet.add(String.valueOf(i)), false)
    }

    for (i <- 0 until 10 by 2) {
      assertEquals(retainSet.add(String.valueOf(i)), true)
      assertEquals(retainSet.add(String.valueOf(i)), false)
    }

    assertEquals(orderedSet.addAll(orderedSet), false)
    assertEquals(retainSet.addAll(retainSet), false)

    assertEquals(orderedSet.retainAll(orderedSet), false)
    assertEquals(retainSet.retainAll(retainSet), false)

    assertEquals(orderedSet.retainAll(retainSet), true)
    assertEquals(orderedSet.equals(retainSet), true)

    var i     = 0
    val iter1 = orderedSet.iterator()
    while (iter1.hasNext) {
      val it = iter1.next()
      assertEquals(it, String.valueOf(i))
      i += 2
    }

    var j = 10
    while ({ j -= 1; j >= 0 })
      assertEquals(orderedSet.remove(String.valueOf(j)), (j & 1) == 0)
  }

  test("testRemoveIteration") {
    val orderedSet = new OrderedSet[String]()

    for (i <- 0 until 10) {
      assertEquals(orderedSet.add(String.valueOf(i)), true)
      assertEquals(orderedSet.add(String.valueOf(i)), false)
    }

    assertEquals(orderedSet.addAll(orderedSet), false)

    var i        = 0
    var iterator = orderedSet.iterator()
    while (iterator.hasNext) {
      val it = iterator.next()
      assertEquals(it, String.valueOf(i))
      i += 1
    }

    iterator = orderedSet.iterator()
    var j = 0
    while (iterator.hasNext) {
      iterator.next()
      iterator.remove()

      assertEquals(orderedSet.getValueList.size(), if (j == 9) 0 else 10)

      var lastJ = j + 1
      val iter2 = orderedSet.iterator()
      while (iter2.hasNext) {
        val it = iter2.next()
        assertEquals(it, String.valueOf(lastJ))
        lastJ += 1
      }

      j += 1
    }
  }

  test("testRemoveReversedIteration") {
    val orderedSet = new OrderedSet[String]()

    for (i <- 0 until 10) {
      assertEquals(orderedSet.add(String.valueOf(i)), true)
      assertEquals(orderedSet.add(String.valueOf(i)), false)
    }

    assertEquals(orderedSet.addAll(orderedSet), false)

    var i        = 9
    var iterator = orderedSet.reversedIterator()
    while (iterator.hasNext) {
      val it = iterator.next()
      assertEquals(it, String.valueOf(i))
      i -= 1
    }

    iterator = orderedSet.reversedIterator()
    var j = 9
    while (iterator.hasNext) {
      iterator.next()
      iterator.remove()

      assertEquals(orderedSet.getValueList.size(), j)

      var lastJ = 0
      val iter2 = orderedSet.iterator()
      while (iter2.hasNext) {
        val it = iter2.next()
        assertEquals(it, String.valueOf(lastJ))
        lastJ += 1
      }

      assertEquals(lastJ, j)
      j -= 1
    }
  }

  test("testConcurrentMod") {
    val orderedSet = new OrderedSet[String]()

    for (i <- 0 until 10) {
      assertEquals(orderedSet.add(String.valueOf(i)), true)
      assertEquals(orderedSet.add(String.valueOf(i)), false)
    }

    assertEquals(orderedSet.addAll(orderedSet), false)

    val iterator = orderedSet.iterator()
    assert(iterator.hasNext)
    orderedSet.removeIndex(0)
    intercept[ConcurrentModificationException] {
      iterator.next()
    }
  }

  test("testSetConflict") {
    val orderedSet = new OrderedSet[String]()

    for (i <- 0 until 10) {
      assertEquals(orderedSet.add(String.valueOf(i)), true)
      assertEquals(orderedSet.add(String.valueOf(i)), false)
    }

    intercept[IllegalStateException] {
      orderedSet.setValueAt(0, String.valueOf(1), "1")
    }
  }
}
