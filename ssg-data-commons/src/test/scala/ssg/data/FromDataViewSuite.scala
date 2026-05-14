/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package data

import ssg.commons.Nullable

import scala.collection.immutable.VectorMap

final case class Coord(x: Int, y: Int) derives AsDataView, FromDataView

class FromDataViewSuite extends munit.FunSuite {

  test("FromDataView derives for Int") {
    val dv = DataView(42)
    val result = FromDataView.derived[Int].fromDataView(dv)
    assertEquals(result.get, 42)
  }

  test("FromDataView derives for String") {
    val dv = DataView("hello")
    val result = FromDataView.derived[String].fromDataView(dv)
    assertEquals(result.get, "hello")
  }

  test("FromDataView derives for Boolean") {
    val dv = DataView(true)
    val result = FromDataView.derived[Boolean].fromDataView(dv)
    assertEquals(result.get, true)
  }

  test("FromDataView returns empty for type mismatch") {
    val dv = DataView("hello")
    val result = FromDataView.derived[Int].fromDataView(dv)
    assert(result.isEmpty)
  }

  test("FromDataView derives for case class") {
    val dv = DataView(VectorMap[String, DataView](
      "x" -> DataView(3),
      "y" -> DataView(4)
    ))
    val result = FromDataView.derived[Coord].fromDataView(dv)
    assertEquals(result.get, Coord(3, 4))
  }

  test("round-trip: case class to DataView and back") {
    val original = Coord(10, 20)
    val dv = original.asDataView
    val result = FromDataView.derived[Coord].fromDataView(dv)
    assertEquals(result.get, original)
  }

  test("FromDataView returns empty for DataView.nil") {
    val result = FromDataView.derived[Int].fromDataView(DataView.nil)
    assert(result.isEmpty)
  }
}
