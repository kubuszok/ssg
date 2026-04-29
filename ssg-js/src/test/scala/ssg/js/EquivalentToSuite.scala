/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from terser/test/mocha/equivalent-to.js
 * Original: 2 it() calls
 */
package ssg
package js

import ssg.js.ast.AstEquivalent
import ssg.js.parse.Parser

final class EquivalentToSuite extends munit.FunSuite {

  private def parse(code: String) = new Parser().parse(code)

  // 1. "(regression) two regexes should not be equivalent if their source or flags differ"
  test("two regexes should not be equivalent if source differs") {
    val ast  = parse("/^\\s*$/u")
    val ast2 = parse("/^\\s*\\*/u")
    assertEquals(AstEquivalent.equivalentTo(ast, ast2), false)
  }

  // 2. "nested calls should not be equivalent even if a tree walk reveals equivalent nodes"
  test("nested calls should not be equivalent") {
    val ast  = parse("hello(1, world(2), 3)")
    val ast2 = parse("hello(1, world(2, 3))")
    assertEquals(AstEquivalent.equivalentTo(ast, ast2), false)
  }
}
