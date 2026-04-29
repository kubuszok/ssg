/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from terser/test/mocha/with.js
 * Original: 2 it() calls
 */
package ssg
package js

import ssg.js.ast.*
import ssg.js.parse.{ JsParseError, Parser }
import ssg.js.scope.ScopeAnalysis

final class WithSuite extends munit.FunSuite {

  private def parse(code: String) = new Parser().parse(code)

  // 1. "Should throw syntaxError when using with statement in strict mode"
  test("should throw syntax error for with in strict mode") {
    val code = "\"use strict\";\nthrow NotEarlyError;\nwith ({}) { }"
    val ex = intercept[JsParseError] { parse(code) }
    assertEquals(ex.message, "Strict mode may not include a with statement")
  }

  // 2. "Should set uses_with for scopes involving With statements"
  test("should set usesWith for scopes involving with statements") {
    val ast = parse("with(e) {f(1, 2)}")
    ScopeAnalysis.figureOutScope(ast)
    assertEquals(ast.usesWith, true)
    // The `with` statement's expression (e) is a SymbolRef whose scope should have usesWith
    val withSt = ast.body(0).asInstanceOf[AstWith]
    val exprSym = withSt.expression.asInstanceOf[AstSymbolRef]
    assert(exprSym.scope != null)
    assertEquals(exprSym.scope.nn.usesWith, true)
    // The body's expression (f(1,2)) — f is a SymbolRef whose scope should have usesWith
    val bodyBlock = withSt.body.asInstanceOf[AstBlockStatement]
    val callStmt = bodyBlock.body(0).asInstanceOf[AstSimpleStatement]
    val call = callStmt.body.asInstanceOf[AstCall]
    val fSym = call.expression.asInstanceOf[AstSymbolRef]
    assert(fSym.scope != null)
    assertEquals(fSym.scope.nn.usesWith, true)
  }
}
