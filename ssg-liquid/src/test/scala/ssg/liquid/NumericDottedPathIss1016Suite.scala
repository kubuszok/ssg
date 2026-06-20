/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

/** Numeric dotted path access (ISS-1016).
  *
  * liqp's Id lexer rule (LiquidLexer.g4:186) matches a leading digit, so `a.0` is parsed
  * as `lookup(id="a", index=Dot id2("0"))` — a Hash lookup with the number's text as the key
  * (NodeVisitor.java:813). ssg's lexer tokenizes `0` as LONG_NUM and `1.` as DOUBLE_NUM,
  * which must be accepted in the DOT-index branch of parseLookup and split into Hash segments
  * matching liqp's IdChain behavior (LiquidLexer.g4:182-184).
  *
  * Reference: liqp LookupNodeTest.java:300-306 `numberAsKeyTest`.
  */
final class NumericDottedPathIss1016Suite extends munit.FunSuite {

  // ---- liqp oracle: LookupNodeTest.java:300-306 ----

  test("{{ Data.1.Value }} looks up numeric key in nested hash (liqp numberAsKeyTest)") {
    // liqp LookupNodeTest.java:301-306:
    //   data = { 'Data' => { '1' => { 'Value' => 'tobi' }} }
    //   @template = Liquid::TemplateParser.DEFAULT.parse("hi {{Data.1.Value}}")
    //   assertThat(...render(assigns), is("hi tobi"))
    //
    // Tokenization: ssg lexer produces DOUBLE_NUM("1.") for `1.Value` because
    // scanNumber greedily consumes the dot (there are no digits after it).
    // The parser must split this into Hash("1") + consume Id("Value") as Hash("Value").
    val inner = TestHelper.mapOf("Value" -> "tobi")
    val vars  = TestHelper.mapOf("Data" -> TestHelper.mapOf("1" -> inner))
    val template = Template.parse("hi {{Data.1.Value}}")
    assertEquals(template.render(vars), "hi tobi")
  }

  // ---- LONG_NUM after DOT: simple numeric key ----

  test("{{ h.0 }} looks up key '0' in a hash") {
    // LONG_NUM("0") after DOT → Hash("0"), map lookup
    val vars = TestHelper.mapOf("h" -> TestHelper.mapOf("0" -> "zero", "1" -> "one"))
    val template = Template.parse("{{ h.0 }}")
    assertEquals(template.render(vars), "zero")
  }

  test("{{ h.1 }} looks up key '1' in a hash") {
    val vars = TestHelper.mapOf("h" -> TestHelper.mapOf("0" -> "zero", "1" -> "one"))
    val template = Template.parse("{{ h.1 }}")
    assertEquals(template.render(vars), "one")
  }

  test("{{ h.42 }} looks up multi-digit numeric key") {
    val vars = TestHelper.mapOf("h" -> TestHelper.mapOf("42" -> "answer"))
    val template = Template.parse("{{ h.42 }}")
    assertEquals(template.render(vars), "answer")
  }

  // ---- DOUBLE_NUM after DOT: embedded dot splitting ----

  test("{{ m.1.2 }} splits DOUBLE_NUM into two Hash segments") {
    // ssg lexer: `1.2` after DOT → DOUBLE_NUM("1.2"). The parser splits at '.'
    // to produce Hash("1"), Hash("2"), matching liqp IdChain semantics.
    val inner = TestHelper.mapOf("2" -> "nested")
    val vars  = TestHelper.mapOf("m" -> TestHelper.mapOf("1" -> inner))
    val template = Template.parse("{{ m.1.2 }}")
    assertEquals(template.render(vars), "nested")
  }

  // ---- list dot-access returns empty (faithful to liqp) ----

  test("{{ a.0 }} on a list returns empty (liqp Hash does not index lists by number)") {
    // liqp Hash.get (LookupNode.java:87-151): for Collections, only size/first/last
    // are recognized; all other hash keys return null. Use a[0] for index access.
    val vars     = TestHelper.mapOf("a" -> TestHelper.listOf("x", "y", "z"))
    val template = Template.parse("{{ a.0 }}")
    assertEquals(template.render(vars), "")
  }
}
