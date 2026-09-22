/*
 * Ported from flexmark-java - https://github.com/vsch/flexmark-java
 * Original source: flexmark-util/src/test/java/com/vladsch/flexmark/util/collection/ClassificationBagTest.java
 * Original license: BSD-2-Clause, Copyright (c) 2015-2016 Atlassian Pty Ltd, 2016-2018 Vladimir Schneider
 *
 * Migration notes:
 *   Origin: the bag classifies by `getClass`, and java answers `java.lang.Integer` for a boxed
 *     `0..9`. Scala.js answers the narrowest box a number fits, `java.lang.Byte` — its documented
 *     rule for boxed numbers — so `containsCategory(classOf[Integer])` cannot hold there. The two
 *     tests are dropped from the generated suite (ssg-md/port/test.conf) and run from here on the
 *     JVM and Scala Native rows, every assertion java's.
 */
package ssg
package md
package util
package collection

trait ClassificationBagByClassTests { this: munit.FunSuite =>

  test("testBasic") {
    val bag = new ClassificationBag[Class[?], Object]((value: Object) => value.getClass())
    var item: Object = null // java interop boundary: the test's own null start value
    var i = 0
    while (i < 10) {
      item = Integer.valueOf(i)
      bag.add(item)
      i += 1
    }
    assertEquals(bag.containsCategory(classOf[Integer]), true)
    assertEquals(bag.getCategoryCount(classOf[Integer]), 10)
    assertEquals(bag.containsCategory(classOf[String]), false)
    assertEquals(bag.getCategoryCount(classOf[String]), 0)
    i = 0
    while (i < 10) {
      item = String.valueOf(i)
      bag.add(item)
      i += 1
    }
    assertEquals(bag.containsCategory(classOf[String]), true)
    assertEquals(bag.getCategoryCount(classOf[String]), 10)
    // now we removeIndex them
    i = 0
    while (i < 10) {
      item = Integer.valueOf(i)
      bag.remove(item)
      i += 2
    }
    assertEquals(bag.containsCategory(classOf[Integer]), true)
    assertEquals(bag.getCategoryCount(classOf[Integer]), 5)
    assertEquals(bag.containsCategory(classOf[String]), true)
    assertEquals(bag.getCategoryCount(classOf[String]), 10)
    // now we removeIndex them
    i = 0
    while (i < 10) {
      item = String.valueOf(i)
      bag.remove(item)
      i += 2
    }
    assertEquals(bag.containsCategory(classOf[Integer]), true)
    assertEquals(bag.getCategoryCount(classOf[Integer]), 5)
    assertEquals(bag.containsCategory(classOf[String]), true)
    assertEquals(bag.getCategoryCount(classOf[String]), 5)
    // now we removeIndex them
    i = 1
    while (i < 10) {
      item = Integer.valueOf(i)
      bag.remove(item)
      i += 2
    }
    assertEquals(bag.containsCategory(classOf[Integer]), false)
    assertEquals(bag.getCategoryCount(classOf[Integer]), 0)
    assertEquals(bag.containsCategory(classOf[String]), true)
    assertEquals(bag.getCategoryCount(classOf[String]), 5)
    // now we removeIndex them
    i = 1
    while (i < 10) {
      item = String.valueOf(i)
      bag.remove(item)
      i += 2
    }
    assertEquals(bag.containsCategory(classOf[Integer]), false)
    assertEquals(bag.getCategoryCount(classOf[Integer]), 0)
    assertEquals(bag.containsCategory(classOf[String]), false)
    assertEquals(bag.getCategoryCount(classOf[String]), 0)
  }

  test("testInterleave") {
    val bag = new ClassificationBag[Class[?], Object]((value: Object) => value.getClass())
    var item: Object = null // java interop boundary: the test's own null start value
    var i = 0
    while (i < 10) {
      item = Integer.valueOf(i)
      bag.add(item)
      item = String.valueOf(i)
      bag.add(item)
      i += 1
    }
    assertEquals(bag.containsCategory(classOf[Integer]), true)
    assertEquals(bag.getCategoryCount(classOf[Integer]), 10)
    assertEquals(bag.containsCategory(classOf[String]), true)
    assertEquals(bag.getCategoryCount(classOf[String]), 10)
  }
}
