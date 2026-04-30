/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from terser/test/mocha/lhs-expressions.js
 * Original: 11 it() calls
 */
package ssg
package js

import ssg.js.ast.*
import ssg.js.parse.{ JsParseError, Parser }

final class LhsExpressionsSuite extends munit.FunSuite {

  private def parse(code: String): AstToplevel = new Parser().parse(code)

  // 1. "Should parse destructuring with const/let/var correctly"
  test("should parse destructuring with const/let/var correctly") {
    val decls = parse("var {a,b} = foo, { c, d } = bar")

    assertEquals(decls.body(0).nodeType, "Var")
    val varStmt = decls.body(0).asInstanceOf[AstVar]
    assertEquals(varStmt.definitions.size, 2)

    // Item 1
    assert(varStmt.definitions(0).asInstanceOf[AstVarDef].name.isInstanceOf[AstDestructuring])
    assert(varStmt.definitions(0).asInstanceOf[AstVarDef].value.isInstanceOf[AstSymbolRef])

    // Item 2
    assert(varStmt.definitions(1).asInstanceOf[AstVarDef].name.isInstanceOf[AstDestructuring])
    assert(varStmt.definitions(1).asInstanceOf[AstVarDef].value.isInstanceOf[AstSymbolRef])

    // Nested destructuring: var [{x}] = foo
    val nestedDef = parse("var [{x}] = foo").body(0).asInstanceOf[AstVar].definitions(0).asInstanceOf[AstVarDef]
    val destr     = nestedDef.name.asInstanceOf[AstDestructuring]
    val innerObj  = destr.names(0).asInstanceOf[AstDestructuring]
    val kv        = innerObj.names(0).asInstanceOf[AstObjectKeyVal]
    assertEquals(kv.key, "x")
    assert(kv.value.isInstanceOf[AstSymbolVar])
    assertEquals(kv.value.asInstanceOf[AstSymbolVar].name, "x")

    // Holey destructuring: const [,,third] = [1,2,3]
    val holeyDef   = parse("const [,,third] = [1,2,3]").body(0).asInstanceOf[AstConst].definitions(0).asInstanceOf[AstVarDef]
    val holeyDestr = holeyDef.name.asInstanceOf[AstDestructuring]
    assert(holeyDestr.names(0).isInstanceOf[AstHole])
    assert(holeyDestr.names(1).isInstanceOf[AstHole])
    assert(holeyDestr.names(2).isInstanceOf[AstSymbolConst])

    // Expanding destructuring: var [first, ...rest] = [1,2,3]
    val expandDef   = parse("var [first, ...rest] = [1,2,3]").body(0).asInstanceOf[AstVar].definitions(0).asInstanceOf[AstVarDef]
    val expandDestr = expandDef.name.asInstanceOf[AstDestructuring]
    assert(expandDestr.names(0).isInstanceOf[AstSymbolVar])
    assert(expandDestr.names(1).isInstanceOf[AstExpansion])
    assert(expandDestr.names(1).asInstanceOf[AstExpansion].expression.isInstanceOf[AstSymbolVar])
  }

  // 2. "Parser should use AST_Array for array literals"
  test("parser should use AstArray for array literals") {
    val ast = parse("[\"foo\", \"bar\"]")
    assert(ast.body(0).isInstanceOf[AstSimpleStatement])
    assert(ast.body(0).asInstanceOf[AstSimpleStatement].body.isInstanceOf[AstArray])

    val ast2   = parse("a = [\"foo\"]")
    val assign = ast2.body(0).asInstanceOf[AstSimpleStatement].body.asInstanceOf[AstAssign]
    assert(assign.left.isInstanceOf[AstSymbolRef])
    assertEquals(assign.operator, "=")
    assert(assign.right.isInstanceOf[AstArray])
  }

  // 3. "Parser should use AST_Object for object literals"
  test("parser should use AstObject for object literals") {
    val ast = parse("({foo: \"bar\"})")
    assert(ast.body(0).asInstanceOf[AstSimpleStatement].body.isInstanceOf[AstObject])

    val ast2   = parse("a = {foo: \"bar\"}")
    val assign = ast2.body(0).asInstanceOf[AstSimpleStatement].body.asInstanceOf[AstAssign]
    assert(assign.left.isInstanceOf[AstSymbolRef])
    assertEquals(assign.operator, "=")
    assert(assign.right.isInstanceOf[AstObject])
  }

