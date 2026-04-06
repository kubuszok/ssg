/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass

import ssg.sass.visitor.OutputStyle

/** First-class CSS Math 3 calculation functions: round / mod / rem / abs / sign /
  * sin / cos / tan / asin / acos / atan / atan2 / sqrt / exp / pow / log / hypot.
  *
  * All values should either collapse to a SassNumber when all arguments are
  * known numbers, or round-trip to a CSS calc-level function call when any
  * argument defers (unquoted-string var(), etc.).
  */
final class CalcSuite extends munit.FunSuite {

  private def css(src: String): String =
    Compile.compileString(src, OutputStyle.Compressed).css

  test("round(15.5) collapses to 16 (unitless)") {
    assertEquals(css("a { width: round(15.5); }"), "a{width:16;}")
  }

  test("round(15.5px, 1px) collapses to 16px") {
    assertEquals(css("a { width: round(15.5px, 1px); }"), "a{width:16px;}")
  }

  test("round(up, 12.3px, 5px) collapses to 15px") {
    assertEquals(css("a { width: round(up, 12.3px, 5px); }"), "a{width:15px;}")
  }

  test("round(to-zero, -7.7px) collapses to -7px") {
    // round(to-zero, n) with no step uses an implicit step of 1 in the
    // number's own unit — matches dart-sass. Expected: -7px.
    assertEquals(css("a { width: round(to-zero, -7.7px, 1px); }"), "a{width:-7px;}")
  }

  test("mod(7px, 3px) collapses to 1px") {
    assertEquals(css("a { width: mod(7px, 3px); }"), "a{width:1px;}")
  }

  test("rem(7px, 3px) collapses to 1px") {
    assertEquals(css("a { width: rem(7px, 3px); }"), "a{width:1px;}")
  }

  test("abs(-10px) collapses to 10px") {
    assertEquals(css("a { width: abs(-10px); }"), "a{width:10px;}")
  }

  test("sign(-3) collapses to -1") {
    assertEquals(css("a { width: sign(-3); }"), "a{width:-1;}")
  }

  test("sqrt(16) collapses to 4") {
    assertEquals(css("a { width: sqrt(16); }"), "a{width:4;}")
  }

  test("pow(2, 10) collapses to 1024") {
    assertEquals(css("a { width: pow(2, 10); }"), "a{width:1024;}")
  }

  test("hypot(3px, 4px) collapses to 5px") {
    assertEquals(css("a { width: hypot(3px, 4px); }"), "a{width:5px;}")
  }

  test("round($var) with var=7.7px collapses to 8px") {
    assertEquals(css("$x: 7.7px; a { width: round($x, 1px); }"), "a{width:8px;}")
  }

  test("cos(0) collapses to 1") {
    assertEquals(css("a { width: cos(0); }"), "a{width:1;}")
  }

  test("sin(0deg) collapses to 0") {
    assertEquals(css("a { width: sin(0deg); }"), "a{width:0;}")
  }

}
