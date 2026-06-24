/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Property mangling tests for computed properties with keep_quoted:"strict".
 * Ported from: terser/test/compress/mangleprops-strict-computed.js (24 test cases)
 *
 * All 24 tests use mangle.properties with keep_quoted:"strict" option
 * and verify output via terser's expect_stdout (minify -> execute in Node ->
 * assert stdout == "bar"). PropMangler IS integrated into Terser.minify()
 * (Terser.scala:492) and produces runtime-correct output; they remain
 * expected-failure only because SSG's test harness has no expect_stdout /
 * JS-execution path — see ISS-1326 (port it or re-author as output checks).
 *
 * Auto-ported by hand since gen-compress-tests.js does not support mangle format. */
package ssg
package js
package compress

final class CompressManglepropsStrictComputedSuite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  // PropMangler IS wired into Terser.minify (Terser.scala:492); they remain
  // expected-failure until SSG ports an expect_stdout / JS-execution path — ISS-1326.

  test("computed_props_keep_quoted_inlined_sub_1".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("computed_props_keep_quoted_inlined_sub_2".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("computed_props_keep_quoted_inlined_sub_3".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("computed_props_keep_quoted_inlined_sub_4".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("computed_props_keep_quoted_inlined_sub_5".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("computed_props_keep_quoted_inlined_sub_6".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("computed_props_keep_quoted_inlined_sub_7".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("computed_props_keep_quoted_inlined_sub_8".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("computed_props_keep_quoted_inlined_sub_9".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("computed_props_keep_quoted_inlined_sub_10".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("no_computed_props_keep_quoted_inlined_sub_1".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("no_computed_props_keep_quoted_inlined_sub_2".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("no_computed_props_keep_quoted_inlined_sub_3".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("no_computed_props_keep_quoted_inlined_sub_4".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("no_computed_props_keep_quoted_inlined_sub_5".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("no_computed_props_keep_quoted_inlined_sub_6".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("no_computed_props_keep_quoted_inlined_sub_7".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("no_computed_props_keep_quoted_inlined_sub_8".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("no_computed_props_keep_quoted_inlined_sub_9".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("no_computed_props_keep_quoted_inlined_sub_10".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("no_computed_props_keep_quoted_property_access_sub".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("computed_props_keep_quoted_property_access_sub".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("no_computed_props_keep_quoted_optional_property_access_sub".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
  test("computed_props_keep_quoted_optional_property_access_sub".fail)(fail("blocked on expect_stdout / JS-execution test runner — ISS-1326"))
}
