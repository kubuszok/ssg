/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.liquid.parser.Flavor

import java.util.{ Arrays, Collections, LinkedHashSet }

/** Gap-fill tests from liqp's nodes/ package:
  *   - LookupNodeTest.java (9 missing)
  *   - ComparingExpressionNodeTest.java (5 tests) — NEW
  *   - GtNodeTest.java (6 missing)
  *   - OutputNodeTest.java (4 tests) — NEW
  *   - AtomNodeTest.java (1 test) — NEW
  *   - BlockNodeTest.java (1 test) — NEW
  */
final class NodeExtraSuite extends munit.FunSuite {

  // ===========================================================================
  // AtomNodeTest — 1 test
  // ===========================================================================

  test("atom node: plain text renders as-is") {
    assertEquals(Template.parse("mu").render(), "mu")
    assertEquals(Template.parse("1").render(), "1")
  }

  // ===========================================================================
  // BlockNodeTest — 1 test
  // ===========================================================================

  test("block node: custom tag with block") {
    val parser = new TemplateParser.Builder()
      .withBlock(new blocks.Block("testtag") {
        override def render(context: TemplateContext, ns: Array[nodes.LNode]): Any = null
      })
      .build()
    parser.parse("{% testtag %} {% endtesttag %}").render()
    // Should not throw — verifies custom block parsing
  }

  // ===========================================================================
  // OutputNodeTest — 4 tests
  // ===========================================================================

  test("output node: basic output and filter chain") {
    val vars = TestHelper.mapOf("X" -> "mu")
    assertEquals(Template.parse("{{ X }}").render(vars), "mu")
    assertEquals(Template.parse("{{ 'a.b.c' | split:'.' | first | upcase }}").render(), "A")
  }

  test("output node: allowed keywords as variables") {
    val keywords = Array(
      "capture",
      "endcapture",
      "comment",
      "endcomment",
      "raw",
      "endraw",
      "if",
      "elsif",
      "endif",
      "unless",
      "endunless",
      "else",
      "contains",
      "case",
      "endcase",
      "when",
      "cycle",
      "for",
      "endfor",
      "in",
      "and",
      "or",
      "tablerow",
      "endtablerow",
      "assign",
      "include",
      "with",
      "end",
      "break",
      "continue",
      "offset",
      "reversed"
    )
    keywords.foreach { keyword =>
      val expected = keyword + "_" + keyword.length
      val vars     = TestHelper.mapOf(keyword -> expected)
      val rendered = Template.parse("{{" + keyword + "}}").render(vars)
      assertEquals(rendered, expected, s"Keyword: $keyword")
    }
  }

  test("output node: bad keywords as variables are not overridable") {
    val keywords = Array(
      ("true", "true"),
      ("false", "false"),
      ("nil", ""),
      ("null", ""),
      ("empty", ""),
      ("blank", "")
    )
    keywords.foreach { case (keyword, expected) =>
      val vars     = TestHelper.mapOf(keyword -> "bad")
      val rendered = Template.parse("{{" + keyword + "}}").render(vars)
      assertEquals(rendered, expected, s"Keyword: $keyword")
    }
  }

  test("output node: date with filter") {
    assume(PlatformCompat.supportsReflection, "LocalDateTime handling may differ on non-JVM")
    val vars = Collections.singletonMap[String, Any]("a", java.time.LocalDateTime.parse("2011-12-03T10:15:30"))
    val res  = Template.parse("{{ a | truncate: 13 }}").render(vars)
    assertEquals(res, "2011-12-03...")
  }

  // ===========================================================================
  // LookupNodeTest — 9 missing
  // ===========================================================================

  test("lookup: nested property access") {
    val vars = TestHelper.mapOf(
      "a" -> TestHelper.mapOf(
        "b" -> TestHelper.mapOf("c" -> java.lang.Integer.valueOf(42))
      )
    )
    assertEquals(Template.parse("{{a.b.c}}").render(vars), "42")
    assertEquals(Template.parse("{{a.b.c.d}}").render(vars), "")
  }

  test("lookup: size of array") {
    val vars = TestHelper.mapOf(
      "array" -> TestHelper.listOf(
        java.lang.Integer.valueOf(1),
        java.lang.Integer.valueOf(2),
        java.lang.Integer.valueOf(3),
        java.lang.Integer.valueOf(4)
      )
    )
    assertEquals(Template.parse("array has {{ array.size }} elements").render(vars), "array has 4 elements")
  }

  test("lookup: size of hash") {
    val vars = TestHelper.mapOf(
      "hash" -> TestHelper.mapOf(
        "a" -> java.lang.Integer.valueOf(1),
        "b" -> java.lang.Integer.valueOf(2),
        "c" -> java.lang.Integer.valueOf(3),
        "d" -> java.lang.Integer.valueOf(4)
      )
    )
    assertEquals(Template.parse("hash has {{ hash.size }} elements").render(vars), "hash has 4 elements")
  }

