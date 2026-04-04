/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package liquid

import java.util.HashMap
import java.util.ArrayList

final class FilterArraySuite extends munit.FunSuite {

  // -- first --

  test("first: returns the first element of an array") {
    val vars   = new HashMap[String, Any]()
    val values = new ArrayList[Any]()
    values.add("Mu")
    values.add("foo")
    values.add("bar")
    vars.put("values", values)
    assertEquals(Template.parse("{{values | first}}").render(vars), "Mu")
  }

  // -- last --

  test("last: returns the last element of an array") {
    val vars   = new HashMap[String, Any]()
    val values = new ArrayList[Any]()
    values.add("Mu")
    values.add("foo")
    values.add("bar")
    vars.put("values", values)
    assertEquals(Template.parse("{{values | last}}").render(vars), "bar")
  }

  // -- join --

  test("join: joins array with default separator (space)") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add("x")
    array.add("y")
    array.add("z")
    vars.put("array", array)
    assertEquals(Template.parse("{{ array | join }}").render(vars), "x y z")
  }

  test("join: joins array with custom separator") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add("x")
    array.add("y")
    array.add("z")
    vars.put("array", array)
    assertEquals(Template.parse("{{ array | join:'@@@' }}").render(vars), "x@@@y@@@z")
  }

  // -- size --

  test("size: returns the size of an array") {
    val vars = new HashMap[String, Any]()
    val n    = new ArrayList[Any]()
    n.add(java.lang.Integer.valueOf(1))
    n.add(java.lang.Integer.valueOf(2))
    n.add(java.lang.Integer.valueOf(3))
    n.add(java.lang.Integer.valueOf(4))
    n.add(java.lang.Integer.valueOf(5))
    vars.put("n", n)
    assertEquals(Template.parse("{{ n | size }}").render(vars), "5")
  }

  test("size: returns the size of a string") {
    assertEquals(Template.parse("{{ 'hello' | size }}").render(), "5")
  }

  test("size: returns 0 for nil") {
    assertEquals(Template.parse("{{ nil | size }}").render(), "0")
  }

  // -- reverse --

  test("reverse: reverses an array") {
    val vars   = new HashMap[String, Any]()
    val values = new ArrayList[Any]()
    values.add(java.lang.Integer.valueOf(1))
    values.add(java.lang.Integer.valueOf(2))
    values.add(java.lang.Integer.valueOf(3))
    vars.put("values", values)
    assertEquals(Template.parse("{{ values | reverse | join:',' }}").render(vars), "3,2,1")
  }

  // -- sort --

  test("sort: sorts an array of numbers") {
    val vars    = new HashMap[String, Any]()
    val numbers = new ArrayList[Any]()
    numbers.add(java.lang.Integer.valueOf(2))
    numbers.add(java.lang.Integer.valueOf(13))
    numbers.add(java.lang.Integer.valueOf(1))
    vars.put("numbers", numbers)
    assertEquals(Template.parse("{{ numbers | sort | join:',' }}").render(vars), "1,2,13")
  }

  // -- sort_natural --

  test("sort_natural: case-insensitive sort") {
    val vars  = new HashMap[String, Any]()
    val words = new ArrayList[Any]()
    words.add("Banana")
    words.add("apple")
    words.add("Cherry")
    vars.put("words", words)
    assertEquals(Template.parse("{{ words | sort_natural | join:',' }}").render(vars), "apple,Banana,Cherry")
  }

  // -- uniq --

  test("uniq: removes duplicate elements") {
    val vars = new HashMap[String, Any]()
    val x    = new ArrayList[Any]()
    x.add(java.lang.Integer.valueOf(1))
    x.add(java.lang.Integer.valueOf(1))
    x.add(java.lang.Integer.valueOf(3))
    x.add(java.lang.Integer.valueOf(4))
    x.add(java.lang.Integer.valueOf(3))
    x.add(java.lang.Integer.valueOf(2))
    vars.put("x", x)
    assertEquals(Template.parse("{{ x | uniq | join:',' }}").render(vars), "1,3,4,2")
  }

  // -- compact --

  test("compact: removes nil values from an array") {
    val vars  = new HashMap[String, Any]()
    val items = new ArrayList[Any]()
    items.add("a")
    items.add(null)
    items.add("b")
    items.add(null)
    items.add("c")
    vars.put("items", items)
    assertEquals(Template.parse("{{ items | compact | join:',' }}").render(vars), "a,b,c")
  }

  // -- concat --

  test("concat: concatenates two arrays") {
    val vars = new HashMap[String, Any]()
    val a    = new ArrayList[Any]()
    a.add(java.lang.Integer.valueOf(1))
    a.add(java.lang.Integer.valueOf(2))
    val b = new ArrayList[Any]()
    b.add(java.lang.Integer.valueOf(3))
    b.add(java.lang.Integer.valueOf(4))
    vars.put("a", a)
    vars.put("b", b)
    assertEquals(Template.parse("{{ a | concat: b | join:',' }}").render(vars), "1,2,3,4")
  }

  // -- slice --

  test("slice: extracts a substring with positive offset") {
    assertEquals(Template.parse("{{ 'foobar' | slice: 1, 3 }}").render(), "oob")
  }

  test("slice: extracts a substring with negative offset") {
    assertEquals(Template.parse("{{ 'foobar' | slice: -2, 2 }}").render(), "ar")
  }

  // -- map --

  test("map: extracts a property from array of maps") {
    val vars     = new HashMap[String, Any]()
    val products = new ArrayList[Any]()
    val p1       = new HashMap[String, Any]()
    p1.put("title", "Shoes")
    val p2 = new HashMap[String, Any]()
    p2.put("title", "Shirt")
    val p3 = new HashMap[String, Any]()
    p3.put("title", "Pants")
    products.add(p1)
    products.add(p2)
    products.add(p3)
    vars.put("products", products)
    assertEquals(Template.parse("{{ products | map:'title' | join:', ' }}").render(vars), "Shoes, Shirt, Pants")
  }

  // -- pop --

  test("pop: removes last element from array") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add("a")
    array.add("b")
    array.add("c")
    vars.put("array", array)
    assertEquals(
      Template.parse("{% assign popped = array | pop %}{{ popped | join:',' }}").render(vars),
      "a,b"
    )
  }

  // -- push --

  test("push: adds element to end of array") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add("a")
    array.add("b")
    vars.put("array", array)
    assertEquals(
      Template.parse("{% assign pushed = array | push: 'c' %}{{ pushed | join:',' }}").render(vars),
      "a,b,c"
    )
  }

  // -- shift --

  test("shift: removes first element from array") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add("a")
    array.add("b")
    array.add("c")
    vars.put("array", array)
    assertEquals(
      Template.parse("{% assign shifted = array | shift %}{{ shifted | join:',' }}").render(vars),
      "b,c"
    )
  }

  // -- unshift --

  test("unshift: prepends element to array") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add("b")
    array.add("c")
    vars.put("array", array)
    assertEquals(
      Template.parse("{% assign result = array | unshift: 'a' %}{{ result | join:',' }}").render(vars),
      "a,b,c"
    )
  }

  // -- default --

  test("default: returns fallback when variable is nil") {
    assertEquals(Template.parse("{{ a | default: 'fallback' }}").render(), "fallback")
  }

  test("default: returns value when variable is set") {
    val vars = new HashMap[String, Any]()
    vars.put("a", "foo")
    assertEquals(Template.parse("{{ a | default: 'fallback' }}").render(vars), "foo")
  }

  test("default: returns fallback for empty string") {
    val vars = new HashMap[String, Any]()
    vars.put("a", "")
    assertEquals(Template.parse("{{ a | default: 'fallback' }}").render(vars), "fallback")
  }

  test("default: returns fallback for false") {
    val vars = new HashMap[String, Any]()
    vars.put("a", java.lang.Boolean.FALSE)
    assertEquals(Template.parse("{{ a | default: 'fallback' }}").render(vars), "fallback")
  }

  // -- where --

  test("where: filters array of maps by property") {
    val vars  = new HashMap[String, Any]()
    val items = new ArrayList[Any]()
    val item1 = new HashMap[String, Any]()
    item1.put("color", "red")
    item1.put("name", "apple")
    val item2 = new HashMap[String, Any]()
    item2.put("color", "blue")
    item2.put("name", "berry")
    val item3 = new HashMap[String, Any]()
    item3.put("color", "red")
    item3.put("name", "cherry")
    items.add(item1)
    items.add(item2)
    items.add(item3)
    vars.put("items", items)
    assertEquals(
      Template.parse("{% assign reds = items | where: 'color', 'red' %}{{ reds | size }}").render(vars),
      "2"
    )
  }

  // -- where_exp (Jekyll) --

  test("where_exp: filters array by expression") {
    val parser = new TemplateParser.Builder().withFlavor(ssg.liquid.parser.Flavor.JEKYLL).build()
    val vars   = new HashMap[String, Any]()
    val items  = new ArrayList[Any]()
    val item1  = new HashMap[String, Any]()
    item1.put("color", "red")
    val item2 = new HashMap[String, Any]()
    item2.put("color", "blue")
    val item3 = new HashMap[String, Any]()
    item3.put("color", "red")
    items.add(item1)
    items.add(item2)
    items.add(item3)
    vars.put("items", items)
    assertEquals(
      parser.parse("{% assign reds = items | where_exp: 'item', 'item.color == \"red\"' %}{{ reds | size }}").render(vars),
      "2"
    )
  }

  test("where_exp: filters numbers with comparison") {
    val parser = new TemplateParser.Builder().withFlavor(ssg.liquid.parser.Flavor.JEKYLL).build()
    val vars   = new HashMap[String, Any]()
    val items  = new ArrayList[Any]()
    items.add(java.lang.Integer.valueOf(1))
    items.add(java.lang.Integer.valueOf(2))
    items.add(java.lang.Integer.valueOf(3))
    items.add(java.lang.Integer.valueOf(4))
    items.add(java.lang.Integer.valueOf(5))
    vars.put("items", items)
    assertEquals(
      parser.parse("{% assign big = items | where_exp: 'n', 'n >= 3' %}{{ big | join: ',' }}").render(vars),
      "3,4,5"
    )
  }
}
