/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * ISS-1289: inBooleanContext omits terser's AST_Sequence tail walk-through.
 *
 * Terser's in_boolean_context() (lib/compress/index.js:353-380) treats a node
 * that is the TAIL of a sequence (comma) expression as inheriting the boolean
 * context of the sequence itself -- the walk-through branch at index.js:373
 * (`p.tail_node() === self` -> `self = p`). SSG's inBooleanContext
 * (Compressor.scala:262-293) is MISSING that case: at an AstSequence ancestor
 * it falls through to `case _ => break(false)`, so a sequence-tail node is not
 * recognized as being in boolean context. (The sibling in32BitContext,
 * Compressor.scala:329-334, correctly HAS the sequence-tail case.)
 *
 * Observable effect: the `!!foo ==> foo, if we're in boolean context`
 * optimization (index.js:2133-2136, AST_UnaryPrefix "!" case) fires for a
 * `!!x` that is the tail of a sequence whose result is used as a boolean, but
 * SSG without the fix keeps the redundant double negation.
 *
 * Expected values derived from the terser oracle:
 *   node -e 'require("original-src/terser/main.js").minify(<input>,
 *            {compress:{defaults:false,booleans:true},mangle:false})'
 *
 * Control (both agree, proves SSG HAS the !!foo->foo rule): plain
 *   `if (!!x) {}` -> `if(x);` in both terser and SSG. Divergence appears only
 *   once the `!!x` sits behind a sequence tail.
 *
 * Uses CompressTestHelper with false_by_default mode + booleans enabled.
 * Runs on JVM, JS, and Native (no java.nio dependency). */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }

final class CompressInBooleanContextSeqTailIss1289Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val booleansOn = AllOff.copy(booleans = true)

  // =========================================================================
  // seq_tail_in_if_condition
  // =========================================================================
  // The if-condition is a sequence `(console.log(1), !!x)`; the `!!x` is the
  // sequence tail. Terser walks through the sequence (index.js:373) and the
  // if-condition (index.js:361) to conclude `!!x` is in boolean context, then
  // drops the double negation (index.js:2133-2136) -> `if(console.log(1),x)`.
  // SSG without the fix stops at the AstSequence ancestor and keeps `!!x`.
  test("seq_tail_in_if_condition") {
    assertCompresses(
      input = "var x; if (console.log(1), !!x) {}",
      expected = "var x;if(console.log(1),x);",
      options = booleansOn
    )
  }

  // =========================================================================
  // seq_tail_under_not
  // =========================================================================
  // The sequence `(console.log(1), !!x)` is the operand of `!` (index.js:362),
  // so its tail `!!x` is in boolean context via the sequence-tail walk-through.
  // Terser drops the double negation -> `!(console.log(1),x)`.
  test("seq_tail_under_not") {
    assertCompresses(
      input = "var x; !(console.log(1), !!x);",
      expected = "var x;!(console.log(1),x);",
      options = booleansOn
    )
  }

  // =========================================================================
  // seq_tail_in_ternary_condition
  // =========================================================================
  // The sequence `(console.log(1), !!x)` is a conditional's condition
  // (index.js:358), so its tail `!!x` is in boolean context via the
  // sequence-tail walk-through. Terser -> `(console.log(1),x)?1:2`.
  test("seq_tail_in_ternary_condition") {
    assertCompresses(
      input = "var x; var y = (console.log(1), !!x) ? 1 : 2;",
      expected = "var x;var y=(console.log(1),x)?1:2;",
      options = booleansOn
    )
  }
}
