/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass
package parse

import ssg.sass.ast.sass.{ AtRule, BooleanExpression, Declaration, NullExpression, NumberExpression, StringExpression, StyleRule, VariableDeclaration, VariableExpression }

final class StylesheetParserSuite extends munit.FunSuite {

  private def parse(source: String) = new ScssParser(source).parse()

  // --- Variable declarations ---

  test("parses a simple variable declaration") {
    val sheet = parse("$foo: 42;")
    assertEquals(sheet.children.get.size, 1)
    val v = sheet.children.get.head.asInstanceOf[VariableDeclaration]
    assertEquals(v.name, "foo")
    assert(v.expression.isInstanceOf[NumberExpression])
    assertEquals(v.expression.asInstanceOf[NumberExpression].value, 42.0)
  }

  test("parses variable with unit") {
    val sheet = parse("$width: 100px;")
    val v     = sheet.children.get.head.asInstanceOf[VariableDeclaration]
    val num   = v.expression.asInstanceOf[NumberExpression]
    assertEquals(num.value, 100.0)
    assertEquals(num.unit.get, "px")
  }

  test("parses variable with string value") {
    val sheet = parse("""$name: "hello";""")
    val v     = sheet.children.get.head.asInstanceOf[VariableDeclaration]
    val str   = v.expression.asInstanceOf[StringExpression]
    assert(str.hasQuotes)
    assertEquals(str.text.asPlain.get, "hello")
  }

  test("parses variable referencing another variable") {
    val sheet = parse("$a: $b;")
    val v     = sheet.children.get.head.asInstanceOf[VariableDeclaration]
    val ref   = v.expression.asInstanceOf[VariableExpression]
    assertEquals(ref.name, "b")
  }

  test("parses variable with boolean value") {
    val sheet = parse("$flag: true;")
    val v     = sheet.children.get.head.asInstanceOf[VariableDeclaration]
    val b     = v.expression.asInstanceOf[BooleanExpression]
    assert(b.value)
  }

  test("parses variable with !default flag") {
    val sheet = parse("$color: red !default;")
    val v     = sheet.children.get.head.asInstanceOf[VariableDeclaration]
    assert(v.isGuarded)
  }

  // --- Style rules ---

  test("parses a simple style rule with declarations") {
    val sheet = parse("a { color: red; }")
    val rule  = sheet.children.get.head.asInstanceOf[StyleRule]
    // almostAnyValue() (dart-sass parity) does not trim trailing whitespace;
    // the evaluator trims when re-parsing the selector. Assert trimmed value.
    assertEquals(rule.selector.get.asPlain.get.trim, "a")
    assertEquals(rule.children.get.size, 1)
    val decl = rule.children.get.head.asInstanceOf[Declaration]
    assertEquals(decl.name.asPlain.get, "color")
  }

  test("parses multiple declarations") {
    val sheet = parse("""
      p {
        color: red;
        font-size: 14px;
        margin: 10px;
      }
    """)
    val rule = sheet.children.get.head.asInstanceOf[StyleRule]
    assertEquals(rule.children.get.size, 3)
  }

  test("parses multiple style rules") {
    val sheet = parse("""
      a { color: red; }
      b { color: blue; }
    """)
    assertEquals(sheet.children.get.size, 2)
  }

  test("parses rule with no declarations") {
    val sheet = parse("a { }")
    val rule  = sheet.children.get.head.asInstanceOf[StyleRule]
    assertEquals(rule.children.get.size, 0)
  }

  // --- Comments ---

  test("parses silent (//) comments") {
    val sheet = parse("// hello\n$foo: 1;")
    // Comment + declaration
    assert(sheet.children.get.size >= 1)
  }

  test("parses loud (/*...*/) comments") {
    val sheet = parse("""/* hello */ $foo: 1;""")
    assert(sheet.children.get.size >= 1)
  }

  // --- Mixed ---

  test("parses complex multi-rule stylesheet") {
    val sheet = parse(
      """
      $primary: #3498db;
      $padding: 10px;

      .button {
        color: $primary;
        padding: $padding;
      }

      .card {
        background: white;
        border: 1px solid gray;
      }
    """
    )
    // 2 variables + 2 style rules = 4 top-level statements
    assertEquals(sheet.children.get.size, 4)
  }

  test("parses generic @-rules") {
    val sheet  = parse("""@page { margin: 1cm; }""")
    val atRule = sheet.children.get.head.asInstanceOf[AtRule]
    assertEquals(atRule.name.asPlain.get, "page")
  }

  // --- Expression parser ---

  test("parseExpression handles number literals") {
    val (expr, _) = new ScssParser("42px").parseExpression()
    val num       = expr.asInstanceOf[NumberExpression]
    assertEquals(num.value, 42.0)
    assertEquals(num.unit.get, "px")
  }

  test("parseExpression handles strings") {
    val (expr, _) = new ScssParser("\"hello\"").parseExpression()
    val str       = expr.asInstanceOf[StringExpression]
    assert(str.hasQuotes)
  }

  test("parseExpression handles null") {
    val (expr, _) = new ScssParser("null").parseExpression()
    assert(expr.isInstanceOf[NullExpression])
  }

  // --- parseVariableDeclaration ---

  test("parseVariableDeclaration standalone") {
    val (decl, _) = new ScssParser("$foo: 42").parseVariableDeclaration()
    assertEquals(decl.name, "foo")
  }
}
