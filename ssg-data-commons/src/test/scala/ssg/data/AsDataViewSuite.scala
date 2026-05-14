/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package data

import scala.collection.immutable.VectorMap

final case class Point(x: Int, y: Int) derives AsDataView

final case class Person(name: String, age: Int) derives AsDataView

final case class Nested(label: String, point: Point) derives AsDataView

enum Color derives AsDataView {
  case Red, Green, Blue
}

class AsDataViewSuite extends munit.FunSuite {

  test("AsDataView derives for Boolean") {
    val dv = AsDataView.derived[Boolean].asDataView(true)
    assertEquals(dv.asBoolean.get, true)
  }

  test("AsDataView derives for Int") {
    val dv = AsDataView.derived[Int].asDataView(42)
    assertEquals(dv.asInt.get, 42)
  }

  test("AsDataView derives for String") {
    val dv = AsDataView.derived[String].asDataView("hello")
    assertEquals(dv.asString.get, "hello")
  }

  test("AsDataView derives for case class") {
    val dv = Point(3, 4).asDataView
    val m  = dv.asMap.get
    assertEquals(m("x").asInt.get, 3)
    assertEquals(m("y").asInt.get, 4)
  }

  test("AsDataView derives for nested case class") {
    val dv = Nested("origin", Point(0, 0)).asDataView
    val m  = dv.asMap.get
    assertEquals(m("label").asString.get, "origin")
    val pointMap = m("point").asMap.get
    assertEquals(pointMap("x").asInt.get, 0)
    assertEquals(pointMap("y").asInt.get, 0)
  }

  test("AsDataView derives for enum singletons") {
    val dv = Color.Red.asDataView
    assertEquals(dv.asString.get, "Red")
  }

  test("AsDataView derives for Vector[Int]") {
    val dv = AsDataView.derived[Vector[Int]].asDataView(Vector(1, 2, 3))
    val v  = dv.asVector.get
    assertEquals(v.length, 3)
    assertEquals(v(0).asInt.get, 1)
    assertEquals(v(1).asInt.get, 2)
    assertEquals(v(2).asInt.get, 3)
  }

  test("AsDataView derives for List[String]") {
    val dv = AsDataView.derived[List[String]].asDataView(List("a", "b"))
    val v  = dv.asVector.get
    assertEquals(v.length, 2)
    assertEquals(v(0).asString.get, "a")
    assertEquals(v(1).asString.get, "b")
  }

  test("AsDataView derives for Option[Int]") {
    val inst   = AsDataView.derived[Option[Int]]
    val dvSome = inst.asDataView(Some(42))
    assertEquals(dvSome.asInt.get, 42)
    val dvNone = inst.asDataView(None)
    assert(dvNone.isNull)
  }

  test("AsDataView derives for Byte (widened to Short)") {
    val dv = AsDataView.derived[Byte].asDataView(7.toByte)
    assertEquals(dv.asShort.get, 7.toShort)
  }

  test("AsDataView derives for Char (converted to String)") {
    val dv = AsDataView.derived[Char].asDataView('Z')
    assertEquals(dv.asString.get, "Z")
  }

  test("AsDataView derives for Map[String, Int]") {
    val dv = AsDataView.derived[Map[String, Int]].asDataView(Map("x" -> 1, "y" -> 2))
    val m  = dv.asMap.get
    assertEquals(m("x").asInt.get, 1)
    assertEquals(m("y").asInt.get, 2)
  }

  test("AsDataView derives for Map[String, String]") {
    val dv = AsDataView.derived[Map[String, String]].asDataView(Map("a" -> "hello", "b" -> "world"))
    val m  = dv.asMap.get
    assertEquals(m("a").asString.get, "hello")
    assertEquals(m("b").asString.get, "world")
  }

  test("AsDataView derives for Java Bean") {
    val bean = new AsDataViewSuite.SimpleBean()
    bean.setName("Alice")
    bean.setAge(30)
    val dv = AsDataView.derived[AsDataViewSuite.SimpleBean].asDataView(bean)
    val m  = dv.asMap.get
    assertEquals(m("name").asString.get, "Alice")
    assertEquals(m("age").asInt.get, 30)
  }
}

object AsDataViewSuite {

  class SimpleBean {
    private var name_     : String = ""
    private var age_      : Int    = 0
    def getName:            String = name_
    def setName(n: String): Unit   = name_ = n
    def getAge:             Int    = age_
    def setAge(a:  Int):    Unit   = age_ = a
  }
}
