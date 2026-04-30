/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Compression tests for block simplification.
 * Ported from: terser/test/compress/blocks.js
 *
 * The blocks.js tests verify that empty blocks `{}`, empty statements `;`,
 * and nested blocks `{{{}}}` are flattened/removed.
 *
 * Note: block simplification happens in tightenBody's eliminateSpuriousBlocks
 * pass, which always runs regardless of options. However, some block patterns
 * involve if-statements which trigger the ISS-031/032 hang.
 *
 * Adapted inputs use only declared variables. */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }

final class CompressBlocksSuite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  // =======================================================================
  // remove_blocks: (no options needed — block elimination is always on)
  // Original: { input: { {;} foo(); {}; { {}; }; bar(); {} }
  //            expect: { foo(); bar(); } }
  //
  // Adapted: avoid undeclared foo/bar, use declared functions.
  // =======================================================================

  test("remove empty blocks and statements") {
    assertCompresses(
      input = "function f() { {;} var x = 1; {}; { {}; }; var y = 2; {} }",
      expected = "function f(){var x=1;var y=2}",
      options = AllOff
    )
  }

  test("remove standalone empty block") {
    assertCompresses(
      input = "function f() { {} return 1; }",
      expected = "function f(){return 1}",
      options = AllOff
    )
  }

  test("remove nested empty blocks") {
    assertCompresses(
      input = "function f() { { { {} } } return 1; }",
      expected = "function f(){return 1}",
      options = AllOff
    )
  }

  test("remove empty statement") {
    assertCompresses(
      input = "function f() { ; ; ; return 1; }",
      expected = "function f(){return 1}",
      options = AllOff
    )
  }

  test("flatten block with var declarations") {
    // Block containing only var declarations should be flattened
    assertCompresses(
      input = "function f() { { var x = 1; var y = 2; } return x + y; }",
      expected = "function f(){var x=1;var y=2;return x+y}",
      options = AllOff
    )
  }

  test("preserve non-empty blocks in functions") {
    assertCompresses(
      input = "function f(a) { var b = a + 1; return b; }",
      expected = "function f(a){var b=a+1;return b}",
      options = AllOff
    )
  }

  // =======================================================================
  // Duplicate directive removal (also part of eliminateSpuriousBlocks)
  // =======================================================================

  test("remove duplicate use strict") {
    assertCompresses(
      input = "function f() { 'use strict'; 'use strict'; return 1; }",
      expected = "function f(){\"use strict\";return 1}",
      options = AllOff
    )
  }
}
