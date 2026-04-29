/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/test/java/liqp/ (multiple test files) Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk Original license: MIT
 *
 * Migration notes:
 * Renames: liqp → ssg.liquid Convention: munit FunSuite instead of JUnit Idiom: Edge cases from StatementsTest, IfTest, ForTest, CaseTest, CycleTest, AssignTest, CaptureTest, LookupNodeTest */
package ssg
package liquid

import java.util.{ ArrayList => JArrayList, HashMap => JHashMap }

class EdgeCaseSuite extends munit.FunSuite {

  // ---------------------------------------------------------------------------
  // Helper
  // ---------------------------------------------------------------------------

  private def render(template: String): String =
    Template.parse(template).render()

  private def render(template: String, vars: JHashMap[String, Any]): String =
    Template.parse(template).render(vars)

  // ---------------------------------------------------------------------------
  // StatementsTest.java — comparisons
  // ---------------------------------------------------------------------------

  test("comparison: 1 == 1 is true") {
    assertEquals(render("{% if 1 == 1 %}true{% else %}false{% endif %}"), "true")
  }

  test("comparison: 1 != 1 is false") {
    assertEquals(render("{% if 1 != 1 %}true{% else %}false{% endif %}"), "false")
  }

  test("comparison: 1 > 0 is true") {
    assertEquals(render("{% if 1 > 0 %}true{% else %}false{% endif %}"), "true")
  }

  test("comparison: 0 > 1 is false") {
    assertEquals(render("{% if 0 > 1 %}true{% else %}false{% endif %}"), "false")
  }

  test("comparison: 0 >= 0 is true") {
    assertEquals(render("{% if 0 >= 0 %}true{% else %}false{% endif %}"), "true")
  }

  test("comparison: string equality") {
    assertEquals(render("{% if 'test' == 'test' %}true{% else %}false{% endif %}"), "true")
  }

  test("comparison: variable to string") {
    val vars = new JHashMap[String, Any]()
    vars.put("var1", "hello")
    assertEquals(render("{% if var1 == 'hello' %}true{% else %}false{% endif %}", vars), "true")
  }

  // ---------------------------------------------------------------------------
  // IfTest.java — complex if
  // ---------------------------------------------------------------------------

  test("if: OR with operators") {
    assertEquals(render("{% if 1 == 2 or 3 == 3 %}true{% endif %}"), "true")
  }

  test("if: AND both true") {
    assertEquals(render("{% if 1 == 1 and 2 == 2 %}true{% endif %}"), "true")
  }

  test("if: AND one false") {
    assertEquals(render("{% if 1 == 1 and 2 == 3 %}true{% else %}false{% endif %}"), "false")
  }

  test("if: variable starting with 'and'") {
    val vars = new JHashMap[String, Any]()
    vars.put("android", "phone")
    assertEquals(render("{% if android == 'phone' %}yes{% endif %}", vars), "yes")
  }

  test("if: hash miss returns falsy") {
    val user = new JHashMap[String, Any]()
    val vars = new JHashMap[String, Any]()
    vars.put("user", user)
    assertEquals(render("{% if user.name %}yes{% else %}no{% endif %}", vars), "no")
  }

  // ---------------------------------------------------------------------------
  // ForTest.java — more for patterns
  // ---------------------------------------------------------------------------

  test("for: range with variable") {
    val vars = new JHashMap[String, Any]()
    vars.put("n", Integer.valueOf(3))
    assertEquals(render("{% for i in (1..n) %}{{ i }}{% endfor %}", vars), "123")
  }

  test("for: offset:continue resumes iteration") {
    val array = new JArrayList[Any]()
    array.add(Integer.valueOf(1))
    array.add(Integer.valueOf(2))
    array.add(Integer.valueOf(3))
    array.add(Integer.valueOf(4))
    array.add(Integer.valueOf(5))
    val vars = new JHashMap[String, Any]()
    vars.put("array", array)
    val tmpl = "{% for i in array limit:2 %}{{ i }}{% endfor %}-{% for i in array limit:2 offset:continue %}{{ i }}{% endfor %}"
    assertEquals(render(tmpl, vars), "12-34")
  }

  // ---------------------------------------------------------------------------
  // CaseTest.java
  // ---------------------------------------------------------------------------

  test("case: else when no match") {
    val vars = new JHashMap[String, Any]()
    vars.put("x", "z")
    assertEquals(render("{% case x %}{% when 'a' %}A{% else %}other{% endcase %}", vars), "other")
  }

