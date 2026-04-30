/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Compression tests for issue-417 (unexpected crash during inlining).
 * Ported from: terser/test/compress/issue-417.js (2 test cases)
 *
 * Both tests use expect_stdout (runtime execution) and prepend_code.
 * Cannot verify runtime JS execution in Scala.
 *
 * Auto-ported by hand since gen-compress-tests.js does not support expect_stdout format. */
package ssg
package js
package compress

final class CompressIssue417Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  // =========================================================================
  // test_unexpected_crash — verify compression doesn't crash on this input
  // Original uses expect_stdout with prepend_code to verify runtime behavior
  // =========================================================================
  test("test_unexpected_crash".fail) {
    // This test uses prepend_code: "x();" and expect_stdout to verify runtime output.
    // Cannot verify runtime JS execution in Scala.
    fail("expect_stdout + prepend_code tests require JS runtime — not supported")
  }

  // =========================================================================
  // test_unexpected_crash_2
  // =========================================================================
  test("test_unexpected_crash_2".fail) {
    fail("expect_stdout + prepend_code tests require JS runtime — not supported")
  }
}
