/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import ssg.liquid.filters.Punycode

/** Unit tests for the pure-Scala RFC 3492 Punycode encoder (ISS-1261).
  *
  * Expected values are derived from RFC 3492 section 7 sample strings and
  * verified against java.net.IDN.toASCII on the JVM.
  */
final class PunycodeIss1261Suite extends munit.FunSuite {

  // -- encode: code-point array to Punycode string --

  test("encode: RFC 3492 - umlaut (u-with-diaeresis mlaut)") {
    // "ümlaut" = code points [0xFC, 'm', 'l', 'a', 'u', 't']
    val input = Array(0xfc, 'm'.toInt, 'l'.toInt, 'a'.toInt, 'u'.toInt, 't'.toInt)
    assertEquals(Punycode.encode(input), "mlaut-jva")
  }

  test("encode: RFC 3492 - bucher (u-with-diaeresis in second position)") {
    // "bücher" = code points ['b', 0xFC, 'c', 'h', 'e', 'r']
    val input = Array('b'.toInt, 0xfc, 'c'.toInt, 'h'.toInt, 'e'.toInt, 'r'.toInt)
    assertEquals(Punycode.encode(input), "bcher-kva")
  }

  test("encode: RFC 3492 - Munchen (u-with-diaeresis in fifth position)") {
    // "München" = code points ['M', 0xFC, 'n', 'c', 'h', 'e', 'n']
    val input = Array('M'.toInt, 0xfc, 'n'.toInt, 'c'.toInt, 'h'.toInt, 'e'.toInt, 'n'.toInt)
    assertEquals(Punycode.encode(input), "Mnchen-3ya")
  }

  test("encode: pure ASCII input produces no xn-- prefix overhead") {
    val input = Array('a'.toInt, 'b'.toInt, 'c'.toInt)
    assertEquals(Punycode.encode(input), "abc-")
  }

  // -- toAscii: internationalized hostname to ACE --

  test("toAscii: internationalized single label") {
    assertEquals(Punycode.toAscii("xn--mlaut-jva"), "xn--mlaut-jva")
  }

  test("toAscii: internationalized hostname with multiple labels") {
    assertEquals(Punycode.toAscii("ümlaut.example.org"), "xn--mlaut-jva.example.org")
  }

  test("toAscii: pure-ASCII hostname is unchanged") {
    assertEquals(Punycode.toAscii("example.com"), "example.com")
  }

  test("toAscii: mixed ASCII and non-ASCII labels") {
    // "a.ü.c" — middle label is a single non-ASCII char (U+00FC)
    // encode([0xFC]) should produce "tda" (single non-basic code point, no basic prefix)
    assertEquals(Punycode.toAscii("a.ü.c"), "a.xn--tda.c")
  }

  test("toAscii: preserves trailing dot") {
    assertEquals(Punycode.toAscii("ümlaut.example.org."), "xn--mlaut-jva.example.org.")
  }
}
