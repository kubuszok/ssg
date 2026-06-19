/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Differential tests for ISS-1192 — the three Inline ancestry guards that were
 * migrated from the (always-null mid-pass) `compressor.parent(...)` walk to the
 * live-ancestry `liveParent`/`activeWalker` pattern (continuation of ISS-1166).
 *
 * Each test exercises one guard so that the previously-DEAD (always-false) guard
 * now correctly affects the compression decision. The expected outputs are cited
 * byte-for-byte from terser 5.46.1 (`original-src/terser`, package.json version
 * 5.46.1) via `minify(...)` with the matching compress options. With the dead
 * guard each of these tests produces DIFFERENT output (proof-of-red); with the
 * live guard the output matches terser.
 *
 * Guards covered:
 *   - isRecursiveRef            (common.js:357 is_recursive_ref via tw.parent(i))
 *   - withinArrayOrObjectLiteral (inline.js:123 within_array_or_object_literal)
 *   - inlineIntoSymbolRef parent (inline.js:171/173 compressor.parent()/find_scope())
 */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }
import ssg.js.compress.InlineLevel

final class InlineAncestryIss1192Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  // Shared option profile: matches the terser oracle runs below
  //   compress: { defaults:false, inline:3, reduce_vars:true, reduce_funcs:true,
  //               unused:true, side_effects:true, toplevel:false, passes:1 }
  private val baseOpts = AllOff.copy(
    inline = InlineLevel.InlineFull, // inline: 3
    reduceVars = true,
    reduceFuncs = true,
    unused = true,
    sideEffects = true,
    passes = 1
  )

  // =========================================================================
  // isRecursiveRef — the correctness bug.
  //
  // `f` is a single-use reference to a NAMED recursive function expression `g`.
  // `inlineIntoSymbolRef` reaches the single-use Lambda branch; the live
  // `isRecursiveRef` (inline.js:184 -> common.js:357) sees the enclosing
  // `AST_Lambda g` whose name's definition is `g`, so the self-reference `g(n-1)`
  // is preserved (single_use stays, the named function is kept). With the DEAD
  // guard `isRecursiveRef` was always false, which would mishandle the recursion.
  //
  // terser 5.46.1:
  //   minify("(function(){var f=function g(n){return n<=1?1:n*g(n-1)};console.log(f(5));})();",
  //          {compress:{defaults:false,evaluate:true,inline:3,reduce_vars:true,
  //                     reduce_funcs:true,unused:true,side_effects:true,
  //                     toplevel:false,passes:1},mangle:false})
  //   => "console.log(function g(n){return n<=1?1:n*g(n-1)}(5));"
  // =========================================================================
  test("iss1192_is_recursive_ref_preserves_self_reference") {
    assertCompresses(
      input = """(function(){
        var f = function g(n) {
          return n <= 1 ? 1 : n * g(n - 1);
        };
        console.log(f(5));
      })();""".stripMargin.trim,
      expected = """console.log(function g(n) {
        return n <= 1 ? 1 : n * g(n - 1);
      }(5));""".stripMargin.trim,
      options = baseOpts.copy(evaluate = true)
    )
  }

  // =========================================================================
  // withinArrayOrObjectLiteral.
  //
  // `f` is declared in the outer scope and has a single, cross-scope reference
  // inside an ARRAY LITERAL in a nested function. inline.js:213-217 only inlines
  // a cross-scope single-use function when it is NOT within an array/object
  // literal; the live `within_array_or_object_literal` walk (inline.js:123) finds
  // the enclosing `AST_Array` and sets single_use = false, so `f` is preserved.
  // With the DEAD guard the walk was always false, wrongly allowing the inline.
  //
  // terser 5.46.1:
  //   minify("(function(){var f=function(){return 42};function inner(){return [f];}console.log(inner()[0]());})();",
  //          {compress:{defaults:false,inline:3,reduce_vars:true,reduce_funcs:true,
  //                     unused:true,side_effects:true,toplevel:false,passes:1},mangle:false})
  //   => "(function(){var f=function(){return 42};console.log([f][0]())})();"
  // =========================================================================
  test("iss1192_within_array_literal_blocks_cross_scope_inline") {
    assertCompresses(
      input = """(function(){
        var f = function() { return 42; };
        function inner() {
          return [f];
        }
        console.log(inner()[0]());
      })();""".stripMargin.trim,
      expected = """(function() {
        var f = function() { return 42; };
        console.log([ f ][0]());
      })();""".stripMargin.trim,
      options = baseOpts
    )
  }

  // =========================================================================
  // inlineIntoSymbolRef live parent / find_scope.
  //
  // `f` is a single-use, SAME-SCOPE function expression passed as a call ARGUMENT
  // (`console.log(f)`), so the live `parent` is an `AST_Call` whose expression is
  // NOT `self` — the direct-call branch does not apply, and inlining hinges on the
  // `in_same_scope` branch (inline.js:234-237): `def.scope === self.scope &&
  // !scope_encloses_variables_in_this_scope(nearest_scope, fixed)`, which requires
  // the live `nearest_scope` (compressor.find_scope, inline.js:173) to be non-null.
  // With the DEAD `find_scope` walk `nearest_scope` was always null, so
  // `in_same_scope` was false and the single-use function was NOT inlined. Live,
  // `find_scope` resolves the enclosing IIFE scope and `f` is inlined.
  //
  // terser 5.46.1:
  //   minify("(function(){var f=function(){return 42};console.log(f);})();",
  //          {compress:{defaults:false,inline:3,reduce_vars:true,reduce_funcs:true,
  //                     unused:true,side_effects:true,toplevel:false,passes:1},mangle:false})
  //   => "console.log(function(){return 42});"
  // =========================================================================
  test("iss1192_inline_into_symbolref_live_find_scope_same_scope") {
    assertCompresses(
      input = """(function(){
        var f = function() { return 42; };
        console.log(f);
      })();""".stripMargin.trim,
      expected = """console.log(function() {
        return 42;
      });""".stripMargin.trim,
      options = baseOpts
    )
  }
}
