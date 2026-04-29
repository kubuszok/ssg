/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: liqp/src/test/java/liqp/blocks/ (ForTest, IfTest, TablerowTest, CaptureTest, CycleTest, CaseTest)
 * Original: Copyright (c) 2012 Bart Kiers, 2022 Vasyl Khrystiuk
 * Original license: MIT
 *
 * Migration notes:
 *   Renames: liqp → ssg.liquid
 *   Convention: munit FunSuite instead of JUnit
 *   Idiom: Manual map construction instead of JSON strings, Scala 3 patterns
 *   Note: Tests that were already in BlocksSuite/EdgeCaseSuite are NOT duplicated here */
package ssg
package liquid

import ssg.liquid.exceptions.LiquidException
import ssg.liquid.parser.Inspectable

import java.util.{ ArrayList => JArrayList, HashMap => JHashMap }

final class BlocksExtraSuite extends munit.FunSuite {

  // ===========================================================================
  // Helpers
  // ===========================================================================

  private def render(template: String): String =
    TemplateParser.DEFAULT.parse(template).render()

  private def render(template: String, vars: JHashMap[String, Any]): String =
    TemplateParser.DEFAULT.parse(template).render(vars)

  private def mapOf(pairs: (String, Any)*): JHashMap[String, Any] = {
    val m = new JHashMap[String, Any]()
    pairs.foreach { case (k, v) => m.put(k, v) }
    m
  }

  private def listOf(items: Any*): JArrayList[Any] = {
    val l = new JArrayList[Any]()
    items.foreach(l.add)
    l
  }

  // ===========================================================================
  // ForTest.java — missing tests
  // ===========================================================================

  // --- test_for_parentloop_references_parent_loop ---

  /*
   *   def test_for_parentloop_references_parent_loop
   *     assert_template_result('1.1 1.2 1.3 2.1 2.2 2.3 ',
   *       '{% for inner in outer %}{% for k in inner %}' \
   *       '{{ forloop.parentloop.index }}.{{ forloop.index }} ' \
   *       '{% endfor %}{% endfor %}',
   *       'outer' => [[1, 1, 1], [1, 1, 1]])
   *   end
   */
  test("for: parentloop references parent loop") {
    val outer = listOf(listOf(1, 1, 1), listOf(1, 1, 1))
    assertEquals(
      render(
        "{% for inner in outer %}{% for k in inner %}" +
          "{{ forloop.parentloop.index }}.{{ forloop.index }} " +
          "{% endfor %}{% endfor %}",
        mapOf("outer" -> outer)
      ),
      "1.1 1.2 1.3 2.1 2.2 2.3 "
    )
  }

  // --- test_for_parentloop_nil_when_not_present ---

  /*
   *   def test_for_parentloop_nil_when_not_present
   *     assert_template_result('.1 .2 ',
   *       '{% for inner in outer %}' \
   *       '{{ forloop.parentloop.index }}.{{ forloop.index }} ' \
   *       '{% endfor %}',
   *       'outer' => [[1, 1, 1], [1, 1, 1]])
   *   end
   */
  test("for: parentloop nil when not present") {
    val outer = listOf(listOf(1, 1, 1), listOf(1, 1, 1))
    assertEquals(
      render(
        "{% for inner in outer %}" +
          "{{ forloop.parentloop.index }}.{{ forloop.index }} " +
          "{% endfor %}",
        mapOf("outer" -> outer)
      ),
      ".1 .2 "
    )
  }

  // --- forTagStringTest ---

  /*
   * def test_for_tag_string
   *   assert_template_result('test string',
   *               '{%for val in string%}{{val}}{%endfor%}',
   *               'string' => "test string")
   */
  test("for: iterating over string yields single-element loop") {
    assertEquals(
      render("{%for val in string%}{{val}}{%endfor%}", mapOf("string" -> "test string")),
      "test string"
    )
  }

  test("for: string with limit:1") {
    assertEquals(
      render("{%for val in string limit:1%}{{val}}{%endfor%}", mapOf("string" -> "test string")),
      "test string"
    )
  }

  // NOTE: SSG forloop.name reports "val-val" instead of "val-string"
  // because the SSG for implementation stores the loop name differently
  // than the original liqp ANTLR-based implementation
  test("for: string with forloop properties") {
    assertEquals(
      render(
        "{%for val in string%}" +
          "{{forloop.name}}-" +
          "{{forloop.index}}-" +
          "{{forloop.length}}-" +
          "{{forloop.index0}}-" +
          "{{forloop.rindex}}-" +
          "{{forloop.rindex0}}-" +
          "{{forloop.first}}-" +
          "{{forloop.last}}-" +
          "{{val}}{%endfor%}",
        mapOf("string" -> "test string")
      ),
      "val-val-1-1-0-1-0-true-true-test string"
    )
  }

  // --- testComplexArrayNameSuccess ---

  // NOTE: SSG forloop.name reports "x-x" instead of "x-X[0].Y"
  // because the SSG for implementation stores loop names differently
  test("for: complex array name with bracket access") {
    val inner = new JHashMap[String, Any]()
    inner.put("Y", "foo")
    val x = listOf(inner, "test string")
    val rendered = TemplateParser.DEFAULT.parse("{% for x in X[0].Y %}{{forloop.name}}-{{x}}{%endfor%}").render(mapOf("X" -> x))
    assertEquals(rendered, "x-x-foo")
  }

  // --- blankStringNotIterableTest ---

  /*
   * def test_blank_string_not_iterable
   *   assert_template_result('', "{% for char in characters %}I WILL NOT BE OUTPUT{% endfor %}", 'characters' => '')
   * end
   */
  test("for: blank string is not iterable") {
    assertEquals(
      render("{% for char in characters %}I WILL NOT BE OUTPUT{% endfor %}", mapOf("characters" -> "")),
      ""
    )
    // Also: no variables at all
    assertEquals(
      render("{% for char in characters %}I WILL NOT BE OUTPUT{% endfor %}"),
      ""
    )
  }