  // 4. "Parser should use AST_Destructuring for array assignment patterns"
  test("parser should use AstDestructuring for array assignment patterns") {
    val ast    = parse("[foo, bar] = [1, 2]")
    val assign = ast.body(0).asInstanceOf[AstSimpleStatement].body.asInstanceOf[AstAssign]
    assert(assign.left.isInstanceOf[AstDestructuring])
    assert(assign.left.asInstanceOf[AstDestructuring].isArray)
    assertEquals(assign.operator, "=")
    assert(assign.right.isInstanceOf[AstArray])
  }

  // 5. "Parser should use AST_Destructuring for object assignment patterns"
  test("parser should use AstDestructuring for object assignment patterns") {
    val ast    = parse("({a: b, b: c} = {b: \"c\", c: \"d\"})")
    val assign = ast.body(0).asInstanceOf[AstSimpleStatement].body.asInstanceOf[AstAssign]
    assert(assign.left.isInstanceOf[AstDestructuring])
    assert(!assign.left.asInstanceOf[AstDestructuring].isArray)
    assertEquals(assign.operator, "=")
    assert(assign.right.isInstanceOf[AstObject])
  }

  // 6. "Parser should be able to handle nested destructuring"
  test("parser should handle nested destructuring") {
    val ast    = parse("[{a,b},[d, e, f, {g, h}]] = [{a: 1, b: 2}, [3, 4, 5, {g: 6, h: 7}]]")
    val assign = ast.body(0).asInstanceOf[AstSimpleStatement].body.asInstanceOf[AstAssign]
    assert(assign.left.isInstanceOf[AstDestructuring])
    assert(assign.left.asInstanceOf[AstDestructuring].isArray)
    assert(assign.right.isInstanceOf[AstArray])

    val names = assign.left.asInstanceOf[AstDestructuring].names
    // First element: {a,b} (object destructuring)
    assert(names(0).isInstanceOf[AstDestructuring])
    assert(!names(0).asInstanceOf[AstDestructuring].isArray)
    // Second element: [d, e, f, {g, h}] (array destructuring)
    assert(names(1).isInstanceOf[AstDestructuring])
    assert(names(1).asInstanceOf[AstDestructuring].isArray)
    // Nested: {g, h} is the 4th element of the inner array
    assert(names(1).asInstanceOf[AstDestructuring].names(3).isInstanceOf[AstDestructuring])
    assert(!names(1).asInstanceOf[AstDestructuring].names(3).asInstanceOf[AstDestructuring].isArray)
  }

  // 7. "Should handle spread operator in destructuring"
  test("should handle spread operator in destructuring") {
    val ast    = parse("[a, b, ...c] = [1, 2, 3, 4, 5]")
    val assign = ast.body(0).asInstanceOf[AstSimpleStatement].body.asInstanceOf[AstAssign]
    assert(assign.left.isInstanceOf[AstDestructuring])
    assert(assign.left.asInstanceOf[AstDestructuring].isArray)
    assert(assign.right.isInstanceOf[AstArray])

    val names = assign.left.asInstanceOf[AstDestructuring].names
    assert(names(0).isInstanceOf[AstSymbolRef])
    assert(names(1).isInstanceOf[AstSymbolRef])
    assert(names(2).isInstanceOf[AstExpansion])
  }

  // 8. "Should handle default assignments in destructuring"
  test("should handle default assignments in destructuring") {
    // Object destructuring with defaults: ({x: v, z = z + 5} = obj)
    val ast    = parse("({x: v, z = z + 5} = obj);")
    val assign = ast.body(0).asInstanceOf[AstSimpleStatement].body.asInstanceOf[AstAssign]
    assert(assign.left.isInstanceOf[AstDestructuring])
    assert(!assign.left.asInstanceOf[AstDestructuring].isArray)
    assert(assign.right.isInstanceOf[AstSymbolRef])

    val names = assign.left.asInstanceOf[AstDestructuring].names
    assert(names(0).asInstanceOf[AstObjectKeyVal].value.isInstanceOf[AstSymbolRef])
    assert(names(1).asInstanceOf[AstObjectKeyVal].value.isInstanceOf[AstDefaultAssign])

    // ({x = 123} = obj)
    val ast2    = parse("({x = 123} = obj);")
    val assign2 = ast2.body(0).asInstanceOf[AstSimpleStatement].body.asInstanceOf[AstAssign]
    val destr2  = assign2.left.asInstanceOf[AstDestructuring]
    assert(destr2.names(0).asInstanceOf[AstObjectKeyVal].value.isInstanceOf[AstDefaultAssign])

    // [x, y = 5] = foo
    val ast3    = parse("[x, y = 5] = foo")
    val assign3 = ast3.body(0).asInstanceOf[AstSimpleStatement].body.asInstanceOf[AstAssign]
    val destr3  = assign3.left.asInstanceOf[AstDestructuring]
    assert(destr3.isArray)
    assert(destr3.names(0).isInstanceOf[AstSymbolRef])
    assert(destr3.names(1).isInstanceOf[AstDefaultAssign])
    assert(destr3.names(1).asInstanceOf[AstDefaultAssign].left.isInstanceOf[AstSymbolRef])
  }

