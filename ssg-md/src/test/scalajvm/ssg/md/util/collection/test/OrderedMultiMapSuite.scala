/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package collection
package test

import ssg.md.util.misc.{ Pair, Paired }

import java.util.ConcurrentModificationException

import scala.language.implicitConversions

final class OrderedMultiMapSuite extends munit.FunSuite {

  test("testAddRemove") {
    val orderedMap = new OrderedMultiMap[String, Integer]()

    for (i <- 0 until 10) {
      assertEquals(orderedMap.put(String.valueOf(i), i), null.asInstanceOf[Integer])
      assertEquals(orderedMap.put(String.valueOf(i), i), Integer.valueOf(i))
    }

    orderedMap.putAll(orderedMap)
    assertEquals(orderedMap.equals(orderedMap), true)

    var i     = 0
    val iter1 = orderedMap.entrySet().iterator()
    while (iter1.hasNext) {
      val it = iter1.next()
      assertEquals(it.getKey, String.valueOf(i))
      assertEquals(it.getValue.intValue(), i)
      i += 1
    }

    for (j <- 0 until 10) {
      assertEquals(orderedMap.remove(String.valueOf(j)), Integer.valueOf(j))

      assertEquals(orderedMap.keySet().getValueList.size(), if (j == 9) 0 else 10)

      var lastJ = j + 1
      val iter2 = orderedMap.entrySet().iterator()
      while (iter2.hasNext) {
        val it = iter2.next()
        assertEquals(it.getKey, String.valueOf(lastJ))
        assertEquals(it.getValue.intValue(), lastJ)
        lastJ += 1
      }
    }
  }

  test("testAddRemoveReversed") {
    val orderedMap = new OrderedMultiMap[String, Integer]()

    for (i <- 0 until 10) {
      assertEquals(orderedMap.put(String.valueOf(i), i), null.asInstanceOf[Integer])
      assertEquals(orderedMap.put(String.valueOf(i), i), Integer.valueOf(i))
    }

    var i         = 0
    val iterFirst = orderedMap.iterator()
    while (iterFirst.hasNext) {
      val it = iterFirst.next()
      assertEquals(it.getKey, String.valueOf(i))
      assertEquals(it.getValue.intValue(), i)
      i += 1
    }

    val entries = orderedMap.entrySet()

    orderedMap.putAll(orderedMap)
    i = 0
    val iter1 = entries.iterator()
    while (iter1.hasNext) {
      val it = iter1.next()
      assertEquals(it.getKey, String.valueOf(i))
      assertEquals(it.getValue.intValue(), i)
      i += 1
    }

    assertEquals(orderedMap.equals(orderedMap), true)

    i = 0
    val iter2 = orderedMap.entrySet().iterator()
    while (iter2.hasNext) {
      val it = iter2.next()
      assertEquals(it.getKey, String.valueOf(i))
      assertEquals(it.getValue.intValue(), i)
      i += 1
    }

    var j = 10
    while ({ j -= 1; j >= 0 }) {
      assertEquals(orderedMap.remove(String.valueOf(j)), Integer.valueOf(j))

      // hosted sets don't shrink
      assertEquals(orderedMap.keySet().getValueList.size(), if (orderedMap.size() == 0) 0 else 10)

      var lastJ = 0
      val iter3 = orderedMap.entrySet().iterator()
      while (iter3.hasNext) {
        val it = iter3.next()
        assertEquals(it.getKey, String.valueOf(lastJ))
        assertEquals(it.getValue.intValue(), lastJ)
        lastJ += 1
      }

      assertEquals(lastJ, j)
    }
  }

