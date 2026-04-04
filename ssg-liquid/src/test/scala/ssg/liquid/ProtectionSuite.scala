/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package liquid

import ssg.liquid.exceptions.{ ExceededMaxIterationsException, VariableNotExistException }
import ssg.liquid.filters.Filter
import ssg.liquid.nodes.LNode
import ssg.liquid.parser.Flavor
import ssg.liquid.tags.Tag

final class ProtectionSuite extends munit.FunSuite {

  test("max iterations - exceeds limit throws ExceededMaxIterationsException") {
    val parser   = new TemplateParser.Builder().withMaxIterations(100).build()
    val template = parser.parse("{% for i in (1..1000000) %}x{% endfor %}")
    intercept[ExceededMaxIterationsException] {
      template.render()
    }
  }

  test("max iterations - within limit succeeds") {
    val parser   = new TemplateParser.Builder().withMaxIterations(100).build()
    val template = parser.parse("{% for i in (1..5) %}x{% endfor %}")
    assertEquals(template.render(), "xxxxx")
  }

  test("max rendered string size - exceeds limit throws RuntimeException") {
    val parser   = new TemplateParser.Builder().withMaxSizeRenderedString(10).build()
    val template = parser.parse("{% for i in (1..100) %}x{% endfor %}")
    intercept[RuntimeException] {
      template.render()
    }
  }

  test("max rendered string size - within limit succeeds") {
    val parser   = new TemplateParser.Builder().withMaxSizeRenderedString(100).build()
    val template = parser.parse("{% for i in (1..5) %}x{% endfor %}")
    assertEquals(template.render(), "xxxxx")
  }

  test("strict variables STRICT - undefined variable throws VariableNotExistException") {
    val parser   = new TemplateParser.Builder().withStrictVariables(true).withErrorMode(TemplateParser.ErrorMode.STRICT).build()
    val template = parser.parse("{{ undefined_var }}")
    intercept[VariableNotExistException] {
      template.render()
    }
  }

  test("strict variables WARN - undefined variable renders empty without throwing") {
    val parser   = new TemplateParser.Builder().withStrictVariables(true).withErrorMode(TemplateParser.ErrorMode.WARN).build()
    val template = parser.parse("hello {{ undefined_var }} world")
    val result   = template.render()
    assertEquals(result, "hello  world")
  }

  test("error mode LAX - tolerant of missing variables without strictVariables") {
    val parser   = new TemplateParser.Builder().withErrorMode(TemplateParser.ErrorMode.LAX).build()
    val template = parser.parse("hello {{ missing }} world")
    val result   = template.render()
    assertEquals(result, "hello  world")
  }

  test("custom filter - registered via Builder") {
    val customFilter = new Filter("shout") {
      override def apply(value: Any, context: TemplateContext, params: Array[Any]): Any =
        String.valueOf(value).toUpperCase() + "!!!"
    }
    val parser   = new TemplateParser.Builder().withFilter(customFilter).build()
    val template = parser.parse("{{ 'hello' | shout }}")
    assertEquals(template.render(), "HELLO!!!")
  }

  test("custom tag - registered via Builder") {
    val customTag = new Tag("greeting") {
      override def render(context: TemplateContext, nodes: Array[LNode]): Any =
        "Hello, World!"
    }
    val parser   = new TemplateParser.Builder().withTag(customTag).build()
    val template = parser.parse("{% greeting %}")
    assertEquals(template.render(), "Hello, World!")
  }

  test("flavor LIQUID - uses liquid-style include by default") {
    val parser = new TemplateParser.Builder().withFlavor(Flavor.LIQUID).build()
    assert(parser.liquidStyleInclude, "LIQUID flavor should have liquidStyleInclude=true")
    assert(parser.liquidStyleWhere, "LIQUID flavor should have liquidStyleWhere=true")
  }

  test("flavor JEKYLL - uses jekyll-style defaults") {
    val parser = new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).build()
    assert(!parser.liquidStyleInclude, "JEKYLL flavor should have liquidStyleInclude=false")
    assert(!parser.liquidStyleWhere, "JEKYLL flavor should have liquidStyleWhere=false")
  }

  test("showExceptionsFromInclude=false - missing include returns empty") {
    val parser   = new TemplateParser.Builder().withShowExceptionsFromInclude(false).build()
    val template = parser.parse("before{% include 'nonexistent' %}after")
    val result   = template.render()
    assertEquals(result, "beforeafter")
  }
}
