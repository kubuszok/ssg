/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Compression tests for constant evaluation (evaluate pass).
 * Ported from: terser/test/compress/evaluate.js
 *
 * Uses CompressTestHelper with false_by_default mode.
 *
 * KNOWN LIMITATIONS:
 * - ISS-031/032: ScopeAnalysis hangs on undeclared globals (NaN, Infinity, etc.)
 * - Bitwise eval for hex literals is broken (& | ^ >>> all produce 0 or wrong result)
 * - Boolean output is `true`/`false` (not `!0`/`!1`) without booleans option
 * - String↔number coercion not folded (e.g. `'x' + 42`, `-""`)
 * - Comparison folding not done by evaluate alone (needs comparisons option)
 *
 * Original file has ~85 test cases; we port the subset that works with
 * declared-only variables and correct evaluate behavior. */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }

final class CompressEvaluateSuite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  private val evalOpts = AllOff.copy(evaluate = true)

  // =======================================================================
  // Arithmetic constant folding — from Terser evaluate.js
  // =======================================================================

  // pow: { options = { evaluate: true }; input: { var a = 5 ** 3; }; expect: { var a = 125; } }
  test("pow") {
    assertCompresses("var a = 5 ** 3;", "var a=125;", evalOpts)
  }

  // pow_sequence: { input: { var a = 2 ** 3 ** 2; }; expect: { var a = 512; } }
  test("pow_sequence") {
    assertCompresses("var a = 2 ** 3 ** 2;", "var a=512;", evalOpts)
  }

  // pow_mixed: multiple pow expressions
  test("pow_mixed") {
    assertCompresses(
      "var a = 5 + 2 ** 3 + 5; var b = 5 * 3 ** 2; var c = 5 ** 3 * 2; var d = 5 ** +3;",
      "var a=18;var b=45;var c=250;var d=125;",
      evalOpts
    )
  }

  test("integer addition") {
    assertCompresses("var x = 1 + 2;", "var x=3;", evalOpts)
  }

  test("integer subtraction") {
    assertCompresses("var x = 10 - 3;", "var x=7;", evalOpts)
  }

  test("integer multiplication") {
    assertCompresses("var x = 2 * 3;", "var x=6;", evalOpts)
  }

  test("integer division") {
    assertCompresses("var x = 10 / 2;", "var x=5;", evalOpts)
  }

  test("integer modulo") {
    assertCompresses("var x = 10 % 3;", "var x=1;", evalOpts)
  }

  test("unary negation") {
    assertCompresses("var x = -5;", "var x=-5;", evalOpts)
  }

  test("unary plus") {
    assertCompresses("var x = +5;", "var x=5;", evalOpts)
  }

  test("bitwise NOT") {
    assertCompresses("var x = ~0;", "var x=-1;", evalOpts)
  }

  test("double bitwise NOT") {
    assertCompresses("var x = ~~5;", "var x=5;", evalOpts)
  }

  test("logical NOT on literal") {
    assertCompresses("var x = !0;", "var x=!0;", evalOpts)
  }

  test("double negation on literal") {
    assertCompresses("var x = !!1;", "var x=!!1;", evalOpts)
  }

  test("left shift") {
    assertCompresses("var x = 1 << 4;", "var x=16;", evalOpts)
  }

  test("right shift") {
    assertCompresses("var x = 16 >> 2;", "var x=4;", evalOpts)
  }

  test("nested arithmetic") {
    assertCompresses("var x = (1 + 2) * (3 + 4);", "var x=21;", evalOpts)
  }

  test("chained addition") {
    assertCompresses("var x = 1 + 2 + 3 + 4 + 5;", "var x=15;", evalOpts)
  }

  // =======================================================================
  // String constant folding
  // =======================================================================

  test("string concatenation") {
    assertCompresses("var x = 'hello' + ' ' + 'world';", "var x=\"hello world\";", evalOpts)
  }

  // String + number is NOT folded by the evaluate pass alone
  // (Terser folds it, ssg-js does not yet — this documents the gap)
  test("string + number NOT folded".fail) {
    assertCompresses("var x = 'value: ' + 42;", "var x=\"value: 42\";", evalOpts)
  }

  // =======================================================================
  // pow with number constants (from evaluate.js pow_with_number_constants)
  // Note: NaN, Infinity references are undeclared globals → ISS-031/032 hang
  // =======================================================================

  test("pow_with_number_constants: +0 exponent") {
    assertCompresses("var b = 42 ** +0;", "var b=1;", evalOpts)
  }

  test("pow_with_number_constants: -0 exponent") {
    assertCompresses("var c = 42 ** -0;", "var c=1;", evalOpts)
  }

  test("pow_with_number_constants: 2 ** -3") {
    assertCompresses("var j = 2 ** (-3);", "var j=.125;", evalOpts)
  }

  test("pow_with_number_constants: 2.0 ** -3") {
    assertCompresses("var k = 2.0 ** -3;", "var k=.125;", evalOpts)
  }

  test("pow_with_number_constants: 2 ** (5-7)") {
    assertCompresses("var l = 2.0 ** (5 - 7);", "var l=.25;", evalOpts)
  }

  // =======================================================================
  // Multiple declarations
  // =======================================================================

  test("multiple constant folds") {
    assertCompresses("var a = 1 + 1, b = 2 * 3, c = 10 - 4;", "var a=2,b=6,c=6;", evalOpts)
  }

  // =======================================================================
  // No folding for non-constant expressions
  // =======================================================================

  test("no folding for declared var arithmetic") {
    assertCompresses("var x = 1; var y = x + 2;", "var x=1;var y=x+2;", evalOpts)
  }

  test("no folding when var is used") {
    assertCompresses("var a = 1; var b = a;", "var a=1;var b=a;", evalOpts)
  }

  // =======================================================================
  // Known broken: bitwise ops with hex literals
  // These document bugs in the evaluate pass.
  // =======================================================================

  test("bitwise AND with hex".fail) {
    assertCompresses("var x = 0xff & 0x0f;", "var x=15;", evalOpts)
  }

  test("bitwise OR with hex".fail) {
    assertCompresses("var x = 0xf0 | 0x0f;", "var x=255;", evalOpts)
  }

  test("bitwise XOR with hex".fail) {
    assertCompresses("var x = 0xff ^ 0x0f;", "var x=240;", evalOpts)
  }

  test("unsigned right shift".fail) {
    assertCompresses("var x = -1 >>> 0;", "var x=4294967295;", evalOpts)
  }

  // =======================================================================
  // Known broken: comparisons (need comparisons option, not just evaluate)
  // Terser evaluates 5 == 5 → true with evaluate alone, our port doesn't.
  // Documenting the gap.
  // =======================================================================

  test("equality folding 5 == 5".fail) {
    assertCompresses("var x = 5 == 5;", "var x=!0;", evalOpts)
  }

  test("equality folding 5 == 6".fail) {
    assertCompresses("var x = 5 == 6;", "var x=!1;", evalOpts)
  }

  // =======================================================================
  // Known broken: string/number coercion
  // =======================================================================

  test("unary minus on empty string".fail) {
    assertCompresses("var x = -\"\";", "var x=-0;", evalOpts)
  }

  test("unary plus on empty string".fail) {
    assertCompresses("var x = +\"\";", "var x=0;", evalOpts)
  }
}