  // 9. "Should handle default assignments containing assignments in a destructuring"
  test("should handle default assignments containing assignments in a destructuring") {
    val ast    = parse("[x, y = z = 2] = a;")
    val assign = ast.body(0).asInstanceOf[AstSimpleStatement].body.asInstanceOf[AstAssign]
    val destr  = assign.left.asInstanceOf[AstDestructuring]
    assert(destr.isArray)
    assert(destr.names(0).isInstanceOf[AstSymbolRef])
    assert(destr.names(1).isInstanceOf[AstDefaultAssign])
    val defAssign = destr.names(1).asInstanceOf[AstDefaultAssign]
    assert(defAssign.left.isInstanceOf[AstSymbolRef])
    assertEquals(defAssign.operator, "=")
    assert(defAssign.right.isInstanceOf[AstAssign])
    val innerAssign = defAssign.right.asInstanceOf[AstAssign]
    assert(innerAssign.left.isInstanceOf[AstSymbolRef])
    assertEquals(innerAssign.operator, "=")
    assert(innerAssign.right.isInstanceOf[AstNumber])

    // ({a: a = 123} = obj)
    val ast2    = parse("({a: a = 123} = obj)")
    val assign2 = ast2.body(0).asInstanceOf[AstSimpleStatement].body.asInstanceOf[AstAssign]
    val destr2  = assign2.left.asInstanceOf[AstDestructuring]
    assert(!destr2.isArray)
    val prop = destr2.names(0).asInstanceOf[AstObjectKeyVal]
    assertEquals(prop.key, "a")
    assert(prop.value.isInstanceOf[AstDefaultAssign])
    val defAssign2 = prop.value.asInstanceOf[AstDefaultAssign]
    assert(defAssign2.left.isInstanceOf[AstSymbolRef])
    assertEquals(defAssign2.operator, "=")
    assert(defAssign2.right.isInstanceOf[AstNumber])
  }

  // 10. "Should allow multiple spread in array literals"
  // Known parser bug: multiple spreads in array literals fail to parse
  test("should allow multiple spread in array literals".fail) {
    val ast = parse("var a = [1, 2, 3], b = [4, 5, 6], joined; joined = [...a, ...b]")
    assert(ast.body(0).isInstanceOf[AstVar])
    assert(ast.body(1).isInstanceOf[AstSimpleStatement])

    val assign = ast.body(1).asInstanceOf[AstSimpleStatement].body.asInstanceOf[AstAssign]
    assert(assign.right.isInstanceOf[AstArray])
    val elements = assign.right.asInstanceOf[AstArray].elements
    assertEquals(elements.size, 2)
    assert(elements(0).isInstanceOf[AstExpansion])
    assert(elements(0).asInstanceOf[AstExpansion].expression.isInstanceOf[AstSymbolRef])
    assert(elements(1).isInstanceOf[AstExpansion])
    assert(elements(1).asInstanceOf[AstExpansion].expression.isInstanceOf[AstSymbolRef])
  }

  // 11. "Should not allow spread on invalid locations"
  test("should not allow spread on invalid locations — array destructuring") {
    // Spreads are not allowed in destructuring array if not the last element
    intercept[JsParseError](parse("[...a, ...b] = [1, 2, 3, 4]"))

    // Array spread must be last in destructuring declaration
    intercept[JsParseError](parse("let [ ...x, a ] = o;"))

    // Only one spread per destructuring array declaration
    intercept[JsParseError](parse("let [ a, ...x, ...y ] = o;"))

    // Spread in block should not be allowed
    intercept[JsParseError](parse("{...a} = foo"))
  }

  // Known parser gap: multiple object rest elements not validated
  test("should not allow spread on invalid locations — object multiple rest".fail) {
    intercept[JsParseError](parse("let { a, ...x, ...y } = o;"))
  }

  // Known parser gap: Object rest must be last is not validated
  test("should not allow spread on invalid locations — object rest must be last".fail) {
    intercept[JsParseError](parse("let { ...x, a } = o;"))
  }
}
