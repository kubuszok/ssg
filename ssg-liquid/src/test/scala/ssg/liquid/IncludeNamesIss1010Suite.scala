/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.liquid.antlr.NameResolver
import ssg.liquid.exceptions.LiquidException
import ssg.liquid.parser.Flavor

import java.util.{ HashMap => JHashMap }

/** Red tests for ISS-1010: unquoted dotted/slashed include names must parse in
  * the default Jekyll flavor.
  *
  * Per the liqp grammar (original-src/liqp/src/main/antlr4/liquid/parser/v4/):
  *   - LiquidLexer.g4:157  — `PathSep : [/\\];` tokenizes path separators.
  *   - LiquidLexer.g4:182-184 — `IdChain : [a-zA-Z_] [a-zA-Z_0-9]* ( '.'
  *     [a-zA-Z_0-9]+ )+ {handleIdChain(getText());} -> skip;` re-emits dotted
  *     names as Id/Dot/Id token runs.
  *   - LiquidLexer.g4:186 — `Id : ( Letter | '_' | Digit) (Letter | '_' | '-' |
  *     Digit)*;` so hyphens are allowed mid-name.
  *   - LiquidParser.g4:206-209 — Jekyll-style include:
  *     `TagStart jekyll=Include file_name_or_output (jekyll_include_params)* TagEnd`.
  *   - LiquidParser.g4:219-222 — `file_name_or_output : output | filename;`
  *     with `filename : ( . )+?` (LiquidParser.g4:359-361), so any token run —
  *     including Id Dot Id and Id PathSep Id — is a valid unquoted file name.
  *   - LiquidParser.g4:225-227 — `jekyll_include_params : id '=' expr;`.
  *
  * Whitespace handling (the bounce area). liqp lexes WS to a HIDDEN channel
  * (LiquidLexer.g4:166) then reassembles the RAW source interval spanning the
  * filename token run and REJECTS any whitespace it finds
  * (original-src/liqp/src/main/java/liqp/parser/v4/NodeVisitor.java:511-526):
  * {{{
  *   Interval interval = Interval.of(ctx.filename().start.getStartIndex(),
  *                                   ctx.filename().stop.getStopIndex());
  *   String filename = ctx.filename().start.getInputStream().getText(interval);
  *   if (filename.matches(".*\\s.*"))
  *     throw new LiquidException("in `{% include filename %}` the `filename` is {"
  *       + filename + "}, but it cannot have spaces for Flavor.JEKYLL", ctx);
  * }}}
  * So `{% include foo bar %}` is a parse-time error (NOT a `foo` name + `bar`
  * param), with the rejected interval `{foo bar}` reported between braces.
  *
  * Upstream test mirrors:
  *   - liqp IncludeTest.java:159-165 renders
  *     src/test/jekyll/index_without_quotes.html = `{% include header.html %}`.
  *   - liqp IncludeTest.java:176-182 (github.com/bkiers/Liqp issue #95) renders
  *     index_without_quotes_subdirectory.html = `{% include wmt/footer.html %}`.
  *
  * These tests assert the FIXED behavior (no `.fail` marks): the unquoted-name
  * cases are expected to FAIL until ISS-1010 is fixed (the lexer/parser has no
  * path-name handling, so `{% include footer.html %}` throws
  * `Expected TAG_END but got DOT`). Uses the default (Jekyll) flavor and the
  * in-memory NameResolver so all 3 platforms can run them.
  */
final class IncludeNamesIss1010Suite extends munit.FunSuite {

