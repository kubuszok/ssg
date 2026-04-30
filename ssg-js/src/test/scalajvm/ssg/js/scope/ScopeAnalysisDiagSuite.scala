package ssg
package js
package scope

import ssg.js.ast.*
import ssg.js.parse.{ Parser, ParserOptions }

final class ScopeAnalysisDiagSuite extends munit.FunSuite {

  private def parse(code: String): AstToplevel =
    new Parser(ParserOptions()).parse(code)

  test("declared ref — should complete") {
    val ast = parse("var x = 1; x;")
    ScopeAnalysis.figureOutScope(ast)
    assert(ast.globals.isEmpty)
  }

  test("undeclared ref — scope analysis completes") {
    val ast = parse("foo();")
    ScopeAnalysis.figureOutScope(ast)
    assert(ast.globals.contains("foo"))
  }

  test("undeclared ref in function — scope analysis completes") {
    val ast = parse("function f() { return g(); }")
    ScopeAnalysis.figureOutScope(ast)
    assert(ast.globals.contains("g"))
  }

  test("toplevel parentScope is null after figureOutScope") {
    val ast = parse("var x = 1;")
    ScopeAnalysis.figureOutScope(ast)
    assert(ast.parentScope == null, "toplevel parentScope must be null, not self-referencing")
  }

  test("mixed declared and undeclared refs") {
    val ast = parse("var x = 1; console.log(x + y);")
    ScopeAnalysis.figureOutScope(ast)
    assert(!ast.globals.contains("x"))
    assert(ast.globals.contains("console"))
    assert(ast.globals.contains("y"))
  }
}
