/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Differential test for ISS-1043: computeCharFrequency must influence mangled
 * output. The wiring is present at Terser.scala:127/133 —
 * Mangler.computeCharFrequency -> Base54.sort -> Mangler.mangleNames; this
 * suite proves that the frequency-sorted Base54 alphabet actually changes
 * which identifier names are assigned, and that the result matches terser.
 *
 * Test strategy:
 *   (1) With-vs-without toggle: parse the same code twice. Run A skips
 *       computeCharFrequency (Base54.reset + sort gives default a,b,c order).
 *       Run B calls computeCharFrequency first (frequency-sorted order).
 *       The two runs MUST produce DIFFERENT mangled names for the same input.
 *   (2) Terser-oracle match: for the same input, SSG's full
 *       Terser.minify(..., mangle=true, compress=false) must byte-match
 *       terser 5.46.1. Each oracle test resets Base54 to default order
 *       BEFORE calling Terser.minify, so the only way the frequency-ordered
 *       result appears is if Terser.minify itself calls computeCharFrequency
 *       internally (Terser.scala:127/133).
 *
 * Input design: the function body includes a 60-char 'z' string literal
 * ("zzzzz..."). This makes 'z' the most frequent Base54-eligible character
 * in the printed output (+1 per char from the whole program, -1 per
 * mangleable symbol name). After frequency sorting, Base54.get(0) yields 'z'
 * instead of the default 'a', so the first parameter is mangled to 'z'.
 *
 * Oracle (C11): vendored terser at original-src/terser, version 5.46.1
 * (package.json:7), executed with node:
 *
 *   cd original-src/terser && node --input-type=module -e "
 *   import { minify } from './main.js';
 *   const code = 'function test(alpha,beta,gamma){return alpha+beta+gamma+\"zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz\"}';
 *   const r = await minify(code, {compress:false, mangle:true});
 *   console.log(JSON.stringify(r.code));"
 *
 * Oracle output (2026-06-16):
 *   "function test(z,t,n){return z+t+n+\"zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz\"}"
 *
 * Without frequency sorting the default Base54 alphabet (a,b,c,...) would
 * assign 'a','b','c' to the three parameters — confirming the differential.
 */
package ssg
package js

import ssg.js.output.OutputStream
import ssg.js.parse.Parser
import ssg.js.scope.{ Base54, Mangler, ManglerOptions, ScopeAnalysis }

final class CharFrequencyIss1043Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  // 60-char z-string makes 'z' the dominant Base54-eligible character
  private val code =
    "function test(alpha,beta,gamma){return alpha+beta+gamma+\"zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz\"}"

  /** Expected output when Base54 uses default a,b,c,... ordering (no charFrequency). */
  private val expectedDefault =
    "function test(a,b,c){return a+b+c+\"zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz\"}"

  /** Expected output when Base54 uses frequency-sorted ordering (z,t,n,...). This is also the terser 5.46.1 oracle output.
    */
  private val expectedFreqSorted =
    "function test(z,t,n){return z+t+n+\"zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz\"}"

  /** Reset Base54 to the default alphabet order (a,b,c,...). All frequencies are zeroed, so the stable sort preserves the original character ordering.
    */
  private def resetBase54ToDefault(): Unit = {
    Base54.reset()
    Base54.sort()
  }

  /** Parse, scope-analyse, and mangle WITHOUT computeCharFrequency (default Base54 alphabet: a,b,c,...).
    */
  private def mangleWithoutFrequency(src: String): String = {
    val ast  = new Parser().parse(src)
    val opts = ManglerOptions()
    ScopeAnalysis.figureOutScope(ast)
    // Reset Base54 to default alphabet order (all-zero frequencies -> stable
    // sort preserves original a,b,c,... ordering)
    resetBase54ToDefault()
    Mangler.mangleNames(ast, opts)
    OutputStream.printToString(ast)
  }

  /** Parse, scope-analyse, compute char frequency, and mangle (frequency- sorted Base54 alphabet).
    */
  private def mangleWithFrequency(src: String): String = {
    val ast  = new Parser().parse(src)
    val opts = ManglerOptions()
    ScopeAnalysis.figureOutScope(ast)
    // computeCharFrequency resets, considers the whole program, subtracts
    // mangleable names, then sorts — same as Terser.scala:127/133
    Mangler.computeCharFrequency(ast, opts)
    Mangler.mangleNames(ast, opts)
    OutputStream.printToString(ast)
  }

  // ---- Part 1: with-vs-without toggle (core differential) ----

  test("ISS-1043 (a): without charFrequency, mangled names follow default a,b,c order") {
    val out = mangleWithoutFrequency(code)
    // Default Base54 order: get(0)='a', get(1)='b', get(2)='c'
    // 'test' is a top-level function decl (not mangled by default), so only
    // the three parameters alpha, beta, gamma are renamed.
    assertEquals(out, expectedDefault, "without charFrequency the default Base54 alphabet assigns a, b, c")
  }

  test("ISS-1043 (b): with charFrequency, mangled names follow frequency-sorted order") {
    val out = mangleWithFrequency(code)
    // With frequency sorting, 'z' dominates (60 in the string literal +
    // occurrences in the rest of the program), so get(0)='z'. The next
    // most frequent characters from the printed source are 't' (from
    // 'function', 'test', 'return') and 'n' (from 'function', 'return'),
    // giving z, t, n.
    assertEquals(out, expectedFreqSorted, "with charFrequency the frequency-sorted Base54 alphabet assigns z, t, n")
  }

  test("ISS-1043 (c): with-vs-without charFrequency produce DIFFERENT output") {
    val withoutFreq = mangleWithoutFrequency(code)
    val withFreq    = mangleWithFrequency(code)
    assertNotEquals(
      withoutFreq,
      withFreq,
      "charFrequency must change the mangled output — if these are equal, frequency is not consulted"
    )
  }

  // ---- Part 2: terser-oracle match ----
  // Each oracle test resets Base54 to default order BEFORE calling
  // Terser.minifyToString. This ensures the only way to get frequency-
  // sorted output (z,t,n) is if Terser.minify internally calls
  // computeCharFrequency (Terser.scala:127/133). If that call were
  // removed, the mangler would see default-order Base54 and produce
  // a,b,c — failing the assertion.

  test("ISS-1043 (d): Terser.minify (ManglerOptions) matches terser 5.46.1 oracle (charFrequency active)") {
    resetBase54ToDefault()
    val opts = MinifyOptions(compress = false, mangle = ManglerOptions())
    val out  = Terser.minifyToString(code, opts)
    assertEquals(out, expectedFreqSorted, "SSG mangle output must byte-match terser 5.46.1 (compress:false, mangle:true)")
  }

  test("ISS-1043 (e): Terser.minify (mangle=true as Boolean) also matches oracle") {
    // minify.js:161-174 normalizes Boolean true to default ManglerOptions
    resetBase54ToDefault()
    val opts = MinifyOptions(compress = false, mangle = true)
    val out  = Terser.minifyToString(code, opts)
    assertEquals(
      out,
      expectedFreqSorted,
      "Boolean mangle=true must resolve to default ManglerOptions with charFrequency"
    )
  }
}
