/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Reproducer for ISS-1145 (R0610-P2): the compress `toplevel` option must
 * DEFAULT to whether `top_retain` is set.
 *
 * terser lib/compress/index.js:267 —
 *   toplevel : !!(options && options["top_retain"]),
 * so passing `top_retain` (without an explicit `toplevel`) ENABLES toplevel
 * compression, which lets drop_unused operate on toplevel declarations
 * (retaining only the top_retain names). SSG does NOT wire this cross-default:
 * CompressorOptions.resolveDefaults does not default `toplevel` from
 * `topRetain`, so `topRetain` alone is a no-op and unused toplevel vars are
 * NOT dropped.
 *
 * terser oracle (run from original-src/terser/):
 *   node --input-type=module -e "import('./main.js').then(async t=>{ \
 *     const src='var used=1;var unused=2;console.log(used)'; \
 *     for(const o of [{unused:true,top_retain:'used',reduce_vars:false,evaluate:false,collapse_vars:false}, \
 *                     {unused:true,reduce_vars:false,evaluate:false,collapse_vars:false}]){ \
 *       const r=await t.minify(src,{compress:o,mangle:false}); \
 *       console.log(JSON.stringify(o),'->',JSON.stringify(r.code))}})"
 *
 *   WITH top_retain    -> "var used=1;console.log(used);"        (unused DROPPED, used kept)
 *   WITHOUT top_retain -> "var used=1,unused=2;console.log(used);" (BOTH kept)
 *
 * The WITH case proves top_retain enables toplevel compression. */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, compressWithTimeout }

final class ToplevelTopRetainIss1145Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  private val input = "var used=1;var unused=2;console.log(used)"

  // PRIMARY (red): topRetain set, toplevel left at default ToplevelConfig().
  // Per index.js:267 this should default toplevel ON, so drop_unused removes
  // the unused toplevel var while retaining `used`. SSG currently leaves the
  // toplevel untouched (cross-default unwired), so `unused` is KEPT -> RED.
  test("ISS-1145 top_retain defaults toplevel ON so unused toplevel var is dropped") {
    val options = AllOff.copy(
      unused = true,
      reduceVars = false,
      evaluate = false,
      collapseVars = false,
      topRetain = Some((n: String) => n == "used")
      // toplevel intentionally left at the default ToplevelConfig() (off),
      // relying on the top_retain cross-default to enable it.
    )
    compressWithTimeout(input, options) match {
      case Some(actual) =>
        assert(
          !actual.contains("unused"),
          s"ISS-1145: unused toplevel var should be dropped when top_retain is set " +
            s"(terser -> \"var used=1;console.log(used);\"), but got: $actual"
        )
        assert(
          actual.contains("used"),
          s"ISS-1145: retained var `used` should be kept, but got: $actual"
        )
      case None =>
        assert(false, "compression timed out (ISS-031/032)")
    }
  }

  // CONTROL (green): same input/flags but WITHOUT topRetain. toplevel stays
  // off, so drop_unused does not touch the toplevel and BOTH vars are kept.
  // Proves the test isolates the cross-default, not just drop_unused.
  test("ISS-1145 control: without top_retain both toplevel vars are kept") {
    val options = AllOff.copy(
      unused = true,
      reduceVars = false,
      evaluate = false,
      collapseVars = false,
      topRetain = None
    )
    compressWithTimeout(input, options) match {
      case Some(actual) =>
        assert(
          actual.contains("unused") && actual.contains("used"),
          s"ISS-1145 control: both vars should be kept without top_retain, but got: $actual"
        )
      case None =>
        assert(false, "compression timed out (ISS-031/032)")
    }
  }
}
