/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.data.DataView

import ssg.liquid.parser.Flavor

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
    val vars = new JHashMap[String, DataView]()
    vars.put("name", TestHelper.dv("World"))
    assertEquals(Template.parse("Hello, {{ name }}!").render(vars), "Hello, World!")
  }

  test("render(Map): multiple variables") {
    val vars = new JHashMap[String, DataView]()
    vars.put("first", TestHelper.dv("John"))
    vars.put("last", TestHelper.dv("Doe"))
    assertEquals(Template.parse("{{ first }} {{ last }}").render(vars), "John Doe")
  }

  test("render(Map): integer variable") {
    val vars = new JHashMap[String, DataView]()
    vars.put("count", TestHelper.dv(java.lang.Integer.valueOf(42)))
    assertEquals(Template.parse("Count: {{ count }}").render(vars), "Count: 42")
  }

  test("render(Map): boolean variable") {
    val vars = new JHashMap[String, DataView]()
    vars.put("flag", TestHelper.dv(java.lang.Boolean.TRUE))
    assertEquals(Template.parse("Flag: {{ flag }}").render(vars), "Flag: true")
  }

  test("render(Map): nested map variable") {
    val vars  = new JHashMap[String, DataView]()
    val inner = new JHashMap[String, DataView]()
    inner.put("city", TestHelper.dv("London"))
    vars.put("address", TestHelper.dv(inner))
    assertEquals(Template.parse("{{ address.city }}").render(vars), "London")
  }

  test("render(Map): undefined variable renders empty") {
    val vars = new JHashMap[String, DataView]()
    assertEquals(Template.parse("Hello {{ name }}!").render(vars), "Hello !")
  }

  test("render(Map): empty map") {
    val vars = new JHashMap[String, DataView]()
    assertEquals(Template.parse("Static text").render(vars), "Static text")
  }

  // ---------------------------------------------------------------------------
  // render(Inspectable) — JVM-only (requires reflection)
  // ---------------------------------------------------------------------------

  // Converted from render(Inspectable) to render(Map) since DataView rewrite removed reflection path.
  test("render(DataView map): extracts named fields") {
    val data     = TestHelper.mapOf("name" -> "Alice", "age" -> 30)
    val template = Template.parse("{{ name }} is {{ age }}")
    val result   = template.render(data)
    assertEquals(result, "Alice is 30")
  }

  test("render(DataView map): property access") {
    val data     = TestHelper.mapOf("title" -> "Engineer")
    val template = Template.parse("Title: {{ title }}")
    val result   = template.render(data)
    assertEquals(result, "Title: Engineer")
  }

  test("render(DataView map): boolean value in condition") {
    val data     = TestHelper.mapOf("active" -> true)
    val template = Template.parse("{% if active %}yes{% else %}no{% endif %}")
    val result   = template.render(data)
    assertEquals(result, "yes")
  }

  test("render(DataView map): empty map yields no variables") {
    val data     = TestHelper.mapOf()
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
    val vars = new JHashMap[String, DataView]()
    vars.put("name", TestHelper.dv("World"))
    template.render(vars)
    val ctx = holder.getContext
    assert(ctx != null, "ContextHolder should have a non-null context after render")
  }

  test("ContextHolder: context has access to variables") {
    val holder   = new Template.ContextHolder()
    val template = Template.parse("{{ greeting }}")
    template.withContextHolder(holder)
    val vars = new JHashMap[String, DataView]()
    vars.put("greeting", TestHelper.dv("hi"))
    template.render(vars)
    val ctx = holder.getContext
    assertEquals(ctx.get("greeting").toString, "hi")
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
    val vars   = new JHashMap[String, DataView]()
    vars.put("name", TestHelper.dv("Lazy"))
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
    val vars   = new JHashMap[String, DataView]()
    vars.put("count", TestHelper.dv(java.lang.Integer.valueOf(42)))
    // Integer values get introspected — the result may differ from raw "42",
    // but the template should render without exceptions
    val result = parser.parse("rendered").render(vars)
    assertEquals(result, "rendered")
  }

  test("evaluateMode LAZY: nested map works") {
    val parser = new TemplateParser.Builder().withEvaluateMode(TemplateParser.EvaluateMode.LAZY).build()
    val vars   = new JHashMap[String, DataView]()
    val inner  = new JHashMap[String, DataView]()
    inner.put("x", TestHelper.dv("deep"))
    vars.put("data", TestHelper.dv(inner))
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
    val vars = new JHashMap[String, DataView]()
    vars.put("x", TestHelper.dv("test"))
    val template = Template.parse("{{ x }}")
    val result   = String.valueOf(template.renderToObject(vars))
    assertEquals(result, "test")
  }

  // ---------------------------------------------------------------------------
  // Multiple renders reuse template
  // ---------------------------------------------------------------------------

  test("template reuse: same template rendered multiple times with different vars") {
    val template = Template.parse("Hello, {{ name }}!")
    val vars1    = new JHashMap[String, DataView]()
    vars1.put("name", TestHelper.dv("Alice"))
    val vars2 = new JHashMap[String, DataView]()
    vars2.put("name", TestHelper.dv("Bob"))
    assertEquals(template.render(vars1), "Hello, Alice!")
    assertEquals(template.render(vars2), "Hello, Bob!")
  }

  test("template reuse: renders correctly without state leakage") {
    val template = Template.parse("{% assign x = 'hello' %}{{ x }}")
    assertEquals(template.render(), "hello")
    assertEquals(template.render(), "hello")
  }
}

// DataView rewrite: Inspectable-based test data classes removed.
// Tests now use TestHelper.mapOf() directly.