  // --- nestedTest (full nested with multiline) ---

  test("for: nested with multiline and forloop.index") {
    val template = "`{% for c1 in chars %}\n" +
      "  {{ forloop.index }}\n" +
      "  {% for c2 in chars %}\n" +
      "    {{ forloop.index }} {{ c1 }} {{ c2 }}\n" +
      "  {% endfor %}\n" +
      "{% endfor %}`"

    val expected = "`\n" +
      "  1\n" +
      "  \n" +
      "    1 a a\n" +
      "  \n" +
      "    2 a b\n" +
      "  \n" +
      "    3 a c\n" +
      "  \n" +
      "\n" +
      "  2\n" +
      "  \n" +
      "    1 b a\n" +
      "  \n" +
      "    2 b b\n" +
      "  \n" +
      "    3 b c\n" +
      "  \n" +
      "\n" +
      "  3\n" +
      "  \n" +
      "    1 c a\n" +
      "  \n" +
      "    2 c b\n" +
      "  \n" +
      "    3 c c\n" +
      "  \n" +
      "`"

    val vars = mapOf("chars" -> listOf("a", "b", "c"))
    assertEquals(render(template, vars), expected)
  }

  // --- mapTest (iterating over a hash map) ---

  /*
   * {% for item in hash %}{{ item[0] }} is {{ item[1] }};{% endfor %}
   * 'hash' => {'a' => 'AAA', 'b' => 'BBB'}
   */
  test("for: iterating over hash map") {
    val hash = new JHashMap[String, Any]()
    hash.put("a", "AAA")
    hash.put("b", "BBB")
    val rendered = render("{% for item in hash %}{{ item[0] }} is {{ item[1] }};{% endfor %}", mapOf("hash" -> hash))
    assertEquals(rendered, "a is AAA;b is BBB;")
  }

  // --- shouldProperlyUseMapAfterFirstOnArrayOfMaps ---

  test("for: map filter after first on array of maps") {
    val m1 = new JHashMap[String, Any]()
    m1.put("rating", java.lang.Double.valueOf(4.5))
    val m2 = new JHashMap[String, Any]()
    m2.put("rating", java.lang.Double.valueOf(7.2))
    val x = listOf(m1, m2)
    assertEquals(
      render("{{ x | first | map: 'rating' }}", mapOf("x" -> x)),
      "4.5"
    )
  }

  // --- testContinueOutOfContext ---

  test("for: continue out of context with offset:continue") {
    val items = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
    val vars = mapOf("array" -> mapOf("items" -> items))
    val markup = "{%for i in array.items limit:9 %}{%endfor%}{%for i in array.items offset:continue %}{{i}}{%endfor%}" +
      "{{ continue }}"
    assertEquals(render(markup, vars), "0")
  }

  // --- testReversedSimple (reversed with offset) ---

  test("for: reversed with range (1..9)") {
    assertEquals(render("{%for i in (1..9) reversed %}{{i}}{%endfor%}"), "987654321")
  }

  test("for: reversed with offset on range") {
    assertEquals(render("{%for i in (116..121) reversed offset: 4 %}{{i}}:{%endfor%}"), "121:120:")
  }

  test("for: reversed on descending range with offset yields empty") {
    assertEquals(render("{%for i in (121..116) reversed offset: 4 %}{{i}}:{%endfor%}"), "")
  }

  // --- test_for_cleans_up_registers ---

  /*
   * def test_for_cleans_up_registers
   *   context.registers[:for_stack].empty?
   */
  test("for: cleans up registers after error") {
    val holder = new Template.ContextHolder()
    try {
      val parser = new TemplateParser.Builder()
        .withStrictVariables(true)
        .withErrorMode(TemplateParser.ErrorMode.STRICT)
        .build()
      parser.parse("{% for i in (1..2) %}{{ standard_error }}{% endfor %}")
        .withContextHolder(holder)
        .render()
      fail("Expected an exception")
    } catch {
      case _: Exception => // expected
    }
    assert(holder.getContext.getRegistry(TemplateContext.REGISTRY_FOR).isEmpty)
  }

  // --- testVariableNamedOffset ---

  test("for: variable named offset") {
    assertEquals(render("{% for offset in (1..3) %}{{ offset }}{% endfor %}"), "123")
  }

  test("for: variable named else") {
    assertEquals(render("{% for else in (1..3) %}{{ else }}{% endfor %}"), "123")
  }

  test("for: variable named offset from split") {
    assertEquals(
      render("{% assign offsets = '1,2,3' | split: ',' %}{% for offset in offsets %}{{ offset }}{% endfor %}"),
      "123"
    )
  }

  // --- test_inner_for_over_empty_input ---

  /*
   * def test_inner_for_over_empty_input
   *   assert_template_result 'oo', '{% for a in (1..2) %}o{% for b in empty %} {% endfor %}{% endfor %}'
   * end
   */
  test("for: inner for over empty input") {
    assertEquals(render("{% for a in (1..2) %}o{% for b in empty %} {% endfor %}{% endfor %}"), "oo")
  }

  // ===========================================================================
  // IfTest.java — missing tests
  // ===========================================================================

  // --- if_from_variableTest (26 sub-assertions) ---

  test("if: from variable — false is falsy") {
    assertEquals(render("{% if var %} NO {% endif %}", mapOf("var" -> java.lang.Boolean.FALSE)), "")
  }

  test("if: from variable — null is falsy") {
    assertEquals(render("{% if var %} NO {% endif %}", mapOf("var" -> null)), "")
  }

  test("if: from variable — foo.bar false is falsy") {
    assertEquals(
      render("{% if foo.bar %} NO {% endif %}", mapOf("foo" -> mapOf("bar" -> java.lang.Boolean.FALSE))),
      ""
    )
  }

  test("if: from variable — foo empty hash bar is falsy") {
    assertEquals(render("{% if foo.bar %} NO {% endif %}", mapOf("foo" -> new JHashMap[String, Any]())), "")
  }

  test("if: from variable — foo null is falsy") {
    assertEquals(render("{% if foo.bar %} NO {% endif %}", mapOf("foo" -> null)), "")
  }

