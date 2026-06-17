/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1212: ssg.js.parse.Token.isIdentifierString does not link on
 * Scala.js. At Token.scala:363 the method calls
 *
 *   rest.codePoints().allMatch(cp => isIdentifierCharCodePoint(cp))
 *
 * `String.codePoints()` returns `java.util.stream.IntStream`, which Scala.js does
 * NOT implement, so any Scala.js program that makes isIdentifierString reachable
 * fails to link with:
 *   Referring to non-existent method java.lang.String.codePoints()java.util.stream.IntStream
 *
 * To reach line 363 the input must (a) fail the ASCII-only basicIdentPattern fast
 * path (Token.scala:344/350) so the slow branch runs, (b) contain no surrogates
 * (default allowSurrogates=false, line 353), (c) be non-empty (line 355), (d) start
 * with a valid identifier-start code point (line 359), and (e) have a NON-EMPTY
 * `rest` (line 362) so `rest.codePoints()` is actually evaluated. A non-ASCII letter
 * such as 'é' (U+00E9, LOWERCASE_LETTER) defeats the ASCII fast path while keeping the
 * string a valid JS identifier, forcing the codePoints() call.
 *
 * This suite runs on JVM (GREEN — codePoints() is supported, proving the expected
 * values) and on Scala.js (RED — fastLinkJS fails to link via the path above). The fix
 * (implementer's job) must replace the codePoints()/IntStream usage at Token.scala:363
 * with a faithful cross-platform code-point iteration; do NOT modify product code here.
 */
package ssg
package js
package parse

final class TokenIdentifierIss1212Suite extends munit.FunSuite {

  // Plain ASCII identifier: valid. (Fast path at Token.scala:350, but kept as a
  // baseline sanity assertion.)
  test("ascii identifier is an identifier string") {
    assert(Token.isIdentifierString("abc"))
  }

  // Leading digit: NOT a valid identifier — first code point '1' is a
  // DECIMAL_DIGIT_NUMBER, not an identifier start (Token.scala:359 -> false).
  test("leading-digit string is not an identifier string") {
    assert(!Token.isIdentifierString("1abc"))
  }

  // Multi-char, non-ASCII identifier: defeats the ASCII fast path so the slow
  // branch runs, has a non-empty `rest` ("afé"), and thus REACHES
  // Token.scala:363 `rest.codePoints().allMatch(...)`. 'c' starts the identifier
  // and 'a','f','é' all continue it, so this is a valid JS identifier.
  test("multi-char non-ascii identifier reaches rest.codePoints() (Token.scala:363)") {
    assert(Token.isIdentifierString("café"))
  }

  // Another non-ASCII identifier that STARTS with a non-ASCII letter ('ü',
  // U+00FC), guaranteeing the slow branch and a non-empty rest ("ber").
  test("non-ascii leading letter identifier reaches rest.codePoints() (Token.scala:363)") {
    assert(Token.isIdentifierString("über"))
  }

  // Non-ASCII letter followed by a non-identifier character: the start is valid
  // but `rest` ("é!") contains '!', so rest.codePoints().allMatch must be false.
  test("non-ascii identifier with invalid continuation is not an identifier string") {
    assert(!Token.isIdentifierString("aé!"))
  }
}
