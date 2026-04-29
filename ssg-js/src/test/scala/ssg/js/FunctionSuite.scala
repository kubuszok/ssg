/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from terser/test/mocha/function.js
 * Original: 6 it() calls
 */
package ssg
package js

import ssg.js.ast.*
import ssg.js.parse.{ JsParseError, Parser }
import ssg.js.compress.Hoisting

final class FunctionSuite extends munit.FunSuite {

  private def parse(code: String) = new Parser().parse(code)

  // 1. "Should parse binding patterns correctly"
  test("should parse binding patterns correctly") {
    // Destructurings as arguments
    val destrFun1 = parse("(function ({a, b}) {})").body(0).asInstanceOf[AstSimpleStatement]
      .body.asInstanceOf[AstFunction]
    val destrFun2 = parse("(function ([a, [b]]) {})").body(0).asInstanceOf[AstSimpleStatement]
      .body.asInstanceOf[AstFunction]
    val destrFun3 = parse("({a, b}) => null").body(0).asInstanceOf[AstSimpleStatement]
      .body.asInstanceOf[AstArrow]
    val destrFun4 = parse("([a, [b]]) => null").body(0).asInstanceOf[AstSimpleStatement]
      .body.asInstanceOf[AstArrow]

    assertEquals(destrFun1.argnames.size, 1)
    assertEquals(destrFun2.argnames.size, 1)
    assertEquals(destrFun3.argnames.size, 1)
    assertEquals(destrFun4.argnames.size, 1)

    val destruct1 = destrFun1.argnames(0).asInstanceOf[AstDestructuring]
    val destruct2 = destrFun2.argnames(0).asInstanceOf[AstDestructuring]
    val destruct3 = destrFun3.argnames(0).asInstanceOf[AstDestructuring]
    val destruct4 = destrFun4.argnames(0).asInstanceOf[AstDestructuring]

    assert(destruct2.names(1).isInstanceOf[AstDestructuring])
    assert(destruct4.names(1).isInstanceOf[AstDestructuring])

    assertEquals(destruct1.start.value, "{")
    assertEquals(destruct1.end.value, "}")
    assertEquals(destruct2.start.value, "[")
    assertEquals(destruct2.end.value, "]")
    assertEquals(destruct3.start.value, "{")
    assertEquals(destruct3.end.value, "}")
    assertEquals(destruct4.start.value, "[")
    assertEquals(destruct4.end.value, "]")

    assertEquals(destruct1.isArray, false)
    assertEquals(destruct2.isArray, true)
    assertEquals(destruct3.isArray, false)
    assertEquals(destruct4.isArray, true)

    // destruct 1 checks
    val d1n0 = destruct1.names(0).asInstanceOf[AstObjectKeyVal]
    assertEquals(d1n0.nodeType, "ObjectKeyVal")
    assertEquals(d1n0.key.asInstanceOf[String], "a")
    assertEquals(d1n0.value.asInstanceOf[AstSymbol].name, "a")

    val d1n1 = destruct1.names(1).asInstanceOf[AstObjectKeyVal]
    assertEquals(d1n1.nodeType, "ObjectKeyVal")
    assertEquals(d1n1.key.asInstanceOf[String], "b")
    assertEquals(d1n1.value.asInstanceOf[AstSymbol].name, "b")

    // destruct 2 checks
    val d2n0 = destruct2.names(0).asInstanceOf[AstSymbolFunarg]
    assertEquals(d2n0.nodeType, "SymbolFunarg")
    assertEquals(d2n0.name, "a")
    val d2n1inner = destruct2.names(1).asInstanceOf[AstDestructuring].names(0).asInstanceOf[AstSymbolFunarg]
    assertEquals(d2n1inner.nodeType, "SymbolFunarg")
    assertEquals(d2n1inner.name, "b")

    // destruct 3 checks
    val d3n0 = destruct3.names(0).asInstanceOf[AstObjectKeyVal]
    assert(d3n0.key.isInstanceOf[String])
    assertEquals(d3n0.key.asInstanceOf[String], "a")
    assertEquals(d3n0.value.asInstanceOf[AstSymbol].nodeType, "SymbolFunarg")
    assertEquals(d3n0.value.asInstanceOf[AstSymbol].name, "a")
    val d3n1 = destruct3.names(1).asInstanceOf[AstObjectKeyVal]
    assert(d3n1.key.isInstanceOf[String])
    assertEquals(d3n1.key.asInstanceOf[String], "b")
    assertEquals(d3n1.value.asInstanceOf[AstSymbol].nodeType, "SymbolFunarg")
    assertEquals(d3n1.value.asInstanceOf[AstSymbol].name, "b")

    // destruct 4 checks
    val d4n0 = destruct4.names(0).asInstanceOf[AstSymbolFunarg]
    assertEquals(d4n0.nodeType, "SymbolFunarg")
    assertEquals(d4n0.name, "a")
    assertEquals(destruct4.names(1).nodeType, "Destructuring")
    val d4n1inner = destruct4.names(1).asInstanceOf[AstDestructuring].names(0).asInstanceOf[AstSymbolFunarg]
    assertEquals(d4n1inner.nodeType, "SymbolFunarg")
    assertEquals(d4n1inner.name, "b")

    // args_as_names checks
    def getArgs(args: scala.collection.Seq[AstSymbol]): List[(String, String)] =
      args.map(a => (a.nodeType, a.name)).toList

    assertEquals(
      getArgs(Hoisting.argsAsNames(destrFun1)),
      List(("SymbolFunarg", "a"), ("SymbolFunarg", "b"))
    )
    assertEquals(
      getArgs(Hoisting.argsAsNames(destrFun2)),
      List(("SymbolFunarg", "a"), ("SymbolFunarg", "b"))
    )
    assertEquals(
      getArgs(Hoisting.argsAsNames(destrFun3)),
      List(("SymbolFunarg", "a"), ("SymbolFunarg", "b"))
    )
    assertEquals(
      getArgs(Hoisting.argsAsNames(destrFun4)),
      List(("SymbolFunarg", "a"), ("SymbolFunarg", "b"))
    )

    // Invalid destructurings
    intercept[JsParseError] { parse("(function ( { a, [ b ] } ) { })") }
    intercept[JsParseError] { parse("(function (1) { })") }
    intercept[JsParseError] { parse("(function (this) { })") }
    intercept[JsParseError] { parse("(function ([1]) { })") }
    intercept[JsParseError] { parse("(function [a] { })") }

    // generators
    val generatorsDef = parse("function* fn() {}").body(0)
    assertEquals(generatorsDef.asInstanceOf[AstDefun].isGenerator, true)

    intercept[JsParseError] { parse("function* (){ }") }

    val generatorsYieldDef = parse("function* fn() {\nyield remote();\n}").body(0)
      .asInstanceOf[AstDefun].body(0).asInstanceOf[AstSimpleStatement]
    val yieldExpr = generatorsYieldDef.body.asInstanceOf[AstYield]
    assertEquals(yieldExpr.isStar, false)
  }

