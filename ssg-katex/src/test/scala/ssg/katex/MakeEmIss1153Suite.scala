/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Cross-platform unit tests for ISS-1153: ssg.katex.data.Units.makeEm must
 * reproduce upstream `+n.toFixed(4) + "em"` semantics (units.ts:99-104) byte
 * for byte, on EVERY platform (JVM/JS/Native), independent of the host locale.
 *
 * Upstream reference (original-src/katex, v0.16.45):
 *   - src/units.ts:99-104 — `export const makeEm = function(n: number): string
 *     { return +n.toFixed(4) + "em"; };`. ECMA-262 `Number.prototype.toFixed`
 *     rounds to 4 decimal places (round-half-up at the 4th place) and the unary
 *     `+` round-trip via `Number::toString` strips trailing zeros, always with a
 *     "." decimal separator.
 *
 * The expected strings below are pinned from a direct evaluation of the upstream
 * expression with node v24.12.0:
 *   node -e 'for (const n of [...]) console.log(+n.toFixed(4) + "em")'
 * yielding (n => result):
 *   0.8141      => 0.8141em
 *   0.05        => 0.05em
 *   -0.05       => -0.05em
 *   0.99999     => 1em        (rounds up across the integral boundary)
 *   0.00004     => 0em        (+(0.00004).toFixed(4) === +"0.0000" === 0)
 *   0.1234      => 0.1234em   (exactly four decimals)
 *   0.5         => 0.5em      (trailing zeros stripped)
 *   0           => 0em
 *   -0          => 0em        (negative zero prints as "0")
 *   1           => 1em
 *   2.7         => 2.7em
 *   3.063       => 3.063em
 *   12345.6789  => 12345.6789em
 *   -1.23456    => -1.2346em  (round-half-up at 4th place: .23456 -> .2346)
 *   0.00005     => 0.0001em   (rounds up)
 *   0.99995     => 1em        (rounds up across the integral boundary)
 */
package ssg
package katex

import ssg.katex.data.Units

class MakeEmIss1153Suite extends KaTeXTestSuite {

  // (input, expected) pins, each evaluated as `+n.toFixed(4) + "em"` in node.
  private val pins: List[(Double, String)] = List(
    0.8141     -> "0.8141em",
    0.05       -> "0.05em",
    -0.05      -> "-0.05em",
    0.99999    -> "1em",
    0.00004    -> "0em",
    0.1234     -> "0.1234em",
    0.5        -> "0.5em",
    0.0        -> "0em",
    -0.0       -> "0em",
    1.0        -> "1em",
    2.7        -> "2.7em",
    3.063      -> "3.063em",
    12345.6789 -> "12345.6789em",
    -1.23456   -> "-1.2346em",
    0.00005    -> "0.0001em",
    0.99995    -> "1em"
  )

  pins.foreach { case (input, expected) =>
    test(s"ISS-1153: makeEm($input) == \"$expected\" (upstream toFixed(4) pin)") {
      assertEquals(Units.makeEm(input), expected)
    }
  }

  test("ISS-1153: makeEm never emits a comma decimal separator") {
    pins.foreach { case (input, _) =>
      val out = Units.makeEm(input)
      assert(
        !out.contains(","),
        s"makeEm($input) = $out must not contain a comma decimal separator"
      )
    }
  }
}
