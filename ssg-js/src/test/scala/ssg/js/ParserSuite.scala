/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package js

import ssg.js.ast.*
import ssg.js.parse.Parser

final class ParserSuite extends munit.FunSuite {

  private def parse(code: String): AstToplevel = {
    val parser = new Parser()
    parser.parse(code)
  }

  // -- Basic statements --

  test("parse empty program") {
    val ast = parse("")
    assertEquals(ast.nodeType, "Toplevel")
    assertEquals(ast.body.size, 0)
  }

  test("parse var declaration") {
    val ast = parse("var x = 1;")
    assertEquals(ast.body.size, 1)
    assert(ast.body(0).isInstanceOf[AstVar], s"Expected AstVar, got ${ast.body(0).nodeType}")
    val varStmt = ast.body(0).asInstanceOf[AstVar]
    assertEquals(varStmt.definitions.size, 1)
    val defn = varStmt.definitions(0).asInstanceOf[AstVarDef]
    assertEquals(defn.name.asInstanceOf[AstSymbolVar].name, "x")
    assert(defn.value.isInstanceOf[AstNumber])
  }

  test("parse let and const") {
    val ast = parse("let a = 1; const b = 2;")
    assertEquals(ast.body.size, 2)
    assert(ast.body(0).isInstanceOf[AstLet])
    assert(ast.body(1).isInstanceOf[AstConst])
  }

  test("parse expression statement") {
    val ast = parse("x + y;")
    assertEquals(ast.body.size, 1)
    assert(ast.body(0).isInstanceOf[AstSimpleStatement])
    val expr = ast.body(0).asInstanceOf[AstSimpleStatement].body
    assert(expr.isInstanceOf[AstBinary], s"Expected AstBinary, got ${expr.nodeType}")
    assertEquals(expr.asInstanceOf[AstBinary].operator, "+")
  }

  // -- Functions --

  test("parse function declaration") {
    val ast = parse("function foo(a, b) { return a + b; }")
    assertEquals(ast.body.size, 1)
    assert(ast.body(0).isInstanceOf[AstDefun])
    val fn = ast.body(0).asInstanceOf[AstDefun]
    assertEquals(fn.name.nn.asInstanceOf[AstSymbolDefun].name, "foo")
    assertEquals(fn.argnames.size, 2)
    assertEquals(fn.body.size, 1)
    assert(fn.body(0).isInstanceOf[AstReturn])
  }

  test("parse arrow function") {
    val ast     = parse("var f = (x) => x * 2;")
    val varStmt = ast.body(0).asInstanceOf[AstVar]
    val defn    = varStmt.definitions(0).asInstanceOf[AstVarDef]
    assert(defn.value.isInstanceOf[AstArrow], s"Expected AstArrow, got ${defn.value.nn.nodeType}")
  }

  test("parse arrow function no parens") {
    val ast     = parse("var f = x => x + 1;")
    val varStmt = ast.body(0).asInstanceOf[AstVar]
    val defn    = varStmt.definitions(0).asInstanceOf[AstVarDef]
    assert(defn.value.isInstanceOf[AstArrow], s"Expected AstArrow, got ${defn.value.nn.nodeType}")
  }

  test("parse arrow function multi-param") {
    val ast  = parse("const add = (a, b) => a + b;")
    val cst  = ast.body(0).asInstanceOf[AstConst]
    val defn = cst.definitions(0).asInstanceOf[AstVarDef]
    assert(defn.value.isInstanceOf[AstArrow], s"Expected AstArrow, got ${defn.value.nn.nodeType}")
    val arrow = defn.value.asInstanceOf[AstArrow]
    assertEquals(arrow.argnames.size, 2)
    assertEquals(arrow.argnames(0).asInstanceOf[AstSymbolFunarg].name, "a")
    assertEquals(arrow.argnames(1).asInstanceOf[AstSymbolFunarg].name, "b")
  }