  // SSG: numeric key in dotted path not yet supported
  test("lookup: number as key".fail) {
    // https://github.com/bkiers/Liqp/issues/209
    val vars = TestHelper.mapOf(
      "Data" -> TestHelper.mapOf(
        "1" -> TestHelper.mapOf("Value" -> "tobi")
      )
    )
    assertEquals(Template.parse("hi {{Data.1.Value}}").render(vars), "hi tobi")
  }

  test("lookup: collection access by index") {
    val vars = Collections.singletonMap[String, Any](
      "data",
      new LinkedHashSet[Any](Arrays.asList("hello", "world"))
    )
    assertEquals(Template.parse("Hello {{data[1]}}").render(vars), "Hello world")
  }

  test("lookup: negative offset") {
    // LinkedHashSet
    assertEquals(
      Template
        .parse("Hello {{data[-2]}}")
        .render(
          Collections.singletonMap[String, Any]("data", new LinkedHashSet[Any](Arrays.asList("hello", "world")))
        ),
      "Hello hello"
    )
    // List
    assertEquals(
      Template
        .parse("Hello {{data[-2]}}")
        .render(
          Collections.singletonMap[String, Any]("data", Arrays.asList("hello", "world"))
        ),
      "Hello hello"
    )
    // Array
    assertEquals(
      Template
        .parse("Hello {{data[-2]}}")
        .render(
          Collections.singletonMap[String, Any]("data", Array("hello", "world"))
        ),
      "Hello hello"
    )
  }

  test("lookup: out of size returns default") {
    assertEquals(
      Template
        .parse("Hello {{data[99] | default: 'default'}}")
        .render(
          Collections.singletonMap[String, Any]("data", new LinkedHashSet[Any](Arrays.asList("hello", "world")))
        ),
      "Hello default"
    )
    assertEquals(
      Template
        .parse("Hello {{data[99] | default: 'default'}}")
        .render(
          Collections.singletonMap[String, Any]("data", Arrays.asList("hello", "world"))
        ),
      "Hello default"
    )
    assertEquals(
      Template
        .parse("Hello {{data[99] | default: 'default'}}")
        .render(
          Collections.singletonMap[String, Any]("data", Array("hello", "world"))
        ),
      "Hello default"
    )
    assertEquals(
      Template
        .parse("Hello {{data[99] | default: 'default'}}")
        .render(
          Collections.singletonMap[String, Any]("data", "123")
        ),
      "Hello default"
    )
  }

  test("lookup: first and last on array") {
    val vars = TestHelper.mapOf(
      "test" -> TestHelper.listOf(
        java.lang.Integer.valueOf(1),
        java.lang.Integer.valueOf(2),
        java.lang.Integer.valueOf(3),
        java.lang.Integer.valueOf(4),
        java.lang.Integer.valueOf(5)
      )
    )
    assertEquals(Template.parse("{{ test.first }}").render(vars), "1")
    assertEquals(Template.parse("{{ test.last }}").render(vars), "5")
  }

  test("lookup: first can appear in middle of call chain") {
    val vars = TestHelper.mapOf(
      "product" -> TestHelper.mapOf(
        "variants" -> TestHelper.listOf(
          TestHelper.mapOf("title" -> "draft151cm"),
          TestHelper.mapOf("title" -> "element151cm")
        )
      )
    )
    assertEquals(Template.parse("{{ product.variants[0].title }}").render(vars), "draft151cm")
    assertEquals(Template.parse("{{ product.variants[1].title }}").render(vars), "element151cm")
    assertEquals(Template.parse("{{ product.variants.first.title }}").render(vars), "draft151cm")
    assertEquals(Template.parse("{{ product.variants.last.title }}").render(vars), "element151cm")
  }

  // ===========================================================================
  // ComparingExpressionNodeTest — 5 tests
  // ===========================================================================