  /** Parser with the DEFAULT flavor (JEKYLL in SSG) and an in-memory resolver. */
  private def parserWith(templates: (String, String)*): TemplateParser = {
    val map = new JHashMap[String, String]()
    templates.foreach { case (name, content) => map.put(name, content) }
    new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).withNameResolver(new NameResolver.InMemory(map)).withShowExceptionsFromInclude(true).build()
  }

  // ---------------------------------------------------------------------------
  // Unquoted dotted/slashed include names (the ISS-1010 gap) — red until fixed
  // ---------------------------------------------------------------------------

  // Mirrors liqp IncludeTest.java:159-165 / index_without_quotes.html.
  // Currently throws `Expected TAG_END but got DOT`.
  test("ISS-1010: unquoted single-dot include name {% include footer.html %}") {
    val parser   = parserWith("footer.html" -> "FOOTER")
    val template = parser.parse("before {% include footer.html %} after")
    assertEquals(template.render(), "before FOOTER after")
  }

  // Mirrors liqp IncludeTest.java:176-182 / index_without_quotes_subdirectory.html
  // ({% include wmt/footer.html %}, github.com/bkiers/Liqp issue #95).
  test("ISS-1010: unquoted slashed include name {% include wmt/footer.html %}") {
    val parser   = parserWith("wmt/footer.html" -> "SUBDIR FOOTER")
    val template = parser.parse("{% include wmt/footer.html %}")
    assertEquals(template.render(), "SUBDIR FOOTER")
  }

  // Plain `dir/file` (no extension) — slash-only, exercises PathSep in isolation.
  test("ISS-1010: unquoted slashed include name {% include dir/file %}") {
    val parser   = parserWith("dir/file" -> "DIRFILE")
    val template = parser.parse("{% include dir/file %}")
    assertEquals(template.render(), "DIRFILE")
  }

  test("ISS-1010: unquoted nested-directory include name {% include dir/sub/file.html %}") {
    val parser   = parserWith("dir/sub/file.html" -> "NESTED")
    val template = parser.parse("{% include dir/sub/file.html %}")
    assertEquals(template.render(), "NESTED")
  }

  test("ISS-1010: unquoted include name with multiple dots {% include name.with.dots.html %}") {
    val parser   = parserWith("name.with.dots.html" -> "DOTS")
    val template = parser.parse("{% include name.with.dots.html %}")
    assertEquals(template.render(), "DOTS")
  }

  // jekyll_include_params (LiquidParser.g4:225-227: `id '=' expr`) after an
  // unquoted dotted file name.
  test("ISS-1010: unquoted dotted include name with parameter {% include card.html title='Hi' %}") {
    val parser   = parserWith("card.html" -> "[{{ include.title }}]")
    val template = parser.parse("{% include card.html title='Hi' %}")
    assertEquals(template.render(), "[Hi]")
  }

  test("ISS-1010: unquoted dotted include name sees outer assigns") {
    val parser   = parserWith("greet.html" -> "Hello {{ who }}!")
    val template = parser.parse("{% assign who = 'World' %}{% include greet.html %}")
    assertEquals(template.render(), "Hello World!")
  }

  // ---------------------------------------------------------------------------
  // Whitespace inside an unquoted include name is a parse-time error
  // (NodeVisitor.java:521-524). liqp reads the raw interval `foo bar`,
  // finds a space, and throws — it is NOT name `foo` + param `bar`.
  // ---------------------------------------------------------------------------

  test("ISS-1010: whitespace-separated unquoted include name {% include foo bar %} throws at parse time") {
    val parser = parserWith("foo bar" -> "WRONG", "foobar" -> "WRONG", "foo" -> "WRONG")
    val ex = intercept[LiquidException] {
      parser.parse("before {% include foo bar %} after")
    }
    assert(
      ex.getMessage.contains("it cannot have spaces for Flavor.JEKYLL"),
      s"expected liqp whitespace-rejection message, got: ${ex.getMessage}"
    )
    // The rejected name is reported between braces, like liqp's interval read —
    // the FULL run, whitespace and all (NodeVisitor.java:519-523).
    assert(
      ex.getMessage.contains("`filename` is {foo bar}"),
      s"expected the rejected name {foo bar} in the message, got: ${ex.getMessage}"
    )
  }

  // ---------------------------------------------------------------------------
  // Controls — must PASS both before and after the fix
  // ---------------------------------------------------------------------------

  test("ISS-1010 control: quoted include name {% include \"footer.html\" %} still works") {
    val parser   = parserWith("footer.html" -> "FOOTER")
    val template = parser.parse("""{% include "footer.html" %}""")
    assertEquals(template.render(), "FOOTER")
  }

  // Guards against a fix over-greedily merging dotted runs everywhere:
  // ordinary dotted variable access must still lex as Id Dot Id and resolve.
  test("ISS-1010 control: dotted variable access {{ a.b }} still resolves") {
    val parser   = parserWith()
    val template = parser.parse("{{ a.b }}")
    val vars     = TestHelper.mapOf("a" -> TestHelper.mapOf("b" -> "NESTED VALUE"))
    assertEquals(template.render(vars), "NESTED VALUE")
  }
}
