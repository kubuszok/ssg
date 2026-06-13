/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Differential regression test for ISS-1039 (R0610-P1): OutputOptions.shorthand
 * must be derived from the output ECMA level, not hardcoded true.
 *
 * terser oracle (original-src/terser/lib/output.js:287,297-298):
 *   `shorthand: undefined` (default), then
 *   `if (options.shorthand === undefined) options.shorthand = options.ecma > 5;`
 * So ECMA 5 yields shorthand=FALSE (emit `{a:a}`) and ECMA > 5 (2015+) yields
 * shorthand=TRUE (emit `{a}`).
 *
 * Verified against the terser CLI on `var a=1;var o={a:a};`
 * (compress:false, mangle:false):
 *   format:{ecma:5}    -> "var a=1;var o={a:a};"
 *   format:{ecma:2015} -> "var a=1;var o={a};"
 *
 * SSG currently hardcodes `shorthand=true` (output/OutputOptions.scala:91), so the
 * ECMA 5 case wrongly leaks ES6 shorthand `{a}`. */
package ssg
package js
package output

import ssg.js.parse.Parser
import ssg.js.output.{ OutputOptions, OutputStream }

final class ShorthandEcmaIss1039Suite extends munit.FunSuite {

  // Object literal whose property key name equals its value identifier, so ES6
  // property shorthand is eligible: `{a: a}` may collapse to `{a}`.
  private val source = "var a=1;var o={a:a};"

  private def render(ecma: Int): String =
    OutputStream.printToString(new Parser().parse(source), OutputOptions(ecma = ecma))

  // Control (passes today): ECMA 2015 allows shorthand, so the value identifier
  // collapses. Oracle: "var a=1;var o={a};".
  test("Iss1039 ecma 2015 uses property shorthand") {
    val result = render(2015)
    assert(result.contains("{a}"), s"expected ES2015 shorthand `{a}`, got: $result")
    assert(!result.contains("a:a"), s"expected no `a:a` long form at ecma 2015, got: $result")
  }

  // RED (must FAIL on current code): ECMA 5 forbids shorthand, so the long form
  // `{a:a}` must be emitted. Oracle: "var a=1;var o={a:a};". On current code
  // `shorthand` is hardcoded true, so ES5 output wrongly emits `{a}`.
  test("Iss1039 ecma 5 must not use property shorthand") {
    val result = render(5)
    assert(
      result.contains("a:a"),
      s"ES5 (ecma=5) must keep long form `a:a` (no shorthand) per terser oracle; " +
        s"expected `{a:a}`, but shorthand `{a}` leaked: $result"
    )
    assert(
      !result.contains("{a}"),
      s"ES5 (ecma=5) must NOT emit ES6 shorthand `{a}` per terser oracle; got: $result"
    )
  }
}