  test("testRetainAll") {
    val orderedMap = new OrderedMultiMap[String, Integer]()
    val retainSet  = new OrderedSet[String]()

    for (i <- 0 until 10) {
      assertEquals(orderedMap.put(String.valueOf(i), i), null.asInstanceOf[Integer])
      assertEquals(orderedMap.put(String.valueOf(i), i), Integer.valueOf(i))
    }

    orderedMap.putAll(orderedMap)
    assertEquals(orderedMap.equals(orderedMap), true)

    var i     = 0
    val iter1 = orderedMap.entrySet().iterator()
    while (iter1.hasNext) {
      val it = iter1.next()
      assertEquals(it.getKey, String.valueOf(i))
      assertEquals(it.getValue.intValue(), i)
      i += 1
    }

    for (i <- 0 until 10 by 2) {
      assertEquals(retainSet.add(String.valueOf(i)), true)
      assertEquals(retainSet.add(String.valueOf(i)), false)
    }

    assertEquals(orderedMap.keySet().retainAll(orderedMap.keySet()), false)
    assertEquals(retainSet.retainAll(retainSet), false)

    assertEquals(orderedMap.keySet().retainAll(retainSet), true)
    assertEquals(orderedMap.keySet().equals(retainSet), true)

    i = 0
    val iter2 = orderedMap.entrySet().iterator()
    while (iter2.hasNext) {
      val it = iter2.next()
      assertEquals(it.getKey, String.valueOf(i))
      assertEquals(it.getValue, Integer.valueOf(i))
      i += 2
    }

    var j = 10
    while ({ j -= 1; j >= 0 }) {
      assertEquals(orderedMap.keySet().remove(String.valueOf(j)), (j & 1) == 0)
      assertEquals(orderedMap.containsKey(String.valueOf(j)), false)
    }
  }

  test("testRemoveIteration") {
    val orderedMap = new OrderedMultiMap[String, Integer]()

    for (i <- 0 until 10) {
      assertEquals(orderedMap.put(String.valueOf(i), i), null.asInstanceOf[Integer])
      assertEquals(orderedMap.put(String.valueOf(i), i), Integer.valueOf(i))
    }

    orderedMap.putAll(orderedMap)
    assertEquals(orderedMap.equals(orderedMap), true)

    var iterator = orderedMap.entrySet().iterator()
    var i        = 0
    while (iterator.hasNext) {
      val it = iterator.next()
      assertEquals(it.getKey, String.valueOf(i))
      assertEquals(it.getValue.intValue(), i)
      i += 1
    }

    iterator = orderedMap.entrySet().iterator()
    var j = 0
    while (iterator.hasNext) {
      iterator.next()
      iterator.remove()

      assertEquals(orderedMap.keySet().getValueList.size(), if (j == 9) 0 else 10)

      var lastJ = j + 1
      val iter2 = orderedMap.entrySet().iterator()
      while (iter2.hasNext) {
        val it = iter2.next()
        assertEquals(it.getKey, String.valueOf(lastJ))
        assertEquals(it.getValue.intValue(), lastJ)
        lastJ += 1
      }

      j += 1
    }
  }

  test("testRemoveReversedReversedIteration") {
    val orderedMap = new OrderedMultiMap[String, Integer]()

    for (i <- 0 until 10) {
      assertEquals(orderedMap.put(String.valueOf(i), i), null.asInstanceOf[Integer])
      assertEquals(orderedMap.put(String.valueOf(i), i), Integer.valueOf(i))
    }

    orderedMap.putAll(orderedMap)
    assertEquals(orderedMap.equals(orderedMap), true)

    var iterator = orderedMap.entrySet().reversedIterable().reversedIterator()
    var i        = 0
    while (iterator.hasNext) {
      val it = iterator.next()
      assertEquals(it.getKey, String.valueOf(i))
      assertEquals(it.getValue.intValue(), i)
      i += 1
    }

    iterator = orderedMap.entrySet().reversedIterable().reversedIterator()
    var j = 0
    while (iterator.hasNext) {
      iterator.next()
      iterator.remove()

      assertEquals(orderedMap.keySet().getValueList.size(), if (j == 9) 0 else 10)

      var lastJ = j + 1
      val iter2 = orderedMap.entrySet().iterator()
      while (iter2.hasNext) {
        val it = iter2.next()
        assertEquals(it.getKey, String.valueOf(lastJ))
        assertEquals(it.getValue.intValue(), lastJ)
        lastJ += 1
      }

      j += 1
    }
  }