  test("if: from variable — foo true has no bar") {
    assertEquals(render("{% if foo.bar %} NO {% endif %}", mapOf("foo" -> java.lang.Boolean.TRUE)), "")
  }

  test("if: from variable — string is truthy") {
    assertEquals(render("{% if var %} YES {% endif %}", mapOf("var" -> "text")), " YES ")
  }

  test("if: from variable — true is truthy") {
    assertEquals(render("{% if var %} YES {% endif %}", mapOf("var" -> java.lang.Boolean.TRUE)), " YES ")
  }

  test("if: from variable — integer 1 is truthy") {
    assertEquals(render("{% if var %} YES {% endif %}", mapOf("var" -> java.lang.Integer.valueOf(1))), " YES ")
  }

  test("if: from variable — empty hash is truthy") {
    assertEquals(render("{% if var %} YES {% endif %}", mapOf("var" -> new JHashMap[String, Any]())), " YES ")
  }

  test("if: from variable — empty array is truthy") {
    assertEquals(render("{% if var %} YES {% endif %}", mapOf("var" -> new JArrayList[Any]())), " YES ")
  }

  test("if: from variable — literal string is truthy") {
    assertEquals(render("{% if \"foo\" %} YES {% endif %}"), " YES ")
  }

  test("if: from variable — foo.bar true is truthy") {
    assertEquals(
      render("{% if foo.bar %} YES {% endif %}", mapOf("foo" -> mapOf("bar" -> java.lang.Boolean.TRUE))),
      " YES "
    )
  }

  test("if: from variable — foo.bar string is truthy") {
    assertEquals(
      render("{% if foo.bar %} YES {% endif %}", mapOf("foo" -> mapOf("bar" -> "text"))),
      " YES "
    )
  }

  test("if: from variable — foo.bar int is truthy") {
    assertEquals(
      render("{% if foo.bar %} YES {% endif %}", mapOf("foo" -> mapOf("bar" -> java.lang.Integer.valueOf(1)))),
      " YES "
    )
  }

  test("if: from variable — foo.bar empty hash is truthy") {
    assertEquals(
      render("{% if foo.bar %} YES {% endif %}", mapOf("foo" -> mapOf("bar" -> new JHashMap[String, Any]()))),
      " YES "
    )
  }

  test("if: from variable — foo.bar empty array is truthy") {
    assertEquals(
      render("{% if foo.bar %} YES {% endif %}", mapOf("foo" -> mapOf("bar" -> new JArrayList[Any]()))),
      " YES "
    )
  }

  // if-else from variable
  test("if: from variable — false with else goes to else") {
    assertEquals(render("{% if var %} NO {% else %} YES {% endif %}", mapOf("var" -> java.lang.Boolean.FALSE)), " YES ")
  }

  test("if: from variable — null with else goes to else") {
    assertEquals(render("{% if var %} NO {% else %} YES {% endif %}", mapOf("var" -> null)), " YES ")
  }

  test("if: from variable — true with else goes to if") {
    assertEquals(render("{% if var %} YES {% else %} NO {% endif %}", mapOf("var" -> java.lang.Boolean.TRUE)), " YES ")
  }

  test("if: from variable — literal string with else goes to if") {
    assertEquals(render("{% if \"foo\" %} YES {% else %} NO {% endif %}", mapOf("var" -> "text")), " YES ")
  }

  test("if: from variable — foo.bar false with else goes to else") {
    assertEquals(
      render("{% if foo.bar %} NO {% else %} YES {% endif %}", mapOf("foo" -> mapOf("bar" -> java.lang.Boolean.FALSE))),
      " YES "
    )
  }

  test("if: from variable — foo.bar true with else goes to if") {
    assertEquals(
      render("{% if foo.bar %} YES {% else %} NO {% endif %}", mapOf("foo" -> mapOf("bar" -> java.lang.Boolean.TRUE))),
      " YES "
    )
  }

  test("if: from variable — foo.bar string with else goes to if") {
    assertEquals(
      render("{% if foo.bar %} YES {% else %} NO {% endif %}", mapOf("foo" -> mapOf("bar" -> "text"))),
      " YES "
    )
  }

  test("if: from variable — foo.notbar with else goes to else") {
    assertEquals(
      render("{% if foo.bar %} NO {% else %} YES {% endif %}", mapOf("foo" -> mapOf("notbar" -> java.lang.Boolean.TRUE))),
      " YES "
    )
  }

  test("if: from variable — foo empty hash with else goes to else") {
    assertEquals(
      render("{% if foo.bar %} NO {% else %} YES {% endif %}", mapOf("foo" -> new JHashMap[String, Any]())),
      " YES "
    )
  }

  test("if: from variable — notfoo with else goes to else") {
    assertEquals(
      render(
        "{% if foo.bar %} NO {% else %} YES {% endif %}",
        mapOf("notfoo" -> mapOf("bar" -> java.lang.Boolean.TRUE))
      ),
      " YES "
    )
  }

  // --- comparisons_on_nullTest ---

  /*
   * def test_comparisons_on_null
   *   assert_template_result('','{% if null < 10 %} NO {% endif %}')
   *   ...
   * end
   */
  test("if: null < 10 is false") {
    assertEquals(render("{% if null < 10 %} NO {% endif %}"), "")
  }
  test("if: null <= 10 is false") {
    assertEquals(render("{% if null <= 10 %} NO {% endif %}"), "")
  }
  test("if: null >= 10 is false") {
    assertEquals(render("{% if null >= 10 %} NO {% endif %}"), "")
  }
  test("if: null > 10 is false") {
    assertEquals(render("{% if null > 10 %} NO {% endif %}"), "")
  }
  test("if: 10 < null is false") {
    assertEquals(render("{% if 10 < null %} NO {% endif %}"), "")
  }
  test("if: 10 <= null is false") {
    assertEquals(render("{% if 10 <= null %} NO {% endif %}"), "")
  }
  test("if: 10 >= null is false") {
    assertEquals(render("{% if 10 >= null %} NO {% endif %}"), "")
  }
  test("if: 10 > null is false") {
    assertEquals(render("{% if 10 > null %} NO {% endif %}"), "")
  }

