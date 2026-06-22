/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Cross-platform unit tests for ISS-1155: ssg.katex.data.Units.makeEm must
 * reproduce upstream `+n.toFixed(4) + "em"` semantics (units.ts:103) on
 * NEGATIVE tie values, on EVERY platform (JVM/JS/Native).
 *
 * Upstream reference (original-src/katex, src/units.ts:103):
 *   `export const makeEm = function(n: number): string {
 *      return +n.toFixed(4) + "em"; };`
 *
 * `Number.prototype.toFixed` rounds the MAGNITUDE half-away-from-zero. The SSG
 * port instead uses `Math.round(n * 10000.0)`, which rounds half toward +Inf.
 * On positive values both agree; on negative values that land exactly on a tie
 * they DIVERGE (e.g. Math.round(-0.5) == -0, Math.round(-1234.5) == -1234).
 * Negative ems are real (e.g. `top:-3.063em` kerns), so the divergence matters.
 *
 * Expected strings are pinned from a direct evaluation of the upstream
 * expression with node:
 *   node -e "function makeEm(n){return +n.toFixed(4)+'em'} for(const n of \
 *     [-0.00005,-0.12345,-3.063,0.00005,0.12345,1.5,0,-0.0,2.0,-1.23455]) \
 *     console.log(n,'->',makeEm(n))"
 * yielding (n => result):
 *   -0.00005 => -0.0001em   (DIVERGENT: SSG Math.round(-0.5)   == -0   -> "0em")
 *   -0.12345 => -0.1235em   (DIVERGENT: SSG Math.round(-1234.5)== -1234-> "-0.1234em")
 *   -1.23455 => -1.2346em   (DIVERGENT: SSG Math.round(-12345.5)==-12345->"-1.2345em")
 *   0.00005  => 0.0001em    (positive control — SSG matches)
 *   0.12345  => 0.1235em    (positive control — SSG matches)
 *   -3.063   => -3.063em    (no rounding tie — SSG matches)
 *   -0.0     => 0em         (negative zero prints as "0")
 *   1.5      => 1.5em
 */
package ssg
package katex
package data

class MakeEmRoundingIss1155Suite extends KaTeXTestSuite {

  // (input, expected) pins, each evaluated as `+n.toFixed(4) + "em"` in node.
  // Negative-tie cases (the bug) plus positive/edge controls (so the test is
  // not over-broad and catches a fix that only changes negative behaviour).
  private val pins: List[(Double, String)] = List(
    // DIVERGENT negative ties — these go red against Math.round.
    -0.00005 -> "-0.0001em",
    -0.12345 -> "-0.1235em",
    -1.23455 -> "-1.2346em",
    // Positive controls — must keep matching.
    0.00005 -> "0.0001em",
    0.12345 -> "0.1235em",
    // Edge controls — must keep matching.
    -3.063 -> "-3.063em",
    -0.0 -> "0em",
    1.5 -> "1.5em"
  )

  pins.foreach { case (input, expected) =>
    test(s"ISS-1155: makeEm($input) == \"$expected\" (upstream toFixed(4) pin)") {
      assertEquals(Units.makeEm(input), expected)
    }
  }
}