  test("testRemoveReversedIteration") {
    val orderedMap = new OrderedMultiMap[String, Integer]()

    for (i <- 0 until 10) {
      assertEquals(orderedMap.put(String.valueOf(i), i), null.asInstanceOf[Integer])
      assertEquals(orderedMap.put(String.valueOf(i), i), Integer.valueOf(i))
    }

    orderedMap.putAll(orderedMap)
    assertEquals(orderedMap.equals(orderedMap), true)

    var iterator = orderedMap.entrySet().reversedIterator()
    var i        = 9
    while (iterator.hasNext) {
      val it = iterator.next()
      assertEquals(it.getKey, String.valueOf(i))
      assertEquals(it.getValue.intValue(), i)
      i -= 1
    }

    assertEquals(i, -1)

    iterator = orderedMap.entrySet().reversedIterator()
    var j = 9
    while (iterator.hasNext) {
      iterator.next()
      iterator.remove()

      // hosted sets don't shrink until empty
      assertEquals(orderedMap.keySet().getValueList.size(), if (orderedMap.size() == 0) 0 else 10)

      var lastJ = 0
      val iter2 = orderedMap.entrySet().iterator()
      while (iter2.hasNext) {
        val it = iter2.next()
        assertEquals(it.getKey, String.valueOf(lastJ))
        assertEquals(it.getValue.intValue(), lastJ)
        lastJ += 1
      }

      assertEquals(lastJ, j)
      j -= 1
    }
  }

  test("testRemoveIteratorReversedIteration") {
    val orderedMap = new OrderedMultiMap[String, Integer]()

    for (i <- 0 until 10) {
      assertEquals(orderedMap.put(String.valueOf(i), i), null.asInstanceOf[Integer])
      assertEquals(orderedMap.put(String.valueOf(i), i), Integer.valueOf(i))
    }

    orderedMap.putAll(orderedMap)
    assertEquals(orderedMap.equals(orderedMap), true)

    var iterator = orderedMap.entrySetIterable().reversed().iterator()
    var i        = 9
    while (iterator.hasNext) {
      val it = iterator.next()
      assertEquals(it.getKey, String.valueOf(i))
      assertEquals(it.getValue.intValue(), i)
      i -= 1
    }

    assertEquals(i, -1)

    iterator = orderedMap.entrySetIterable().reversedIterator()
    var j = 9
    while (iterator.hasNext) {
      iterator.next()
      iterator.remove()

      // hosted sets don't shrink
      assertEquals(orderedMap.keySet().getValueList.size(), if (orderedMap.size() == 0) 0 else 10)

      var lastJ = 0
      val iter2 = orderedMap.entrySet().iterator()
      while (iter2.hasNext) {
        val it = iter2.next()
        assertEquals(it.getKey, String.valueOf(lastJ))
        assertEquals(it.getValue.intValue(), lastJ)
        lastJ += 1
      }

      assertEquals(lastJ, j)
      j -= 1
    }
  }

  /** reverse key/values */
  test("testAddRemoveValue") {
    val orderedMap = new OrderedMultiMap[String, Integer]()

    for (i <- 0 until 10) {
      assertEquals(orderedMap.putValueKey(i, String.valueOf(i)), null.asInstanceOf[String])
      assertEquals(orderedMap.putValueKey(i, String.valueOf(i)), String.valueOf(i))
    }

    orderedMap.putAll(orderedMap)
    assertEquals(orderedMap.equals(orderedMap), true)

    var i     = 0
    val iter1 = orderedMap.entrySet().iterator()
    while (iter1.hasNext) {
      val it = iter1.next()
      assertEquals(it.getKey, String.valueOf(i))
      assertEquals(it.getValue.intValue(), i)
      i += 1
    }

    for (j <- 0 until 10) {
      assertEquals(orderedMap.removeValue(Integer.valueOf(j)), String.valueOf(j))

      assertEquals(orderedMap.keySet().getValueList.size(), if (j == 9) 0 else 10)

      var lastJ = j + 1
      val iter2 = orderedMap.entrySet().iterator()
      while (iter2.hasNext) {
        val it = iter2.next()
        assertEquals(it.getKey, String.valueOf(lastJ))
        assertEquals(it.getValue.intValue(), lastJ)
        lastJ += 1
      }
    }
  }

