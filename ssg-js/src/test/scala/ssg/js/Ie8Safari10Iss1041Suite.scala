/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Differential tests for ISS-1041: the `ie8` compress option and the `ie8`/`safari10`
 * output options must be honored exactly as upstream Terser does.
 *
 * Each assertion cites the upstream Terser oracle output, derived via:
 *   cd original-src/terser && node --input-type=module -e \
 *     "import {minify} from './main.js'; \
 *      console.log((await minify(SRC, {compress:..., mangle:false, format:...})).code)"
 *
 * Oracle reference sites:
 *   compress/index.js:2310-2311, :2323-2324 — typeofs vs AST_PropAccess under ie8
 *   compress/index.js:2942               — AST_SymbolRef undefined/NaN/Infinity collapse under !ie8
 *   output.js:379                        — "\x0B" escape: ie8 ? "\\x0B" : "\\v"
 *   output.js:341                        — ascii_only surrogate escape: ecma>=2015 && !safari10
 *   output.js:1041                       — AST_Await PARENS: safari10 && p instanceof AST_UnaryPrefix
 *   output.js:1623                       — make_then: ie8 && b instanceof AST_Do
 *   output.js:1994-1999                  — AST_Dot computed: reserved ? ie8 : !is_identifier_string(...)
 *   output.js:2173-2179                  — print_property_name: reserved ? ie8 : (ecma<2015||safari10 ? ...)
 */
package ssg
package js

import ssg.js.compress.CompressorOptions
import ssg.js.output.OutputOptions

