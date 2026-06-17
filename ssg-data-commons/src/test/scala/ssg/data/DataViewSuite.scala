/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package data

import lowlevel.Nullable

import scala.collection.immutable.VectorMap

class DataViewSuite extends munit.FunSuite {

  test("DataView wraps Boolean") {
    val dv = DataView(true)
    assertEquals(dv.asBoolean.get, true)
    assert(dv.asInt.isEmpty)
    assert(dv.asString.isEmpty)
    assert(!dv.isNull)
  }

  test("DataView wraps Short") {
    val dv = DataView(42.toShort)
    assertEquals(dv.asShort.get, 42.toShort)
    assert(dv.asString.isEmpty)
  }

  test("DataView wraps Int") {
    val dv = DataView(42)
    assertEquals(dv.asInt.get, 42)
    assert(dv.asString.isEmpty)
  }

  test("DataView wraps Long") {
    val dv = DataView(42L)
    assertEquals(dv.asLong.get, 42L)
    assert(dv.asString.isEmpty)
  }

  test("DataView wraps Float") {
    val dv = DataView(3.14f)
    assertEquals(dv.asFloat.get, 3.14f)
    assert(dv.asString.isEmpty)
  }

  test("DataView wraps Double") {
    val dv = DataView(3.14)
    assertEquals(dv.asDouble.get, 3.14)
    assert(dv.asString.isEmpty)
  }

  test("DataView wraps String") {
    val dv = DataView("hello")
    assertEquals(dv.asString.get, "hello")
    assert(dv.asInt.isEmpty)
  }

  test("DataView wraps BigDecimal") {
    val bd = new java.math.BigDecimal("123.456")
    val dv = DataView(bd)
    assertEquals(dv.asBigDecimal.get, bd)
    assert(dv.asString.isEmpty)
  }

  test("DataView wraps Vector") {
    val inner = Vector(DataView(1), DataView(2), DataView(3))
    val dv    = DataView(inner)
    val v     = dv.asVector.get
    assertEquals(v.length, 3)
    assertEquals(v(0).asInt.get, 1)
    assertEquals(v(1).asInt.get, 2)
    assertEquals(v(2).asInt.get, 3)
  }

  test("DataView wraps VectorMap") {
    val map = VectorMap("a" -> DataView(1), "b" -> DataView("hello"))
    val dv  = DataView(map)
    val m   = dv.asMap.get
    assertEquals(m("a").asInt.get, 1)
    assertEquals(m("b").asString.get, "hello")
  }

  test("DataView.nil is null") {
    val dv = DataView.nil
    assert(dv.isNull)
    assert(dv.asBoolean.isEmpty)
    assert(dv.asInt.isEmpty)
    assert(dv.asString.isEmpty)
    assert(dv.asVector.isEmpty)
    assert(dv.asMap.isEmpty)
  }

  test("DataView lazy evaluation") {
    var evaluated = false
    val dv        = DataView {
      evaluated = true
      42
    }
    assert(!evaluated)
    assertEquals(dv.asInt.get, 42)
    assert(evaluated)
  }

  test("DataView lazy evaluation happens only once") {
    var count = 0
    val dv    = DataView {
      count += 1
      "hello"
    }
    assertEquals(dv.asString.get, "hello")
    assertEquals(dv.asString.get, "hello")
    assertEquals(count, 1)
  }

  test("deepMerge overlays a subset of keys (present-keys-win)") {
    val base = DataView.from(
      VectorMap[String, DataView]("a" -> DataView("base-a"), "b" -> DataView("base-b"))
    )
    val overlay = DataView.from(VectorMap[String, DataView]("a" -> DataView("over-a")))
    val merged  = DataView.deepMerge(base, overlay).asMap.get
    assertEquals(merged("a").asString.get, "over-a")
    assertEquals(merged("b").asString.get, "base-b")
  }

  test("deepMerge preserves a key absent in overlay") {
    val base    = DataView.from(VectorMap[String, DataView]("keep" -> DataView(1)))
    val overlay = DataView.from(VectorMap[String, DataView]("other" -> DataView(2)))
    val merged  = DataView.deepMerge(base, overlay).asMap.get
    assertEquals(merged("keep").asInt.get, 1)
    assertEquals(merged("other").asInt.get, 2)
  }

  test("deepMerge recurses into nested maps") {
    val base = DataView.from(
      VectorMap[String, DataView](
        "nested" -> DataView.from(
          VectorMap[String, DataView]("x" -> DataView(1), "y" -> DataView(2))
        )
      )
    )
    val overlay = DataView.from(
      VectorMap[String, DataView](
        "nested" -> DataView.from(VectorMap[String, DataView]("y" -> DataView(99)))
      )
    )
    val nested = DataView.deepMerge(base, overlay).asMap.get("nested").asMap.get
    assertEquals(nested("x").asInt.get, 1)
    assertEquals(nested("y").asInt.get, 99)
  }

  test("deepMerge replaces a base map with an overlay scalar") {
    val base    = DataView.from(VectorMap[String, DataView]("k" -> DataView.from(VectorMap.empty[String, DataView])))
    val overlay = DataView.from(VectorMap[String, DataView]("k" -> DataView("scalar")))
    val merged  = DataView.deepMerge(base, overlay).asMap.get
    assertEquals(merged("k").asString.get, "scalar")
  }

  test("deepMerge replaces a base scalar with an overlay map") {
    val base    = DataView.from(VectorMap[String, DataView]("k" -> DataView("scalar")))
    val overlay = DataView.from(VectorMap[String, DataView]("k" -> DataView.from(VectorMap[String, DataView]("inner" -> DataView(1)))))
    val merged  = DataView.deepMerge(base, overlay).asMap.get
    assertEquals(merged("k").asMap.get("inner").asInt.get, 1)
  }

  test("deepMerge with non-map overlay replaces base wholesale") {
    val base    = DataView.from(VectorMap[String, DataView]("a" -> DataView(1)))
    val overlay = DataView("scalar")
    assertEquals(DataView.deepMerge(base, overlay).asString.get, "scalar")
  }
}
