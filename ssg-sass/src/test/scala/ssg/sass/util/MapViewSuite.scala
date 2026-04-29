/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package sass
package util

import scala.collection.mutable

final class MapViewSuite extends munit.FunSuite {

  test("LimitedMapView.safelist filters to allowed keys") {
    val map  = mutable.HashMap("a" -> 1, "b" -> 2, "c" -> 3)
    val view = LimitedMapView.safelist(map, Set("a", "c"))
    assertEquals(view.get("a"), Some(1))
    assertEquals(view.get("b"), None)
    assertEquals(view.get("c"), Some(3))
    assertEquals(view.size, 2)
  }

  test("LimitedMapView.blocklist hides blocked keys") {
    val map  = mutable.HashMap("a" -> 1, "b" -> 2, "c" -> 3)
    val view = LimitedMapView.blocklist(map, Set("b"))
    assertEquals(view.get("a"), Some(1))
    assertEquals(view.get("b"), None)
    assertEquals(view.get("c"), Some(3))
  }

  test("LimitedMapView.remove removes from underlying") {
    val map  = mutable.HashMap("a" -> 1, "b" -> 2)
    val view = LimitedMapView.safelist(map, Set("a", "b"))
    assertEquals(view.remove("a"), Some(1))
    assert(!map.contains("a"))
    assertEquals(view.remove("x"), None)
  }

  test("MergedMapView merges multiple maps with later precedence") {
    val map1   = mutable.HashMap("a" -> 1, "b" -> 2)
    val map2   = mutable.HashMap("b" -> 20, "c" -> 3)
    val merged = MergedMapView(Seq(map1, map2))
    assertEquals(merged.get("a"), Some(1))
    assertEquals(merged.get("b"), Some(20)) // map2 takes precedence
    assertEquals(merged.get("c"), Some(3))
    assertEquals(merged.size, 3)
  }

  test("MergedMapView update modifies owning map") {
    val map1   = mutable.HashMap("a" -> 1)
    val map2   = mutable.HashMap("b" -> 2)
    val merged = MergedMapView(Seq(map1, map2))
    merged.update("a", 10)
    assertEquals(map1("a"), 10)
  }

  test("MergedMapView rejects new keys") {
    val map1   = mutable.HashMap("a" -> 1)
    val merged = MergedMapView(Seq(map1))
    intercept[UnsupportedOperationException] {
      merged.update("z", 99)
    }
  }

  test("PrefixedMapView adds prefix to keys") {
    val map  = Map("color" -> "red", "size" -> "10px")
    val view = PrefixedMapView(map, "--my-")
    assertEquals(view.get("--my-color"), Some("red"))
    assertEquals(view.get("color"), None)
    assertEquals(view.size, 2)
    assert(view.iterator.map(_._1).toSet.contains("--my-color"))
  }

  test("UnprefixedMapView strips prefix from keys") {
    val map  = mutable.HashMap("--my-color" -> "red", "--my-size" -> "10px", "other" -> "ignored")
    val view = UnprefixedMapView(map, "--my-")
    assertEquals(view.get("color"), Some("red"))
    assertEquals(view.get("other"), None)
    assertEquals(view.size, 2)
  }

  test("UnprefixedMapView.remove removes with prefix") {
    val map  = mutable.HashMap("--my-color" -> "red")
    val view = UnprefixedMapView(map, "--my-")
    assertEquals(view.remove("color"), Some("red"))
    assert(!map.contains("--my-color"))
  }

  test("PublicMemberMapView hides private members") {
    val map  = Map("color" -> 1, "-private" -> 2, "_also_private" -> 3, "public" -> 4)
    val view = PublicMemberMapView(map)
    assertEquals(view.get("color"), Some(1))
    assertEquals(view.get("-private"), None)
    assertEquals(view.get("_also_private"), None)
    assertEquals(view.get("public"), Some(4))
    assertEquals(view.size, 2)
  }
}
