/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package liquid

import ssg.liquid.parser.Flavor

import java.util.{ ArrayList => JArrayList, HashMap => JHashMap }

final class WhereFilterSuite extends munit.FunSuite {

  // ---------------------------------------------------------------------------
  // Helper
  // ---------------------------------------------------------------------------

  private val jekyllParser: TemplateParser =
    new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).build()

  private val liquidParser: TemplateParser =
    new TemplateParser.Builder().withFlavor(Flavor.LIQP).build()

  private def makeItem(pairs: (String, Any)*): JHashMap[String, Any] = {
    val map = new JHashMap[String, Any]()
    pairs.foreach { case (k, v) => map.put(k, v) }
    map
  }

  private def makeList(items: Any*): JArrayList[Any] = {
    val list = new JArrayList[Any]()
    items.foreach(list.add)
    list
  }

  // ---------------------------------------------------------------------------
  // Jekyll-style where (2-param: property, value)
  // ---------------------------------------------------------------------------

  test("jekyll where: filter array of maps by property value") {
    val items = makeList(
      makeItem("color" -> "red", "name" -> "apple"),
      makeItem("color" -> "blue", "name" -> "berry"),
      makeItem("color" -> "red", "name" -> "cherry")
    )
    val vars = new JHashMap[String, Any]()
    vars.put("items", items)
    val result = jekyllParser.parse("{% assign reds = items | where: 'color', 'red' %}{{ reds | map: 'name' | join: ',' }}").render(vars)
    assertEquals(result, "apple,cherry")
  }

  test("jekyll where: no matches returns empty") {
    val items = makeList(
      makeItem("color" -> "red", "name" -> "apple"),
      makeItem("color" -> "blue", "name" -> "berry")
    )
    val vars = new JHashMap[String, Any]()
    vars.put("items", items)
    val result = jekyllParser.parse("{% assign greens = items | where: 'color', 'green' %}{{ greens | size }}").render(vars)
    assertEquals(result, "0")
  }

  test("jekyll where: nil target matches items with nil property") {
    val items = makeList(
      makeItem("color" -> "red", "name" -> "apple"),
      makeItem("name" -> "mystery") // no "color" key
    )
    val vars = new JHashMap[String, Any]()
    vars.put("items", items)
    val result = jekyllParser.parse("{% assign nils = items | where: 'color', nil %}{{ nils | size }}").render(vars)
    assertEquals(result, "1")
  }

  test("jekyll where: missing property returns nil for item") {
    val items = makeList(
      makeItem("name" -> "apple"),
      makeItem("color" -> "blue", "name" -> "berry")
    )
    val vars = new JHashMap[String, Any]()
    vars.put("items", items)
    val result = jekyllParser.parse("{% assign blues = items | where: 'color', 'blue' %}{{ blues | map: 'name' | join: ',' }}").render(vars)
    assertEquals(result, "berry")
  }

  test("jekyll where: numeric coercion via parseSortInput") {
    // Jekyll's where uses parseSortInput, which parses numeric strings to Double
    val items = makeList(
      makeItem("score" -> "42", "name" -> "high"),
      makeItem("score" -> "10", "name" -> "low"),
      makeItem("score" -> "42", "name" -> "also-high")
    )
    val vars = new JHashMap[String, Any]()
    vars.put("items", items)
    // The string "42" is coerced to Double 42.0, so comparing with string "42" won't match
    // because parseSortInput turns "42" into 42.0, and comparePropertyVsTarget calls asString
    // on the target. So "42" matches the string representation of 42.0 which is "42.0"
    // Actually: the target value "42" stays as string, but the property is coerced to Double 42.0.
    // comparePropertyVsTarget does: strTarget.equals(itemProperty) for String, and for non-string
    // it converts each array element to string. Since 42.0.toString = "42.0" != "42", this will
    // NOT match. Let's test with the actual numeric value:
    val result = jekyllParser.parse("{% assign found = items | where: 'score', '42' %}{{ found | size }}").render(vars)
    // parseSortInput coerces "42" -> 42.0, then comparePropertyVsTarget:
    //   target = "42" (string), property = 42.0 (Double)
    //   isString(42.0) = false, so goes to array branch: asString(42.0) = "42.0", which != "42"
    // So the match fails. This tests that numeric coercion is actually happening.
    assertEquals(result, "0")
  }

  test("jekyll where: string property matches string target") {
    val items = makeList(
      makeItem("status" -> "draft", "title" -> "Post A"),
      makeItem("status" -> "published", "title" -> "Post B"),
      makeItem("status" -> "draft", "title" -> "Post C")
    )
    val vars = new JHashMap[String, Any]()
    vars.put("items", items)
    val result = jekyllParser.parse("{% assign drafts = items | where: 'status', 'draft' %}{{ drafts | map: 'title' | join: ',' }}").render(vars)
    assertEquals(result, "Post A,Post C")
  }

  test("jekyll where: null input returns empty string") {
    val vars = new JHashMap[String, Any]()
    val result = jekyllParser.parse("{% assign res = nothing | where: 'key', 'val' %}{{ res }}").render(vars)
    assertEquals(result, "")
  }

  test("jekyll where: single-item array") {
    val items = makeList(
      makeItem("type" -> "fruit", "name" -> "banana")
    )
    val vars = new JHashMap[String, Any]()
    vars.put("items", items)
    val result = jekyllParser.parse("{% assign fruits = items | where: 'type', 'fruit' %}{{ fruits | map: 'name' | join: ',' }}").render(vars)
    assertEquals(result, "banana")
  }

  test("jekyll where: empty array returns empty") {
    val items = makeList()
    val vars  = new JHashMap[String, Any]()
    vars.put("items", items)
    val result = jekyllParser.parse("{% assign res = items | where: 'key', 'val' %}{{ res | size }}").render(vars)
    assertEquals(result, "0")
  }

  // ---------------------------------------------------------------------------
  // Liquid-style where (1-param: truthy check)
  // ---------------------------------------------------------------------------

  test("liquid where: 1-param truthy check filters by presence") {
    val items = makeList(
      makeItem("featured" -> java.lang.Boolean.TRUE, "name" -> "A"),
      makeItem("featured" -> java.lang.Boolean.FALSE, "name" -> "B"),
      makeItem("featured" -> java.lang.Boolean.TRUE, "name" -> "C")
    )
    val vars = new JHashMap[String, Any]()
    vars.put("items", items)
    val result = liquidParser.parse("{% assign featured = items | where: 'featured' %}{{ featured | map: 'name' | join: ',' }}").render(vars)
    assertEquals(result, "A,C")
  }

  test("liquid where: 1-param with nil property excludes item") {
    val items = makeList(
      makeItem("tag" -> "a", "name" -> "X"),
      makeItem("name" -> "Y") // no "tag" key
    )
    val vars = new JHashMap[String, Any]()
    vars.put("items", items)
    val result = liquidParser.parse("{% assign tagged = items | where: 'tag' %}{{ tagged | map: 'name' | join: ',' }}").render(vars)
    assertEquals(result, "X")
  }

  // ---------------------------------------------------------------------------
  // Liquid-style where (2-param: equality check)
  // ---------------------------------------------------------------------------

  test("liquid where: 2-param equality check") {
    val items = makeList(
      makeItem("color" -> "red", "name" -> "apple"),
      makeItem("color" -> "blue", "name" -> "berry"),
      makeItem("color" -> "red", "name" -> "cherry")
    )
    val vars = new JHashMap[String, Any]()
    vars.put("items", items)
    val result = liquidParser.parse("{% assign reds = items | where: 'color', 'red' %}{{ reds | map: 'name' | join: ',' }}").render(vars)
    assertEquals(result, "apple,cherry")
  }

  test("liquid where: 2-param with numeric equality") {
    val items = makeList(
      makeItem("count" -> java.lang.Integer.valueOf(3), "name" -> "A"),
      makeItem("count" -> java.lang.Integer.valueOf(5), "name" -> "B"),
      makeItem("count" -> java.lang.Integer.valueOf(3), "name" -> "C")
    )
    val vars = new JHashMap[String, Any]()
    vars.put("items", items)
    val result = liquidParser.parse("{% assign threes = items | where: 'count', 3 %}{{ threes | map: 'name' | join: ',' }}").render(vars)
    assertEquals(result, "A,C")
  }

  test("liquid where: empty array returns empty array") {
    val items = makeList()
    val vars  = new JHashMap[String, Any]()
    vars.put("items", items)
    val result = liquidParser.parse("{% assign res = items | where: 'key' %}{{ res | size }}").render(vars)
    assertEquals(result, "0")
  }

  // ---------------------------------------------------------------------------
  // Map input (Jekyll-style)
  // ---------------------------------------------------------------------------

  test("jekyll where: map values treated as collection") {
    val map = new JHashMap[String, Any]()
    val item1 = makeItem("color" -> "red", "name" -> "a")
    val item2 = makeItem("color" -> "blue", "name" -> "b")
    map.put("x", item1)
    map.put("y", item2)
    val vars = new JHashMap[String, Any]()
    vars.put("data", map)
    val result = jekyllParser.parse("{% assign reds = data | where: 'color', 'red' %}{{ reds | map: 'name' | join: ',' }}").render(vars)
    assertEquals(result, "a")
  }

  // ---------------------------------------------------------------------------
  // Liquid-style where: map wrapping
  // ---------------------------------------------------------------------------

  test("liquid where: map input is wrapped in array") {
    val map = makeItem("status" -> "active", "name" -> "item1")
    val vars = new JHashMap[String, Any]()
    vars.put("data", map)
    val result = liquidParser.parse("{% assign res = data | where: 'status', 'active' %}{{ res | map: 'name' | join: ',' }}").render(vars)
    assertEquals(result, "item1")
  }

  test("liquid where: map with non-matching value returns empty") {
    val map = makeItem("status" -> "inactive", "name" -> "item1")
    val vars = new JHashMap[String, Any]()
    vars.put("data", map)
    val result = liquidParser.parse("{% assign res = data | where: 'status', 'active' %}{{ res | size }}").render(vars)
    assertEquals(result, "0")
  }
}
