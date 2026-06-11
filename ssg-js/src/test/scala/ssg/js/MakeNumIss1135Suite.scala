/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Direct coverage for ISS-1135: number-shortening in OutputStream.makeNum must be
 * Native-safe and byte-identical to the JVM path. The original number-shortening logic
 * lives in original-src/terser/lib/output.js, function make_num (lines 2427-2449):
 *
 *   function make_num(num) {
 *       var str = num.toString(10).replace(/^0\./, ".").replace("e+", "e");   // :2428
 *       var candidates = [ str ];
 *       if (Math.floor(num) === num) {                                        // :2430
 *           if (num < 0) candidates.push("-0x" + (-num).toString(16)...);     // :2432
 *           else        candidates.push("0x"  +  num .toString(16)...);       // :2434
 *       }
 *       var match, len, digits;
 *       if (match = /^\.0+/.exec(str)) { ... e- ... }                         // :2438-2441
 *       else if (match = /0+$/.exec(str)) { ... e ... }                       // :2442-2444
 *       else if (match = /^(\d)\.(\d+)e(-?\d+)$/.exec(str)) { ... }           // :2445-2446
 *       return best_of(candidates);                                          // :2448 (min length)
 *   }
 *
 * The port replaces the four JVM-regex constructs (`^0\.`, `^\.0+`, `0+$`,
 * `^(\d)\.(\d+)e(-?\d+)$`) with plain string manipulation as a defensive, regex-free
 * rewrite that yields identical results. (These makeNum patterns compile fine on Scala
 * Native; the actual ISS-1135 Native blocker was Compressor.reSafeRegexp's `\0` escape
 * spelling, fixed separately.) These cases were derived from the make_num logic above and
 * pinned (expected values verified byte-identical to the prior regex implementation across
 * 400k+ random doubles). They run on JVM, JS, and Native.
 */
package ssg
package js

import ssg.js.compress.CompressorOptions
import ssg.js.output.OutputStream

final class MakeNumIss1135Suite extends munit.FunSuite {

  private val out = new OutputStream()

  private def check(num: Double, expected: String, clue: String): Unit =
    assertEquals(out.makeNum(num), expected, clue)

  // output.js:2428 — `replace(/^0\./, ".")`: a leading "0" before "." is dropped.
  test("strip leading zero before decimal point (output.js:2428)") {
    check(0.5, ".5", "0.5 -> .5 (drop leading 0)")
    check(0.25, ".25", "0.25 -> .25")
    check(0.1, ".1", "0.1 -> .1")
  }

  // output.js:2428 — the regex is anchored at the start, so a sign prefix is untouched.
  test("leading-zero strip is anchored: negative keeps its 0 (output.js:2428)") {
    check(-0.5, "-0.5", "-0.5 must keep the 0 (anchored ^0\\.) ")
  }

  // output.js:2442-2444 — `/0+$/`: a run of trailing zeros becomes "<head>e<count>".
  test("trailing zeros become exponent form (output.js:2442-2444)") {
    check(1000.0, "1e3", "1000 -> 1e3")
    check(100000.0, "1e5", "100000 -> 1e5")
  }

  // output.js:2445-2446 — `/^(\d)\.(\d+)e(-?\d+)$/`: d.dddde±dd → digits then adjusted exp.
  // The cases below use exponent strings that are identical across the platforms' float
  // formatters (Double.toString differs between JVM/Native and JS, so values like 1.0e-4 /
  // 5e-7 print differently per platform — those are excluded here; they are exercised
  // indirectly by the 400k differential check that pins JVM == prior regex behavior).
  // 1.23e-10: lead=1, frac=23, exp=-10 -> "1"+"23"+"e"+(-10-2) = "123e-12".
  // 4.5e30 (after "e+"->"e"): lead=4, frac=5, exp=30 -> "4"+"5"+"e"+(30-1) = "45e29".
  test("d.dddde+-dd exponent form is recombined (output.js:2445-2446)") {
    check(1.23e-10, "123e-12", "1.23e-10 -> 123e-12 (frac=23, exp=-10 -> e-(10+2))")
    check(4.5e30, "45e29", "4.5e30 -> 45e29 (frac=5, exp=30 -> e(30-1))")
  }

