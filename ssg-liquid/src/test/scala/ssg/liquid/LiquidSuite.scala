/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package liquid

import java.util.HashMap

final class LiquidSuite extends munit.FunSuite {

  test("ssg-liquid module loads") {
    assertEquals(Version, "0.1.0-SNAPSHOT")
  }

  test("parse and render plain text") {
    val template = Template.parse("Hello, world!")
    assertEquals(template.render(), "Hello, world!")
  }

  test("parse and render variable output") {
    val vars = new HashMap[String, Any]()
    vars.put("name", "Liquid")
    val template = Template.parse("Hello, {{ name }}!")
    assertEquals(template.render(vars), "Hello, Liquid!")
  }

  test("parse and render with filter") {
    val vars = new HashMap[String, Any]()
    vars.put("name", "world")
    val template = Template.parse("{{ name | upcase }}")
    assertEquals(template.render(vars), "WORLD")
  }

  test("parse and render if tag") {
    val vars = new HashMap[String, Any]()
    vars.put("show", java.lang.Boolean.TRUE)
    val template = Template.parse("{% if show %}visible{% endif %}")
    assertEquals(template.render(vars), "visible")
  }

  test("parse and render if/else tag") {
    val vars = new HashMap[String, Any]()
    vars.put("show", java.lang.Boolean.FALSE)
    val template = Template.parse("{% if show %}yes{% else %}no{% endif %}")
    assertEquals(template.render(vars), "no")
  }

  test("parse and render for loop") {
    val vars  = new HashMap[String, Any]()
    val items = new java.util.ArrayList[Any]()
    items.add("a")
    items.add("b")
    items.add("c")
    vars.put("items", items)
    val template = Template.parse("{% for item in items %}{{ item }}{% endfor %}")
    assertEquals(template.render(vars), "abc")
  }

  test("parse and render assign tag") {
    val template = Template.parse("{% assign x = 'hello' %}{{ x }}")
    assertEquals(template.render(), "hello")
  }

  test("parse and render multiple filters") {
    val vars = new HashMap[String, Any]()
    vars.put("msg", "hello world")
    val template = Template.parse("{{ msg | upcase | truncate: 5 }}")
    assertEquals(template.render(vars), "HE...")
  }

  test("parse and render nested property access") {
    val vars = new HashMap[String, Any]()
    val user = new HashMap[String, Any]()
    user.put("name", "Alice")
    vars.put("user", user)
    val template = Template.parse("{{ user.name }}")
    assertEquals(template.render(vars), "Alice")
  }

  test("parse and render unless tag") {
    val vars = new HashMap[String, Any]()
    vars.put("hidden", java.lang.Boolean.FALSE)
    val template = Template.parse("{% unless hidden %}shown{% endunless %}")
    assertEquals(template.render(vars), "shown")
  }

  test("parse and render case/when tag") {
    val vars = new HashMap[String, Any]()
    vars.put("color", "blue")
    val template = Template.parse("{% case color %}{% when 'red' %}R{% when 'blue' %}B{% else %}?{% endcase %}")
    assertEquals(template.render(vars), "B")
  }

  test("parse and render comment tag") {
    val template = Template.parse("a{% comment %}hidden{% endcomment %}b")
    assertEquals(template.render(), "ab")
  }

  test("parse and render capture tag") {
    val template = Template.parse("{% capture greeting %}hello{% endcapture %}{{ greeting }}")
    assertEquals(template.render(), "hello")
  }

  test("parse and render increment/decrement") {
    val template = Template.parse("{% increment x %}{% increment x %}{% increment x %}")
    assertEquals(template.render(), "012")
  }

  test("parse and render comparison operators") {
    val vars = new HashMap[String, Any]()
    vars.put("a", java.lang.Integer.valueOf(5))
    vars.put("b", java.lang.Integer.valueOf(3))
    val template = Template.parse("{% if a > b %}yes{% endif %}")
    assertEquals(template.render(vars), "yes")
  }

  test("parse and render contains operator") {
    val vars = new HashMap[String, Any]()
    vars.put("msg", "hello world")
    val template = Template.parse("{% if msg contains 'world' %}found{% endif %}")
    assertEquals(template.render(vars), "found")
  }

  test("parse and render math filters") {
    val vars = new HashMap[String, Any]()
    vars.put("price", java.lang.Integer.valueOf(10))
    val template = Template.parse("{{ price | plus: 5 }}")
    assertEquals(template.render(vars), "15")
  }

  test("parse and render default filter") {
    val template = Template.parse("{{ nothing | default: 'fallback' }}")
    assertEquals(template.render(), "fallback")
  }

  test("parse and render size filter") {
    val vars = new HashMap[String, Any]()
    vars.put("name", "hello")
    val template = Template.parse("{{ name | size }}")
    assertEquals(template.render(vars), "5")
  }
}
