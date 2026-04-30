/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Compression tests for defaults behavior and option independence.
 * Ported from: terser/test/compress/defaults.js
 *
 * Verifies that `defaults: false` turns off all optimizations, and that
 * individual options can be selectively enabled.
 *
 * NOTE: Uses AllOff base (false_by_default) and only enables specific
 * flags to avoid the ISS-031/032 hang. All inputs use declared-only
 * variables to avoid the ScopeAnalysis hang on undeclared globals. */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }

final class CompressDefaultsSuite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  // =======================================================================
  // Options disabled: code is only reformatted
  // =======================================================================

  test("all_off: no optimization on arithmetic") {
    assertCompresses(
      input = "var x = 1 + 2;",
      expected = "var x=1+2;",
      options = AllOff
    )
  }

  test("all_off: no dead code removal") {
    assertCompresses(
      input = "function f() { return 1; var x = 2; }",
      expected = "function f(){return 1;var x=2}",
      options = AllOff
    )
  }

  test("all_off: no var joining") {
    assertCompresses(
      input = "var a = 1; var b = 2;",
      expected = "var a=1;var b=2;",
      options = AllOff
    )
  }

  // =======================================================================
  // Single option enabled at a time — verify independence
  // =======================================================================

  test("evaluate only: folds constants but not dead code") {
    val opts = AllOff.copy(evaluate = true)
    assertCompresses(
      input = "function f() { return 1 + 2; var x = 3 + 4; }",
      expected = "function f(){return 3;var x=7}",
      options = opts
    )
  }

  test("dead_code only: removes dead code but not constants") {
    val opts = AllOff.copy(deadCode = true)
    assertCompresses(
      input = "function f() { return 1 + 2; var x = 3 + 4; }",
      expected = "function f(){return 1+2;var x}",
      options = opts
    )
  }

  test("join_vars only: joins vars but no other optimization") {
    val opts = AllOff.copy(joinVars = true)
    assertCompresses(
      input = "var a = 1 + 2; var b = 3 + 4;",
      expected = "var a=1+2,b=3+4;",
      options = opts
    )
  }

  test("drop_debugger only: removes debugger but nothing else") {
    val opts = AllOff.copy(dropDebugger = true)
    assertCompresses(
      input = "var x = 1 + 2; debugger; var y = 3;",
      expected = "var x=1+2;var y=3;",
      options = opts
    )
  }

  // =======================================================================
  // Two options enabled — verify combination
  // =======================================================================

  test("evaluate + dead_code: both active") {
    val opts = AllOff.copy(evaluate = true, deadCode = true)
    assertCompresses(
      input = "function f() { return 1 + 2; var x = 3 + 4; }",
      expected = "function f(){return 3;var x}",
      options = opts
    )
  }

  test("evaluate + join_vars: both active") {
    val opts = AllOff.copy(evaluate = true, joinVars = true)
    assertCompresses(
      input = "var a = 1 + 2; var b = 3 + 4;",
      expected = "var a=3,b=7;",
      options = opts
    )
  }

  test("dead_code + join_vars: both active") {
    // dead_code hoists var c to the joined declaration before return
    val opts = AllOff.copy(deadCode = true, joinVars = true)
    assertCompresses(
      input = "function f() { var a = 1; var b = 2; return a; var c = 3; }",
      expected = "function f(){var a=1,b=2,c;return a}",
      options = opts
    )
  }

  test("evaluate + dead_code + drop_debugger: triple combo") {
    val opts = AllOff.copy(evaluate = true, deadCode = true, dropDebugger = true)
    assertCompresses(
      input = "function f() { debugger; return 1 + 2; var x = 3; }",
      expected = "function f(){return 3;var x}",
      options = opts
    )
  }
}