  test("parse arrow function three params") {
    val ast  = parse("const f = (a, b, c) => a + b + c;")
    val cst  = ast.body(0).asInstanceOf[AstConst]
    val defn = cst.definitions(0).asInstanceOf[AstVarDef]
    assert(defn.value.isInstanceOf[AstArrow], s"Expected AstArrow, got ${defn.value.nn.nodeType}")
    val arrow = defn.value.asInstanceOf[AstArrow]
    assertEquals(arrow.argnames.size, 3)
  }

  test("parse arrow function destructuring param") {
    val ast  = parse("const f = ({a, b}) => a + b;")
    val cst  = ast.body(0).asInstanceOf[AstConst]
    val defn = cst.definitions(0).asInstanceOf[AstVarDef]
    assert(defn.value.isInstanceOf[AstArrow], s"Expected AstArrow, got ${defn.value.nn.nodeType}")
    val arrow = defn.value.asInstanceOf[AstArrow]
    assertEquals(arrow.argnames.size, 1)
    assert(arrow.argnames(0).isInstanceOf[AstDestructuring], s"Expected AstDestructuring, got ${arrow.argnames(0).nodeType}")
  }

  test("parse arrow function default param") {
    val ast  = parse("const f = (a = 1) => a;")
    val cst  = ast.body(0).asInstanceOf[AstConst]
    val defn = cst.definitions(0).asInstanceOf[AstVarDef]
    assert(defn.value.isInstanceOf[AstArrow], s"Expected AstArrow, got ${defn.value.nn.nodeType}")
    val arrow = defn.value.asInstanceOf[AstArrow]
    assertEquals(arrow.argnames.size, 1)
    assert(arrow.argnames(0).isInstanceOf[AstDefaultAssign], s"Expected AstDefaultAssign, got ${arrow.argnames(0).nodeType}")
  }

  // -- Control flow --

  test("parse if/else") {
    val ast = parse("if (x) { a(); } else { b(); }")
    assert(ast.body(0).isInstanceOf[AstIf])
    val ifStmt = ast.body(0).asInstanceOf[AstIf]
    assert(ifStmt.alternative != null, "Expected else branch")
  }

  test("parse for loop") {
    val ast = parse("for (var i = 0; i < 10; i++) { x(); }")
    assert(ast.body(0).isInstanceOf[AstFor])
  }

  test("parse for-in loop") {
    val ast = parse("for (var k in obj) { x(k); }")
    assert(ast.body(0).isInstanceOf[AstForIn])
  }

  test("parse for-of loop") {
    val ast = parse("for (var v of arr) { x(v); }")
    assert(ast.body(0).isInstanceOf[AstForOf])
  }

  test("parse while loop") {
    val ast = parse("while (x) { y(); }")
    assert(ast.body(0).isInstanceOf[AstWhile])
  }

  test("parse do-while") {
    val ast = parse("do { x(); } while (y);")
    assert(ast.body(0).isInstanceOf[AstDo])
  }

  test("parse switch") {
    val ast = parse("switch (x) { case 1: a(); break; default: b(); }")
    assert(ast.body(0).isInstanceOf[AstSwitch])
  }

  test("parse try/catch/finally") {
    val ast = parse("try { x(); } catch (e) { y(); } finally { z(); }")
    assert(ast.body(0).isInstanceOf[AstTry])
    val tryStmt = ast.body(0).asInstanceOf[AstTry]
    assert(tryStmt.bcatch != null)
    assert(tryStmt.bfinally != null)
  }

  // -- Expressions --

  test("parse binary operators") {
    val ast  = parse("a + b * c;")
    val expr = ast.body(0).asInstanceOf[AstSimpleStatement].body
    // Should be: a + (b * c) due to precedence
    assert(expr.isInstanceOf[AstBinary])
    val add = expr.asInstanceOf[AstBinary]
    assertEquals(add.operator, "+")
    assert(add.right.isInstanceOf[AstBinary])
    assertEquals(add.right.asInstanceOf[AstBinary].operator, "*")
  }

