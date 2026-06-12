/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1163: statement-position IIFEs are printed without the
 * first-in-statement parentheses, producing INVALID JavaScript. A pure
 * parse+print round-trip of `(function(){x()})();` emits
 * `function(){x()}();` — a statement cannot begin with the `function`
 * keyword as an expression (likewise `(`-less arrow/object starts).
 *
 * Upstream truth: original-src/terser/lib/output.js PARENS rules — an
 * AST_Function/AST_Arrow (and AST_Object, AST_ClassExpression, ...) needs
 * parens when it is the first token of an expression statement; the check
 * walks the output stack via lib/utils/first_in_statement.js
 * (`first_in_statement(output)`). The SSG port's output package has no
 * firstInStatement logic at all (`git grep firstInStatement ssg-js/src/main`
 * only hits the compress package), so the parens are never emitted.
 *
 * Oracle (C11): the vendored original terser at original-src/terser,
 * version 5.46.1 (package.json:7), executed with node v24.12.0 on
 * 2026-06-12:
 *
 *   cd original-src/terser && node --input-type=module -e "
 *   import { minify } from './main.js';
 *   const r = await minify(CODE, OPTS);
 *   console.log(JSON.stringify(r.code));"
 *
 * Expected outputs recorded from that run:
 *   a) "(function(){x()})();"                  {compress:false, mangle:false}
 *        -> "(function(){x()})();"
 *   b) "(function(o){console.log(o)})(42);"    {compress:false, mangle:false}
 *        -> "(function(o){console.log(o)})(42);"
 *   c) "(()=>{x()})();"                        {compress:false, mangle:false}
 *        -> "(()=>{x()})();"                       (control, see below)
 *   d) "(function(){var a=foo();bar(a)})();"   {compress:{}, mangle:false}
 *        -> "!function(){var a=foo();bar(a)}();"   (negate_iife, F1/F10/F11 shape)
 *   e) "var f=function(){};"                   {compress:false, mangle:false}
 *        -> "var f=function(){};"                  (control: not first-in-statement)
 *   f) "var r=(function(){return 1})();"       {compress:false, mangle:false}
 *        -> "var r=function(){return 1}();"        (control: parens dropped when
 *                                                    NOT first-in-statement)
 *
 * Probe results (2026-06-12, this suite's first red run): (a), (b), (d) are RED
 * — the port emits "function(){x()}();", "function(o){console.log(o)}(42);",
 * and "function(){var a=foo();bar(a)}();" respectively. (c) PASSES today: the
 * port's arrow print path already emits the statement-position parens, so the
 * gap is specific to function expressions; (c) is kept as a control pinning the
 * working arrow half of the PARENS contract. (e) and (f) pass as expected.
 *
 * Invalid-JS proof for the red cases (2026-06-12): feeding the port's obtained
 * outputs back to node v24.12.0 and to terser 5.46.1's own parser:
 *
 *   node -e 'new Function("function(){x()}();")'
 *     -> SyntaxError: Function statements require a function name
 *        (identically for the (b) and (d) obtained outputs)
 *   cd original-src/terser && node --input-type=module -e "
 *     import { minify } from './main.js';
 *     await minify('function(){x()}();', {compress:false,mangle:false});"
 *     -> JS_Parse_Error [SyntaxError]: Unexpected token: punc «(»
 *        (parse.js:1663 function_ requires a name in statement position)
 *
 * so the port's output is genuinely unparsable, not merely styled differently.
 */
package ssg
package js

import ssg.js.compress.CompressorOptions

final class IifeStatementParensIss1163Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  /** Equivalent of terser's `{compress: false, mangle: false}` — pure parse+print. */
  private val purePrint = MinifyOptions(compress = false, mangle = false)

  /** Explicit CompressorOptions() == terser `{compress: {}}` defaults (per ISS-1033). */
  private val compressDefaults = MinifyOptions(compress = CompressorOptions(), mangle = false)

  // -- Red: statement-position IIFEs, expected output verbatim from terser 5.46.1 --

  test("red ISS-1163 (a): statement-position function IIFE keeps first-in-statement parens") {
    val out = Terser.minifyToString("(function(){x()})();", purePrint)
    assertEquals(out, "(function(){x()})();", "pure print must match terser 5.46.1")
  }

  test("red ISS-1163 (b): statement-position function IIFE with argument keeps parens") {
    val out = Terser.minifyToString("(function(o){console.log(o)})(42);", purePrint)
    assertEquals(out, "(function(o){console.log(o)})(42);", "pure print must match terser 5.46.1")
  }

  test("red ISS-1163 (d): compress-retained IIFE gets negate-iife `!` (or parens) treatment") {
    val out = Terser.minifyToString("(function(){var a=foo();bar(a)})();", compressDefaults)
    assertEquals(out, "!function(){var a=foo();bar(a)}();", "compressed output must match terser 5.46.1")
  }

  // -- Controls: must PASS today (probed green on 2026-06-12, see header) --

  test("control ISS-1163 (c): statement-position arrow IIFE keeps first-in-statement parens") {
    val out = Terser.minifyToString("(()=>{x()})();", purePrint)
    assertEquals(out, "(()=>{x()})();", "pure print must match terser 5.46.1")
  }

  test("control ISS-1163 (e): function expression in declaration init prints without parens") {
    val out = Terser.minifyToString("var f=function(){};", purePrint)
    assertEquals(out, "var f=function(){};", "non-statement-position output must match terser 5.46.1")
  }

  test("control ISS-1163 (f): parenthesized IIFE callee in expression position drops the parens") {
    val out = Terser.minifyToString("var r=(function(){return 1})();", purePrint)
    assertEquals(out, "var r=function(){return 1}();", "non-statement-position output must match terser 5.46.1")
  }
}
