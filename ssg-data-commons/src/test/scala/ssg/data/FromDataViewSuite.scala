/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package data

import lowlevel.Nullable

import scala.collection.immutable.VectorMap

final case class Coord(x: Int, y: Int) derives AsDataView, FromDataView

final case class WithNick(name: String, nickname: Nullable[String]) derives AsDataView, FromDataView

class FromDataViewSuite extends munit.FunSuite {

  test("FromDataView derives for Int") {
    val dv     = DataView(42)
    val result = FromDataView.derived[Int].fromDataView(dv)
    assertEquals(result.get, 42)
  }

  test("FromDataView derives for String") {
    val dv     = DataView("hello")
    val result = FromDataView.derived[String].fromDataView(dv)
    assertEquals(result.get, "hello")
  }

  test("FromDataView derives for Boolean") {
    val dv     = DataView(true)
    val result = FromDataView.derived[Boolean].fromDataView(dv)
    assertEquals(result.get, true)
  }

  test("FromDataView returns empty for type mismatch") {
    val dv     = DataView("hello")
    val result = FromDataView.derived[Int].fromDataView(dv)
    assert(result.isEmpty)
  }

  test("FromDataView derives for case class") {
    val dv = DataView(
      VectorMap[String, DataView](
        "x" -> DataView(3),
        "y" -> DataView(4)
      )
    )
    val result = FromDataView.derived[Coord].fromDataView(dv)
    assertEquals(result.get, Coord(3, 4))
  }

  test("round-trip: case class to DataView and back") {
    val original = Coord(10, 20)
    val dv       = original.asDataView
    val result   = FromDataView.derived[Coord].fromDataView(dv)
    assertEquals(result.get, original)
  }

  test("FromDataView returns empty for DataView.nil") {
    val result = FromDataView.derived[Int].fromDataView(DataView.nil)
    assert(result.isEmpty)
  }

  // FromDataView[Nullable[A]] returns the FromDataView wrapper Nullable[Nullable[A]];
  // the field-level Nullable[A] is obtained with `.flatten` (the same unwrap the
  // derivation uses for Nullable-typed case-class fields — `.get` cannot bind a
  // nested empty in the allocation-free NestedNone encoding).
  test("FromDataView derives for Nullable[String] (present)") {
    val result = FromDataView.derived[Nullable[String]].fromDataView(DataView("hi")).flatten
    assertEquals(result.get, "hi")
  }

  test("FromDataView derives for Nullable[String] (absent yields empty)") {
    val result = FromDataView.derived[Nullable[String]].fromDataView(DataView.nil).flatten
    assert(result.isEmpty)
  }

  test("FromDataView derives for Option[Int] (present)") {
    val result = FromDataView.derived[Option[Int]].fromDataView(DataView(7)).get
    assertEquals(result, Some(7))
  }

  test("FromDataView derives for Option[Int] (absent yields None)") {
    val result = FromDataView.derived[Option[Int]].fromDataView(DataView.nil).get
    assertEquals(result, None)
  }

  test("round-trip: Nullable[String] present through As and From") {
    val dv     = AsDataView.derived[Nullable[String]].asDataView(Nullable("x"))
    val result = FromDataView.derived[Nullable[String]].fromDataView(dv).flatten
    assertEquals(result.get, "x")
  }

  test("round-trip: Nullable[String] empty through As and From") {
    val dv     = AsDataView.derived[Nullable[String]].asDataView(Nullable.empty[String])
    val result = FromDataView.derived[Nullable[String]].fromDataView(dv).flatten
    assert(result.isEmpty)
  }

  test("round-trip: Option[Int] through As and From") {
    val dvSome     = AsDataView.derived[Option[Int]].asDataView(Some(5))
    val resultSome = FromDataView.derived[Option[Int]].fromDataView(dvSome).get
    assertEquals(resultSome, Some(5))
    val dvNone     = AsDataView.derived[Option[Int]].asDataView(None)
    val resultNone = FromDataView.derived[Option[Int]].fromDataView(dvNone).get
    assertEquals(resultNone, None)
  }

  test("round-trip: case class with Nullable field (present)") {
    val original = WithNick("alice", Nullable("al"))
    val dv       = original.asDataView
    val result   = FromDataView.derived[WithNick].fromDataView(dv).get
    assertEquals(result.name, "alice")
    assertEquals(result.nickname.get, "al")
  }

  test("FromDataView derives for Map[String, Int]") {
    val dv     = DataView.from(VectorMap[String, DataView]("a" -> DataView(1), "b" -> DataView(2)))
    val result = FromDataView.derived[Map[String, Int]].fromDataView(dv).get
    assertEquals(result, Map("a" -> 1, "b" -> 2))
  }

  test("round-trip: Map[String, String] through As and From") {
    val original = Map("x" -> "1", "y" -> "2")
    val dv       = AsDataView.derived[Map[String, String]].asDataView(original)
    val result   = FromDataView.derived[Map[String, String]].fromDataView(dv).get
    assertEquals(result, original)
  }

  test("round-trip: case class with Nullable field (empty)") {
    val original = WithNick("bob", Nullable.empty[String])
    val dv       = original.asDataView
    val result   = FromDataView.derived[WithNick].fromDataView(dv).get
    assertEquals(result.name, "bob")
    assert(result.nickname.isEmpty)
  }
}
