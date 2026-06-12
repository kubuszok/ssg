/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1136: mangled names never reach the printed output even with an
 * explicit ManglerOptions. The mangler DOES assign `SymbolDef.mangledName` (verified
 * during ISS-1033 fixture validation; see TerserBooleanOptionsIss1033Suite control B),
 * and `OutputStream.getSymbolName` (OutputStream.scala:1710-1714) honors
 * `thedef.mangledName` — yet `function f(longName){return longName}` prints UNMANGLED
 * while its SymbolDef carries a mangled name. The issue text blames a missing `thedef`
 * linkage on the printed symbol nodes; the white-box control below refutes that half
 * (the linkage IS present) and narrows the defect to the print path: `printSymbol`
 * (OutputStream.scala:1780) emits `node.name` directly, bypassing getSymbolName.
 *
 * Oracle (C11): the vendored original terser at original-src/terser, version 5.46.1
 * (package.json:7), executed with node v24.12.0:
 *
 *   cd original-src/terser && node --input-type=module -e "
 *   import { minify } from './main.js';
 *   const r = await minify(CODE, {mangle: true, compress: false});
 *   console.log(r.code);"
 *
 * Expected outputs recorded from that run (2026-06-12):
 *   a) function f(longName){return longName}
 *        → function f(n){return n}
 *   b) function outer(alpha){function inner(beta){var gamma=beta+alpha;return gamma}return inner(alpha)}
 *        → function outer(n){function r(r){var t=r+n;return t}return r(n)}
 *   c) function sum(){var first=1;var second=2;return first+second}
 *        → function sum(){var r=1;var n=2;return r+n}
 *   d) function f(longName){return longName}  with {mangle: false, compress: false}
 *        → function f(longName){return longName}      (control: unmangled)
 *   e) function h(one,two,three){var four=one+two;return four*three}
 *        → function h(n,r,t){var u=n+r;return u*t}     (collisions force distinct names)
 *
 * Upstream semantics: lib/minify.js:270-276 — truthy `options.mangle` runs
 * `figure_out_scope`, `compute_char_frequency`, and `mangle_names`; the printer
 * (lib/output.js, `get_name(this.definition())`) then emits the mangled name via the
 * symbol's definition linkage. The terser default mangler picks names by character
 * frequency of the source (hence `n`, `r`, `t`, `u` above rather than `a`, `b`, `c`).
 *
 * Distinct from the existing ISS-1136 pins in CompressIssue1704Suite (catch-variable
 * fixtures, harness issue ISS-1144): the fixtures here are function parameters and
 * `var` declarations only.
 */
package ssg
package js

import ssg.js.ast.{ AstDefun, AstSymbol }
import ssg.js.scope.{ ManglerOptions, SymbolDef }

final class MangledNamesIss1136Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  /** Equivalent of terser's `{mangle: true, compress: false}` (minify.js:117,123 defaults). */
  private val mangleOnly = MinifyOptions(compress = false, mangle = ManglerOptions())

  private val noOpt = MinifyOptions(compress = false, mangle = false)

  // -- Red: mangle-enabled cases, expected output taken verbatim from terser 5.46.1 --

  test("red ISS-1136 (a): function param is renamed in printed output") {
    val out = Terser.minifyToString("function f(longName){return longName}", mangleOnly)
    assertEquals(out, "function f(n){return n}", "mangled output must match terser 5.46.1")
  }

  test("red ISS-1136 (b): nested function params and inner vars are renamed") {
    val out = Terser.minifyToString(
      "function outer(alpha){function inner(beta){var gamma=beta+alpha;return gamma}return inner(alpha)}",
      mangleOnly
    )
    assertEquals(
      out,
      "function outer(n){function r(r){var t=r+n;return t}return r(n)}",
      "mangled output must match terser 5.46.1"
    )
  }

  test("red ISS-1136 (c): var declarations inside a function body are renamed") {
    val out = Terser.minifyToString("function sum(){var first=1;var second=2;return first+second}", mangleOnly)
    assertEquals(out, "function sum(){var r=1;var n=2;return r+n}", "mangled output must match terser 5.46.1")
  }

  test("red ISS-1136 (e): colliding names receive distinct mangled names") {
    val out = Terser.minifyToString("function h(one,two,three){var four=one+two;return four*three}", mangleOnly)
    assertEquals(out, "function h(n,r,t){var u=n+r;return u*t}", "mangled output must match terser 5.46.1")
  }

  // -- Control, white-box: the printed symbol node DOES carry the SymbolDef linkage --
  // Probe result (2026-06-12, this suite's first red run): this test PASSES today.
  // It refines the issue's diagnosis: the AstSymbolFunarg node the printer visits IS
  // linked (`thedef` is the SymbolDef carrying `mangledName`), so the defect is not a
  // missing linkage but the print path itself — `printSymbol` (OutputStream.scala:1780)
  // emits `node.name` directly and never consults getSymbolName / thedef.mangledName.
  // Keep this green: it pins the mangler+linkage half of the contract.

  test("control ISS-1136 (white-box): funarg symbol node is linked to its mangled SymbolDef") {
    val result = Terser.minify("function f(longName){return longName}", mangleOnly)
    result.ast.body.head match {
      case defun: AstDefun =>
        // The mangler's side of the contract (passes today, per ISS-1033 control B):
        val sd = defun.variables.get("longName") match {
          case Some(d) => d.asInstanceOf[SymbolDef]
          case None    => fail("scope analysis must have registered longName in function f")
        }
        assert(sd.mangledName != null, "the mangler must have assigned a mangled name to longName")
        // The printer's side of the contract (the bug): the argname node must reach that def.
        val argSym = defun.argnames.head.asInstanceOf[AstSymbol]
        argSym.thedef match {
          case linked: SymbolDef =>
            assert(
              linked.mangledName != null,
              s"funarg node's thedef must carry the mangled name (def for longName has mangledName=${sd.mangledName})"
            )
          case other =>
            fail(s"funarg symbol node lacks SymbolDef linkage: thedef = $other (def carries mangledName=${sd.mangledName})")
        }
      case other => fail(s"Expected AstDefun, got: $other")
    }
  }

  // -- Control: mangle disabled must keep original names (must PASS today) --

  test("control ISS-1136 (d): mangle disabled keeps original names") {
    val out = Terser.minifyToString("function f(longName){return longName}", noOpt)
    assertEquals(out, "function f(longName){return longName}", "unmangled output must match terser 5.46.1")
  }
}
