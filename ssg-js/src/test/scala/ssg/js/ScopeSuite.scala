/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package js

import ssg.js.ast.*
import ssg.js.parse.Parser
import ssg.js.output.{ OutputOptions, OutputStream }
import ssg.js.scope.{ Mangler, ManglerOptions, ScopeAnalysis, SymbolDef }

final class ScopeSuite extends munit.FunSuite {

  override val munitTimeout = scala.concurrent.duration.Duration(10, "s")

  private def parse(code: String): AstToplevel = new Parser().parse(code)

  test("SymbolDef creation") {
    val scope = new AstToplevel
    val sym   = new AstSymbolVar
    sym.name = "x"
    val sd = new SymbolDef(scope, sym)
    assertEquals(sd.name, "x")
  }

  test("scope: var x = 1") {
    val ast = parse("var x = 1;")
    ScopeAnalysis.figureOutScope(ast)
    assert(ast.variables.contains("x"), s"Expected 'x' in: ${ast.variables.keys}")
  }

  test("scope: let a = 1; const b = 2") {
    val ast = parse("let a = 1; const b = 2;")
    ScopeAnalysis.figureOutScope(ast)
  }

  test("scope: function foo(a)") {
    val ast = parse("function foo(a) { return a; }")
    ScopeAnalysis.figureOutScope(ast)
    assert(ast.variables.contains("foo"), s"Expected 'foo' in: ${ast.variables.keys}")
  }

  test("scope: reference linking") {
    val ast = parse("var x = 1; x + 2;")
    ScopeAnalysis.figureOutScope(ast)
    val xDef = ast.variables.get("x")
    assert(xDef.isDefined, "Expected SymbolDef for x")
    val sd = xDef.get.asInstanceOf[SymbolDef]
    assert(sd.references.nonEmpty, s"Expected refs to x, got ${sd.references.size}")
  }

  test("scope: if/else") {
    val ast = parse("var x = 1; if (x > 0) { x = 2; }")
    ScopeAnalysis.figureOutScope(ast)
    assert(ast.variables.contains("x"))
  }

  test("scope: for loop") {
    val ast = parse("for (var i = 0; i < 10; i++) { i; }")
    ScopeAnalysis.figureOutScope(ast)
  }

  private def generate(ast: AstNode): String = {
    val out = new OutputStream(OutputOptions())
    out.printNode(ast)
    out.get()
  }

  test("mangle: does not crash") {
    val ast = parse("function foo(longParam) { return longParam; }")
    ScopeAnalysis.figureOutScope(ast)
    Mangler.mangleNames(ast, ManglerOptions())
    val result = generate(ast)
    // Verify output is valid (mangling may or may not rename depending on config)
    assert(result.contains("function"), s"Expected function keyword, got: $result")
    assert(result.contains("return"), s"Expected return keyword, got: $result")
  }
}
