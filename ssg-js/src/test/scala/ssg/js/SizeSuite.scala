/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from terser/test/mocha/size.js
 * Original: 2 it() calls
 */
package ssg
package js

import ssg.js.ast.AstSize
import ssg.js.parse.Parser

final class SizeSuite extends munit.FunSuite {

  private def parse(code: String) = new Parser().parse(code)

  // 1. "approximates the size of expression"
  test("approximates the size of expression") {
    assertEquals(AstSize.size(parse("()=>{return}")), 12)
    assertEquals(AstSize.size(parse("()=>{return 2}")), 5)
    assertEquals(AstSize.size(parse("()=>2")), 5)
  }

  // 2. "approximates the size of variable declaration"
  test("approximates the size of variable declaration") {
    assertEquals(AstSize.size(parse("using x=null")), 12)
    assertEquals(AstSize.size(parse("async()=>{await using x=null}")), 30)
  }
}
