/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Property mangling tests for computed properties with keep_quoted:"strict".
 * Ported from: terser/test/compress/mangleprops-strict-computed.js (24 test cases)
 *
 * All 24 tests use mangle.properties with keep_quoted:"strict" option
 * and verify output via expect_stdout. Property mangling is not integrated
 * into the Terser.minify() API, so these are marked .fail.
 *
 * Auto-ported by hand since gen-compress-tests.js does not support mangle format. */
package ssg
package js
package compress

final class CompressManglepropsStrictComputedSuite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  // All 24 tests require mangle.properties integration in Terser.minify()
  // which is not yet implemented. PropMangler exists but is not wired in.

  test("computed_props_keep_quoted_inlined_sub_1".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("computed_props_keep_quoted_inlined_sub_2".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("computed_props_keep_quoted_inlined_sub_3".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("computed_props_keep_quoted_inlined_sub_4".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("computed_props_keep_quoted_inlined_sub_5".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("computed_props_keep_quoted_inlined_sub_6".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("computed_props_keep_quoted_inlined_sub_7".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("computed_props_keep_quoted_inlined_sub_8".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("computed_props_keep_quoted_inlined_sub_9".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("computed_props_keep_quoted_inlined_sub_10".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("no_computed_props_keep_quoted_inlined_sub_1".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("no_computed_props_keep_quoted_inlined_sub_2".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("no_computed_props_keep_quoted_inlined_sub_3".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("no_computed_props_keep_quoted_inlined_sub_4".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("no_computed_props_keep_quoted_inlined_sub_5".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("no_computed_props_keep_quoted_inlined_sub_6".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("no_computed_props_keep_quoted_inlined_sub_7".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("no_computed_props_keep_quoted_inlined_sub_8".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("no_computed_props_keep_quoted_inlined_sub_9".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("no_computed_props_keep_quoted_inlined_sub_10".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("no_computed_props_keep_quoted_property_access_sub".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("computed_props_keep_quoted_property_access_sub".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("no_computed_props_keep_quoted_optional_property_access_sub".fail) { fail("mangle.properties not integrated into Terser.minify()") }
  test("computed_props_keep_quoted_optional_property_access_sub".fail) { fail("mangle.properties not integrated into Terser.minify()") }
}