  test("parse function call") {
    val ast  = parse("foo(1, 2, 3);")
    val expr = ast.body(0).asInstanceOf[AstSimpleStatement].body
    assert(expr.isInstanceOf[AstCall])
    val call = expr.asInstanceOf[AstCall]
    assertEquals(call.args.size, 3)
  }

  test("parse member access") {
    val ast  = parse("a.b.c;")
    val expr = ast.body(0).asInstanceOf[AstSimpleStatement].body
    assert(expr.isInstanceOf[AstDot])
  }

  test("parse subscript access") {
    val ast  = parse("a[0];")
    val expr = ast.body(0).asInstanceOf[AstSimpleStatement].body
    assert(expr.isInstanceOf[AstSub])
  }

  test("parse new expression") {
    val ast  = parse("new Foo(1);")
    val expr = ast.body(0).asInstanceOf[AstSimpleStatement].body
    assert(expr.isInstanceOf[AstNew])
  }

  test("parse ternary") {
    val ast  = parse("x ? a : b;")
    val expr = ast.body(0).asInstanceOf[AstSimpleStatement].body
    assert(expr.isInstanceOf[AstConditional])
  }

  test("parse assignment") {
    val ast  = parse("x = 1;")
    val expr = ast.body(0).asInstanceOf[AstSimpleStatement].body
    assert(expr.isInstanceOf[AstAssign])
  }

  test("parse unary prefix") {
    val ast  = parse("typeof x;")
    val expr = ast.body(0).asInstanceOf[AstSimpleStatement].body
    assert(expr.isInstanceOf[AstUnaryPrefix])
    assertEquals(expr.asInstanceOf[AstUnaryPrefix].operator, "typeof")
  }

  test("parse unary postfix") {
    val ast  = parse("x++;")
    val expr = ast.body(0).asInstanceOf[AstSimpleStatement].body
    assert(expr.isInstanceOf[AstUnaryPostfix])
    assertEquals(expr.asInstanceOf[AstUnaryPostfix].operator, "++")
  }

  // -- ES6+ features --

  test("parse class declaration") {
    val ast = parse("class Foo extends Bar { }")
    assert(ast.body(0).isInstanceOf[AstDefClass])
    val cls = ast.body(0).asInstanceOf[AstDefClass]
    assertEquals(cls.name.nn.asInstanceOf[AstSymbolDefClass].name, "Foo")
    assert(cls.superClass != null)
  }

  test("parse template literal") {
    val ast  = parse("`hello ${name} world`;")
    val expr = ast.body(0).asInstanceOf[AstSimpleStatement].body
    assert(expr.isInstanceOf[AstTemplateString])
  }

  test("parse spread/rest") {
    val ast = parse("var [a, ...b] = arr;")
    assertEquals(ast.body.size, 1)
  }

  test("parse destructuring object") {
    val ast = parse("var {a, b: c} = obj;")
    assertEquals(ast.body.size, 1)
  }

  test("parse optional chaining") {
    val ast  = parse("a?.b?.c;")
    val expr = ast.body(0).asInstanceOf[AstSimpleStatement].body
    assert(expr.isInstanceOf[AstChain])
  }

  test("parse nullish coalescing") {
    val ast  = parse("a ?? b;")
    val expr = ast.body(0).asInstanceOf[AstSimpleStatement].body
    assert(expr.isInstanceOf[AstBinary])
    assertEquals(expr.asInstanceOf[AstBinary].operator, "??")
  }

  test("parse async/await") {
    val ast = parse("async function f() { await x(); }")
    val fn  = ast.body(0).asInstanceOf[AstDefun]
    assert(fn.isAsync)
    assert(fn.body(0).isInstanceOf[AstSimpleStatement])
    val expr = fn.body(0).asInstanceOf[AstSimpleStatement].body
    assert(expr.isInstanceOf[AstAwait])
  }

  // -- Modules --

  test("parse import") {
    val ast = parse("import foo from 'bar';")
    assert(ast.body(0).isInstanceOf[AstImport])
  }

  test("parse export") {
    val ast = parse("export default 42;")
    assert(ast.body(0).isInstanceOf[AstExport])
  }

