/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Exercises the new `_rd*` recursive-descent expression parser scaffold
 * in StylesheetParser. These methods are a staged port of the dart-sass
 * `_expression` / `_singleExpression` machinery and are NOT yet wired
 * into the main parse path — this suite calls them directly.
 */
package ssg
package sass
package parse

import ssg.sass.ast.sass.{
  BinaryOperationExpression,
  BinaryOperator,
  BooleanExpression,
  ColorExpression,
  Expression,
  FunctionExpression,
  ListExpression,
  NumberExpression,
  ParenthesizedExpression,
  StringExpression,
  UnaryOperationExpression,
  UnaryOperator,
  VariableExpression
}
import ssg.sass.value.ListSeparator

final class RecursiveDescentExpressionSuite extends munit.FunSuite {

  /** Bridge class that exposes the protected `_rd*` methods on ScssParser. */
  private final class TestParser(source: String) extends ScssParser(source) {
    def rdParse(): Expression = {
      whitespace(consumeNewlines = true)
      _rdExpression()
    }
  }

  private def rd(source: String): Expression = new TestParser(source).rdParse()

  test("_rdExpression parses a bare integer") {
    val e = rd("42")
    val n = e.asInstanceOf[NumberExpression]
    assertEquals(n.value, 42.0)
    assert(n.unit.isEmpty)
  }

  test("_rdExpression parses a number with a unit") {
    val e = rd("100px")
    val n = e.asInstanceOf[NumberExpression]
    assertEquals(n.value, 100.0)
    assertEquals(n.unit.get, "px")
  }

  test("_rdExpression parses a decimal") {
    val e = rd("3.14")
    val n = e.asInstanceOf[NumberExpression]
    assertEquals(n.value, 3.14)
  }

  test("_rdExpression parses a variable reference") {
    val e = rd("$foo")
    val v = e.asInstanceOf[VariableExpression]
    assertEquals(v.name, "foo")
  }

  test("_rdExpression parses a quoted string") {
    val e = rd("\"hello\"")
    val s = e.asInstanceOf[StringExpression]
    assert(s.hasQuotes)
    assertEquals(s.text.asPlain.get, "hello")
  }

  test("_rdExpression parses boolean identifier") {
    val e = rd("true")
    val b = e.asInstanceOf[BooleanExpression]
    assert(b.value)
  }

  test("_rdExpression parses a named color keyword") {
    val e = rd("red")
    assert(e.isInstanceOf[ColorExpression])
  }

  test("_rdExpression handles simple addition") {
    val e = rd("1 + 2")
    val b = e.asInstanceOf[BinaryOperationExpression]
    assertEquals(b.operator, BinaryOperator.Plus)
    assertEquals(b.left.asInstanceOf[NumberExpression].value, 1.0)
    assertEquals(b.right.asInstanceOf[NumberExpression].value, 2.0)
  }

  test("_rdExpression respects operator precedence") {
    // 1 + 2 * 3 → 1 + (2 * 3)
    val e   = rd("1 + 2 * 3")
    val top = e.asInstanceOf[BinaryOperationExpression]
    assertEquals(top.operator, BinaryOperator.Plus)
    val rhs = top.right.asInstanceOf[BinaryOperationExpression]
    assertEquals(rhs.operator, BinaryOperator.Times)
  }

  test("_rdExpression parses a parenthesized expression") {
    val e = rd("(1 + 2)")
    val p = e.asInstanceOf[ParenthesizedExpression]
    assert(p.expression.isInstanceOf[BinaryOperationExpression])
  }

  test("_rdExpression parses a unary minus on a variable") {
    val e = rd("-$x")
    val u = e.asInstanceOf[UnaryOperationExpression]
    assertEquals(u.operator, UnaryOperator.Minus)
    assert(u.operand.isInstanceOf[VariableExpression])
  }

  test("_rdExpression parses a function call") {
    val e  = rd("rgb(1, 2, 3)")
    val fn = e.asInstanceOf[FunctionExpression]
    assertEquals(fn.name, "rgb")
    assertEquals(fn.arguments.positional.length, 3)
  }

  test("_rdExpression parses comma-separated list") {
    val e = rd("1, 2, 3")
    val l = e.asInstanceOf[ListExpression]
    assertEquals(l.separator, ListSeparator.Comma)
    assertEquals(l.contents.length, 3)
  }

  test("_rdExpression parses space-separated list") {
    val e = rd("1px 2px 3px")
    val l = e.asInstanceOf[ListExpression]
    assertEquals(l.separator, ListSeparator.Space)
    assertEquals(l.contents.length, 3)
  }

  test("_rdExpression parses a namespaced function call") {
    val e  = rd("math.abs(-5)")
    val fn = e.asInstanceOf[FunctionExpression]
    assertEquals(fn.namespace.get, "math")
    assertEquals(fn.name, "abs")
  }

  test("_rdExpression parses equality comparison") {
    val e = rd("1 == 2")
    val b = e.asInstanceOf[BinaryOperationExpression]
    assertEquals(b.operator, BinaryOperator.Equals)
  }
}
