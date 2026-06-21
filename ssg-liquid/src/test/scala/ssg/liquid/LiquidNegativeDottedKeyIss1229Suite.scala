/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.liquid.exceptions.LiquidException

/** Differential tests for ISS-1229: a negative numeric segment after a dot must be rejected.
  *
  * liqp oracle (original-src/liqp/src/main/antlr4/liquid/parser/v4/LiquidLexer.g4:182-184):
  *
  * IdChain : [a-zA-Z_] [a-zA-Z_0-9]* ( '.' [a-zA-Z_0-9]+ )+ ;
  *
  * The after-dot segment charset `[a-zA-Z_0-9]+` EXCLUDES `-`, so `h.-1` does not match IdChain. Upstream liqp instead lexes `h . - 1` and the parser ERRORS on the `Dot Minus` sequence, so
  * `{{ h.-1 }}` is a parse error. SSG is currently MORE permissive: its lexer `case '-'` (LiquidLexer.scala) calls scanNumber() when a digit follows, yielding LONG_NUM("-1"), and the parser's
  * dotted-access loop (LiquidParser.scala) blindly turns that into Hash("-1"), silently accepting `{{ h.-1 }}`. Asserting at the Template.parse(...) level keeps this faithful whether the failure is
  * reported during lexing or during parsing.
  *
  * A negative index via BRACKETS (`h[-1]`) is a different code path and remains valid, as does a positive numeric dotted key (`h.1`, the ISS-1016 feature) and ordinary property access (`h.a`).
  */
final class LiquidNegativeDottedKeyIss1229Suite extends munit.FunSuite {

  // THE RED ASSERTION: a negative numeric segment after a dot excludes `-` from the IdChain
  // after-dot charset, so upstream raises a parse error; SSG currently accepts it as Hash("-1").
  test("ISS-1229 negative numeric dotted key {{ h.-1 }} raises LiquidException") {
    intercept[LiquidException] {
      Template.parse("{{ h.-1 }}")
    }
  }

  // THE RED ASSERTION (double form): a negative DOUBLE_NUM after a dot is likewise rejected.
  test("ISS-1229 negative double dotted key {{ h.-1.5 }} raises LiquidException") {
    intercept[LiquidException] {
      Template.parse("{{ h.-1.5 }}")
    }
  }

  // GUARD: a positive numeric dotted key is valid (the ISS-1016 feature) — Hash("1").
  test("ISS-1229 positive numeric dotted key {{ h.1 }} is accepted and renders") {
    val rendered = Template.parse("{{ h.1 }}").render()
    assert(rendered != null, s"expected a rendered string, got: $rendered")
  }

  // GUARD: a negative index via brackets is valid (a different code path).
  test("ISS-1229 negative bracket index {{ h[-1] }} is accepted") {
    Template.parse("{{ h[-1] }}")
  }

  // GUARD: ordinary property access is valid.
  test("ISS-1229 plain property access {{ h.a }} is accepted") {
    Template.parse("{{ h.a }}")
  }
}
