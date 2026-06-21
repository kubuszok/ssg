/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Red reproducer for ISS-1236 (R0610-P1, test-isolation): a cross-run leak of the
 * global SymbolDef id counter changes reduce_vars/inline folding for an unrelated
 * later compression.
 *
 * The leaked global is `SymbolDef.nextId` (ssg-js/.../scope/SymbolDef.scala:186), a
 * process-wide monotonic counter assigned to every `SymbolDef.id` (SymbolDef.scala:76).
 * It is NEVER reset between compile units. Any earlier operation that allocates symbol
 * defs (here: a `Terser.minify` with `mangle` enabled, the same thing the mangle suites
 * do) advances `nextId`, so a subsequent compression sees its symbols allocated at a
 * higher id offset.
 *
 * The compressor's reduce_vars / inline folding for `recursive_inlining_3` is sensitive
 * to that absolute id value: with the counter at its fresh value the inlined call
 * argument `x - 1` for `foo` is preserved; once the counter has been advanced by a prior
 * mangle the same argument is constant-folded to `0`. The output therefore depends on
 * what ran before in the same JVM — a cross-run global-state leak.
 *
 * Diagnosis evidence (this branch HEAD): `pollute()` then compress yields
 *   ...if(x)bar(0)...if(x)qux(x-1)...if(x)foo(0)...
 * Setting `SymbolDef.nextId` to the same value WITHOUT polluting reproduces it exactly,
 * and resetting `SymbolDef.nextId` back to 1 after the pollute flips `foo(0)` back to
 * `foo(x - 1)` — proving `nextId` is the leaked global that drives the `foo` divergence.
 *
 * terser is faithful here (its module-level `SymbolDef.next_id` is likewise never reset)
 * because its reduce_vars does not key folding on the absolute id; the SSG port does.
 *
 * Oracle (C11): the input/expected/options below are copied VERBATIM from
 * CompressReduceVarsSuite.recursive_inlining_3, whose expected value was recorded from
 * vendored terser 5.46.1 (original-src/terser/package.json:7). The terser-faithful
 * output keeps every inlined argument as `x - 1`. This test asserts that faithful output
 * and goes red on current HEAD because the leaked id counter makes the compressor fold
 * the inlined arguments to `0`. */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }

import ssg.js.{ MinifyOptions, Terser }
import ssg.js.scope.ManglerOptions

final class CompressStateLeakIss1236Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  /** Self-contained polluting operation: a mangle minify that allocates symbol defs and thereby advances the process-wide `SymbolDef.nextId` counter. This mirrors what the ssg-js mangle suites (e.g.
    * MangledLabelsIss1136Suite) do before this compression in a full `ssg.js.*` run — but here it is performed inline so the leak does not depend on suite execution order.
    */
  private def pollutNextIdCounter(): Unit = {
    val _ = Terser.minifyToString(
      "function f(x){outer:for(var i=0;i<x;i++){for(var j=0;j<i;j++){if(j===2)continue outer}}return i}",
      MinifyOptions(compress = false, mangle = ManglerOptions())
    )
  }

  test("ISS-1236: recursive_inlining_3 survives a prior mangle that advances SymbolDef.nextId") {
    // First: pollute the global SymbolDef.nextId counter via a prior mangle minify.
    pollutNextIdCounter()

    // Then: compress recursive_inlining_3's EXACT input with its EXACT options and assert
    // its EXACT terser-faithful expected output. Copied verbatim from
    // CompressReduceVarsSuite.recursive_inlining_3 (terser 5.46.1 oracle).
    assertCompresses(
      input = """!function() {
            function foo(x) { console.log("foo", x); if (x) bar(x-1); }
            function bar(x) { console.log("bar", x); if (x) qux(x-1); }
            function qux(x) { console.log("qux", x); if (x) foo(x-1); }
            qux(4);
        }()""".stripMargin.trim,
      expected = """!function() {
            function qux(x) {
                console.log("qux", x);
                if (x) (function(x) {
                    console.log("foo", x);
                    if (x) (function(x) {
                        console.log("bar", x);
                        if (x) qux(x - 1);
                    })(x - 1);
                })(x - 1);
            }
            qux(4);
        }()""".stripMargin.trim,
      options = AllOff.copy(
        passes = 2,
        reduceFuncs = true,
        reduceVars = true,
        unused = true
      )
    )
  }
}