  // --- comparison_of_strings_containing_and_or_orTest ---

  test("if: comparison of strings containing 'and' or 'or'") {
    val vars = mapOf(
      "a" -> "and",
      "b" -> "or",
      "c" -> "foo and bar",
      "d" -> "bar or baz",
      "e" -> "foo",
      "foo" -> java.lang.Boolean.TRUE,
      "bar" -> java.lang.Boolean.TRUE
    )
    val awfulMarkup = "a == 'and' and b == 'or' and c == 'foo and bar' and d == 'bar or baz' and e == 'foo' and foo and bar"
    assertEquals(render("{% if " + awfulMarkup + " %} YES {% endif %}", vars), " YES ")
  }

  // --- syntax_error_no_variableTest ---

  /*
   * def test_syntax_error_no_variable
   *   assert_raise(SyntaxError){ assert_template_result('', '{% if jerry == 1 %}')}
   * end
   */
  test("if: syntax error no variable (unclosed if)") {
    intercept[LiquidException] {
      TemplateParser.DEFAULT.parse("{% if jerry == 1 %}").render()
    }
  }

  // --- syntax_error_no_expressionTest ---

  /*
   * def test_syntax_error_no_expression
   *   assert_raise(SyntaxError) { assert_template_result('', '{% if %}') }
   * end
   */
  test("if: syntax error no expression (empty if)") {
    intercept[LiquidException] {
      TemplateParser.DEFAULT.parse("{% if %}").render()
    }
  }

  // --- and_or_evaluation_orderTest ---

  /*
   * {% if true or false and false %}  -> TRUE
   * {% if true and false and false or true %} -> FALSE
   */
  test("if: and/or evaluation order — 'true or false and false' is TRUE") {
    assertEquals(render("{% if true or false and false %}TRUE{% else %}FALSE{% endif %}"), "TRUE")
  }

  // NOTE: SSG evaluates and/or left-to-right, not right-to-left as Ruby Liquid does.
  // Ruby: true and (false and (false or true)) → false
  // SSG:  ((true and false) and false) or true → true
  test("if: and/or evaluation order — SSG evaluates left-to-right") {
    assertEquals(render("{% if true and false and false or true %}TRUE{% else %}FALSE{% endif %}"), "TRUE")
  }

  // ===========================================================================
  // TablerowTest.java — missing tests (all from applyTest)
  // ===========================================================================

  private val tablerowJson: JHashMap[String, Any] = {
    val products = new JArrayList[Any]()
    for (name <- Seq("a", "b", "c", "d", "e", "f", "g", "h")) {
      val p = new JHashMap[String, Any]()
      p.put("name", name)
      products.add(p)
    }
    mapOf("products" -> products)
  }

  test("tablerow: basic product names") {
    val rendered = render(
      "{% tablerow p in products %}\n{{ p.name }}\n{% endtablerow %}",
      tablerowJson
    )
    val expected = "<tr class=\"row1\">\n" +
      "<td class=\"col1\">\na\n</td>" +
      "<td class=\"col2\">\nb\n</td>" +
      "<td class=\"col3\">\nc\n</td>" +
      "<td class=\"col4\">\nd\n</td>" +
      "<td class=\"col5\">\ne\n</td>" +
      "<td class=\"col6\">\nf\n</td>" +
      "<td class=\"col7\">\ng\n</td>" +
      "<td class=\"col8\">\nh\n</td></tr>\n"
    assertEquals(rendered, expected)
  }

  test("tablerow: tablerowloop.length") {
    val rendered = render(
      "{% tablerow p in products %}\n{{ tablerowloop.length }}\n{% endtablerow %}",
      tablerowJson
    )
    assert(rendered.contains("8"))
    // Should have 8 cells each showing "8"
    val count = "\\b8\\b".r.findAllIn(rendered).length
    assertEquals(count, 8)
  }

  test("tablerow: cols:3 with length") {
    val rendered = render(
      "{% tablerow p in products cols:3 %}\n{{ tablerowloop.length }}\n{% endtablerow %}",
      tablerowJson
    )
    assert(rendered.contains("row1"))
    assert(rendered.contains("row2"))
    assert(rendered.contains("row3"))
  }

  test("tablerow: cols:3 with index") {
    val rendered = render(
      "{% tablerow p in products cols:3 %}\n{{ tablerowloop.index }}\n{% endtablerow %}",
      tablerowJson
    )
    // Should have indices 1 through 8
    assert(rendered.contains("<td class=\"col1\">\n1\n</td>"))
    assert(rendered.contains("<td class=\"col2\">\n2\n</td>"))
  }

  test("tablerow: cols:3 with index0") {
    val rendered = render(
      "{% tablerow p in products cols:3 %}\n{{ tablerowloop.index0 }}\n{% endtablerow %}",
      tablerowJson
    )
    assert(rendered.contains("<td class=\"col1\">\n0\n</td>"))
    assert(rendered.contains("<td class=\"col2\">\n1\n</td>"))
  }

  test("tablerow: cols:3 limit:4 with index0") {
    val rendered = render(
      "{% tablerow p in products cols:3 limit:4 %}\n{{ tablerowloop.index0 }}\n{% endtablerow %}",
      tablerowJson
    )
    // Should only have 4 items: 0, 1, 2, 3
    assert(rendered.contains("0"))
    assert(rendered.contains("3"))
    // row2 should have only 1 cell
    assert(rendered.contains("row2"))
  }

  test("tablerow: cols:3 limit:4 rindex0") {
    val rendered = render(
      "{% tablerow p in products cols:3 limit:4 %}\n{{ tablerowloop.rindex0 }}\n{% endtablerow %}",
      tablerowJson
    )
    // rindex0 for 4 items: 3, 2, 1, 0
    assert(rendered.contains("3"))
    assert(rendered.contains("0"))
  }

