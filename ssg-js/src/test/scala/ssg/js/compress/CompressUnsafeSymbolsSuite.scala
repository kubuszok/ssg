/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Compression tests for unsafe_symbols optimization.
 * Ported from: terser/test/compress/unsafe_symbols.js (2 test cases)
 *
 * Auto-ported by hand since gen-compress-tests.js does not support expect_exact format. */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }

final class CompressUnsafeSymbolsSuite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  // =========================================================================
  // unsafe_symbols_1 — no optimization, preserve Symbol("kDog")
  // =========================================================================
  test("unsafe_symbols_1") {
    assertCompresses(
      input = "Symbol(\"kDog\");",
      expected = "Symbol(\"kDog\");",
      options = AllOff
    )
  }

  // =========================================================================
  // unsafe_symbols_2 — with unsafe+unsafe_symbols, drop Symbol argument
  // =========================================================================
  test("unsafe_symbols_2") {
    assertCompresses(
      input = "Symbol(\"kDog\");",
      expected = "Symbol();",
      options = AllOff.copy(unsafe = true, unsafeSymbols = true)
    )
  }
}