  test("testAddRemoveReversedValue") {
    val orderedMap = new OrderedMultiMap[String, Integer]()

    for (i <- 0 until 10) {
      assertEquals(orderedMap.putValueKey(i, String.valueOf(i)), null.asInstanceOf[String])
      assertEquals(orderedMap.put(String.valueOf(i), i), Integer.valueOf(i))
    }

    orderedMap.putAll(orderedMap)
    assertEquals(orderedMap.equals(orderedMap), true)

    var i     = 0
    val iter1 = orderedMap.entrySet().iterator()
    while (iter1.hasNext) {
      val it = iter1.next()
      assertEquals(it.getKey, String.valueOf(i))
      assertEquals(it.getValue.intValue(), i)
      i += 1
    }

    i = 0
    val iter1b = orderedMap.iterator()
    while (iter1b.hasNext) {
      val it = iter1b.next()
      assertEquals(it.getKey, String.valueOf(i))
      assertEquals(it.getValue.intValue(), i)
      i += 1
    }

    var j = 10
    while ({ j -= 1; j >= 0 }) {
      assertEquals(orderedMap.removeValue(Integer.valueOf(j)), String.valueOf(j))

      // hosted sets don't shrink
      assertEquals(orderedMap.keySet().getValueList.size(), if (orderedMap.size() == 0) 0 else 10)

      var lastJ = 0
      val iter2 = orderedMap.iterator()
      while (iter2.hasNext) {
        val it = iter2.next()
        assertEquals(it.getKey, String.valueOf(lastJ))
        assertEquals(it.getValue.intValue(), lastJ)
        lastJ += 1
      }

      assertEquals(lastJ, j)
    }
  }

  test("testConcurrentModIterator") {
    val orderedMap = new OrderedMultiMap[String, Integer]()
    orderedMap.put("0", 0)
    orderedMap.put("1", 1)

    val iterator = orderedMap.iterator()
    assert(iterator.hasNext)
    orderedMap.remove("0")
    intercept[ConcurrentModificationException] {
      iterator.next()
    }
  }

  test("testConcurrentModKeyIterator") {
    val orderedMap = new OrderedMultiMap[String, Integer]()
    orderedMap.put("0", 0)
    orderedMap.put("1", 1)

    val iterator1 = orderedMap.keyIterator()
    assert(iterator1.hasNext)
    orderedMap.remove("0")
    intercept[ConcurrentModificationException] {
      iterator1.next()
    }
  }

  test("testConcurrentModValueIterator") {
    val orderedMap = new OrderedMultiMap[String, Integer]()
    orderedMap.put("0", 0)
    orderedMap.put("1", 1)

    val iterator = orderedMap.valueIterator()
    assert(iterator.hasNext)
    orderedMap.remove("0")
    intercept[ConcurrentModificationException] {
      iterator.next()
    }
  }

  test("testConcurrentModIteratorOnKey") {
    val orderedMap = new OrderedMultiMap[String, Integer]()
    orderedMap.put("0", 0)
    orderedMap.put("1", 1)

    val iterator = orderedMap.iterator()
    assert(iterator.hasNext)
    orderedMap.keySet().remove("0")
    intercept[ConcurrentModificationException] {
      iterator.next()
    }
  }

  test("testConcurrentModKeyIteratorOnKey") {
    val orderedMap = new OrderedMultiMap[String, Integer]()
    orderedMap.put("0", 0)
    orderedMap.put("1", 1)

    val iterator1 = orderedMap.keyIterator()
    assert(iterator1.hasNext)
    orderedMap.keySet().remove("0")
    intercept[ConcurrentModificationException] {
      iterator1.next()
    }
  }

  test("testConcurrentModValueIteratorOnKey") {
    val orderedMap = new OrderedMultiMap[String, Integer]()
    orderedMap.put("0", 0)
    orderedMap.put("1", 1)

    val iterator = orderedMap.valueIterator()
    assert(iterator.hasNext)
    orderedMap.keySet().remove("0")
    intercept[ConcurrentModificationException] {
      iterator.next()
    }
  }

