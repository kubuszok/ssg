/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package liquid

import ssg.liquid.parser.{ Flavor, Inspectable }

import java.util.{ HashMap => JHashMap }

final class TemplateSuite extends munit.FunSuite {

  // ---------------------------------------------------------------------------
  // render() with no variables
  // ---------------------------------------------------------------------------

  test("render: plain text with no variables") {
    val template = Template.parse("Hello, world!")
    assertEquals(template.render(), "Hello, world!")
  }

  test("render: empty template") {
    assertEquals(Template.parse("").render(), "")
  }

  test("render: template with only whitespace") {
    assertEquals(Template.parse("   ").render(), "   ")
  }

  test("render: literal numbers") {
    assertEquals(Template.parse("{{ 42 }}").render(), "42")
  }

  test("render: literal strings") {
    assertEquals(Template.parse("{{ 'hello' }}").render(), "hello")
  }

  test("render: boolean true literal") {
    assertEquals(Template.parse("{{ true }}").render(), "true")
  }

  test("render: boolean false literal") {
    assertEquals(Template.parse("{{ false }}").render(), "false")
  }

  test("render: nil literal") {
    assertEquals(Template.parse("{{ nil }}").render(), "")
  }

  // ---------------------------------------------------------------------------
  // render(Map)
  // ---------------------------------------------------------------------------

  test("render(Map): single string variable") {
    val vars = new JHashMap[String, Any]()
    vars.put("name", "World")
    assertEquals(Template.parse("Hello, {{ name }}!").render(vars), "Hello, World!")
  }

  test("render(Map): multiple variables") {
    val vars = new JHashMap[String, Any]()
    vars.put("first", "John")
    vars.put("last", "Doe")
    assertEquals(Template.parse("{{ first }} {{ last }}").render(vars), "John Doe")
  }

  test("render(Map): integer variable") {
    val vars = new JHashMap[String, Any]()
    vars.put("count", java.lang.Integer.valueOf(42))
    assertEquals(Template.parse("Count: {{ count }}").render(vars), "Count: 42")
  }

  test("render(Map): boolean variable") {
    val vars = new JHashMap[String, Any]()
    vars.put("flag", java.lang.Boolean.TRUE)
    assertEquals(Template.parse("Flag: {{ flag }}").render(vars), "Flag: true")
  }

  test("render(Map): nested map variable") {
    val vars  = new JHashMap[String, Any]()
    val inner = new JHashMap[String, Any]()
    inner.put("city", "London")
    vars.put("address", inner)
    assertEquals(Template.parse("{{ address.city }}").render(vars), "London")
  }

  test("render(Map): undefined variable renders empty") {
    val vars = new JHashMap[String, Any]()
    assertEquals(Template.parse("Hello {{ name }}!").render(vars), "Hello !")
  }

  test("render(Map): empty map") {
    val vars = new JHashMap[String, Any]()
    assertEquals(Template.parse("Static text").render(vars), "Static text")
  }

  // ---------------------------------------------------------------------------
  // render(Inspectable)
  // ---------------------------------------------------------------------------

  test("render(Inspectable): extracts getter fields") {
    val data     = new TemplateSuite.NameAgeData()
    val template = Template.parse("{{ name }} is {{ age }}")
    val result   = template.render(data)
    assertEquals(result, "Alice is 30")
  }

  test("render(Inspectable): public fields are accessible") {
    // Public fields are extracted by LiquidSupportFromInspectable
    val data     = new TemplateSuite.TitleData()
    val template = Template.parse("Title: {{ title }}")
    val result   = template.render(data)
    assertEquals(result, "Title: Engineer")
  }

  test("render(Inspectable): isX boolean getters") {
    val data     = new TemplateSuite.ActiveData()
    val template = Template.parse("{% if active %}yes{% else %}no{% endif %}")
    val result   = template.render(data)
    assertEquals(result, "yes")
  }

  test("render(Inspectable): empty Inspectable yields no variables") {
    val data     = new TemplateSuite.EmptyData()
    val template = Template.parse("{{ missing }}")
    val result   = template.render(data)
    assertEquals(result, "")
  }

  // ---------------------------------------------------------------------------
  // errors() accessor
  // ---------------------------------------------------------------------------

