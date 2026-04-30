/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package collection
package test

import java.util.ConcurrentModificationException

final class OrderedMapSuite extends munit.FunSuite {

  test("testAddRemove") {
    val orderedMap = new OrderedMap[String, Integer]()

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
      val iter2 = orderedMap.iterator()
      while (iter2.hasNext) {
        val it = iter2.next()
        assertEquals(it.getKey, String.valueOf(lastJ))
        assertEquals(it.getValue.intValue(), lastJ)
        lastJ += 1
      }
    }
  }

  test("testAddRemoveReversed") {
    val orderedMap = new OrderedMap[String, Integer]()

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

    var j = 10
    while ({ j -= 1; j >= 0 }) {
      assertEquals(orderedMap.remove(String.valueOf(j)), Integer.valueOf(j))

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
    }
  }

  test("testRetainAll") {
    val orderedMap = new OrderedMap[String, Integer]()
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
    val orderedMap = new OrderedMap[String, Integer]()

    for (i <- 0 until 10) {
      assertEquals(orderedMap.put(String.valueOf(i), i), null.asInstanceOf[Integer])
      assertEquals(orderedMap.put(String.valueOf(i), i), Integer.valueOf(i))
    }

    orderedMap.putAll(orderedMap)
    assertEquals(orderedMap.equals(orderedMap), true)

    var iterator = orderedMap.iterator()
    var i        = 0
    while (iterator.hasNext) {
      val it = iterator.next()
      assertEquals(it.getKey, String.valueOf(i))
      assertEquals(it.getValue.intValue(), i)
      i += 1
    }

    iterator = orderedMap.iterator()
    var j = 0
    while (iterator.hasNext) {
      iterator.next()
      iterator.remove()

      assertEquals(orderedMap.keySet().getValueList.size(), if (j == 9) 0 else 10)

      var lastJ = j + 1
      val iter2 = orderedMap.iterator()
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
    val orderedMap = new OrderedMap[String, Integer]()

    for (i <- 0 until 10) {
      assertEquals(orderedMap.put(String.valueOf(i), i), null.asInstanceOf[Integer])
      assertEquals(orderedMap.put(String.valueOf(i), i), Integer.valueOf(i))
    }

    orderedMap.putAll(orderedMap)
    assertEquals(orderedMap.equals(orderedMap), true)

    var iterator = orderedMap.reversedIterable().reversed().iterator()
    var i        = 0
    while (iterator.hasNext) {
      val it = iterator.next()
      assertEquals(it.getKey, String.valueOf(i))
      assertEquals(it.getValue.intValue(), i)
      i += 1
    }

    iterator = orderedMap.reversedIterable().reversedIterator()
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
    val orderedMap = new OrderedMap[String, Integer]()

    for (i <- 0 until 10) {
      assertEquals(orderedMap.put(String.valueOf(i), i), null.asInstanceOf[Integer])
      assertEquals(orderedMap.put(String.valueOf(i), i), Integer.valueOf(i))
    }

    orderedMap.putAll(orderedMap)
    assertEquals(orderedMap.equals(orderedMap), true)

    var iterator = orderedMap.reversedEntryIterator()
    var i        = 9
    while (iterator.hasNext) {
      val it = iterator.next()
      assertEquals(it.getKey, String.valueOf(i))
      assertEquals(it.getValue.intValue(), i)
      i -= 1
    }

    assertEquals(i, -1)

    iterator = orderedMap.reversedEntryIterator()
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
    val orderedMap = new OrderedMap[String, Integer]()

    for (i <- 0 until 10) {
      assertEquals(orderedMap.put(String.valueOf(i), i), null.asInstanceOf[Integer])
      assertEquals(orderedMap.put(String.valueOf(i), i), Integer.valueOf(i))
    }

    orderedMap.putAll(orderedMap)
    assertEquals(orderedMap.equals(orderedMap), true)

    var iterator = orderedMap.entryIterable().reversed().reversed().reversedIterator()
    var i        = 9
    while (iterator.hasNext) {
      val it = iterator.next()
      assertEquals(it.getKey, String.valueOf(i))
      assertEquals(it.getValue.intValue(), i)
      i -= 1
    }

    assertEquals(i, -1)

    iterator = orderedMap.entryIterable().reversed().reversed().reversedIterator()
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

  test("testConcurrentModIterator") {
    val orderedMap = new OrderedMap[String, Integer]()
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
    val orderedMap = new OrderedMap[String, Integer]()
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
    val orderedMap = new OrderedMap[String, Integer]()
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
    val orderedMap = new OrderedMap[String, Integer]()
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
    val orderedMap = new OrderedMap[String, Integer]()
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
    val orderedMap = new OrderedMap[String, Integer]()
    orderedMap.put("0", 0)
    orderedMap.put("1", 1)

    val iterator = orderedMap.valueIterator()
    assert(iterator.hasNext)
    orderedMap.keySet().remove("0")
    intercept[ConcurrentModificationException] {
      iterator.next()
    }
  }

  test("testHostedCallback") {
    val validator  = new CollectionHostValidator[String]()
    val orderedMap = new OrderedMap[String, Integer](validator.getHost)

    // validator.trace()

    validator.reset().expectAdding(0, "0", Integer.valueOf(0)).test(() => orderedMap.put("0", 0))

    validator.reset().expectAdding(1, "1", Integer.valueOf(1)).test(() => orderedMap.put("1", 1))

    for (j <- 0 until 2) {
      val finalJ = j
      validator.reset().id(j).expectRemoving(j, String.valueOf(j)).onCond(j == 1).expectClearing().test(() => orderedMap.keySet().remove(String.valueOf(finalJ)))
    }
  }
}