  test("testConcurrentModIteratorOnValue") {
    val orderedMap = new OrderedMultiMap[String, Integer]()
    orderedMap.put("0", 0)
    orderedMap.put("1", 1)

    val iterator = orderedMap.iterator()
    assert(iterator.hasNext)
    orderedMap.valueSet().removeIndex(0)
    intercept[ConcurrentModificationException] {
      iterator.next()
    }
  }

  test("testConcurrentModKeyIteratorOnValue") {
    val orderedMap = new OrderedMultiMap[String, Integer]()
    orderedMap.put("0", 0)
    orderedMap.put("1", 1)

    val iterator1 = orderedMap.keyIterator()
    assert(iterator1.hasNext)
    orderedMap.valueSet().removeIndex(0)
    intercept[ConcurrentModificationException] {
      iterator1.next()
    }
  }

  test("testConcurrentModValueIteratorOnValue") {
    val orderedMap = new OrderedMultiMap[String, Integer]()
    orderedMap.put("0", 0)
    orderedMap.put("1", 1)

    val iterator = orderedMap.valueIterator()
    assert(iterator.hasNext)
    orderedMap.valueSet().removeIndex(0)
    intercept[ConcurrentModificationException] {
      iterator.next()
    }
  }

  test("testAddConflict") {
    val orderedMap = new OrderedMultiMap[String, Integer]()
    orderedMap.put("0", 0)
    orderedMap.put("1", 1)
    intercept[IllegalStateException] {
      orderedMap.putKeyValue("1", 0)
    }
  }

  test("testAddKeyValuePair") {
    val orderedMap = new OrderedMultiMap[String, Integer]()
    orderedMap.put("0", 0)
    orderedMap.put("1", 1)
    assertEquals(orderedMap.putKeyValuePair(new Pair("2", Integer.valueOf(2))), true)
    assertEquals(orderedMap.putKeyValuePair(new Pair("2", Integer.valueOf(2))), false)

    intercept[IllegalStateException] {
      orderedMap.putKeyValuePair(new Pair("1", Integer.valueOf(0)))
    }
  }

  test("testAddValueKeyPair") {
    val orderedMap = new OrderedMultiMap[String, Integer]()
    orderedMap.put("0", 0)
    orderedMap.put("1", 1)
    assertEquals(orderedMap.putValueKeyPair(new Pair(Integer.valueOf(2), "2")), true)
    assertEquals(orderedMap.putValueKeyPair(new Pair(Integer.valueOf(2), "2")), false)

    intercept[IllegalStateException] {
      orderedMap.putValueKeyPair(new Pair(Integer.valueOf(0), "1"))
    }
  }

  test("testHostedCallback") {
    val validator  = new CollectionHostValidator[Paired[Nullable[String], Nullable[Integer]]]()
    val orderedMap = new OrderedMultiMap[String, Integer](validator.getHost.asInstanceOf[CollectionHost[Paired[Nullable[String], Nullable[Integer]]]])

    // validator.trace()

    validator.reset().expectAdding(0, Pair.of("0", Integer.valueOf(0)).asInstanceOf[Paired[Nullable[String], Nullable[Integer]]], null).test(() => orderedMap.put("0", 0))

    validator.reset().expectAdding(1, Pair.of("1", Integer.valueOf(1)).asInstanceOf[Paired[Nullable[String], Nullable[Integer]]], null).test(() => orderedMap.put("1", 1))

    for (j <- 0 until 2) {
      val finalJ = j
      validator
        .reset()
        .id(j)
        .expectRemoving(j, Pair.of(String.valueOf(j), null.asInstanceOf[Integer]).asInstanceOf[Paired[Nullable[String], Nullable[Integer]]])
        .expectRemoving(j, Pair.of(null.asInstanceOf[String], Integer.valueOf(j)).asInstanceOf[Paired[Nullable[String], Nullable[Integer]]])
        .repeat(2)
        .onCond(j == 1)
        .expectClearing()
        .test((() => orderedMap.keySet().remove(String.valueOf(finalJ))): Runnable)
    }
  }
}