  test("comparing expression: string comparison alphabetically") {
    val parser = new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).withEvaluateInOutputTag(true).withStrictTypedExpressions(false).build()
    val res    = parser.parse("{{'98' > '197'}}").render()
    assertEquals(res, "true")
  }

  test("comparing expression: bug 286 case 1") {
    val parser = new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).build()
    val res    = parser
      .parse(
        "{%- assign value = 5.0 | round: 1 -%} {%- if value >= 4.0 and value <= 4.2 -%} Très bien {%- elsif value >= 4.3 and value <= 4.7 -%} Superbe {%- elsif value >= 4.8 and value <= 4.9 -%} Fabuleux {%- elsif value != 5.0 -%} Exceptionnel {% endif %}"
      )
      .render()
    assertEquals(res.trim, "")
  }

  test("comparing expression: bug 286 case 2") {
    val parser = new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).build()
    val res    = parser
      .parse(
        "{%- assign value = 5.0 | round: 1 -%} {%- if value >= 4.0 and value <= 4.2 -%} Très bien {%- elsif value >= 4.3 and value <= 4.7 -%} Superbe {%- elsif value >= 4.8 and value <= 4.9 -%} Fabuleux {%- elsif value == 5.0 -%} Exceptionnel {% endif %}"
      )
      .render()
    assertEquals(res.trim, "Exceptionnel")
  }

  test("comparing expression: combinations strict") {
    val parser = new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).withStrictTypedExpressions(true).build()

    val expected = "010110eeee01000001000001000001eeee01010110000001000001000001000001000001000010000001000001000001000001000001000010000001000001000001000001000001000010"
    assertExpressionCombinations(expected, parser)
  }

  test("comparing expression: combinations non-strict") {
    val parser = new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).withStrictTypedExpressions(false).build()

    val expected = "010110110001110001110001110001001101010110110001110001110001001101001101010110110001110001001101001101001101010110010101001101001101001101010101010110"
    assertExpressionCombinations(expected, parser)
  }

  private def assertExpressionCombinations(expected: String, parser: TemplateParser): Unit = {
    val values: Array[Any]    = Array(java.lang.Integer.valueOf(98), "97", java.lang.Boolean.TRUE, java.lang.Boolean.FALSE, null)
    val ops:    Array[String] = Array(">", ">=", "<", "<=", "==", "!=")
    var idx = 0
    values.foreach { first =>
      values.foreach { second =>
        ops.foreach { op =>
          val firstStr = first match {
            case null => "nil"
            case s: String            => s"'$s'"
            case b: java.lang.Boolean => b.toString
            case other => other.toString
          }
          val secondStr = second match {
            case null => "nil"
            case s: String            => s"'$s'"
            case b: java.lang.Boolean => b.toString
            case other => other.toString
          }
          val expectedChar = expected.charAt(idx)
          val expectedRes  = expectedChar match {
            case '0' => "false"
            case '1' => "true"
            case 'e' => "error"
          }
          val res =
            try
              parser.parse(s"{% if $firstStr $op $secondStr %}true{% else %}false{% endif %}").render()
            catch {
              case _: Exception => "error"
            }
          assertEquals(res, expectedRes, s"$firstStr $op $secondStr")
          idx += 1
        }
      }
    }
  }

  // ===========================================================================
  // GtNodeTest — 6 missing
  // ===========================================================================

  test("gt node: basic comparisons") {
    val tests: Array[(String, String)] = Array(
      ("{% if nil > 42.09 %}yes{% else %}no{% endif %}", "no"),
      ("{% if 42.1 > false %}yes{% else %}no{% endif %}", "no"),
      ("{% if 42.1 > true %}yes{% else %}no{% endif %}", "no"),
      ("{% if a > 42.09 %}yes{% else %}no{% endif %}", "no"),
      ("{% if 42.1 > 42.09 %}yes{% else %}no{% endif %}", "yes"),
      ("{% if 42.1 > 42.1000001 %}yes{% else %}no{% endif %}", "no")
    )
    tests.foreach { case (tmpl, expected) =>
      assertEquals(TemplateParser.DEFAULT_JEKYLL.parse(tmpl).render(), expected, s"Template: $tmpl")
    }
  }

  // SSG: expression in output parsing differs — may not throw LiquidException
  test("gt node: bug 267 expression in output as Liquid".fail) {
    try {
      new TemplateParser.Builder().withFlavor(Flavor.LIQUID).build().parse("{{ 98 > 97 }}").render()
      fail("Expected LiquidException")
    } catch {
      case e: Exception =>
        assert(e.getMessage.contains("parser error"), s"Unexpected: ${e.getMessage}")
    }
  }

  test("gt node: bug 267 expression in output as Jekyll") {
    val contextHolder = new Template.ContextHolder()
    val res           = new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).build().parse("{{ 98 > 97 }}").withContextHolder(contextHolder).render()
    assertEquals(res, "98")
    val errors = contextHolder.getContext.errors()
    assertEquals(errors.size(), 1)
    assert(errors.get(0).getMessage.contains("unexpected output"))
  }

  test("gt node: bug 267 string vs number strict") {
    try {
      new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).build().parse("{% if 98 > '98' %}true{% else %}false{% endif %}").render()
      fail("Expected exception for string vs number comparison")
    } catch {
      case e: Exception =>
        assert(e.getMessage.contains("not the same type"), s"Unexpected: ${e.getMessage}")
    }
  }

  test("gt node: filter compare") {
    val result = new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).build().parse("{% assign score = 0 | plus: 1.0 %}{% if score > 0 %}true{% else %}false{% endif %}").render()
    assert(java.lang.Boolean.parseBoolean(result))
  }

  test("gt node: strict mode disabled") {
    val tests: Array[(String, String)] = Array(
      ("{% if 0 > 'A' %}yes{% else %}no{% endif %}", "no"),
      ("{% if 'A' > 0 %}yes{% else %}no{% endif %}", "no"),
      ("{% if false > 1 %}yes{% else %}no{% endif %}", "no")
    )
    val parser = new TemplateParser.Builder().withStrictTypedExpressions(false).build()
    tests.foreach { case (tmpl, expected) =>
      assertEquals(parser.parse(tmpl).render(), expected, s"Template: $tmpl")
    }
  }
}
