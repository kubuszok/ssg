/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Regression suite for ISS-1394: `void 0` -> `null` fold in `==`/`!=`
 * comparisons must be gated on the `comparisons` compressor option.
 *
 * In terser (compress/index.js:2280) the whole equality-optimization
 * switch is guarded by `if (compressor.option("comparisons"))`, and the
 * `void 0 == x => null == x` / `x == void 0 => x == null` fold lives at
 * lines 2298-2302 inside that switch. Therefore, with `comparisons` OFF,
 * terser KEEPS `void 0` and does NOT fold it to `null`; with `comparisons`
 * ON it does fold.
 *
 * Ground truth from real terser (original-src/terser, defaults:false):
 *   comparisons=false  x = a == void 0;  ->  x=a==void 0;
 *   comparisons=false  x = a != void 0;  ->  x=a!=void 0;
 *   comparisons=false  x = void 0 == a;  ->  x=void 0==a;
 *   comparisons=true   x = a == void 0;  ->  x=a==null;
 *   comparisons=true   x = a != void 0;  ->  x=a!=null;
 *   comparisons=true   x = void 0 == a;  ->  x=null==a; */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }

final class CompressVoid0NullFoldIss1394Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  // =========================================================================
  // comparisons OFF: the void 0 -> null fold must NOT happen
  // (terser index.js:2280 gates the whole switch on option("comparisons");
  //  the fold itself is at 2298-2302). SSG currently folds unconditionally.
  // =========================================================================
  test("void 0 kept on rhs of == when comparisons off") {
    assertCompresses(
      input = "x = a == void 0;",
      expected = "x = a == void 0;",
      options = AllOff.copy(
        comparisons = false
      )
    )
  }

  test("void 0 kept on rhs of != when comparisons off") {
    assertCompresses(
      input = "x = a != void 0;",
      expected = "x = a != void 0;",
      options = AllOff.copy(
        comparisons = false
      )
    )
  }

  test("void 0 kept on lhs of == when comparisons off") {
    assertCompresses(
      input = "x = void 0 == a;",
      expected = "x = void 0 == a;",
      options = AllOff.copy(
        comparisons = false
      )
    )
  }

  // =========================================================================
  // Positive control: comparisons ON => terser DOES fold void 0 -> null.
  // SSG already folds, so this guards against the fix over-correcting.
  // =========================================================================
  test("void 0 folds to null on rhs of == when comparisons on") {
    assertCompresses(
      input = "x = a == void 0;",
      expected = "x = a == null;",
      options = AllOff.copy(
        comparisons = true
      )
    )
  }

}
