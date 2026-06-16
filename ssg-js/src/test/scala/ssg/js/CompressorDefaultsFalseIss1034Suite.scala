/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Differential test for ISS-1034: CompressorOptions(defaults = false) must
 * disable all default-gated optimization passes, matching upstream terser
 * `compress: { defaults: false }` (lib/compress/index.js:222).
 *
 * Oracle: original-src/terser (vendored at upstream-commit 6080510)
 *   - `{compress:{defaults:false}, mangle:false}` on `if(true){a()}else{b()}`
 *     produces `if(true)a();else b();` (conditionals + dead_code are off).
 *   - `{compress:{}, mangle:false}` on the same input produces `a();`.
 *   - `{compress:{defaults:false, evaluate:true}, mangle:false}` on `var x = 1 + 2;`
 *     produces `var x=3;` (evaluate re-enabled under defaults:false).
 *   - `{compress:{defaults:false}, mangle:false}` on `var x = 1 + 2;`
 *     produces `var x=1+2;` (evaluate off).
 *   - `{compress:{defaults:false}, mangle:false}` on `a(); b(); c();`
 *     produces `a();b();c();` (sequences off, no comma-joining).
 *   - `{compress:{}, mangle:false}` on `a(); b(); c();`
 *     produces `a(),b(),c();` (sequences on, comma-joined).
 */
package ssg
package js

import ssg.js.compress.CompressorOptions

