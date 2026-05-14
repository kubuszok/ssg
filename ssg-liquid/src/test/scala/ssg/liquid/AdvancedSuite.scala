/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.data.DataView

import java.util.HashMap
import java.util.ArrayList

final class AdvancedSuite extends munit.FunSuite {

  // ===== Chained filters =====

  test("nested output: upcase then downcase") {
    assertEquals(
      Template.parse("{{ 'hello' | upcase | downcase }}").render(),
      "hello"
    )
  }

  test("variable with property and filter: user.name | upcase") {
    val vars = new HashMap[String, DataView]()
    val user = new HashMap[String, DataView]()
    user.put("name", TestHelper.dv("alice"))
    vars.put("user", TestHelper.dv(user))
    assertEquals(
      Template.parse("{{ user.name | upcase }}").render(vars),
      "ALICE"
    )
  }

  // ===== Array access =====

  test("array index access: array[1]") {
    val vars  = new HashMap[String, DataView]()
    val array = new ArrayList[Any]()
    array.add("a")
    array.add("b")
    array.add("c")
    vars.put("array", TestHelper.dv(array))
    assertEquals(
      Template.parse("{{ array[1] }}").render(vars),
      "b"
    )
  }

  test("array negative index: array[-1]") {
    val vars  = new HashMap[String, DataView]()
    val array = new ArrayList[Any]()
    array.add("a")
    array.add("b")
    array.add("c")
    vars.put("array", TestHelper.dv(array))
    assertEquals(
      Template.parse("{{ array[-1] }}").render(vars),
      "c"
    )
  }

  // ===== Nested for loops =====

  test("nested for loops with inner variable") {
    val vars  = new HashMap[String, DataView]()
    val outer = new ArrayList[Any]()
    outer.add(java.lang.Integer.valueOf(1))
    outer.add(java.lang.Integer.valueOf(2))
    val inner = new ArrayList[Any]()
    inner.add("x")
    inner.add("y")
    vars.put("outer", TestHelper.dv(outer))
    vars.put("inner", TestHelper.dv(inner))
    assertEquals(
      Template.parse("{% for a in outer %}{% for b in inner %}{{a}}{{b}} {% endfor %}{% endfor %}").render(vars),
      "1x 1y 2x 2y "
    )
  }

  // ===== Assign with filters =====

  test("assign with filter chain: upcase and split then join") {
    assertEquals(
      Template.parse("{% assign x = 'hello world' | upcase | split: ' ' %}{{ x | join: '-' }}").render(),
      "HELLO-WORLD"
    )
  }

  test("for with range variable") {
    assertEquals(
      Template.parse("{% assign n = 3 %}{% for i in (1..n) %}{{i}}{% endfor %}").render(),
      "123"
    )
  }

  // ===== Complex filter chains =====

  test("complex output with multiple filters: capitalize and append") {
    assertEquals(
      Template.parse("{{ 'hello world' | capitalize | append: '!' }}").render(),
      "Hello world!"
    )
  }

  // ===== Empty checks =====

  test("empty check: size filter on empty array") {
    val vars  = new HashMap[String, DataView]()
    val array = new ArrayList[Any]()
    vars.put("array", TestHelper.dv(array))
    assertEquals(
      Template.parse("{% if array.size == 0 %}yes{% endif %}").render(vars),
      "yes"
    )
  }

  test("empty check: size filter on non-empty array") {
    val vars  = new HashMap[String, DataView]()
    val array = new ArrayList[Any]()
    array.add("a")
    array.add("b")
    vars.put("array", TestHelper.dv(array))
    assertEquals(
      Template.parse("{% if array.size == 2 %}yes{% endif %}").render(vars),
      "yes"
    )
  }

  // ===== Whitespace tolerance =====

  test("whitespace in tags: extra spaces in output tags") {
    val vars = new HashMap[String, DataView]()
    vars.put("name", TestHelper.dv("hi"))
    assertEquals(
      Template.parse("{{  name  }}").render(vars),
      "hi"
    )
  }

  // ===== Multiple assigns =====

  test("multiple assign: two variables") {
    assertEquals(
      Template.parse("{% assign a = 1 %}{% assign b = 2 %}{{ a }}{{ b }}").render(),
      "12"
    )
  }

  // ===== Capture with filters =====

  test("capture with filters: capture then upcase") {
    assertEquals(
      Template.parse("{% capture x %}hello{% endcapture %}{{ x | upcase }}").render(),
      "HELLO"
    )
  }

  // ===== For loop break/continue =====

  test("for loop break with output: stops before 4") {
    assertEquals(
      Template.parse("{% for i in (1..10) %}{% if i == 4 %}{% break %}{% endif %}{{ i }}{% endfor %}").render(),
      "123"
    )
  }

  test("for loop continue with output: skips 3") {
    assertEquals(
      Template.parse("{% for i in (1..5) %}{% if i == 3 %}{% continue %}{% endif %}{{ i }}{% endfor %}").render(),
      "1245"
    )
  }

  // ===== Case with variable =====

  test("case with variable: matching red") {
    val vars = new HashMap[String, DataView]()
    vars.put("color", TestHelper.dv("red"))
    assertEquals(
      Template.parse("{% case color %}{% when 'red' %}danger{% when 'blue' %}info{% endcase %}").render(vars),
      "danger"
    )
  }

  // ===== Nested if in for =====

  test("nested if in for: conditional formatting") {
    assertEquals(
      Template.parse("{% for i in (1..3) %}{% if i == 2 %}[{{i}}]{% else %}{{i}}{% endif %}{% endfor %}").render(),
      "1[2]3"
    )
  }

  // ===== Empty for loop =====

  test("empty for loop: no output for empty array") {
    val vars       = new HashMap[String, DataView]()
    val emptyArray = new ArrayList[Any]()
    vars.put("empty_array", TestHelper.dv(emptyArray))
    assertEquals(
      Template.parse("{% for item in empty_array %}x{% endfor %}").render(vars),
      ""
    )
  }

  // ===== Undefined variables =====

  test("render nil variable: undefined produces empty string") {
    assertEquals(
      Template.parse("{{ nothing }}").render(),
      ""
    )
  }

  // ===== Chained property access =====

  test("chained property access: site.data.title") {
    val vars = new HashMap[String, DataView]()
    val data = new HashMap[String, DataView]()
    data.put("title", TestHelper.dv("My Site"))
    val site = new HashMap[String, DataView]()
    site.put("data", TestHelper.dv(data))
    vars.put("site", TestHelper.dv(site))
    assertEquals(
      Template.parse("{{ site.data.title }}").render(vars),
      "My Site"
    )
  }
}