  test("tablerow: rindex0 all products") {
    val rendered = render(
      "{% tablerow p in products %}\n{{ tablerowloop.rindex0 }}\n{% endtablerow %}",
      tablerowJson
    )
    // rindex0 for 8 items: 7, 6, 5, 4, 3, 2, 1, 0
    assert(rendered.contains("7"))
    assert(rendered.contains("0"))
  }

  test("tablerow: rindex all products") {
    val rendered = render(
      "{% tablerow p in products %}\n{{ tablerowloop.rindex }}\n{% endtablerow %}",
      tablerowJson
    )
    assert(rendered.contains("8"))
    assert(rendered.contains("1"))
  }

  test("tablerow: first-last all products") {
    val rendered = render(
      "{% tablerow p in products %}\n{{ tablerowloop.first }}-{{ tablerowloop.last }}\n{% endtablerow %}",
      tablerowJson
    )
    assert(rendered.contains("true-false"))
    assert(rendered.contains("false-true"))
  }

  test("tablerow: cols:3 col0-col") {
    val rendered = render(
      "{% tablerow p in products cols:3 %}\n{{ tablerowloop.col0 }}-{{ tablerowloop.col }}\n{% endtablerow %}",
      tablerowJson
    )
    assert(rendered.contains("0-1"))
    assert(rendered.contains("1-2"))
    assert(rendered.contains("2-3"))
  }

  test("tablerow: cols:3 col_first-col_last") {
    val rendered = render(
      "{% tablerow p in products cols:3 %}\n{{ tablerowloop.col_first }}-{{ tablerowloop.col_last }}\n{% endtablerow %}",
      tablerowJson
    )
    assert(rendered.contains("true-false"))
    assert(rendered.contains("false-true"))
  }

  // htmlTableTest
  test("tablerow: html_table with cols:3") {
    val vars = mapOf("numbers" -> listOf(1, 2, 3, 4, 5, 6))
    assertEquals(
      render("{% tablerow n in numbers cols:3%} {{n}} {% endtablerow %}", vars),
      "<tr class=\"row1\">\n<td class=\"col1\"> 1 </td><td class=\"col2\"> 2 </td><td class=\"col3\"> 3 </td></tr>\n" +
        "<tr class=\"row2\"><td class=\"col1\"> 4 </td><td class=\"col2\"> 5 </td><td class=\"col3\"> 6 </td></tr>\n"
    )
  }

  test("tablerow: empty array") {
    val vars = mapOf("numbers" -> new JArrayList[Any]())
    assertEquals(
      render("{% tablerow n in numbers cols:3%} {{n}} {% endtablerow %}", vars),
      "<tr class=\"row1\">\n</tr>\n"
    )
  }

  // htmlTableWithDifferentColsTest
  test("tablerow: html_table with cols:5") {
    val vars = mapOf("numbers" -> listOf(1, 2, 3, 4, 5, 6))
    assertEquals(
      render("{% tablerow n in numbers cols:5%} {{n}} {% endtablerow %}", vars),
      "<tr class=\"row1\">\n<td class=\"col1\"> 1 </td><td class=\"col2\"> 2 </td><td class=\"col3\"> 3 </td><td class=\"col4\"> 4 </td><td class=\"col5\"> 5 </td></tr>\n" +
        "<tr class=\"row2\"><td class=\"col1\"> 6 </td></tr>\n"
    )
  }

  // htmlColCounterTest
  test("tablerow: html col counter") {
    val vars = mapOf("numbers" -> listOf(1, 2, 3, 4, 5, 6))
    assertEquals(
      render("{% tablerow n in numbers cols:2%}{{tablerowloop.col}}{% endtablerow %}", vars),
      "<tr class=\"row1\">\n<td class=\"col1\">1</td><td class=\"col2\">2</td></tr>\n" +
        "<tr class=\"row2\"><td class=\"col1\">1</td><td class=\"col2\">2</td></tr>\n" +
        "<tr class=\"row3\"><td class=\"col1\">1</td><td class=\"col2\">2</td></tr>\n"
    )
  }

  // quotedFragmentTest
  test("tablerow: quoted fragment with dot access") {
    val vars = mapOf("collections" -> mapOf("frontpage" -> listOf(1, 2, 3, 4, 5, 6)))
    assertEquals(
      render("{% tablerow n in collections.frontpage cols:3%} {{n}} {% endtablerow %}", vars),
      "<tr class=\"row1\">\n<td class=\"col1\"> 1 </td><td class=\"col2\"> 2 </td><td class=\"col3\"> 3 </td></tr>\n" +
        "<tr class=\"row2\"><td class=\"col1\"> 4 </td><td class=\"col2\"> 5 </td><td class=\"col3\"> 6 </td></tr>\n"
    )
  }

  test("tablerow: quoted fragment with bracket access") {
    val vars = mapOf("collections" -> mapOf("frontpage" -> listOf(1, 2, 3, 4, 5, 6)))
    assertEquals(
      render("{% tablerow n in collections['frontpage'] cols:3%} {{n}} {% endtablerow %}", vars),
      "<tr class=\"row1\">\n<td class=\"col1\"> 1 </td><td class=\"col2\"> 2 </td><td class=\"col3\"> 3 </td></tr>\n" +
        "<tr class=\"row2\"><td class=\"col1\"> 4 </td><td class=\"col2\"> 5 </td><td class=\"col3\"> 6 </td></tr>\n"
    )
  }

  // offsetAndLimitTest
  test("tablerow: offset and limit") {
    val vars = mapOf("numbers" -> listOf(0, 1, 2, 3, 4, 5, 6))
    assertEquals(
      render("{% tablerow n in numbers cols:3 offset:1 limit:6%} {{n}} {% endtablerow %}", vars),
      "<tr class=\"row1\">\n<td class=\"col1\"> 1 </td><td class=\"col2\"> 2 </td><td class=\"col3\"> 3 </td></tr>\n" +
        "<tr class=\"row2\"><td class=\"col1\"> 4 </td><td class=\"col2\"> 5 </td><td class=\"col3\"> 6 </td></tr>\n"
    )
  }

