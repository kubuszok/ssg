/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Red tests for ISS-1141 bounce 1: the identity-inline `this`-preservation
 * guard in ssg/js/compress/Inline.scala is dead code.
 *
 * Upstream terser inline.js:378-394: when the identity-inlined replacement is
 * an AST_PropAccess and the parent (via compressor.parent()) is an AST_Call
 * whose expression is the identity call itself, terser wraps the replacement
 * in `(0, replacement)` so that calling it does NOT rebind `this`:
 *
 *     id(bag.no_this)(...) -> (0, bag.no_this)(...)
 *
 * The port's guard reads the Compressor's OWN TreeWalker stack — always empty
 * during a transform pass (the live ancestry lives on the active
 * TreeTransformer) — so the guard never fires and the wrap is dropped,
 * rebinding `this` (a miscompilation).
 *
 * Executed oracle (terser 5.46.1 per original-src/terser/package.json:7,
 * node v24.12.0, run on 2026-06-12):
 *
 *   cd original-src/terser && node --input-type=module -e "
 *     import { minify } from './main.js';
 *     const r = await minify(CODE,
 *       { compress: { inline: true, reduce_vars: true, unused: true,
 *         toplevel: true }, mangle: false });
 *     console.log(JSON.stringify(r.code));"
 *
 * `run` is referenced by two source-level call expressions (a comma sequence)
 * so it is NOT itself inlinable; this keeps the identity-inline transformation
 * visible in `run`'s body in BOTH terser (passes: 1) and the port (single
 * pass), so the outputs align on the `return (0, bag.f)()` shape rather than
 * on a fully-reduced program. The two calls are written as a source-level
 * comma sequence so neither the port nor terser has to merge statements (the
 * `;` vs `,` join is orthogonal to this fix).
 *
 *   CASE 1 (function-form id, object-literal bag PropAccess):
 *     CODE = "function id(x){return x} function run(bag){return id(bag.f)()} \
 *             run({f:function(){return this}}), run({f:function(){return this}});"
 *     -> "function run(bag){return(0,bag.f)()}\
 *         run({f:function(){return this}}),run({f:function(){return this}});"
 *
 *   CASE 2 (arrow id, two-arg PropAccess call):
 *     CODE = "const id=x=>x; function run(bag){return id(bag.no_this)(1,2)} \
 *             run({no_this:function(a,b){return a+b}}), run({no_this:function(a,b){return a+b}});"
 *     -> "function run(bag){return(0,bag.no_this)(1,2)}\
 *         run({no_this:function(a,b){return a+b}}),run({no_this:function(a,b){return a+b}});"
 *
 *   CONTROL (non-PropAccess replacement — plain function, no wrap, direct inline):
 *     CODE = "const id=x=>x; function run(fn){return id(fn)()} \
 *             run(function(){return this.x}), run(function(){return this.x});"
 *     -> "function run(fn){return fn()}\
 *         run(function(){return this.x}),run(function(){return this.x});"
 *
 * Before the fix the guard is dead, so the PropAccess cases inline directly to
 * `return bag.f()` / `return bag.no_this(1,2)` (THIS REBOUND).
 */
package ssg
package js
package compress

import CompressTestHelper.compressAndNormalize
import CompressTestHelper.AllOff

final class IifeInlineThisGuardIss1141Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  // inline + reduce_vars + unused + toplevel (the auditor's option set)
  private val Opts: CompressorOptions = AllOff.copy(
    inline = InlineLevel.InlineFull,
    reduceVars = true,
    unused = true,
    toplevel = ToplevelConfig(funcs = true, vars = true)
  )

  private def assertCompressesTo(
    input:    String,
    expected: String,
    message:  String
  )(using loc: munit.Location): Unit =
    compressAndNormalize(input, expected, Opts) match {
      case Some((actual, exp)) => assertEquals(actual, exp, message)
      case None                => fail(s"compression timed out — $message")
    }

  // =========================================================================
  // RED 1: function-form identity, object-literal PropAccess replacement.
  // terser wraps in (0, …) to preserve `this`; the dead guard drops the wrap.
  // =========================================================================
  test("identity inline of bag.f() preserves this via (0,…) wrap (inline.js:390)") {
    assertCompressesTo(
      input = "function id(x){return x} function run(bag){return id(bag.f)()} " +
        "run({f:function(){return this}}), run({f:function(){return this}});",
      expected = "function run(bag){return(0,bag.f)()}" +
        "run({f:function(){return this}}),run({f:function(){return this}});",
      message = "ISS-1141 bounce 1: terser wraps the identity-inlined PropAccess in " +
        "(0,…) to avoid rebinding `this` (executed oracle 2026-06-12: " +
        "\"function run(bag){return(0,bag.f)()}run(...),run(...);\"); " +
        "the dead guard inlines to return bag.f() (this rebound)"
    )
  }

  // =========================================================================
  // RED 2: arrow identity, two-arg PropAccess call (upstream's own doc case).
  // =========================================================================
  test("identity inline of bag.no_this(1,2) preserves this via (0,…) wrap (inline.js:390)") {
    assertCompressesTo(
      input = "const id=x=>x; function run(bag){return id(bag.no_this)(1,2)} " +
        "run({no_this:function(a,b){return a+b}}), run({no_this:function(a,b){return a+b}});",
      expected = "function run(bag){return(0,bag.no_this)(1,2)}" +
        "run({no_this:function(a,b){return a+b}}),run({no_this:function(a,b){return a+b}});",
      message = "ISS-1141 bounce 1: terser wraps the identity-inlined PropAccess in " +
        "(0,…) (executed oracle 2026-06-12: " +
        "\"function run(bag){return(0,bag.no_this)(1,2)}run(...),run(...);\"); " +
        "the dead guard inlines to return bag.no_this(1,2) (this rebound)"
    )
  }

  // =========================================================================
  // CONTROL: replacement is a plain function (NOT a PropAccess) — no wrap,
  // direct inline per the oracle. Pins that the guard fires ONLY for PropAccess.
  // =========================================================================
  test("control: identity inline of a non-PropAccess function does not wrap (inline.js:395)") {
    assertCompressesTo(
      input = "const id=x=>x; function run(fn){return id(fn)()} " +
        "run(function(){return this.x}), run(function(){return this.x});",
      expected = "function run(fn){return fn()}" +
        "run(function(){return this.x}),run(function(){return this.x});",
      message = "control: a non-PropAccess replacement inlines directly with no (0,…) wrap " +
        "(executed oracle 2026-06-12: \"function run(fn){return fn()}run(...),run(...);\")"
    )
  }
}
