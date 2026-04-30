/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Compression tests for join_vars optimization.
 *
 * The join_vars pass merges consecutive `var` declarations into one:
 *   var a = 1; var b = 2; → var a = 1, b = 2;
 *
 * All inputs use declared-only variables. */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }

final class CompressJoinVarsSuite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  private val joinOpts = AllOff.copy(joinVars = true)

  test("join two consecutive vars") {
    assertCompresses(
      input = "var a = 1; var b = 2;",
      expected = "var a=1,b=2;",
      options = joinOpts
    )
  }

  test("join three consecutive vars") {
    assertCompresses(
      input = "var a = 1; var b = 2; var c = 3;",
      expected = "var a=1,b=2,c=3;",
      options = joinOpts
    )
  }

  test("join vars with different initializers") {
    assertCompresses(
      input = "var x = 'hello'; var y = 42; var z = true;",
      expected = "var x=\"hello\",y=42,z=true;",
      options = joinOpts
    )
  }

  test("join vars: no initializer") {
    assertCompresses(
      input = "var a; var b; var c;",
      expected = "var a,b,c;",
      options = joinOpts
    )
  }

  test("join vars: mixed initializers") {
    assertCompresses(
      input = "var a = 1; var b; var c = 3;",
      expected = "var a=1,b,c=3;",
      options = joinOpts
    )
  }

  test("join vars: inside function") {
    assertCompresses(
      input = "function f() { var a = 1; var b = 2; return a + b; }",
      expected = "function f(){var a=1,b=2;return a+b}",
      options = joinOpts
    )
  }

  test("join vars: disabled by default in AllOff") {
    assertCompresses(
      input = "var a = 1; var b = 2;",
      expected = "var a=1;var b=2;",
      options = AllOff
    )
  }

  // Note: ssg-js joins let and const declarations, matching the join_vars
  // behavior for var. Terser also joins them when join_vars is enabled.

  test("join vars: also joins let") {
    assertCompresses(
      input = "let a = 1; let b = 2;",
      expected = "let a=1,b=2;",
      options = joinOpts
    )
  }

  test("join vars: also joins const") {
    assertCompresses(
      input = "const a = 1; const b = 2;",
      expected = "const a=1,b=2;",
      options = joinOpts
    )
  }

  test("join vars: does not join var and let") {
    assertCompresses(
      input = "var a = 1; let b = 2;",
      expected = "var a=1;let b=2;",
      options = joinOpts
    )
  }
}
