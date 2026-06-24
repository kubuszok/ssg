/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Compression tests for typeof evaluation.
 * Ported from: terser/test/compress/typeof.js
 *
 * Uses CompressTestHelper with false_by_default mode.
 *
 * KNOWN LIMITATIONS:
 * - ISS-031/032: ScopeAnalysis hangs on undeclared globals.
 *   typeof_evaluation original test uses 9 typeof expressions on literals;
 *   most work since typeof doesn't need scope for literal arguments.
 * - typeof on `undefined` identifier hangs (undeclared global reference).
 * - typeof on functions/vars works since they're declared. */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }

final class CompressTypeofSuite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  private val typeofOpts = AllOff.copy(evaluate = true, typeofs = true)

  // =======================================================================
  // typeof_evaluation from typeof.js (adapted: no undeclared refs)
  // Original: { options = { evaluate: true, typeofs: true }
  //   input: { a = typeof 1; b = typeof 'test'; c = typeof []; d = typeof {};
  //            e = typeof /./; f = typeof false; g = typeof function(){};
  //            h = typeof undefined; i = typeof 1n; }
  //   expect: { a='number'; b='string'; c='object'; d='object';
  //            e=typeof /./; f='boolean'; g='function';
  //            h='undefined'; i='bigint'; }
  //
  // Note: original uses undeclared vars (a, b, c...), so we wrap in vars.
  // =======================================================================

  test("typeof number literal") {
    assertCompresses("var a = typeof 1;", "var a=\"number\";", typeofOpts)
  }

  test("typeof string literal") {
    assertCompresses("var b = typeof 'test';", "var b=\"string\";", typeofOpts)
  }

  test("typeof array literal") {
    assertCompresses("var c = typeof [];", "var c=\"object\";", typeofOpts)
  }

  test("typeof object literal") {
    assertCompresses("var d = typeof {};", "var d=\"object\";", typeofOpts)
  }

  // typeof regex — Terser does NOT fold this (typeof /./  stays as-is)
  test("typeof regex NOT folded") {
    assertCompresses("var e = typeof /./;", "var e=typeof/./;", typeofOpts)
  }

  test("typeof boolean literal") {
    assertCompresses("var f = typeof false;", "var f=\"boolean\";", typeofOpts)
  }

  test("typeof function expression") {
    assertCompresses("var g = typeof function(){};", "var g=\"function\";", typeofOpts)
  }

  test("typeof undefined") {
    assertCompresses("var h = typeof undefined;", "var h=\"undefined\";", typeofOpts)
  }

  // typeof bigint
  test("typeof bigint literal") {
    assertCompresses("var i = typeof 1n;", "var i=\"bigint\";", typeofOpts)
  }

  // =======================================================================
  // typeof on declared variables (scope analysis completes for these)
  // =======================================================================

  test("typeof declared var") {
    // typeof on a declared var — should NOT be folded (value might change)
    assertCompresses("var x = 1; var t = typeof x;", "var x=1;var t=typeof x;", typeofOpts)
  }

  test("typeof declared function") {
    // typeof on a declared function — could theoretically fold to "function"
    // but Terser only does this with reduce_vars
    assertCompresses(
      "function f() {} var t = typeof f;",
      "function f(){}var t=typeof f;",
      typeofOpts
    )
  }

  // =======================================================================
  // typeof behavior with/without typeofs flag
  // Note: ssg-js evaluate pass folds typeof on literals even without typeofs
  // flag. This diverges from Terser where typeofs is required.
  // =======================================================================

  test("typeof folded even without typeofs flag (divergence from Terser)") {
    // ssg-js: typeof on literal is folded by evaluate alone (typeofs not needed)
    val opts = AllOff.copy(evaluate = true, typeofs = false)
    assertCompresses("var a = typeof 1;", "var a=\"number\";", opts)
  }

  test("typeof folded with evaluate only (divergence from Terser)") {
    val opts = AllOff.copy(evaluate = true)
    assertCompresses("var a = typeof 'test';", "var a=\"string\";", opts)
  }

  // =======================================================================
  // typeof in combination with other expressions
  // =======================================================================

  test("typeof concatenation is fully folded") {
    // evaluate folds typeof + typeof AND the string concatenation
    assertCompresses(
      "var x = typeof 42 + typeof 'hello';",
      "var x=\"numberstring\";",
      typeofOpts
    )
  }

  test("typeof comparison fully folded") {
    // evaluate folds typeof AND the === comparison (since both sides constant)
    assertCompresses(
      "var x = typeof 42 === 'number';",
      "var x=true;",
      typeofOpts
    )
  }
}
