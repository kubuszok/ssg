/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Red suite for ISS-1139: OutputStream.makeNum (ssg-js OutputStream.scala) diverges from
 * terser's make_num (original-src/terser/lib/output.js:2428-2450). The upstream function is:
 *
 *   function make_num(num) {
 *       var str = num.toString(10).replace(/^0\./, ".").replace("e+", "e");   // :2428
 *       var candidates = [ str ];
 *       if (Math.floor(num) === num) { ...push hex... }                       // :2430-2435
 *       var match, len, digits;
 *       if      (match = /^\.0+/.exec(str))            { ... "e-" ... }        // :2438-2441
 *       else if (match = /0+$/.exec(str))              { ... "e"  ... }        // :2442-2444
 *       else if (match = /^(\d)\.(\d+)e(-?\d+)$/.exec(str)) { ...recombine...} // :2445-2447
 *       return best_of(candidates);  // shortest; ties keep the earlier candidate  // :2449
 *   }
 *
 * The three shortening branches (:2438/:2442/:2445) form an if / else-if / else-if chain, so at
 * most ONE fires. SSG instead uses three independent `if`s AND adds a
 * `!str.contains('.') && !str.contains('e')` guard on the trailing-zero branch. As a result, for a
 * value whose decimal string carries a trailing zero in its EXPONENT digits *and* a fractional
 * mantissa (e.g. "1.5e300"), upstream takes only the else-if `/0+$/` branch (whose candidate is
 * longer and is discarded, leaving the original), whereas SSG skips the guarded trailing-zero
 * branch and instead runs the exponent-recombine branch, producing a shorter — but wrong —
 * candidate that best_of then selects.
 *
 * Expected values below are terser's ACTUAL output, verified by running the real terser minifier
 * from original-src/terser on each literal (minify("x=<n>;",{compress:false,mangle:false})):
 *   1.5e300  => x=1.5e300;
 *   4.5e30   => x=4.5e30;
 *   1.23e-10 => x=1.23e-10;
 *
 * All three use short mantissas that JVM, JS, and Native all format identically after makeNum's
 * `e+`->`e` / case normalization, so the suite is cross-platform (JVM, JS, Native). (Large integral
 * values > Long.MaxValue that would exercise makeNum's added `num <= Long.MaxValue` hex guard are
 * intentionally NOT pinned here: their raw Double.toString differs between JVM's `E` notation and
 * JS's full-integer form, which would make such a case platform-divergent. The `.replace("e+","e")`
 * replace-all-vs-first divergence is likewise unobservable from a single number, whose toString
 * contains at most one `e+`.)
 */
package ssg
package js

import ssg.js.output.OutputStream

final class MakeNumIss1139Suite extends munit.FunSuite {

  private val out = new OutputStream()

  private def check(num: Double, expected: String, clue: String): Unit =
    assertEquals(out.makeNum(num), expected, clue)

  // output.js:2442-2447 — the else-if chain means the trailing-zero branch (fired by the trailing
  // "0" of the exponent "300") short-circuits the exponent-recombine branch. Its candidate
  // "1.5e3e2" is longer than the original, so best_of (:2449) keeps "1.5e300". SSG's guarded,
  // independent branches instead recombine to the shorter (wrong) "15e299".
  test("1.5e300 keeps its form; recombine must not win (output.js:2442-2449)") {
    check(1.5e300, "1.5e300", "1.5e300 -> 1.5e300 (terser real minify: x=1.5e300;)")
  }

  // output.js:2442-2447 — same divergence at a smaller magnitude. str "4.5e30" ends in "0", so
  // upstream takes only the trailing-zero else-if (candidate "4.5e3e1", discarded), keeping
  // "4.5e30". SSG recombines to "45e29". (MakeNumIss1135Suite pins the buggy "45e29".)
  test("4.5e30 keeps its form; recombine must not win (output.js:2442-2449)") {
    check(4.5e30, "4.5e30", "4.5e30 -> 4.5e30 (terser real minify: x=4.5e30;)")
  }

  // output.js:2442-2447 — negative-exponent variant. str "1.23e-10" ends in "0", upstream takes
  // the trailing-zero else-if (candidate "1.23e-1e1", discarded), keeping "1.23e-10". SSG's
  // guarded branches recombine to "123e-12". (MakeNumIss1135Suite pins the buggy "123e-12".)
  test("1.23e-10 keeps its form; recombine must not win (output.js:2442-2449)") {
    check(1.23e-10, "1.23e-10", "1.23e-10 -> 1.23e-10 (terser real minify: x=1.23e-10;)")
  }
}