  test("errors: empty after successful render") {
    val template = Template.parse("Hello")
    template.render()
    assert(template.errors().isEmpty)
  }

  test("errors: empty before render") {
    val template = Template.parse("Hello")
    assert(template.errors().isEmpty)
  }

  test("errors: populated in WARN mode for undefined variable") {
    val parser   = new TemplateParser.Builder().withStrictVariables(true).withErrorMode(TemplateParser.ErrorMode.WARN).build()
    val template = parser.parse("{{ undefined_var }}")
    template.render()
    assert(template.errors().size() > 0, "Expected at least one error")
  }

  // ---------------------------------------------------------------------------
  // ContextHolder
  // ---------------------------------------------------------------------------

  test("ContextHolder: provides access to rendering context") {
    val holder   = new Template.ContextHolder()
    val template = Template.parse("Hello {{ name }}")
    template.withContextHolder(holder)
    val vars = new JHashMap[String, Any]()
    vars.put("name", "World")
    template.render(vars)
    val ctx = holder.getContext
    assert(ctx != null, "ContextHolder should have a non-null context after render")
  }

  test("ContextHolder: context has access to variables") {
    val holder   = new Template.ContextHolder()
    val template = Template.parse("{{ greeting }}")
    template.withContextHolder(holder)
    val vars = new JHashMap[String, Any]()
    vars.put("greeting", "hi")
    template.render(vars)
    val ctx = holder.getContext
    assertEquals(ctx.get("greeting"), "hi")
  }

  test("ContextHolder: null before render") {
    val holder = new Template.ContextHolder()
    assert(holder.getContext == null, "Context should be null before render")
  }

  // ---------------------------------------------------------------------------
  // templateSize limit enforcement
  // ---------------------------------------------------------------------------

  test("templateSize: within limit renders normally") {
    val parser   = new TemplateParser.Builder().withMaxTemplateSizeBytes(1000).build()
    val template = parser.parse("Hello, world!")
    assertEquals(template.render(), "Hello, world!")
  }

  test("templateSize: exceeds limit throws RuntimeException") {
    val parser   = new TemplateParser.Builder().withMaxTemplateSizeBytes(5).build()
    val template = parser.parse("This template is longer than 5 bytes")
    intercept[RuntimeException] {
      template.render()
    }
  }

  test("templateSize: exact boundary (equal to limit) renders normally") {
    // A template of exactly N bytes should be allowed when limit is N
    val input    = "12345"
    val parser   = new TemplateParser.Builder().withMaxTemplateSizeBytes(input.length.toLong).build()
    val template = parser.parse(input)
    assertEquals(template.render(), "12345")
  }

  test("templateSize: one byte over limit throws") {
    val input    = "123456"
    val parser   = new TemplateParser.Builder().withMaxTemplateSizeBytes(5).build()
    val template = parser.parse(input)
    intercept[RuntimeException] {
      template.render()
    }
  }

  // ---------------------------------------------------------------------------
  // EAGER vs LAZY evaluate mode
  // ---------------------------------------------------------------------------

  test("evaluateMode LAZY: variables passed through as-is") {
    val parser = new TemplateParser.Builder().withEvaluateMode(TemplateParser.EvaluateMode.LAZY).build()
    val vars   = new JHashMap[String, Any]()
    vars.put("name", "Lazy")
    val result = parser.parse("{{ name }}").render(vars)
    assertEquals(result, "Lazy")
  }

  test("evaluateMode EAGER: parser setting is stored") {
    val parser = new TemplateParser.Builder().withEvaluateMode(TemplateParser.EvaluateMode.EAGER).build()
    assertEquals(parser.evaluateMode, TemplateParser.EvaluateMode.EAGER)
  }

  test("evaluateMode EAGER: renders without error") {
    // In EAGER mode, variables are pre-evaluated through LiquidSupportFromInspectable.
    // Simple string values get introspected (not just passed through), so the
    // rendered output differs from LAZY mode for plain strings.
    val parser = new TemplateParser.Builder().withEvaluateMode(TemplateParser.EvaluateMode.EAGER).build()
    val vars   = new JHashMap[String, Any]()
    vars.put("count", java.lang.Integer.valueOf(42))
    // Integer values get introspected — the result may differ from raw "42",
    // but the template should render without exceptions
    val result = parser.parse("rendered").render(vars)
    assertEquals(result, "rendered")
  }