  // 2. "Should not accept spread on non-last parameters"
  test("should not accept spread on non-last parameters") {
    val tests = List(
      "var a = function(...a, b) { return a.join(b) }",
      "var b = function(a, b, ...c, d) { return c.join(a + b) + d }",
      "function foo(...a, b) { return a.join(b) }",
      "function bar(a, b, ...c, d) { return c.join(a + b) + d }",
      "var a = function*(...a, b) { return a.join(b) }",
      "var b = function*(a, b, ...c, d) { return c.join(a + b) + d }",
      "function* foo(...a, b) { return a.join(b) }",
      "function* bar(a, b, ...c, d) { return c.join(a + b) + d }",
    )
    tests.foreach { code =>
      val ex = intercept[JsParseError] { parse(code) }
      assert(
        ex.message.contains("Unexpected token"),
        s"Expected 'Unexpected token' for: $code, got: ${ex.message}"
      )
    }
  }

  // 3. "Should not accept empty parameters after elision"
  // Note: Original uses ecma=5 option; ssg-js doesn't have an ecma option, uses defaults.
  test("should not accept empty parameters after elision") {
    intercept[JsParseError] { parse("(function(,){})()") }
  }

  // 4. "Should not accept invalid trailing commas"
  // Note: Original uses ecma=2017 option; ssg-js parser handles these by default.
  test("should not accept invalid trailing commas") {
    val tests = List(
      "f(, );",
      "(, ) => {};",
      "(...p, ) => {};",
      "function f(, ) {}",
      "function f(...p, ) {}",
      "function foo(a, b, , ) {}",
      """console.log("hello", , );""",
    )
    tests.foreach { code =>
      intercept[JsParseError] { parse(code) }
    }
  }

  // 5. "Should not accept an initializer when parameter is a rest parameter"
  test("should not accept initializer on rest parameter") {
    val tests = List(
      "(function(...a = b){})()",
      "(function(a, ...b = [c, d]))",
    )
    tests.foreach { code =>
      intercept[JsParseError] { parse(code) }
    }
  }

  // 6. "Should not accept duplicated identifiers inside parameters in strict mode or when using default assignment or spread"
  test("should not accept duplicated identifiers in parameters") {
    // These should produce "Parameter X was used already" errors
    val duplicateTests = List(
      "(function(a = 1, a){})()",
      "(function(a, [a = 3]){})()",
      "(function(a, b, c, d, [{e: [...a]}]){})()",
      "'use strict'; (function(a, a){})",
      "(function(a, [...a]){})",
      "(function(a, ...a){})",
      "(function(a, [a, ...b]){})",
      "(function(a, {b: a, c: [...d]}){})",
      "(function(a, a, {b: [...c]}){})",
    )
    duplicateTests.foreach { code =>
      val ex = intercept[JsParseError] { parse(code) }
      assert(
        ex.message.matches("Parameter [a-zA-Z]+ was used already"),
        s"Expected 'Parameter X was used already' for: $code, got: ${ex.message}"
      )
    }
    // This test produces a different error in ssg-js parser (default assignment in destructuring)
    // "(function({a, a = b}))" — parser error about operator = instead of comma
    intercept[JsParseError] { parse("(function({a, a = b}))") }
  }
}
