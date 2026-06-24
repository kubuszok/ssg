/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1324: bitwise-fold sites omit terser's is_32_bit_integer() gate.
 *
 * Terser gates x|0->x, x^0->x, x&-1->x, x^-1->~x on
 * 'non_zero_side.is_32_bit_integer(compressor) || compressor.in_32_bit_context(true)'
 * (index.js:2853/2888-2889/2900-2901). SSG only had in32BitContext, missing the
 * is_32_bit_integer alternative -- so folds that SHOULD fire when the non-zero
 * operand is intrinsically a 32-bit integer (e.g. bar&1) did NOT fire unless
 * there happened to be a bitwise ancestor context.
 *
 * Additionally, terser's x&0->0 fold (index.js:2862-2863) requires
 * non_zero_side.is_32_bit_integer(compressor); SSG omitted this gate entirely,
 * causing a CORRECTNESS over-fold: bar&0 was wrongly folded to 0 even when bar
 * is not a 32-bit integer.
 *
 * Expected values derived from terser oracle:
 *   node original-src/terser/bin/terser --compress evaluate,passes=1 --no-rename
 *
 * Terser commutatively reorders operands (e.g. bar&1 -> 1&bar); SSG does not
 * (ISS-1323, orthogonal). Where the fold result differs only by operand order,
 * we use SSG's form and note the ISS-1323 gap.
 *
 * Uses CompressTestHelper with false_by_default mode.
 * Runs on JVM, JS, and Native (no java.nio dependency). */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }

final class CompressIs32BitFoldIss1324Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  // =========================================================================
  // or_zero_32bit_operand
  // =========================================================================
  // (bar & 1) is a 32-bit integer (AST_Binary with bitwise op and numeric
  // operand). Terser folds (bar & 1) | 0 -> bar & 1 via is_32_bit_integer.
  // Oracle: console.log(1&bar); -- terser also reorders (ISS-1323).
  // SSG expected: console.log(bar&1); (no reorder).
  test("or_zero_32bit_operand") {
    assertCompresses(
      input = "console.log((bar & 1) | 0);",
      // Terser: console.log(1&bar); -- ISS-1323 reorder gap
      expected = "console.log(bar&1);",
      options = AllOff.copy(evaluate = true)
    )
  }

  // =========================================================================
  // xor_zero_32bit_operand
  // =========================================================================
  // (bar & 1) ^ 0 -> bar & 1 via is_32_bit_integer (terser index.js:2853).
  // Oracle: console.log(1&bar); -- terser reorders (ISS-1323).
  // SSG expected: console.log(bar&1);
  test("xor_zero_32bit_operand") {
    assertCompresses(
      input = "console.log((bar & 1) ^ 0);",
      // Terser: console.log(1&bar); -- ISS-1323 reorder gap
      expected = "console.log(bar&1);",
      options = AllOff.copy(evaluate = true)
    )
  }

  // =========================================================================
  // and_minus1_32bit_operand
  // =========================================================================
  // (bar & 1) & -1 -> bar & 1 via is_32_bit_integer (terser index.js:2888-2889).
  // Oracle: console.log(1&bar); -- terser reorders (ISS-1323).
  // SSG expected: console.log(bar&1);
  test("and_minus1_32bit_operand") {
    assertCompresses(
      input = "console.log((bar & 1) & -1);",
      // Terser: console.log(1&bar); -- ISS-1323 reorder gap
      expected = "console.log(bar&1);",
      options = AllOff.copy(evaluate = true)
    )
  }

  // =========================================================================
  // xor_minus1_32bit_operand
  // =========================================================================
  // (bar & 1) ^ -1 -> ~(bar & 1) via is_32_bit_integer (terser index.js:2900-2901).
  // Oracle: console.log(~(1&bar)); -- terser reorders (ISS-1323).
  // SSG expected: console.log(~(bar&1));
  test("xor_minus1_32bit_operand") {
    assertCompresses(
      input = "console.log((bar & 1) ^ -1);",
      // Terser: console.log(~(1&bar)); -- ISS-1323 reorder gap
      expected = "console.log(~(bar&1));",
      options = AllOff.copy(evaluate = true)
    )
  }

  // =========================================================================
  // and_zero_non_32bit_kept (the over-fold correctness fix)
  // =========================================================================
  // bar is declared (no side effects) but NOT a 32-bit integer.
  // Terser's x&0->0 fold (index.js:2862-2863) requires BOTH
  // !non_zero_side.has_side_effects(compressor) AND
  // non_zero_side.is_32_bit_integer(compressor). bar passes the first check
  // (declared var, no ReferenceError) but fails the second (plain var is not
  // a 32-bit int), so terser does NOT fold to 0.
  // SSG without the fix wrongly folded bar&0 -> 0 (the over-fold).
  // Oracle: var bar;console.log(0&bar); -- terser reorders (ISS-1323).
  // SSG expected: var bar;console.log(bar&0); (no reorder).
  test("and_zero_non_32bit_kept") {
    assertCompresses(
      input = "var bar; console.log(bar & 0);",
      // Terser: var bar;console.log(0&bar); -- ISS-1323 reorder gap
      expected = "var bar;console.log(bar&0);",
      options = AllOff.copy(evaluate = true)
    )
  }
}