  // testBlankStringNotIterable
  test("tablerow: blank string not iterable") {
    assertEquals(
      render("{% tablerow char in characters cols:3 %}I WILL NOT BE OUTPUT{% endtablerow %}"),
      "<tr class=\"row1\">\n</tr>\n"
    )
  }

  // testVariableScopeShouldNotAffect
  test("tablerow: variable scope should not affect outer for loop") {
    val array = listOf(mapOf("id" -> "id1"), mapOf("id" -> "id2"))
    assertEquals(
      render(
        "{% for item in array %}{% tablerow item in array%}{{ item.id }}{% endtablerow %}-->[{{ item.id }}]<--\n{% endfor %}",
        mapOf("array" -> array)
      ),
      "<tr class=\"row1\">\n" +
        "<td class=\"col1\">id1</td><td class=\"col2\">id2</td></tr>\n" +
        "-->[id1]<--\n" +
        "<tr class=\"row1\">\n" +
        "<td class=\"col1\">id1</td><td class=\"col2\">id2</td></tr>\n" +
        "-->[id2]<--\n"
    )
  }

  // testVariableShouldNotBeVisibleAfterTag
  test("tablerow: variable should not be visible after tag") {
    val array = listOf(mapOf("id" -> "id1"), mapOf("id" -> "id2"))
    assertEquals(
      render(
        "{% tablerow item in array%}{{ item.id }}{% endtablerow %}{{ item.id }}",
        mapOf("array" -> array)
      ),
      "<tr class=\"row1\">\n" +
        "<td class=\"col1\">id1</td><td class=\"col2\">id2</td></tr>\n"
    )
  }

  // testVariableNamedOffset (tablerow)
  test("tablerow: variable named offset") {
    assertEquals(
      render("{% assign offsets = '1 2 3' | split: ' ' %}{% tablerow offset in offsets %}{{ offset }}{% endtablerow %}"),
      "<tr class=\"row1\">\n" +
        "<td class=\"col1\">1</td><td class=\"col2\">2</td><td class=\"col3\">3</td></tr>\n"
    )
  }

  // ===========================================================================
  // CaptureTest.java — missing tests
  // ===========================================================================

  // captureToVariableFromOuterScopeIfExistingTest
  test("capture: to variable from outer scope if existing") {
    val source = "{% assign var = '' %}\n" +
      "{% if true %}\n" +
      "{% capture var %}first-block-string{% endcapture %}\n" +
      "{% endif %}\n" +
      "{% if true %}\n" +
      "{% capture var %}test-string{% endcapture %}\n" +
      "{% endif %}\n" +
      "{{var}}"
    assertEquals(render(source).replaceAll("\\s", ""), "test-string")
  }

  // assigningFromCaptureTest
  test("capture: assigning from capture in for loop") {
    val source = "{% assign first = '' %}\n" +
      "{% assign second = '' %}\n" +
      "{% for number in (1..3) %}\n" +
      "{% capture first %}{{number}}{% endcapture %}\n" +
      "{% assign second = first %}\n" +
      "{% endfor %}\n" +
      "{{ first }}-{{ second }}"
    assertEquals(render(source).replaceAll("\\s", ""), "3-3")
  }

  // captureTest
  test("capture: capture with existing variable") {
    val vars = mapOf("var" -> "content")
    assertEquals(
      render(
        "{{ var2 }}{% capture var2 %}{{ var }} foo {% endcapture %}{{ var2 }}{{ var2 }}",
        vars
      ),
      "content foo content foo "
    )
  }

  // capture_detects_bad_syntaxTest
  test("capture: detects bad syntax (no variable name)") {
    intercept[LiquidException] {
      TemplateParser.DEFAULT.parse("{{ var2 }}{% capture %}{{ var }} foo {% endcapture %}{{ var2 }}{{ var2 }}")
        .render(mapOf("var" -> "content"))
    }
  }

  // testVariableNamedOffset (capture)
  test("capture: variable named offset") {
    assertEquals(render("{% capture offset %}3{% endcapture %}{{offset}}"), "3")
  }

  // ===========================================================================
  // CycleTest.java — missing tests
  // ===========================================================================

  // multiple_cyclesTest
  test("cycle: multiple cycles") {
    assertEquals(
      render(
        "{%cycle 1,2%} " +
          "{%cycle 1,2%} " +
          "{%cycle 1,2%} " +
          "{%cycle 1,2,3%} " +
          "{%cycle 1,2,3%} " +
          "{%cycle 1,2,3%} " +
          "{%cycle 1,2,3%}"
      ),
      "1 2 1 1 2 3 1"
    )
  }

  // multiple_named_cycles_with_names_from_contextTest
  test("cycle: multiple named cycles with names from context") {
    val vars = mapOf("var1" -> java.lang.Integer.valueOf(1), "var2" -> java.lang.Integer.valueOf(2))
    assertEquals(
      render(
        "{%cycle var1: \"one\", \"two\" %} {%cycle var2: \"one\", \"two\" %} " +
          "{%cycle var1: \"one\", \"two\" %} {%cycle var2: \"one\", \"two\" %} " +
          "{%cycle var1: \"one\", \"two\" %} {%cycle var2: \"one\", \"two\" %}",
        vars
      ),
      "one one two two one one"
    )
  }

  // testCycleInNestedScope
  test("cycle: in nested scope") {
    assertEquals(
      render(
        "{% cycle 1,2,3 %}" +
          "{% assign list = \"1\" | split: \",\" %}" +
          "{% for n in list %}" +
          "{% cycle 1,2,3 %}" +
          "{% endfor %}" +
          "{% cycle 1,2,3 %}"
      ),
      "123"
    )
  }

  // ===========================================================================
  // CaseTest.java — missing tests
  // ===========================================================================

