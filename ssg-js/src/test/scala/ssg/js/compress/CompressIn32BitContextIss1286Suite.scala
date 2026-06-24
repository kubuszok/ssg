/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1286: in32BitContext omits terser's other_operand_must_be_number param.
 *
 * Terser's in_32_bit_context(other_operand_must_be_number) (index.js:387-393)
 * narrows the bitwise-binop branch: when other_operand_must_be_number is true
 * it checks (self===p.left ? p.right : p.left).is_number(compressor). SSG's
 * parameterless in32BitContext always returns true for any bitwise-binop
 * ancestor, making it OVER-PERMISSIVE -- it wrongly collapses e.g. (a|0)&g
 * to a&g when terser keeps (0|a)&g because g is not a number.
 *
 * Expected values derived from terser oracle:
 *   node original-src/terser/bin/terser --compress evaluate,passes=1 --no-rename
 *
 * Uses CompressTestHelper with false_by_default mode.
 * Runs on JVM, JS, and Native (no java.nio dependency). */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }

final class CompressIn32BitContextIss1286Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  // =========================================================================
  // divergence_or_zero_non_number_other
  // =========================================================================
  // Oracle: terser keeps (0|a)&g because g.is_number() is false, so
  // in_32_bit_context(true) returns false and the x|0->x fold does NOT fire.
  // SSG without the fix wrongly collapses to a&g. The (a|0) is preserved
  // (terser commutatively reorders to (0|a) but SSG does not -- separate
  // concern, not part of ISS-1286).
  test("divergence_or_zero_non_number_other") {
    assertCompresses(
      input = "var a, g; console.log((a|0) & g);",
      expected = "var a,g;console.log((a|0)&g);",
      options = AllOff.copy(evaluate = true)
    )
  }

  // =========================================================================
  // divergence_xor_minus1_non_number_other
  // =========================================================================
  // Oracle: terser keeps (-1^a)&g because g.is_number() is false, so
  // in_32_bit_context(true) returns false and the x^-1->~x fold does NOT fire.
  // SSG without the fix wrongly collapses to ~a&g. The (a^-1) is preserved
  // (terser commutatively reorders to (-1^a) but SSG does not -- separate
  // concern, not part of ISS-1286).
  test("divergence_xor_minus1_non_number_other") {
    assertCompresses(
      input = "var a, g; console.log((a^-1) & g);",
      expected = "var a,g;console.log((a^-1)&g);",
      options = AllOff.copy(evaluate = true)
    )
  }

  // =========================================================================
  // control_or_zero_number_other
  // =========================================================================
  // Oracle: terser DOES collapse (a|0)&5 because 5.is_number() is true, so
  // in_32_bit_context(true) returns true and x|0->x fires. Terser then
  // commutatively reorders to 5&a; SSG does not do that reorder (separate
  // concern), so the expected output is a&5.
  test("control_or_zero_number_other") {
    assertCompresses(
      input = "var a; console.log((a|0) & 5);",
      expected = "var a;console.log(a&5);",
      options = AllOff.copy(evaluate = true)
    )
  }
}
