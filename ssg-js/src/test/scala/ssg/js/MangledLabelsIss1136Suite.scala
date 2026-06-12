/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1136 (bounce 1): label symbols are mangled by the mangler but
 * printed UNMANGLED, and label names are omitted from character-frequency counting —
 * so label-bearing programs diverge from terser on the label itself AND on every
 * other identifier choice.
 *
 * Two defects this suite pins:
 *   1. printSymbol (OutputStream.scala) — upstream output.js:2341-2343 prints
 *      `def.mangled_name || def.name` where `def = this.definition()` (= this.thedef).
 *      For AST_Label / AST_LabelRef the thedef IS the AstLabel itself
 *      (ScopeAnalysis: AstLabel.thedef = self; AstLabelRef.thedef = its AstLabel,
 *      mirroring scope.js:366,376), and that AstLabel carries `mangledName` assigned
 *      by mangleNames (Mangler.scala:385-393, mirroring scope.js:868-874). The port
 *      matched only `case sd: SymbolDef` and fell back to node.name, so labels printed
 *      unmangled.
 *   2. computeCharFrequency (Mangler.scala) — upstream scope.js:987-988 subtracts
 *      (`consider(name, -1)`) for ANY printed AST_Symbol with `!unmangleable(options)`.
 *      AST_Label.unmangleable is return_false (scope.js:768) and AST_LabelRef resolves
 *      to its label, so label/label-ref names participate in frequency. The port
 *      required a SymbolDef thedef, dropping labels, so identifier choices shifted.
 *
 * Oracle (C11): the vendored original terser at original-src/terser, version 5.46.1
 * (package.json:7), executed with node v24.12.0 on 2026-06-12:
 *
 *   cd original-src/terser && node --input-type=module -e "
 *   import { minify } from './main.js';
 *   const r = await minify(CODE, {mangle: true, compress: false});
 *   console.log(r.code);"
 *
 * Expected outputs recorded from that run:
 *   t8) function f(x){outer:for(var i=0;i<x;i++){for(var j=0;j<i;j++){if(j===2)continue outer}}return i}
 *        → function f(r){r:for(var f=0;f<r;f++){for(var n=0;n<f;n++){if(n===2)continue r}}return f}
 *   collide) function h(loop){loop:for(var k=0;k<loop;k++){if(k===1)break loop}return k}
 *        → function h(r){r:for(var f=0;f<r;f++){if(f===1)break r}return f}
 *   break) function s(arr){found:for(var i=0;i<arr.length;i++){if(arr[i])break found}return i}
 *        → function s(r){r:for(var n=0;n<r.length;n++){if(r[n])break r}return n}
 *   control) function h(loop){loop:for(var k=0;k<loop;k++){if(k===1)break loop}return k}  {mangle:false}
 *        → function h(loop){loop:for(var k=0;k<loop;k++){if(k===1)break loop}return k}   (unmangled)
 *
 * The `t8` case is the audit's T8 oracle. Note the label and the param can receive the
 * same mangled letter (`r` here) because labels occupy a separate namespace.
 *
 * Distinct from MangledNamesIss1136Suite (function params / var decls; no labels) and
 * CompressIssue1704Suite (catch-variable harness). Do not modify those suites.
 */
package ssg
package js

import ssg.js.scope.ManglerOptions

final class MangledLabelsIss1136Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(30, "s")

  /** Equivalent of terser's `{mangle: true, compress: false}`. */
  private val mangleOnly = MinifyOptions(compress = false, mangle = ManglerOptions())

  private val noOpt = MinifyOptions(compress = false, mangle = false)

  // -- Red: label declaration + label-ref (continue) must both be mangled, and the
  //    label name must participate in char frequency (so non-label names match too). --

  test("red ISS-1136 (t8): continue-label is mangled and identifier choices match terser") {
    val out = Terser.minifyToString(
      "function f(x){outer:for(var i=0;i<x;i++){for(var j=0;j<i;j++){if(j===2)continue outer}}return i}",
      mangleOnly
    )
    assertEquals(
      out,
      "function f(r){r:for(var f=0;f<r;f++){for(var n=0;n<f;n++){if(n===2)continue r}}return f}",
      "mangled output (label decl + continue label-ref) must match terser 5.46.1"
    )
  }

  test("red ISS-1136 (collide): label name colliding with var lexeme is mangled (break label-ref)") {
    val out = Terser.minifyToString(
      "function h(loop){loop:for(var k=0;k<loop;k++){if(k===1)break loop}return k}",
      mangleOnly
    )
    assertEquals(
      out,
      "function h(r){r:for(var f=0;f<r;f++){if(f===1)break r}return f}",
      "mangled output (label name shadowing a var lexeme + break label-ref) must match terser 5.46.1"
    )
  }

  test("red ISS-1136 (break): break-label is mangled and identifier choices match terser") {
    val out = Terser.minifyToString(
      "function s(arr){found:for(var i=0;i<arr.length;i++){if(arr[i])break found}return i}",
      mangleOnly
    )
    assertEquals(
      out,
      "function s(r){r:for(var n=0;n<r.length;n++){if(r[n])break r}return n}",
      "mangled output (label decl + break label-ref) must match terser 5.46.1"
    )
  }

  // -- Control: mangle disabled keeps the original label and identifier names. --

  test("control ISS-1136 (label, unmangled): mangle disabled keeps original names") {
    val out = Terser.minifyToString(
      "function h(loop){loop:for(var k=0;k<loop;k++){if(k===1)break loop}return k}",
      noOpt
    )
    assertEquals(
      out,
      "function h(loop){loop:for(var k=0;k<loop;k++){if(k===1)break loop}return k}",
      "unmangled output must match terser 5.46.1"
    )
  }
}