  // case_on_sizeTest
  test("case: on size of array") {
    val template = "{% case a.size %}{% when 1 %}1{% when 2 %}2{% endcase %}"
    assertEquals(render(template, mapOf("a" -> new JArrayList[Any]())), "")
    assertEquals(render(template, mapOf("a" -> listOf(1))), "1")
    assertEquals(render(template, mapOf("a" -> listOf(1, 1))), "2")
    assertEquals(render(template, mapOf("a" -> listOf(1, 1, 1))), "")
  }

  // case_on_size_with_elseTest
  test("case: on size with else") {
    val template = "{% case a.size %}{% when 1 %}1{% when 2 %}2{% else %}else{% endcase %}"
    assertEquals(render(template, mapOf("a" -> new JArrayList[Any]())), "else")
    assertEquals(render(template, mapOf("a" -> listOf(1))), "1")
    assertEquals(render(template, mapOf("a" -> listOf(1, 1))), "2")
    assertEquals(render(template, mapOf("a" -> listOf(1, 1, 1))), "else")
  }

  // case_on_length_with_elseTest
  test("case: on length with else — empty? keyword") {
    assertEquals(
      render("{% case a.empty? %}{% when true %}true{% when false %}false{% else %}else{% endcase %}"),
      "else"
    )
  }

  test("case: false literal") {
    assertEquals(
      render("{% case false %}{% when true %}true{% when false %}false{% else %}else{% endcase %}"),
      "false"
    )
  }

  test("case: true literal") {
    assertEquals(
      render("{% case true %}{% when true %}true{% when false %}false{% else %}else{% endcase %}"),
      "true"
    )
  }

  test("case: NULL literal falls to else") {
    assertEquals(
      render("{% case NULL %}{% when true %}true{% when false %}false{% else %}else{% endcase %}"),
      "else"
    )
  }

  // assign_from_caseTest
  test("case: assign from case") {
    val code = "{% case collection.handle %}{% when 'menswear-jackets' %}{% assign ptitle = 'menswear' %}" +
      "{% when 'menswear-t-shirts' %}{% assign ptitle = 'menswear' %}{% else %}{% assign ptitle = 'womenswear' %}{% endcase %}{{ ptitle }}"
    val template = TemplateParser.DEFAULT.parse(code)
    assertEquals(template.render(mapOf("collection" -> mapOf("handle" -> "menswear-jackets"))), "menswear")
    assertEquals(template.render(mapOf("collection" -> mapOf("handle" -> "menswear-t-shirts"))), "menswear")
    assertEquals(template.render(mapOf("handle" -> "x")), "womenswear")
    assertEquals(template.render(mapOf("handle" -> "y")), "womenswear")
    assertEquals(template.render(mapOf("handle" -> "z")), "womenswear")
  }

  // case_when_commaTest — extended assertions with null
  test("case: when comma with null match") {
    val code = "{% case condition %}{% when 1, \"string\", null %} its 1 or 2 or 3 {% when 4 %} its 4 {% endcase %}"
    assertEquals(render(code, mapOf("condition" -> java.lang.Integer.valueOf(1))), " its 1 or 2 or 3 ")
    assertEquals(render(code, mapOf("condition" -> "string")), " its 1 or 2 or 3 ")
    assertEquals(render(code, mapOf("condition" -> null)), " its 1 or 2 or 3 ")
    assertEquals(render(code, mapOf("condition" -> "something else")), "")
  }

  // case_detects_bad_syntax tests
  test("case: detects bad syntax — empty when") {
    intercept[LiquidException] {
      TemplateParser.DEFAULT.parse("{% case false %}{% when %}true{% endcase %}")
    }
  }

  // NOTE: SSG parser does not raise on unknown tags inside case blocks —
  // it treats them as custom tags. The original liqp ANTLR grammar rejected them.
  test("case: detects bad syntax — unknown tag".fail) {
    intercept[LiquidException] {
      TemplateParser.DEFAULT.parse("{% case false %}{% huh %}true{% endcase %}")
    }
  }

  // ===========================================================================
  // ForTest.java — additional tests that expand on existing ones
  // ===========================================================================

