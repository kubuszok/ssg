/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1163 bounce 1: the first-in-statement walk over-matches
 * AST_New. In the SSG port `AstNew extends AstCall` (AstExpressions.scala:66),
 * so the `case call: AstCall` arms in FirstInStatement.scala (firstInStatement
 * walk :62 and leftIsObject :101) also match AST_New. Upstream's
 * lib/utils/first_in_statement.js guards these with `p.TYPE === "Call"`
 * (line 23) and `node.TYPE === "Call"` (line 43), which EXCLUDE AST_New
 * (TYPE "New"). The over-match was dormant until the AstSimpleStatement arm
 * (fix-sha 518f656f) made the statement-position walk live, so a pure
 * parse+print of `new function(){};` now emits `new(function(){});` — terser
 * emits `new function(){};`. The leftIsObject divergence (case f13) is
 * pre-existing but is mandated to be fixed in the same stroke.
 *
 * Oracle: the vendored original terser at original-src/terser, version
 * 5.46.1 (package.json:7), executed with node v24.12.0 on 2026-06-12:
 *
 *   cd original-src/terser && node --input-type=module -e "
 *   import { minify } from './main.js';
 *   const r = await minify(CODE, {compress:false, mangle:false});
 *   console.log(JSON.stringify(r.code));"
 *
 * Expected outputs recorded from that run (all {compress:false, mangle:false}):
 *   a) "new function(){};"   -> "new function(){};"
 *   b) "new class{};"        -> "new class{};"
 *   c) "() => new {}.foo;"   -> "()=>new{}.foo;"   (f13 leftIsObject shape:
 *        the arrow body is `new {}.foo`, an AST_New whose left spine is an
 *        object literal; because New is TYPE "New" not "Call", left_is_object
 *        returns false and terser does NOT wrap the implicit-return body)
 *   d) "new Foo();"          -> "new Foo;"          (control: not first-in-
 *        statement over-match; bare `new` with parenthesized args, stays green)
 *
 * Probe results (2026-06-12, port HEAD before the fix): (a), (b), (c) are RED —
 * the port emits "new(function(){});", "new(class{});", and "()=>(new{}.foo);"
 * respectively, because `case call: AstCall` swallows AST_New. (d) is GREEN —
 * the port already emits "new Foo;".
 */
package ssg
package js

final class IifeStatementParensNewIss1163Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  /** Equivalent of terser's `{compress: false, mangle: false}` — pure parse+print. */
  private val purePrint = MinifyOptions(compress = false, mangle = false)

  // -- Red: first-in-statement walk must not over-match AST_New --

  test("red ISS-1163 (a): statement-position `new function(){}` keeps no spurious parens") {
    val out = Terser.minifyToString("new function(){};", purePrint)
    assertEquals(out, "new function(){};", "pure print must match terser 5.46.1")
  }

  test("red ISS-1163 (b): statement-position `new class{}` keeps no spurious parens") {
    val out = Terser.minifyToString("new class{};", purePrint)
    assertEquals(out, "new class{};", "pure print must match terser 5.46.1")
  }

  test("red ISS-1163 (c): leftIsObject excludes AST_New — arrow returning `new {}.foo` is unwrapped") {
    val out = Terser.minifyToString("() => new {}.foo;", purePrint)
    assertEquals(out, "()=>new{}.foo;", "pure print must match terser 5.46.1")
  }

  // -- Control: must PASS today (probed green on 2026-06-12, see header) --

  test("control ISS-1163 (d): bare `new Foo()` prints `new Foo` without spurious walk parens") {
    val out = Terser.minifyToString("new Foo();", purePrint)
    assertEquals(out, "new Foo;", "pure print must match terser 5.46.1")
  }
}
