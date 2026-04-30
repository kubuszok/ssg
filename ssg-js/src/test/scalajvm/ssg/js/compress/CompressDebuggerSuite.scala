/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Compression tests for debugger statement removal.
 * Ported from: terser/test/compress/debugger.js (2 test cases)
 *
 * Uses CompressTestHelper with false_by_default mode.
 *
 * ISS-031/032: drop_debugger combined with if-statement hangs because
 * after dropping the debugger, the resulting `if (foo);` triggers
 * the ScopeAnalysis hang on the undeclared `foo` reference. */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }

final class CompressDebuggerSuite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  // =======================================================================
  // keep_debugger: { options = { drop_debugger: false }
  //   input: { debugger; }
  //   expect: { debugger; }
  // }
  // =======================================================================
  test("keep_debugger") {
    assertCompresses(
      input = "debugger;",
      expected = "debugger;",
      options = AllOff.copy(dropDebugger = false)
    )
  }

  // =======================================================================
  // drop_debugger: { options = { drop_debugger: true }
  //   input: { debugger; if (foo) debugger; }
  //   expect: { if (foo); }
  //
  // Original test uses undeclared `foo` → ISS-031/032 hang.
  // Split into: standalone debugger (works) + if-debugger (hangs).
  // =======================================================================

  test("drop_debugger: standalone") {
    assertCompresses(
      input = "debugger;",
      expected = "",
      options = AllOff.copy(dropDebugger = true)
    )
  }

  test("drop_debugger: with surrounding code") {
    assertCompresses(
      input = "var x = 1; debugger; var y = 2;",
      expected = "var x=1;var y=2;",
      options = AllOff.copy(dropDebugger = true)
    )
  }

  test("drop_debugger: multiple debuggers") {
    assertCompresses(
      input = "debugger; debugger; debugger;",
      expected = "",
      options = AllOff.copy(dropDebugger = true)
    )
  }

  test("drop_debugger: inside function") {
    assertCompresses(
      input = "function f() { debugger; return 1; }",
      expected = "function f(){return 1}",
      options = AllOff.copy(dropDebugger = true)
    )
  }

  test("drop_debugger: in function with vars") {
    assertCompresses(
      input = "function f(a) { debugger; var b = a + 1; debugger; return b; }",
      expected = "function f(a){var b=a+1;return b}",
      options = AllOff.copy(dropDebugger = true)
    )
  }

  test("drop_debugger: in if statement (undeclared ref)") {
    assertCompresses("debugger; if (foo) debugger;", "if(foo);", AllOff.copy(dropDebugger = true))
  }
}
