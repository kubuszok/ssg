/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-yaml-front-matter/.../YamlFrontMatterTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package ext
package yaml
package front
package matter
package test

import ssg.md.ext.yaml.front.matter.{ AbstractYamlFrontMatterVisitor, YamlFrontMatterExtension }
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.test.util.TestUtils
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Collections
import scala.language.implicitConversions

final class YamlFrontMatterTest extends munit.FunSuite {

  private val OPTIONS: DataHolder = new MutableDataSet().set(TestUtils.NO_FILE_EOL, false).set(Parser.EXTENSIONS, Collections.singleton(YamlFrontMatterExtension.create())).toImmutable

  private val PARSER:   Parser       = Parser.builder(OPTIONS).build()
  private val RENDERER: HtmlRenderer = HtmlRenderer.builder(OPTIONS).build()

  private def assertRendering(input: String, expectedHtml: String): Unit = {
    val document   = PARSER.parse(input)
    val actualHtml = RENDERER.render(document)
    assertEquals(actualHtml, expectedHtml)
  }

  test("simpleValue") {
    val input    = "---\nhello: world\n...\n\ngreat"
    val rendered = "<p>great</p>\n"

    val visitor  = new AbstractYamlFrontMatterVisitor()
    val document = PARSER.parse(input)
    visitor.visit(document)

    val data = visitor.getData
    assertEquals(data.size(), 1)
    assertEquals(data.keySet().iterator().next(), "hello")
    assertEquals(data.get("hello").size(), 1)
    assertEquals(data.get("hello").get(0), "world")

    assertRendering(input, rendered)
  }

  test("emptyValue") {
    val input    = "---\nkey:\n---\n\ngreat"
    val rendered = "<p>great</p>\n"

    val visitor  = new AbstractYamlFrontMatterVisitor()
    val document = PARSER.parse(input)
    visitor.visit(document)

    val data = visitor.getData
    assertEquals(data.size(), 1)
    assertEquals(data.keySet().iterator().next(), "key")
    assertEquals(data.get("key").size(), 0)

    assertRendering(input, rendered)
  }

  test("listValues") {
    val input    = "---\nlist:\n  - value1\n  - value2\n...\n\ngreat"
    val rendered = "<p>great</p>\n"

    val visitor  = new AbstractYamlFrontMatterVisitor()
    val document = PARSER.parse(input)
    visitor.visit(document)

    val data = visitor.getData
    assertEquals(data.size(), 1)
    assert(data.containsKey("list"))
    assertEquals(data.get("list").size(), 2)
    assertEquals(data.get("list").get(0), "value1")
    assertEquals(data.get("list").get(1), "value2")

    assertRendering(input, rendered)
  }

  test("literalValue1") {
    val input    = "---\nliteral: |\n  hello markdown!\n  literal thing...\n---\n\ngreat"
    val rendered = "<p>great</p>\n"

    val visitor  = new AbstractYamlFrontMatterVisitor()
    val document = PARSER.parse(input)
    visitor.visit(document)

    val data = visitor.getData
    assertEquals(data.size(), 1)
    assert(data.containsKey("literal"))
    assertEquals(data.get("literal").size(), 1)
    assertEquals(data.get("literal").get(0), "hello markdown!\nliteral thing...")

    assertRendering(input, rendered)
  }

  test("literalValue2") {
    val input    = "---\nliteral: |\n  - hello markdown!\n---\n\ngreat"
    val rendered = "<p>great</p>\n"

    val visitor  = new AbstractYamlFrontMatterVisitor()
    val document = PARSER.parse(input)
    visitor.visit(document)

    val data = visitor.getData
    assertEquals(data.size(), 1)
    assert(data.containsKey("literal"))
    assertEquals(data.get("literal").size(), 1)
    assertEquals(data.get("literal").get(0), "- hello markdown!")

    assertRendering(input, rendered)
  }

  test("complexValues") {
    val input    = "---\nsimple: value\nliteral: |\n  hello markdown!\n\n  literal literal\nlist:\n    - value1\n    - value2\n---\ngreat"
    val rendered = "<p>great</p>\n"

    val visitor  = new AbstractYamlFrontMatterVisitor()
    val document = PARSER.parse(input)
    visitor.visit(document)

    val data = visitor.getData
    assertEquals(data.size(), 3)

    assert(data.containsKey("simple"))
    assertEquals(data.get("simple").size(), 1)
    assertEquals(data.get("simple").get(0), "value")

    assert(data.containsKey("literal"))
    assertEquals(data.get("literal").size(), 1)
    assertEquals(data.get("literal").get(0), "hello markdown!\n\nliteral literal")

    assert(data.containsKey("list"))
    assertEquals(data.get("list").size(), 2)
    assertEquals(data.get("list").get(0), "value1")
    assertEquals(data.get("list").get(1), "value2")

    assertRendering(input, rendered)
  }

  test("yamlInParagraph") {
    val input    = "# hello\n\nhello markdown world!\n---\nhello: world\n---"
    val rendered = "<h1>hello</h1>\n<h2>hello markdown world!</h2>\n<h2>hello: world</h2>\n"

    val visitor  = new AbstractYamlFrontMatterVisitor()
    val document = PARSER.parse(input)
    visitor.visit(document)

    val data = visitor.getData
    assert(data.isEmpty)

    assertRendering(input, rendered)
  }

  test("nonMatchedStartTag") {
    val input    = "----\ntest"
    val rendered = "<hr />\n<p>test</p>\n"

    val visitor  = new AbstractYamlFrontMatterVisitor()
    val document = PARSER.parse(input)
    visitor.visit(document)

    val data = visitor.getData
    assert(data.isEmpty)

    assertRendering(input, rendered)
  }
}