  // -- Literals --

  test("parse string literal") {
    // Bare string at start of body is parsed as a directive
    val ast = parse("'hello';")
    assert(ast.body(0).isInstanceOf[AstDirective])
    assertEquals(ast.body(0).asInstanceOf[AstDirective].value, "hello")
    // String as expression (after non-directive) is a simple statement
    val ast2 = parse("1; 'hello';")
    val expr = ast2.body(1).asInstanceOf[AstSimpleStatement].body
    assert(expr.isInstanceOf[AstString])
    assertEquals(expr.asInstanceOf[AstString].value, "hello")
  }

  test("parse number literal") {
    val ast  = parse("42;")
    val expr = ast.body(0).asInstanceOf[AstSimpleStatement].body
    assert(expr.isInstanceOf[AstNumber])
    assertEquals(expr.asInstanceOf[AstNumber].value, 42.0)
  }

  test("parse boolean literals") {
    val ast = parse("true; false;")
    val t   = ast.body(0).asInstanceOf[AstSimpleStatement].body
    val f   = ast.body(1).asInstanceOf[AstSimpleStatement].body
    assert(t.isInstanceOf[AstTrue])
    assert(f.isInstanceOf[AstFalse])
  }

  test("parse null literal") {
    val ast  = parse("null;")
    val expr = ast.body(0).asInstanceOf[AstSimpleStatement].body
    assert(expr.isInstanceOf[AstNull])
  }

  test("parse regex literal") {
    val ast  = parse("var r = /pattern/gi;")
    val defn = ast.body(0).asInstanceOf[AstVar].definitions(0).asInstanceOf[AstVarDef]
    assert(defn.value.isInstanceOf[AstRegExp])
  }

  test("parse array literal") {
    val ast  = parse("[1, 2, 3];")
    val expr = ast.body(0).asInstanceOf[AstSimpleStatement].body
    assert(expr.isInstanceOf[AstArray])
    assertEquals(expr.asInstanceOf[AstArray].elements.size, 3)
  }

  test("parse object literal") {
    val ast  = parse("({a: 1, b: 2});")
    val expr = ast.body(0).asInstanceOf[AstSimpleStatement].body
    assert(expr.isInstanceOf[AstObject])
    assertEquals(expr.asInstanceOf[AstObject].properties.size, 2)
  }

  // -- Edge cases --

  test("parse multiple statements") {
    val ast = parse("var x = 1; var y = 2; x + y;")
    assertEquals(ast.body.size, 3)
  }

  test("parse empty statement") {
    val ast = parse(";")
    assertEquals(ast.body.size, 1)
    assert(ast.body(0).isInstanceOf[AstEmptyStatement])
  }

  test("parse labeled statement") {
    val ast = parse("outer: for (;;) { break outer; }")
    assert(ast.body(0).isInstanceOf[AstLabeledStatement])
  }

  test("parse debugger") {
    val ast = parse("debugger;")
    assert(ast.body(0).isInstanceOf[AstDebugger])
  }

  test("parse use strict directive") {
    val ast = parse("'use strict'; var x = 1;")
    assert(ast.body(0).isInstanceOf[AstDirective])
    assertEquals(ast.body(0).asInstanceOf[AstDirective].value, "use strict")
  }

  test("parse comma expression") {
    val ast  = parse("(a, b, c);")
    val expr = ast.body(0).asInstanceOf[AstSimpleStatement].body
    assert(expr.isInstanceOf[AstSequence])
    assertEquals(expr.asInstanceOf[AstSequence].expressions.size, 3)
  }

  test("parse real-world snippet") {
    val code =
      """function fibonacci(n) {
        |  if (n <= 1) return n;
        |  return fibonacci(n - 1) + fibonacci(n - 2);
        |}
        |var result = fibonacci(10);
        |console.log(result);""".stripMargin
    val ast = parse(code)
    assertEquals(ast.body.size, 3) // function + var + expression
    assert(ast.body(0).isInstanceOf[AstDefun])
  }
}