  test("case: multiple when values using or") {
    val vars = new JHashMap[String, Any]()
    vars.put("x", "b")
    assertEquals(render("{% case x %}{% when 'a' or 'b' %}AB{% endcase %}", vars), "AB")
  }

  // ---------------------------------------------------------------------------
  // CycleTest.java
  // ---------------------------------------------------------------------------

  test("cycle: in for loop") {
    assertEquals(render("{% for i in (1..4) %}{% cycle 'a', 'b' %}{% endfor %}"), "abab")
  }

  test("cycle: named cycles") {
    assertEquals(
      render("{% cycle 'g1': 'a', 'b' %} {% cycle 'g2': 'x', 'y' %} {% cycle 'g1': 'a', 'b' %}"),
      "a x b"
    )
  }

  // ---------------------------------------------------------------------------
  // AssignTest.java
  // ---------------------------------------------------------------------------

  test("assign: with filter") {
    assertEquals(render("{% assign x = 'hello world' | upcase %}{{ x }}"), "HELLO WORLD")
  }

  test("assign: from variable") {
    val vars = new JHashMap[String, Any]()
    vars.put("name", "world")
    assertEquals(render("{% assign greeting = name %}{{ greeting }}", vars), "world")
  }

  test("assign: hyphenated variable name") {
    assertEquals(render("{% assign my-var = 'test' %}{{ my-var }}"), "test")
  }

  // ---------------------------------------------------------------------------
  // CaptureTest.java
  // ---------------------------------------------------------------------------

  test("capture: preserves scope") {
    assertEquals(render("{% assign x = 'before' %}{% capture y %}{{ x }}{% endcapture %}{{ y }}"), "before")
  }

  // ---------------------------------------------------------------------------
  // LookupNodeTest.java
  // ---------------------------------------------------------------------------

  test("lookup: size of string") {
    val vars = new JHashMap[String, Any]()
    vars.put("s", "hello")
    assertEquals(render("{{ s.size }}", vars), "5")
  }

  test("lookup: size of array") {
    val a = new JArrayList[Any]()
    a.add(Integer.valueOf(1))
    a.add(Integer.valueOf(2))
    a.add(Integer.valueOf(3))
    val vars = new JHashMap[String, Any]()
    vars.put("a", a)
    assertEquals(render("{{ a.size }}", vars), "3")
  }

  test("lookup: first of array") {
    val a = new JArrayList[Any]()
    a.add(Integer.valueOf(10))
    a.add(Integer.valueOf(20))
    a.add(Integer.valueOf(30))
    val vars = new JHashMap[String, Any]()
    vars.put("a", a)
    assertEquals(render("{{ a.first }}", vars), "10")
  }

  test("lookup: last of array") {
    val a = new JArrayList[Any]()
    a.add(Integer.valueOf(10))
    a.add(Integer.valueOf(20))
    a.add(Integer.valueOf(30))
    val vars = new JHashMap[String, Any]()
    vars.put("a", a)
    assertEquals(render("{{ a.last }}", vars), "30")
  }

  test("lookup: nested map access") {
    val user = new JHashMap[String, Any]()
    user.put("name", "Alice")
    val data = new JHashMap[String, Any]()
    data.put("user", user)
    val vars = new JHashMap[String, Any]()
    vars.put("data", data)
    assertEquals(render("{{ data.user.name }}", vars), "Alice")
  }

  // ---------------------------------------------------------------------------
  // Misc edge cases
  // ---------------------------------------------------------------------------

  test("multiple filters chained") {
    assertEquals(render("{{ 'hello WORLD' | downcase | capitalize }}"), "Hello world")
  }

  test("nil through default filter") {
    assertEquals(render("{{ nil | default: 'none' }}"), "none")
  }

  test("number with append filter") {
    assertEquals(render("{{ 42 | append: '!' }}"), "42!")
  }

  test("boolean output: true") {
    assertEquals(render("{{ true }}"), "true")
  }

  test("boolean output: false") {
    assertEquals(render("{{ false }}"), "false")
  }

  test("array output is concatenated") {
    val a = new JArrayList[Any]()
    a.add(Integer.valueOf(1))
    a.add(Integer.valueOf(2))
    a.add(Integer.valueOf(3))
    val vars = new JHashMap[String, Any]()
    vars.put("a", a)
    assertEquals(render("{{ a }}", vars), "123")
  }
}
