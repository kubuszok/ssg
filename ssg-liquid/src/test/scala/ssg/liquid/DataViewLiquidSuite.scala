/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Cross-platform tests for DataView integration with ssg-liquid.
 * These tests do NOT require reflection and run on JVM, JS, and Native. */
package ssg
package liquid

import ssg.data.{ AsDataView, DataView }

import java.util.{ HashMap => JHashMap }

import scala.collection.immutable.VectorMap

class DataViewLiquidSuite extends munit.FunSuite {

  private val EAGER_PARSER: TemplateParser =
    new TemplateParser.Builder().withEvaluateMode(TemplateParser.EvaluateMode.EAGER).build()

  private def render(template: String, vars: JHashMap[String, DataView]): String =
    TemplateParser.DEFAULT.parse(template).render(vars)

  private def renderEager(template: String, vars: JHashMap[String, DataView]): String =
    EAGER_PARSER.parse(template).render(vars)

  private def varsOf(pairs: (String, Any)*): JHashMap[String, DataView] =
    TestHelper.mapOf(pairs*)

  // --- Scalar DataView in template variables ---

  test("DataView: render string scalar") {
    val vars = varsOf("name" -> DataView("Alice"))
    assertEquals(render("hi {{name}}", vars), "hi Alice")
  }

  test("DataView: render int scalar") {
    val vars = varsOf("count" -> DataView(42))
    assertEquals(render("count={{count}}", vars), "count=42")
  }

  // --- DataView map as structured object ---

  test("DataView: render map properties") {
    val person = DataView(
      VectorMap[String, DataView](
        "name" -> DataView("Bob"),
        "age" -> DataView(25)
      )
    )
    val vars = varsOf("person" -> person)
    assertEquals(render("{{person.name}} is {{person.age}}", vars), "Bob is 25")
  }

  test("DataView: nested map access") {
    val child = DataView(
      VectorMap[String, DataView](
        "val" -> DataView("childOK")
      )
    )
    val parent = DataView(
      VectorMap[String, DataView](
        "child" -> child
      )
    )
    val vars = varsOf("foo" -> parent)
    assertEquals(render("{{foo.child.val}}", vars), "childOK")
  }

  test("DataView: map size property") {
    val m = DataView(
      VectorMap[String, DataView](
        "a" -> DataView(1),
        "b" -> DataView(2),
        "c" -> DataView(3)
      )
    )
    val vars = varsOf("obj" -> m)
    assertEquals(render("{{obj.size}}", vars), "3")
  }

  // --- DataView vector in for loop ---

  test("DataView: for loop over vector") {
    val items = DataView(Vector(DataView(1), DataView(2), DataView(3)))
    val vars  = varsOf("items" -> items)
    assertEquals(render("{% for i in items %}{{i}}{% endfor %}", vars), "123")
  }

  test("DataView: for loop over vector of maps") {
    val products = DataView(
      Vector(
        DataView(VectorMap[String, DataView]("title" -> DataView("Apple"), "price" -> DataView(1))),
        DataView(VectorMap[String, DataView]("title" -> DataView("Banana"), "price" -> DataView(2)))
      )
    )
    val vars = varsOf("products" -> products)
    assertEquals(
      render("{% for p in products %}{{p.title}}:{{p.price}} {% endfor %}", vars),
      "Apple:1 Banana:2 "
    )
  }

  // --- DataView with EAGER mode ---

  test("DataView: EAGER mode with map") {
    val foo = DataView(
      VectorMap[String, DataView](
        "a" -> DataView("A"),
        "b" -> DataView("B")
      )
    )
    val vars = varsOf("foo" -> foo)
    assertEquals(renderEager("{{foo.a}}{{foo.b}}", vars), "AB")
  }

  test("DataView: EAGER mode with nested map") {
    val child = DataView(
      VectorMap[String, DataView](
        "val" -> DataView("childOK")
      )
    )
    val parent = DataView(
      VectorMap[String, DataView](
        "child" -> child
      )
    )
    val vars = varsOf("foo" -> parent)
    assertEquals(renderEager("{{foo.child.val}}", vars), "childOK")
  }

  // --- DataView with map filter ---

  test("DataView: map filter on DataView") {
    val child = DataView(
      VectorMap[String, DataView](
        "val" -> DataView("filtered")
      )
    )
    val parent = DataView(
      VectorMap[String, DataView](
        "child" -> child
      )
    )
    val vars = varsOf("foo" -> parent)
    assertEquals(render("{{ foo | map: 'child' | map: 'val' }}", vars), "filtered")
  }

  // --- DataView nil handling ---

  test("DataView: nil renders empty") {
    val vars = varsOf("x" -> DataView.nil)
    assertEquals(render("{{x}}", vars), "")
  }

  test("DataView: nil in if/else") {
    val vars = varsOf("x" -> DataView.nil)
    assertEquals(render("{% if x %}yes{% else %}no{% endif %}", vars), "no")
  }

  // --- AsDataView derived case class ---

  test("DataView: derived case class via AsDataView") {
    val person = DataViewLiquidSuite.SimplePerson("Eve", 28).asDataView
    val vars   = varsOf("p" -> person)
    assertEquals(render("{{p.name}} is {{p.age}}", vars), "Eve is 28")
  }

  test("DataView: derived nested case class via AsDataView") {
    val address = DataViewLiquidSuite.Address("NYC", "10001")
    val person  = DataViewLiquidSuite.PersonWithAddress("Frank", address).asDataView
    val vars    = varsOf("p" -> person)
    assertEquals(render("{{p.name}} in {{p.address.city}}", vars), "Frank in NYC")
  }

  // --- DataView in where filter ---

  test("DataView: where filter on DataView list") {
    val items = new java.util.ArrayList[Any]()
    items.add(DataView(VectorMap[String, DataView]("name" -> DataView("a"), "ok" -> DataView("yes"))))
    items.add(DataView(VectorMap[String, DataView]("name" -> DataView("b"), "ok" -> DataView("no"))))
    items.add(DataView(VectorMap[String, DataView]("name" -> DataView("c"), "ok" -> DataView("yes"))))
    val vars = varsOf("items" -> items)
    assertEquals(
      render("{% assign filtered = items | where: 'ok', 'yes' %}{% for i in filtered %}{{i.name}}{% endfor %}", vars),
      "ac"
    )
  }

  // --- Vector size / first / last ---

  test("DataView: vector size property") {
    val items = DataView(Vector(DataView(1), DataView(2), DataView(3)))
    val vars  = varsOf("items" -> items)
    assertEquals(render("{{items.size}}", vars), "3")
  }

  test("DataView: vector first and last") {
    val items = DataView(Vector(DataView("alpha"), DataView("beta"), DataView("gamma")))
    val vars  = varsOf("items" -> items)
    assertEquals(render("{{items.first}} {{items.last}}", vars), "alpha gamma")
  }
}

object DataViewLiquidSuite {

  final case class SimplePerson(name: String, age: Int) derives AsDataView

  final case class Address(city: String, zip: String) derives AsDataView

  final case class PersonWithAddress(name: String, address: Address) derives AsDataView
}
