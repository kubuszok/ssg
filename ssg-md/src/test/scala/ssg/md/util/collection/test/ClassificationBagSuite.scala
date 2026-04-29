/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package collection
package test

import scala.language.implicitConversions

final class ClassificationBagSuite extends munit.FunSuite {

  test("testBasic") {
    val bag = new ClassificationBag[Class[?], Object]((value: Object) => value.getClass)

    var item: Object = null // @nowarn — test helper

    for (i <- 0 until 10) {
      item = Integer.valueOf(i)
      bag.add(item)
    }

    assertEquals(bag.containsCategory(classOf[Integer]), true)
    assertEquals(bag.getCategoryCount(classOf[Integer]), 10)

    assertEquals(bag.containsCategory(classOf[String]), false)
    assertEquals(bag.getCategoryCount(classOf[String]), 0)

    for (i <- 0 until 10) {
      item = String.valueOf(i)
      bag.add(item)
    }

    assertEquals(bag.containsCategory(classOf[String]), true)
    assertEquals(bag.getCategoryCount(classOf[String]), 10)

    // now we remove them
    for (i <- 0 until 10 by 2) {
      item = Integer.valueOf(i)
      bag.remove(item)
    }

    assertEquals(bag.containsCategory(classOf[Integer]), true)
    assertEquals(bag.getCategoryCount(classOf[Integer]), 5)

    assertEquals(bag.containsCategory(classOf[String]), true)
    assertEquals(bag.getCategoryCount(classOf[String]), 10)

    // now we remove them
    for (i <- 0 until 10 by 2) {
      item = String.valueOf(i)
      bag.remove(item)
    }

    assertEquals(bag.containsCategory(classOf[Integer]), true)
    assertEquals(bag.getCategoryCount(classOf[Integer]), 5)

    assertEquals(bag.containsCategory(classOf[String]), true)
    assertEquals(bag.getCategoryCount(classOf[String]), 5)

    // now we remove them
    for (i <- 1 until 10 by 2) {
      item = Integer.valueOf(i)
      bag.remove(item)
    }

    assertEquals(bag.containsCategory(classOf[Integer]), false)
    assertEquals(bag.getCategoryCount(classOf[Integer]), 0)

    assertEquals(bag.containsCategory(classOf[String]), true)
    assertEquals(bag.getCategoryCount(classOf[String]), 5)

    // now we remove them
    for (i <- 1 until 10 by 2) {
      item = String.valueOf(i)
      bag.remove(item)
    }

    assertEquals(bag.containsCategory(classOf[Integer]), false)
    assertEquals(bag.getCategoryCount(classOf[Integer]), 0)

    assertEquals(bag.containsCategory(classOf[String]), false)
    assertEquals(bag.getCategoryCount(classOf[String]), 0)
  }

  test("testInterleave") {
    val bag = new ClassificationBag[Class[?], Object]((value: Object) => value.getClass)

    var item: Object = null // @nowarn — test helper
    for (i <- 0 until 10) {
      item = Integer.valueOf(i)
      bag.add(item)
      item = String.valueOf(i)
      bag.add(item)
    }

    assertEquals(bag.containsCategory(classOf[Integer]), true)
    assertEquals(bag.getCategoryCount(classOf[Integer]), 10)

    assertEquals(bag.containsCategory(classOf[String]), true)
    assertEquals(bag.getCategoryCount(classOf[String]), 10)
  }
}