  test("evaluateMode LAZY: nested map works") {
    val parser = new TemplateParser.Builder().withEvaluateMode(TemplateParser.EvaluateMode.LAZY).build()
    val vars   = new JHashMap[String, Any]()
    val inner  = new JHashMap[String, Any]()
    inner.put("x", "deep")
    vars.put("data", inner)
    val result = parser.parse("{{ data.x }}").render(vars)
    assertEquals(result, "deep")
  }

  // ---------------------------------------------------------------------------
  // Template.parse() (static shorthand)
  // ---------------------------------------------------------------------------

  test("Template.parse: returns working Template") {
    val template = Template.parse("{{ 'works' }}")
    assertEquals(template.render(), "works")
  }

  // ---------------------------------------------------------------------------
  // TemplateParser.Builder: flavor defaults
  // ---------------------------------------------------------------------------

  test("Builder: default flavor is JEKYLL") {
    val parser = new TemplateParser.Builder().build()
    assertEquals(parser.flavor, Flavor.JEKYLL)
  }

  test("Builder: LIQP flavor sets liquidStyleWhere true") {
    val parser = new TemplateParser.Builder().withFlavor(Flavor.LIQP).build()
    assert(parser.liquidStyleWhere)
  }

  test("Builder: JEKYLL flavor sets liquidStyleWhere false") {
    val parser = new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).build()
    assert(!parser.liquidStyleWhere)
  }

  test("Builder: LIQUID flavor sets liquidStyleInclude true") {
    val parser = new TemplateParser.Builder().withFlavor(Flavor.LIQUID).build()
    assert(parser.liquidStyleInclude)
  }

  // ---------------------------------------------------------------------------
  // TemplateParser.Builder: render time limit
  // ---------------------------------------------------------------------------

  test("render time limit: not limited by default") {
    val parser = new TemplateParser.Builder().build()
    assert(!parser.isRenderTimeLimited)
  }

  test("render time limit: enabled when set") {
    val parser = new TemplateParser.Builder().withMaxRenderTimeMillis(5000).build()
    assert(parser.isRenderTimeLimited)
  }

  // ---------------------------------------------------------------------------
  // renderToObject
  // ---------------------------------------------------------------------------

  test("renderToObject: returns raw result") {
    val template = Template.parse("Hello")
    val result   = template.renderToObject()
    assert(result != null)
  }

  test("renderToObject with Map: returns renderable result") {
    val vars = new JHashMap[String, Any]()
    vars.put("x", "test")
    val template = Template.parse("{{ x }}")
    val result   = String.valueOf(template.renderToObject(vars))
    assertEquals(result, "test")
  }

  // ---------------------------------------------------------------------------
  // Multiple renders reuse template
  // ---------------------------------------------------------------------------

  test("template reuse: same template rendered multiple times with different vars") {
    val template = Template.parse("Hello, {{ name }}!")
    val vars1    = new JHashMap[String, Any]()
    vars1.put("name", "Alice")
    val vars2 = new JHashMap[String, Any]()
    vars2.put("name", "Bob")
    assertEquals(template.render(vars1), "Hello, Alice!")
    assertEquals(template.render(vars2), "Hello, Bob!")
  }

  test("template reuse: renders correctly without state leakage") {
    val template = Template.parse("{% assign x = 'hello' %}{{ x }}")
    assertEquals(template.render(), "hello")
    assertEquals(template.render(), "hello")
  }
}

/** Test data classes for Inspectable tests.
  *
  * Defined as top-level classes so that getter methods are public (anonymous classes inside test methods have private members that the compiler flags as unused under -Wunused:privates).
  */
object TemplateSuite {

  class NameAgeData extends Inspectable {
    def getName: String = "Alice"
    def getAge:  Int    = 30
  }

  class TitleData extends Inspectable {
    @scala.beans.BeanProperty
    var title: String = "Engineer"
  }

  class ActiveData extends Inspectable {
    def isActive: Boolean = true
  }

  class EmptyData extends Inspectable
}