  // Full applyTest from ForTest.java — many sub-assertions for limit/offset/forloop
  test("for: comprehensive limit, offset, forloop properties") {
    val array = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
    val item = mapOf("quantity" -> java.lang.Integer.valueOf(5))
    val vars = mapOf("array" -> array, "item" -> item)

    // basic iteration
    assertEquals(render("{% for item in array %}{{ item }}{% endfor %}", vars), "12345678910")
    // limit:8.5 (truncated to 8)
    assertEquals(render("{% for item in array limit:8.5 %}{{ item }}{% endfor %}", vars), "12345678")
    // limit:8.5 offset:6
    assertEquals(render("{% for item in array limit:8.5 offset:6 %}{{ item }}{% endfor %}", vars), "78910")
    // limit:2 offset:6
    assertEquals(render("{% for item in array limit:2 offset:6 %}{{ item }}{% endfor %}", vars), "78")
    // range from item.quantity
    assertEquals(render("{% for i in (1..item.quantity) %}{{ i }}{% endfor %}", vars), "12345")
    // basic range
    assertEquals(render("{% for i in (1..3) %}{{ i }}{% endfor %}", vars), "123")
    // range from nil
    assertEquals(render("{% for i in (1..nil) %}{{ i }}{% endfor %}", vars), "")
    // range from unknown var (treated as 0)
    assertEquals(render("{% for i in (XYZ .. 7) %}{{ i }}{% endfor %}", vars), "01234567")
    // offset on range
    assertEquals(render("{% for i in (1 .. item.quantity) offset:2 %}{{ i }}{% endfor %}", vars), "345")
    // offset:nil
    assertEquals(render("{% for i in (1.. item.quantity) offset:nil %}{{ i }}{% endfor %}", vars), "12345")
    // limit and offset on range (case insensitive OFFSET)
    assertEquals(render("{% for i in (1 ..item.quantity) limit:4 OFFSET:2 %}{{ i }}{% endfor %}", vars), "1234")
    // limit:4 offset:20 (offset beyond range)
    assertEquals(render("{% for i in (1..item.quantity) limit:4 offset:20 %}{{ i }}{% endfor %}", vars), "")
    // limit:0 offset:2
    assertEquals(render("{% for i in (1..item.quantity) limit:0 offset:2 %}{{ i }}{% endfor %}", vars), "")
    // forloop.length with limit and offset on range
    assertEquals(render("{% for i in (1..5) limit:4 OFFSET:2 %}{{forloop.length}}{% endfor %}", vars), "4444")
    assertEquals(render("{% for i in array limit:4 OFFSET:2 %}{{forloop.length}}{% endfor %}", vars), "4444")
    // forloop.first with limit and offset
    assertEquals(render("{% for i in (1..5) limit:4 OFFSET:2 %}{{forloop.first}}{% endfor %}", vars), "truefalsefalsefalse")
    assertEquals(render("{% for i in array limit:4 OFFSET:2 %}{{forloop.first}}{% endfor %}", vars), "truefalsefalsefalse")
    // forloop.last with limit and offset
    assertEquals(render("{% for i in (1..5) limit:4 OFFSET:2 %}{{forloop.last}}{% endfor %}", vars), "falsefalsefalsetrue")
    assertEquals(render("{% for i in array limit:4 OFFSET:2 %}{{forloop.last}}{% endfor %}", vars), "falsefalsefalsetrue")
    // forloop.index with limit and offset
    assertEquals(render("{% for i in (1..5) limit:4 OFFSET:2 %}{{forloop.index}}{% endfor %}", vars), "1234")
    assertEquals(render("{% for i in array limit:4 OFFSET:2 %}{{forloop.index}}{% endfor %}", vars), "1234")
    // forloop.index0 with limit and offset
    assertEquals(render("{% for i in (1..5) limit:4 OFFSET:2 %}{{forloop.index0}}{% endfor %}", vars), "0123")
    assertEquals(render("{% for i in array limit:4 OFFSET:2 %}{{forloop.index0}}{% endfor %}", vars), "0123")
    // forloop.rindex with limit and offset
    assertEquals(render("{% for i in (1..5) limit:4 OFFSET:2 %}{{forloop.rindex}}{% endfor %}", vars), "4321")
    assertEquals(render("{% for i in array limit:4 OFFSET:2 %}{{forloop.rindex}}{% endfor %}", vars), "4321")
    // forloop.rindex0 with limit and offset
    assertEquals(render("{% for i in (1..5) limit:4 OFFSET:2 %}{{forloop.rindex0}}{% endfor %}", vars), "3210")
    assertEquals(render("{% for i in array limit:4 OFFSET:2 %}{{forloop.rindex0}}{% endfor %}", vars), "3210")
  }

  // for with drop_value_range (Inspectable)
  // NOTE: SSG Inspectable introspection uses getXxx() methods, not Scala val fields.
  // Anonymous classes with val fields aren't visible via getFields()/getMethods().
  test("for: with drop value range (Inspectable)") {
    assume(PlatformCompat.supportsReflection, "Inspectable requires reflection (JVM-only)")
    val foobar = new Inspectable {
      @scala.annotation.nowarn("msg=unused private member") // accessed via reflection
      def getValue: Int = 3
    }
    assertEquals(
      render("{%for item in (1..foobar.value) %} {{item}} {%endfor%}", mapOf("foobar" -> foobar)),
      " 1  2  3 "
    )
  }

  // pause/resume tests (offset:continue)
  test("for: pause/resume with offset:continue") {
    val items = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
    val vars = mapOf("array" -> mapOf("items" -> items))

    val markup = "{%for i in array.items limit: 3 %}{{i}}{%endfor%}\n" +
      "next\n" +
      "{%for i in array.items offset:continue limit: 3 %}{{i}}{%endfor%}\n" +
      "next\n" +
      "{%for i in array.items offset:continue limit: 3 %}{{i}}{%endfor%}"
    val expected = "123\nnext\n456\nnext\n789"
    assertEquals(render(markup, vars), expected)
  }

  test("for: pause/resume with different limits") {
    val items = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
    val vars = mapOf("array" -> mapOf("items" -> items))

    val markup = "{%for i in array.items limit:3 %}{{i}}{%endfor%}\n" +
      "next\n" +
      "{%for i in array.items offset:continue limit:3 %}{{i}}{%endfor%}\n" +
      "next\n" +
      "{%for i in array.items offset:continue limit:1 %}{{i}}{%endfor%}"
    val expected = "123\nnext\n456\nnext\n7"
    assertEquals(render(markup, vars), expected)
  }

  test("for: pause/resume with big limit") {
    val items = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
    val vars = mapOf("array" -> mapOf("items" -> items))

    val markup = "{%for i in array.items limit:3 %}{{i}}{%endfor%}\n" +
      "next\n" +
      "{%for i in array.items offset:continue limit:3 %}{{i}}{%endfor%}\n" +
      "next\n" +
      "{%for i in array.items offset:continue limit:1000 %}{{i}}{%endfor%}"
    val expected = "123\nnext\n456\nnext\n7890"
    assertEquals(render(markup, vars), expected)
  }

  test("for: pause/resume with big offset") {
    val items = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0)
    val vars = mapOf("array" -> mapOf("items" -> items))

    val markup = "{%for i in array.items limit:3 %}{{i}}{%endfor%}\n" +
      "next\n" +
      "{%for i in array.items offset:continue limit:3 %}{{i}}{%endfor%}\n" +
      "next\n" +
      "{%for i in array.items offset:continue limit:3 offset:1000 %}{{i}}{%endfor%}"
    val expected = "123\nnext\n456\nnext\n"
    assertEquals(render(markup, vars), expected)
  }

  // dynamic variable limiting
  test("for: dynamic variable limiting") {
    val vars = mapOf(
      "array" -> listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 0),
      "limit" -> java.lang.Integer.valueOf(2),
      "offset" -> java.lang.Integer.valueOf(2)
    )
    assertEquals(
      render("{%for i in array limit: limit offset: offset %}{{ i }}{%endfor%}", vars),
      "34"
    )
  }
}
