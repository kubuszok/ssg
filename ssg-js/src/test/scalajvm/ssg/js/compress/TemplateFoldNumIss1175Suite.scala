/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Red test for ISS-1175 (R0610-P2): when a template interpolation `${expr}`
 * folds to a numeric (Double) constant, ssg/js/compress stringifies it via
 * `case d: Double => d.toString` (compress/Compressor.scala:4980 in
 * optimizeTemplateString). On JVM/Native `(1.0).toString == "1.0"`, so
 * `var u=`a${1}b`` compresses to "a1.0b" — but JS/terser format a Number 1
 * as "1", yielding "a1b". The fix routes through the JS-number formatting
 * `OutputStream.numToString` (output/OutputStream.scala:2134), sibling of
 * ISS-1140.
 *
 * terser oracle (terser 5.x per original-src/terser/package.json), folding
 * driven by `evaluate` to match AllOff.copy(evaluate = true):
 *
 *   cd original-src/terser && node --input-type=module -e "
 *     import('./main.js').then(async t=>{
 *       const r=await t.minify('var u=`a${1}b`;console.log(u)',
 *         {compress:{},mangle:false});
 *       console.log(JSON.stringify(r.code))})"
 *
 *   integer 1:   "var u=\"a1b\";console.log(u);"
 *   decimal 2.5: "var u=\"a2.5b\";console.log(u);"
 *   `x${10}y${2.5}z`:  "var u=\"x10y2.5z\";console.log(u);"
 *
 * The integer case is RED today: SSG folds it but emits "a1.0b" (the Double
 * `.toString`). The decimal case is a control — 2.5 formats identically on
 * the JVM and in JS, so it should already pass, proving the fold itself fires. */
package ssg
package js
package compress

import CompressTestHelper.{ AllOff, assertCompresses }

final class TemplateFoldNumIss1175Suite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(120, "s")

  // =========================================================================
  // ISS-1175: integer template-fold must emit "a1b", not "a1.0b"
  //
  // terser oracle: var u=`a${1}b` -> var u="a1b" (Number 1 -> "1")
  // RED on JVM today: optimizeTemplateString uses (1.0).toString == "1.0".
  // =========================================================================
  test("ISS-1175 template fold of integer 1 stringifies as 1 not 1.0") {
    assertCompresses(
      input = "var u=`a${1}b`;console.log(u)",
      expected = "var u=\"a1b\";console.log(u)",
      options = AllOff.copy(
        evaluate = true
      )
    )
  }

  // =========================================================================
  // ISS-1175 control: non-integer 2.5 already formats as "2.5" on the JVM,
  // so this passes today and confirms the fold actually triggers.
  //
  // terser oracle: var u=`a${2.5}b` -> var u="a2.5b"
  // =========================================================================
  test("ISS-1175 template fold of decimal 2.5 stringifies as 2.5") {
    assertCompresses(
      input = "var u=`a${2.5}b`;console.log(u)",
      expected = "var u=\"a2.5b\";console.log(u)",
      options = AllOff.copy(
        evaluate = true
      )
    )
  }
}
