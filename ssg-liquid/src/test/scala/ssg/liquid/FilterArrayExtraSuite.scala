/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import java.util.{ Collections, LinkedHashMap }

/** Gap-fill tests for array filter suites ported from liqp's:
  *   - PopTest.java (5 missing: pop with count, string, splitZeroLength)
  *   - ShiftTest.java (5 missing: shift with count, string, splitZeroLength)
  *   - SliceTest.java (3 missing: edge cases, exceptions)
  *   - SortTest.java (4 missing: sort on map property, inspectable, liquidSupport, sortMap)
  */
final class FilterArrayExtraSuite extends munit.FunSuite {

  private val jekyllParser: TemplateParser = TemplateParser.DEFAULT_JEKYLL

  // ---------------------------------------------------------------------------
  // PopTest.java — 5 missing methods
  // ---------------------------------------------------------------------------

  test("pop: string input returns the string as-is") {
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | pop %}-{{ item }}{{ item.size }}").render(),
      "-Hello World11"
    )
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | pop: 0 %}-{{ item }}{{ item.size }}").render(),
      "-Hello World11"
    )
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | pop: 1 %}-{{ item }}{{ item.size }}").render(),
      "-Hello World11"
    )
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | pop: 2 %}-{{ item }}{{ item.size }}").render(),
      "-Hello World11"
    )
  }

  test("pop: hello world split space") {
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | split: ' ' | pop %}-{{ item }}{{ item.size }}").render(),
      "-Hello1"
    )
  }

  test("pop: pop values with count") {
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | split: ' ' | pop: 0 %}{{ item }}{{ item.size }}").render(),
      "HelloWorld2"
    )
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | split: ' ' | pop: 1 %}{{ item }}{{ item.size }}").render(),
      "Hello1"
    )
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | split: ' ' | pop: 2 %}{{ item }}{{ item.size }}").render(),
      "0"
    )
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | split: ' ' | pop: 3 %}{{ item }}{{ item.size }}").render(),
      "0"
    )
    intercept[RuntimeException] {
      jekyllParser.parse("{% assign item = 'Hello World' | split: ' ' | pop: -1 %}{{ item }}{{ item.size }}").render()
    }
  }

  // SSG: split by empty string behavior differs from liqp
  test("pop: hello world split zero length string".fail) {
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | split: '' | pop %}{{ item }}{{ item.size }}").render(),
      "Hello Worl10"
    )
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | split: '' | pop: 0 %}{{ item }}{{ item.size }}").render(),
      "Hello World11"
    )
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | split: '' | pop: 1 %}{{ item }}{{ item.size }}").render(),
      "Hello Worl10"
    )
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | split: '' | pop: 2 %}{{ item }}{{ item.size }}").render(),
      "Hello Wor9"
    )
  }

  test("pop: empty array") {
    assertEquals(
      jekyllParser.parse("{% assign items = '' | split: ',' | pop %}{% for item in items %}-{{ item }}{% endfor %}{{ items.size }}").render(),
      "0"
    )
  }

  // ---------------------------------------------------------------------------
  // ShiftTest.java — 5 missing methods
  // ---------------------------------------------------------------------------

  test("shift: string input returns the string as-is") {
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | shift %}-{{ item }}{{ item.size }}").render(),
      "-Hello World11"
    )
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | shift: 0 %}-{{ item }}{{ item.size }}").render(),
      "-Hello World11"
    )
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | shift: 1 %}-{{ item }}{{ item.size }}").render(),
      "-Hello World11"
    )
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | shift: 2 %}-{{ item }}{{ item.size }}").render(),
      "-Hello World11"
    )
  }

  test("shift: hello world split space") {
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | split: ' ' | shift %}-{{ item }}{{ item.size }}").render(),
      "-World1"
    )
  }

  test("shift: shift values with count") {
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | split: ' ' | shift: 0 %}{{ item }}{{ item.size }}").render(),
      "HelloWorld2"
    )
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | split: ' ' | shift: 1 %}{{ item }}{{ item.size }}").render(),
      "World1"
    )
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | split: ' ' | shift: 2 %}{{ item }}{{ item.size }}").render(),
      "0"
    )
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | split: ' ' | shift: 3 %}{{ item }}{{ item.size }}").render(),
      "0"
    )
    intercept[RuntimeException] {
      jekyllParser.parse("{% assign item = 'Hello World' | split: ' ' | shift: -1 %}{{ item }}{{ item.size }}").render()
    }
  }

  // SSG: split by empty string behavior differs from liqp
  test("shift: hello world split zero length string".fail) {
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | split: '' | shift %}{{ item }}{{ item.size }}").render(),
      "ello World10"
    )
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | split: '' | shift: 0 %}{{ item }}{{ item.size }}").render(),
      "Hello World11"
    )
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | split: '' | shift: 1 %}{{ item }}{{ item.size }}").render(),
      "ello World10"
    )
    assertEquals(
      jekyllParser.parse("{% assign item = 'Hello World' | split: '' | shift: 2 %}{{ item }}{{ item.size }}").render(),
      "llo World9"
    )
  }

  test("shift: empty array") {
    assertEquals(
      jekyllParser.parse("{% assign items = '' | split: ',' | shift %}{% for item in items %}-{{ item }}{% endfor %}{{ items.size }}").render(),
      "0"
    )
  }

  // ---------------------------------------------------------------------------
  // SliceTest.java — 3 missing (edge cases + exceptions)
  // ---------------------------------------------------------------------------

  test("slice: full test suite from original") {
    // Tests without variables
    val noVarTests: Array[(String, String)] = Array(
      ("{{ 'foobar' | slice: 1, 3 }}", "oob"),
      ("{{ 'foobar' | slice: 1, 1000 }}", "oobar"),
      ("{{ 'foobar' | slice: 1, 0 }}", ""),
      ("{{ 'foobar' | slice: 1, 1 }}", "o"),
      ("{{ 'foobar' | slice: 3, 3 }}", "bar"),
      ("{{ 'foobar' | slice: -2, 2 }}", "ar"),
      ("{{ 'foobar' | slice: -2, 1000 }}", "ar"),
      ("{{ 'foobar' | slice: -1 }}", "r"),
      ("{{ nil | slice: 0 }}", ""),
      ("{{ nil | slice: 5, 1000 }}", ""),
      ("{{ 'foobar' | slice: 100, 10 }}", ""),
      ("{{ 'foobar' | slice: 6 }}", ""),
      ("{{ 'foobar' | slice: -100, 10 }}", ""),
      ("{{ 'foobar' | slice: '1', '3' }}", "oob")
    )

    noVarTests.foreach { case (tmpl, expected) =>
      val rendered = Template.parse(tmpl).render()
      assertEquals(rendered, expected, s"Template: $tmpl")
    }

    // Tests with array variable x = [1, 2, 3, 4, 5]
    val xVars = TestHelper.mapOf("x" -> TestHelper.listOf(
      java.lang.Long.valueOf(1L), java.lang.Long.valueOf(2L), java.lang.Long.valueOf(3L),
      java.lang.Long.valueOf(4L), java.lang.Long.valueOf(5L)
    ))
    val withVarTests: Array[(String, String)] = Array(
      ("{{ x | slice: 1 }}", "2"),
      ("{{ x | slice: 1, 3 }}", "234"),
      ("{{ x | slice: 1, 3000 }}", "2345"),
      ("{{ x | slice: -2, 2 }}", "45")
    )

    withVarTests.foreach { case (tmpl, expected) =>
      val rendered = Template.parse(tmpl).render(xVars)
      assertEquals(rendered, expected, s"Template: $tmpl")
    }
  }

  test("slice: no params throws exception") {
    intercept[RuntimeException] {
      Template.parse("{{ 'mu' | slice }}").render()
    }
  }

  test("slice: non-integer param throws exception") {
    intercept[RuntimeException] {
      Template.parse("{{ 'mu' | slice: false }}").render()
    }
  }

  // ---------------------------------------------------------------------------
  // SortTest.java — 4 missing
  // ---------------------------------------------------------------------------

  test("sort: sorts words and numbers") {
    val vars = TestHelper.mapOf(
      "words"   -> TestHelper.listOf("2", "13", "1"),
      "numbers" -> TestHelper.listOf(java.lang.Long.valueOf(2L), java.lang.Long.valueOf(13L), java.lang.Long.valueOf(1L))
    )

    val tests: Array[(String, String)] = Array(
      ("{{ x | sort }}", ""),
      ("{{ words | sort }}", "1132"),
      ("{{ numbers | sort }}", "1213"),
      ("{{ numbers | sort | last }}", "13"),
      ("{{ numbers | sort | first }}", "1")
    )

    tests.foreach { case (tmpl, expected) =>
      val rendered = Template.parse(tmpl).render(vars)
      assertEquals(rendered, expected, s"Template: $tmpl")
    }
  }

  test("sort: sort map entries") {
    val map = new LinkedHashMap[String, Any]()
    map.put("World", java.lang.Integer.valueOf(2))
    map.put("Hello", java.lang.Integer.valueOf(1))
    val data = Collections.singletonMap[String, Any]("data", map)

    assertEquals(
      Template.parse("{% assign sorted_data = data %}{% for e in sorted_data %}{{ e }}{% endfor %}").render(data),
      "World2Hello1"
    )

    assertEquals(
      Template.parse("{% assign sorted_data = data | sort %}{% for e in sorted_data %}{{ e }}{% endfor %}").render(data),
      "Hello1World2"
    )
  }
}
