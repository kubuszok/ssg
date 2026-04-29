/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.liquid.antlr.NameResolver
import ssg.liquid.parser.Flavor

import java.util.HashMap

final class IncludeSuite extends munit.FunSuite {

  /** Creates a TemplateParser with an in-memory name resolver for testing. */
  private def parserWith(templates: (String, String)*): TemplateParser = {
    val map = new HashMap[String, String]()
    templates.foreach { case (name, content) => map.put(name, content) }
    new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).withNameResolver(new NameResolver.InMemory(map)).withShowExceptionsFromInclude(true).build()
  }

  test("include: basic include") {
    val parser   = parserWith("header" -> "HEADER")
    val template = parser.parse("before {% include 'header' %} after")
    assertEquals(template.render(), "before HEADER after")
  }

  test("include: include with .liquid extension resolution") {
    val parser   = parserWith("footer.liquid" -> "FOOTER")
    val template = parser.parse("{% include 'footer.liquid' %}")
    assertEquals(template.render(), "FOOTER")
  }

  test("include: included template can access parent variables") {
    val parser   = parserWith("greeting" -> "Hello {{ name }}!")
    val template = parser.parse("{% include 'greeting' %}")
    val vars     = new HashMap[String, Any]()
    vars.put("name", "World")
    assertEquals(template.render(vars), "Hello World!")
  }

  test("include: included template can use filters") {
    val parser   = parserWith("upper" -> "{{ text | upcase }}")
    val template = parser.parse("{% assign text = 'hello' %}{% include 'upper' %}")
    assertEquals(template.render(), "HELLO")
  }

  test("include: multiple includes") {
    val parser = parserWith(
      "head" -> "<head>",
      "foot" -> "</foot>"
    )
    val template = parser.parse("{% include 'head' %}body{% include 'foot' %}")
    assertEquals(template.render(), "<head>body</foot>")
  }

  test("include: include with variables (Jekyll style)") {
    val parser   = parserWith("item" -> "{{ include.title }}")
    val template = parser.parse("{% include 'item' title='Hello' %}")
    assertEquals(template.render(), "Hello")
  }

  test("include: nested includes") {
    val parser = parserWith(
      "outer" -> "outer[{% include 'inner' %}]",
      "inner" -> "inner"
    )
    val template = parser.parse("{% include 'outer' %}")
    assertEquals(template.render(), "outer[inner]")
  }

  test("include: include with if in included template") {
    val parser   = parserWith("conditional" -> "{% if show %}visible{% endif %}")
    val template = parser.parse("{% include 'conditional' %}")
    val vars     = new HashMap[String, Any]()
    vars.put("show", java.lang.Boolean.TRUE)
    assertEquals(template.render(vars), "visible")
  }

  test("include: include with for loop in included template") {
    val parser   = parserWith("list" -> "{% for item in items %}{{ item }}{% endfor %}")
    val template = parser.parse("{% include 'list' %}")
    val vars     = new HashMap[String, Any]()
    val items    = new java.util.ArrayList[Any]()
    items.add("a")
    items.add("b")
    items.add("c")
    vars.put("items", items)
    assertEquals(template.render(vars), "abc")
  }

  test("include: missing template with showExceptionsFromInclude=true throws") {
    val parser   = parserWith() // empty map
    val template = parser.parse("{% include 'nonexistent' %}")
    intercept[RuntimeException] {
      template.render()
    }
  }

  test("include: missing template with showExceptionsFromInclude=false returns empty") {
    val map         = new HashMap[String, String]()
    val quietParser = new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).withNameResolver(new NameResolver.InMemory(map)).withShowExceptionsFromInclude(false).build()
    val template    = quietParser.parse("before{% include 'nonexistent' %}after")
    assertEquals(template.render(), "beforeafter")
  }

  test("include: assign in included template affects parent scope") {
    val parser   = parserWith("setter" -> "{% assign x = 'from_include' %}")
    val template = parser.parse("{% include 'setter' %}{{ x }}")
    assertEquals(template.render(), "from_include")
  }
}
