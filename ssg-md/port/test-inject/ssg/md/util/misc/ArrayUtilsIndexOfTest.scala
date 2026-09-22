/*
 * Ported from flexmark-java - https://github.com/vsch/flexmark-java
 * Original source: flexmark-util/src/test/java/com/vladsch/flexmark/util/misc/ArrayUtilsTest.java
 *   (test_indexOf and test_lastIndexOf)
 * Original license: BSD-2-Clause, Copyright (c) 2015-2016 Atlassian Pty Ltd, 2016-2018 Vladimir Schneider
 *
 * Migration notes:
 *   Origin: java passes `Objects::isNull` to the overloaded `indexOf`/`lastIndexOf`. Written as a
 *     method value in Scala that compiles on the JVM and on Scala Native and is refused by the
 *     Scala.js compiler ("object creation impossible ... Predicate"), so these two tests are dropped
 *     from the generated `ArrayUtilsTest` (ssg-md/port/test.conf) and kept here for every platform.
 *   Convention: every assertion is java's, in java's order; `Objects::isNull` is the function literal
 *     `i => Objects.isNull(i)`, which is the only difference.
 */
package ssg
package md
package util
package misc

import java.util.Objects
import java.util.function.Predicate

class ArrayUtilsIndexOfTest extends munit.FunSuite {

  private def ints: Array[Integer] = Array[Integer](
    1, // 0
    4, // 1
    1, // 2
    null, // 3
    3, // 4
    5, // 5
    null, // 6
    1, // 7
    2, // 8
    4, // 9
    3, // 10
    2, // 11
    0 // 12
  )

  private def is(n: Int): Predicate[Integer] = (i: Integer) => i != null && i.intValue == n

  private val isNull: Predicate[Integer] = (i: Integer) => Objects.isNull(i)

  test("test_indexOf") {
    assertEquals(ArrayUtils.indexOf[Integer](ints, is(6)), -1)
    assertEquals(ArrayUtils.indexOf[Integer](ints, is(1)), 0)
    assertEquals(ArrayUtils.indexOf[Integer](ints, 0, is(1)), 0)
    assertEquals(ArrayUtils.indexOf[Integer](ints, 1, is(1)), 2)
    assertEquals(ArrayUtils.indexOf[Integer](ints, 2, is(1)), 2)
    assertEquals(ArrayUtils.indexOf[Integer](ints, 2, is(1)), 2)
    assertEquals(ArrayUtils.indexOf[Integer](ints, 0, isNull), 3)
    assertEquals(ArrayUtils.indexOf[Integer](ints, 0, is(5)), 5)
    assertEquals(ArrayUtils.indexOf[Integer](ints, 0, is(0)), 12)
  }

  test("test_lastIndexOf") {
    assertEquals(ArrayUtils.lastIndexOf[Integer](ints, is(6)), -1)
    assertEquals(ArrayUtils.lastIndexOf[Integer](ints, is(1)), 7)
    assertEquals(ArrayUtils.lastIndexOf[Integer](ints, 7, is(1)), 7)
    assertEquals(ArrayUtils.lastIndexOf[Integer](ints, 6, is(1)), 2)
    assertEquals(ArrayUtils.lastIndexOf[Integer](ints, 3, is(1)), 2)
    assertEquals(ArrayUtils.lastIndexOf[Integer](ints, 2, is(1)), 2)
    assertEquals(ArrayUtils.lastIndexOf[Integer](ints, 1, is(1)), 0)
    assertEquals(ArrayUtils.lastIndexOf[Integer](ints, 1, 1, is(1)), -1)
    assertEquals(ArrayUtils.lastIndexOf[Integer](ints, 10, is(1)), 7)
    assertEquals(ArrayUtils.lastIndexOf[Integer](ints, 0, isNull), -1)
    assertEquals(ArrayUtils.lastIndexOf[Integer](ints, 5, isNull), 3)
    assertEquals(ArrayUtils.lastIndexOf[Integer](ints, 3, 5, isNull), 3)
    assertEquals(ArrayUtils.lastIndexOf[Integer](ints, 4, 5, isNull), -1)
    assertEquals(ArrayUtils.lastIndexOf[Integer](ints, 20, is(5)), 5)
    assertEquals(ArrayUtils.lastIndexOf[Integer](ints, 5, is(5)), 5)
    assertEquals(ArrayUtils.lastIndexOf[Integer](ints, 4, is(5)), -1)
    assertEquals(ArrayUtils.lastIndexOf[Integer](ints, 15, is(0)), 12)
    assertEquals(ArrayUtils.lastIndexOf[Integer](ints, 12, is(0)), 12)
    assertEquals(ArrayUtils.lastIndexOf[Integer](ints, 11, is(0)), -1)
  }
}