final class CompressorDefaultsFalseIss1034Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  // -- Core differential: defaults=false disables gated passes --
  // Oracle: terser `{compress:{defaults:false}, mangle:false}` on
  // `if(true){a()}else{b()}` => `if(true)a();else b();`
  // (conditionals + dead_code both off under defaults:false).
  // Vs defaults=true: `a();` (dead branch eliminated).
  test("ISS-1034: defaults=false disables conditionals+dead_code (if-true branch preserved)") {
    val src       = "if(true){a()}else{b()}"
    val withDefaults    = Terser.minifyToString(src, MinifyOptions(compress = CompressorOptions(), mangle = false))
    val withoutDefaults = Terser.minifyToString(src, MinifyOptions(compress = CompressorOptions(defaults = false), mangle = false))

    // Oracle: terser defaults:true => "a();"
    assertEquals(withDefaults, "a();", "defaults=true must eliminate dead branch (oracle: terser compress:{} => 'a();')")

    // Oracle: terser defaults:false => "if(true)a();else b();"
    assertEquals(
      withoutDefaults,
      "if(true)a();else b();",
      "defaults=false must preserve both branches (oracle: terser compress:{defaults:false} => 'if(true)a();else b();')"
    )

    assertNotEquals(withDefaults, withoutDefaults, "defaults=false must produce DIFFERENT output from defaults=true")
  }

  // -- defaults=false disables evaluate (constant folding off) --
  // Oracle: terser `{compress:{defaults:false}, mangle:false}` on
  // `var x = 1 + 2;` => `var x=1+2;` (evaluate off).
  // Vs defaults=true: `var x=3;` (evaluate folds).
  test("ISS-1034: defaults=false disables evaluate (constant folding off)") {
    val src       = "var x = 1 + 2;"
    val withDefaults    = Terser.minifyToString(src, MinifyOptions(compress = CompressorOptions(), mangle = false))
    val withoutDefaults = Terser.minifyToString(src, MinifyOptions(compress = CompressorOptions(defaults = false), mangle = false))

    // Oracle: terser defaults:true => "var x=3;"
    assertEquals(withDefaults, "var x=3;", "defaults=true must fold 1+2 (oracle: terser compress:{} => 'var x=3;')")

    // Oracle: terser defaults:false => "var x=1+2;"
    assertEquals(
      withoutDefaults,
      "var x=1+2;",
      "defaults=false must NOT fold 1+2 (oracle: terser compress:{defaults:false} => 'var x=1+2;')"
    )
  }

  // -- defaults=false disables sequences (no comma-joining) --
  // Oracle: terser `{compress:{defaults:false}, mangle:false}` on
  // `a(); b(); c();` => `a();b();c();` (sequences off).
  // Vs defaults=true: `a(),b(),c();` (sequences on).
  test("ISS-1034: defaults=false disables sequences (no comma-joining)") {
    val src       = "a(); b(); c();"
    val withDefaults    = Terser.minifyToString(src, MinifyOptions(compress = CompressorOptions(), mangle = false))
    val withoutDefaults = Terser.minifyToString(src, MinifyOptions(compress = CompressorOptions(defaults = false), mangle = false))

    // Oracle: terser defaults:true => "a(),b(),c();"
    assertEquals(withDefaults, "a(),b(),c();", "defaults=true must comma-join (oracle: terser compress:{} => 'a(),b(),c();')")

    // Oracle: terser defaults:false => "a();b();c();"
    assertEquals(
      withoutDefaults,
      "a();b();c();",
      "defaults=false must NOT comma-join (oracle: terser compress:{defaults:false} => 'a();b();c();')"
    )
  }

  // -- Toggle: enabling a normally-OFF option under defaults=false --
  // Oracle: terser `{compress:{defaults:false, evaluate:true}, mangle:false}`
  // on `var x = 1 + 2;` => `var x=3;` (evaluate re-enabled).
  //
  // Value-comparison limitation: `CompressorOptions(defaults = false, evaluate = true)`
  // cannot distinguish the explicit `true` from the case-class default `true`, so
  // resolveDefaults turns evaluate off. The documented API for this edge case is
  // `NoDefaults.copy(evaluate = true)`, which works because evaluate starts as `false`
  // in NoDefaults and the caller's `.copy` sets it to `true` explicitly.
  test("ISS-1034: NoDefaults.copy(evaluate=true) re-enables evaluate (toggle preserved)") {
    val src = "var x = 1 + 2;"

    // NoDefaults.copy(evaluate = true) — the documented API for enabling a
    // normally-ON pass under defaults=false (oracle: terser {defaults:false,
    // evaluate:true} => "var x=3;")
    val viaNoDefaults = Terser.minifyToString(
      src,
      MinifyOptions(compress = CompressorOptions.NoDefaults.copy(evaluate = true), mangle = false)
    )

    // Oracle: terser {defaults:false, evaluate:true} => "var x=3;"
    assertEquals(viaNoDefaults, "var x=3;", "NoDefaults.copy(evaluate=true) must fold 1+2 (oracle: 'var x=3;')")

    // Confirm that without the toggle, evaluate is indeed off
    val withoutToggle = Terser.minifyToString(
      src,
      MinifyOptions(compress = CompressorOptions.NoDefaults, mangle = false)
    )
    assertEquals(withoutToggle, "var x=1+2;", "NoDefaults alone must NOT fold 1+2 (oracle: 'var x=1+2;')")
  }

  // -- Toggle: enabling a normally-OFF option (arguments) under defaults=false --
  // `arguments` defaults to `false` in both Defaults and NoDefaults, so
  // `CompressorOptions(defaults = false, arguments = true)` sets a value that
  // differs from Defaults.arguments (false), and resolveDefaults preserves it.
  test("ISS-1034: defaults=false + arguments=true preserves the toggle (value differs from Defaults)") {
    val resolved = CompressorOptions.resolveDefaults(CompressorOptions(defaults = false, arguments = true))
    assert(resolved.arguments, "arguments=true must survive resolveDefaults (value differs from Defaults.arguments=false)")
    // All gated passes must still be off
    assert(!resolved.evaluate, "evaluate must be off under defaults=false")
    assert(!resolved.deadCode, "deadCode must be off under defaults=false")
  }

  // -- Equivalence: CompressorOptions(defaults=false) == CompressorOptions.NoDefaults --
  // After resolveDefaults wiring, both paths must produce identical compressor output.
  test("ISS-1034: CompressorOptions(defaults=false) produces same output as NoDefaults") {
    val src = "if(true){a()}else{b()}"

    val viaDefaultsFalse = Terser.minifyToString(
      src,
      MinifyOptions(compress = CompressorOptions(defaults = false), mangle = false)
    )

    val viaNoDefaults = Terser.minifyToString(
      src,
      MinifyOptions(compress = CompressorOptions.NoDefaults, mangle = false)
    )

    assertEquals(
      viaDefaultsFalse,
      viaNoDefaults,
      "CompressorOptions(defaults=false) must produce identical output to CompressorOptions.NoDefaults"
    )
  }

  // -- Baseline: defaults=true (or omitted) is unchanged --
  // Ensures the resolution is a no-op when defaults != false.
  test("ISS-1034: defaults=true (default) behavior is unchanged") {
    val src = "if(true){a()}else{b()}"
    val explicit   = Terser.minifyToString(src, MinifyOptions(compress = CompressorOptions(), mangle = false))
    val defaultTrue = Terser.minifyToString(src, MinifyOptions(compress = CompressorOptions(defaults = true), mangle = false))

    assertEquals(explicit, defaultTrue, "explicit defaults=true must match omitted defaults (both are Defaults)")
    assertEquals(explicit, "a();", "defaults=true must still eliminate dead branches (oracle: 'a();')")
  }
}