  // output.js:2430-2435 — integral values also get a hex candidate; best_of (:2448) picks it
  // only when strictly shorter. 1099511627775 = 0xffffffffff (12 chars) < 13 decimal chars.
  test("hexadecimal candidate wins when shorter (output.js:2430-2435,2448)") {
    check(1099511627775.0, "0xffffffffff", "2^40-1 -> 0xffffffffff")
    check(281474976710655.0, "0xffffffffffff", "2^48-1 -> 0xffffffffffff")
    check(-1099511627775.0, "-0xffffffffff", "negative integral -> -0x... (output.js:2432)")
  }

  // output.js:2438-2441 — `/^\.0+/`: a leading ".0+" run is detected; here the produced
  // candidate ties the original and best_of keeps the original (output.js:2448, min length).
  test("leading .0+ run is handled (output.js:2438-2441)") {
    check(0.001, ".001", "0.001 -> .001 (candidate 1e-3 ties; original kept)")
  }

  // output.js:2448 — best_of returns the shortest; values with no shorter form are unchanged.
  test("values with no shorter form pass through (output.js:2448)") {
    check(3.0, "3", "3 -> 3")
    check(255.0, "255", "255 -> 255 (0xff ties length, decimal kept)")
    check(256.0, "256", "256 -> 256 (0x100 longer)")
    check(1.5, "1.5", "1.5 -> 1.5")
    check(0.0, "0", "0 -> 0")
  }

  // Port guards above make_num (OutputStream.makeNum head): NaN, +/-Infinity, -0.
  test("special values: NaN, Infinity, negative zero") {
    check(Double.NaN, "NaN", "NaN")
    check(Double.PositiveInfinity, "1/0", "+Infinity -> 1/0")
    check(Double.NegativeInfinity, "-1/0", "-Infinity -> -1/0")
    check(-0.0, "-0", "negative zero -> -0")
  }

  // NUL safe-set pin for Compressor.reSafeRegexp (utils/index.js:244 — `\0` is in the safe
  // set: /^[\\/|\0\s\w^$.[\]()]*$/). No escape spelling compiles on all three platforms: the
  // `\uXXXX` escape inside a character class is rejected by Scala Native's re2 engine (accepted
  // by JVM/JS) — the original ISS-1135 Native blocker, where reSafeRegexp threw
  // PatternSyntaxException at class-load — while the `\0` octal escape is rejected as an illegal
  // octal escape by the JVM and Scala.js (accepted by Native). A LITERAL NUL char is the only
  // spelling JVM, JS, AND Native all accept, and it compiles and matches identically on all three.
  // Driving a NUL-containing RegExp source through the real Compressor path here
  // both exercises reSafeRegexp (so a regression re-throws) and pins that NUL is treated as
  // safe again — `RegExp("\0")` is recognized as a safe regexp and folded to a `/.../` literal.
  test("reSafeRegexp accepts a literal NUL in the source (utils/index.js:244)") {
    val nul  = 0.toChar.toString // a literal NUL char (U+0000), built without an escape spelling
    val opts = MinifyOptions(
      compress = CompressorOptions(unsafe = true, evaluate = true, unsafeRegexp = true),
      mangle = false
    )
    // Calibrate: a plain safe source folds RegExp(...) -> /.../ under these options.
    assertEquals(Terser.minifyToString("x = RegExp(\"abc\");", opts), "x=/abc/;", "safe ASCII source folds to /abc/")
    // The NUL source must likewise be treated safe and fold; regexpSourceFix re-escapes the
    // literal NUL to the `\0` line-terminator escape (Compressor.regexpSourceFix). Before the
    // fix, reSafeRegexp threw a PatternSyntaxException on Native at class-load and NUL was
    // also excluded from the safe set, so this source could never fold.
    val result = Terser.minifyToString("x = RegExp(\"" + nul + "\");", opts)
    assertEquals(result, "x=/\\0/;", "NUL source folds to /\\0/ (NUL is in the safe set): " + result)
  }
}
