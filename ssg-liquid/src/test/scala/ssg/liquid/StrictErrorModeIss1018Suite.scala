/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.liquid.exceptions.LiquidException
import ssg.liquid.parser.Flavor

/** Differential tests for ISS-1018: faithful `ErrorMode` behavior, split into the two
  * distinct liqp mechanisms.
  *
  * liqp oracle (original-src/liqp):
  *
  *   1. Output-tag trailing content is gated by the errorMode predicates on the `output`
  *      rule (LiquidParser.g4:229-233):
  *        output
  *          : {isEvaluateInOutputTag()}? outStart evaluate=expr filter* OutEnd
  *          | {isStrict()}?              outStart term       filter* OutEnd
  *          | {isWarn() || isLax()}?     outStart term       filter* unparsed=not_out_end? OutEnd
  *      Under STRICT (g4:231) there is NO `unparsed` alternative, so trailing junk fails the
  *      rule, the ANTLR error listener fires and Template.java:91-98 throws unconditionally.
  *      Under WARN/LAX (g4:232) the junk is captured as `unparsed`: WARN records an
  *      "unexpected output" error and still renders (GtNodeTest:101-114 asserts the rendered
  *      output AND that errors.get(0).getMessage().contains("unexpected output")); LAX renders
  *      silently with no error.
  *
  *   2. Built-in block ends (if_tag g4:134 `... TagStart IfEnd TagEnd`, and likewise
  *      unless/case/for/tablerow/capture) and top-level EOF appear in rules that carry NO
  *      errorMode predicate, so the end token is REQUIRED in all modes. A missing/mismatched
  *      end produces an ANTLR error and Template.java:91-98 throws unconditionally — under
  *      STRICT, WARN and LAX alike.
  *
  * SSG ports (1) in OutputNode.render (dispatching on context.parser.errorMode) and (2) in
  * LiquidParser.expect (which throws unconditionally for every built-in block-end / EOF).
  *
  * Construct choice: under the JEKYLL flavor `evaluateInOutputTag` is false (Flavor.scala:54),
  * so `{{ 98 > 97 }}` parses the leading `term` (`98`) and leaves `> 97` as `unparsed` — the
  * exact construct GtNodeTest:101-114 uses. We hold the flavor at JEKYLL and vary only the
  * error mode via withErrorMode so the three branches are isolated.
  */
final class StrictErrorModeIss1018Suite extends munit.FunSuite {

  private def parser(mode: TemplateParser.ErrorMode): TemplateParser =
    new TemplateParser.Builder().withFlavor(Flavor.JEKYLL).withErrorMode(mode).build()

  // --- (1) output-tag trailing junk: STRICT throws, WARN renders+records, LAX renders silent ---

  test("ISS-1018 output-tag STRICT: trailing junk throws 'unexpected output'") {
    val ex = intercept[LiquidException] {
      parser(TemplateParser.ErrorMode.STRICT).parse("{{ 98 > 97 }}").render()
    }
    assert(
      ex.getMessage.contains("unexpected output"),
      s"expected 'unexpected output' message, got: ${ex.getMessage}"
    )
  }

  test("ISS-1018 output-tag WARN: trailing junk renders AND records an 'unexpected output' error") {
    val holder = new Template.ContextHolder()
    val res    = parser(TemplateParser.ErrorMode.WARN).parse("{{ 98 > 97 }}").withContextHolder(holder).render()
    assertEquals(res, "98")
    val errors = holder.getContext.errors()
    assertEquals(errors.size(), 1)
    assert(
      errors.get(0).getMessage.contains("unexpected output"),
      s"expected 'unexpected output' message, got: ${errors.get(0).getMessage}"
    )
  }

  test("ISS-1018 output-tag LAX: trailing junk renders silently with no recorded error") {
    val holder = new Template.ContextHolder()
    val res    = parser(TemplateParser.ErrorMode.LAX).parse("{{ 98 > 97 }}").withContextHolder(holder).render()
    assertEquals(res, "98")
    assertEquals(holder.getContext.errors().size(), 0)
  }

  // --- (2) built-in block missing its end: throws in ALL modes (required-end grammar) ---

  test("ISS-1018 missing endif STRICT: throws") {
    intercept[LiquidException] {
      parser(TemplateParser.ErrorMode.STRICT).parse("{% if true %}yes").render()
    }
  }

  test("ISS-1018 missing endif WARN: throws (built-in end required in all modes)") {
    intercept[LiquidException] {
      parser(TemplateParser.ErrorMode.WARN).parse("{% if true %}yes").render()
    }
  }

  test("ISS-1018 missing endif LAX: throws (built-in end required in all modes)") {
    intercept[LiquidException] {
      parser(TemplateParser.ErrorMode.LAX).parse("{% if true %}yes").render()
    }
  }

  // A mismatched built-in end (endfor closing an if) must also throw in all modes:
  // if_tag (g4:134) requires IfEnd, so an endfor fails the required-token rule.
  test("ISS-1018 mismatched end WARN: {% if %}...{% endfor %} throws") {
    intercept[LiquidException] {
      parser(TemplateParser.ErrorMode.WARN).parse("{% if true %}yes{% endfor %}").render()
    }
  }

  test("ISS-1018 mismatched end LAX: {% if %}...{% endfor %} throws") {
    intercept[LiquidException] {
      parser(TemplateParser.ErrorMode.LAX).parse("{% if true %}yes{% endfor %}").render()
    }
  }
}