final class Ie8Safari10Iss1041Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  private def out(src: String, opts: OutputOptions): String =
    Terser.minifyToString(src, MinifyOptions(compress = false, mangle = false, output = opts))

  // ======================================================================
  // Part B — output: ie8
  // ======================================================================

  // U+000B VERTICAL TAB embedded in a string literal.
  private val vtSource = "var s = \"ab\";"

  test("output ie8 (output.js:379): vertical tab escapes as \\x0B under ie8") {
    // Oracle format:{ie8:true}  => "var s=\"a\\x0Bb\";"
    val res = out(vtSource, OutputOptions(ie8 = true))
    assertEquals(res, "var s=\"a\\x0Bb\";", s"ie8=true must emit \\x0B for U+000B, got: $res")
  }

  test("output ie8 (output.js:379): vertical tab escapes as \\v without ie8") {
    // Oracle format:{ie8:false} => "var s=\"a\\vb\";"
    val res = out(vtSource, OutputOptions(ie8 = false))
    assertEquals(res, "var s=\"a\\vb\";", s"ie8=false must emit \\v for U+000B, got: $res")
    assertNotEquals(res, out(vtSource, OutputOptions(ie8 = true)), "ie8 must change \\x0B vs \\v")
  }

  test("output ie8 (output.js:1623): do-while inside an if-then gets braces under ie8") {
    // Oracle format:{ie8:true}  => "if(x){do{y()}while(z)}else w();"
    // Oracle format:{ie8:false} => "if(x)do{y()}while(z);else w();"
    val src   = "if (x) do { y() } while (z); else w();"
    val ie8On = out(src, OutputOptions(ie8 = true))
    val ie8Off = out(src, OutputOptions(ie8 = false))
    assertEquals(ie8On, "if(x){do{y()}while(z)}else w();", s"ie8=true must brace the do-while then-body, got: $ie8On")
    assertEquals(ie8Off, "if(x)do{y()}while(z);else w();", s"ie8=false must not add braces, got: $ie8Off")
    assertNotEquals(ie8On, ie8Off, "ie8 must change do-while then-body bracing")
  }

  test("output ie8 (output.js:1994-1999): reserved-word property access is computed under ie8") {
    // Oracle format:{ie8:true}  => "a[\"default\"];"
    // Oracle format:{ie8:false} => "a.default;"
    val src    = "a.default;"
    val ie8On  = out(src, OutputOptions(ie8 = true))
    val ie8Off = out(src, OutputOptions(ie8 = false))
    assertEquals(ie8On, "a[\"default\"];", s"ie8=true must bracket reserved-word property, got: $ie8On")
    assertEquals(ie8Off, "a.default;", s"ie8=false must keep dot access, got: $ie8Off")
    assertNotEquals(ie8On, ie8Off, "ie8 must change reserved-word property access")
  }

  // ======================================================================
  // Part B — output: safari10
  // ======================================================================

  test("output safari10 (output.js:1041): await inside void gets parens under safari10") {
    // Oracle format:{safari10:true, ecma:2017}  => "async function f(){return void(await g())}"
    // Oracle format:{safari10:false, ecma:2017} => "async function f(){return void await g()}"
    val src   = "async function f(){ return void await g(); }"
    val s10On = out(src, OutputOptions(safari10 = true, ecma = 2017))
    val s10Off = out(src, OutputOptions(safari10 = false, ecma = 2017))
    assertEquals(s10On, "async function f(){return void(await g())}", s"safari10=true must parenthesize await under unary, got: $s10On")
    assertEquals(s10Off, "async function f(){return void await g()}", s"safari10=false must not add parens, got: $s10Off")
    assertNotEquals(s10On, s10Off, "safari10 must change await-under-unary parens")
  }

  test("output safari10 (output.js:341): ascii-only surrogate uses \\u pairs under safari10") {
    // Oracle format:{safari10:true,  ecma:2015, ascii_only:true} => "var s=\"\\ud83d\\ude00\";"
    // Oracle format:{safari10:false, ecma:2015, ascii_only:true} => "var s=\"\\u{1f600}\";"
    val src    = "var s = \"😀\";" // U+1F600 GRINNING FACE
    val s10On  = out(src, OutputOptions(safari10 = true, ecma = 2015, asciiOnly = true))
    val s10Off = out(src, OutputOptions(safari10 = false, ecma = 2015, asciiOnly = true))
    assertEquals(s10On, "var s=\"\\ud83d\\ude00\";", s"safari10=true must emit surrogate \\u pairs, got: $s10On")
    assertEquals(s10Off, "var s=\"\\u{1f600}\";", s"safari10=false (ecma2015) must emit code-point escape, got: $s10Off")
    assertNotEquals(s10On, s10Off, "safari10 must change surrogate escaping under ecma2015 ascii_only")
  }

  test("output safari10 (output.js:2173-2179): unicode property key is quoted under safari10") {
    // Oracle format:{safari10:true,  ecma:2015} => "var o={\"é\":1};"
    // Oracle format:{safari10:false, ecma:2015} => "var o={é:1};"
    val src    = "var o = {é: 1};" // key é (U+00E9)
    val s10On  = out(src, OutputOptions(safari10 = true, ecma = 2015))
    val s10Off = out(src, OutputOptions(safari10 = false, ecma = 2015))
    assertEquals(s10On, "var o={\"é\":1};", s"safari10=true must quote non-basic-ident key, got: $s10On")
    assertEquals(s10Off, "var o={é:1};", s"safari10=false (ecma2015) must keep bare unicode key, got: $s10Off")
    assertNotEquals(s10On, s10Off, "safari10 must change unicode property key quoting under ecma2015")
  }

  // ======================================================================
  // Part A — compress: ie8
  // ======================================================================

  test("compress ie8 (index.js:2310-2311,2323-2324): typeof of a property access is not collapsed under ie8") {
    // Oracle compress:{ie8:true}  => "\"undefined\"==typeof a.b&&foo();"
    // Oracle compress:{ie8:false} => "void 0===a.b&&foo();"
    val src    = "if (typeof a.b === \"undefined\") foo();"
    val ie8On  = Terser.minifyToString(src, MinifyOptions(compress = CompressorOptions(ie8 = true), mangle = false))
    val ie8Off = Terser.minifyToString(src, MinifyOptions(compress = CompressorOptions(ie8 = false), mangle = false))
    assertEquals(ie8On, "\"undefined\"==typeof a.b&&foo();", s"ie8=true must keep the typeof guard, got: $ie8On")
    assertEquals(ie8Off, "void 0===a.b&&foo();", s"ie8=false must collapse typeof to void 0, got: $ie8Off")
    assertNotEquals(ie8On, ie8Off, "compress ie8 must change typeof-of-property-access handling")
  }
}
