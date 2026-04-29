/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package liquid

import java.util.HashMap
import java.util.ArrayList

final class ExpressionSuite extends munit.FunSuite {

  // ===== Equality =====

  test("equality: integer 1 == 1") {
    assertEquals(
      Template.parse("{% if 1 == 1 %}true{% endif %}").render(),
      "true"
    )
  }

  test("inequality: integer 1 != 2") {
    assertEquals(
      Template.parse("{% if 1 != 2 %}true{% endif %}").render(),
      "true"
    )
  }

  test("greater than: 2 > 1") {
    assertEquals(
      Template.parse("{% if 2 > 1 %}true{% endif %}").render(),
      "true"
    )
  }

  test("less than: 1 < 2") {
    assertEquals(
      Template.parse("{% if 1 < 2 %}true{% endif %}").render(),
      "true"
    )
  }

  test("greater or equal: 2 >= 2") {
    assertEquals(
      Template.parse("{% if 2 >= 2 %}true{% endif %}").render(),
      "true"
    )
  }

  test("less or equal: 1 <= 2") {
    assertEquals(
      Template.parse("{% if 1 <= 2 %}true{% endif %}").render(),
      "true"
    )
  }

  // ===== String comparisons =====

  test("string equality: 'test' == 'test'") {
    assertEquals(
      Template.parse("{% if 'test' == 'test' %}true{% endif %}").render(),
      "true"
    )
  }

  test("string inequality: 'test' != 'other'") {
    assertEquals(
      Template.parse("{% if 'test' != 'other' %}true{% endif %}").render(),
      "true"
    )
  }

  // ===== Variable comparisons =====

  test("variable equality: var == 'hello' with var='hello'") {
    val vars = new HashMap[String, Any]()
    vars.put("var", "hello")
    assertEquals(
      Template.parse("{% if var == 'hello' %}true{% endif %}").render(vars),
      "true"
    )
  }

  test("nil equality: var == nil with no var set") {
    assertEquals(
      Template.parse("{% if var == nil %}true{% endif %}").render(),
      "true"
    )
  }

  // ===== Contains operator =====

  test("contains: string contains substring") {
    assertEquals(
      Template.parse("{% if 'foobar' contains 'foo' %}true{% endif %}").render(),
      "true"
    )
  }

  test("contains: array contains element") {
    val vars  = new HashMap[String, Any]()
    val array = new ArrayList[Any]()
    array.add(java.lang.Integer.valueOf(1))
    array.add(java.lang.Integer.valueOf(2))
    array.add(java.lang.Integer.valueOf(3))
    vars.put("array", array)
    assertEquals(
      Template.parse("{% if array contains 2 %}true{% endif %}").render(vars),
      "true"
    )
  }

  test("contains: string does not contain substring") {
    assertEquals(
      Template.parse("{% if 'foobar' contains 'xyz' %}false{% endif %}").render(),
      ""
    )
  }

  // ===== Logical operators =====

  test("and: true and true") {
    assertEquals(
      Template.parse("{% if true and true %}true{% endif %}").render(),
      "true"
    )
  }

  test("and: true and false goes to else") {
    assertEquals(
      Template.parse("{% if true and false %}true{% else %}false{% endif %}").render(),
      "false"
    )
  }

  test("or: false or true") {
    assertEquals(
      Template.parse("{% if false or true %}true{% endif %}").render(),
      "true"
    )
  }

  test("or: false or false goes to else") {
    assertEquals(
      Template.parse("{% if false or false %}true{% else %}false{% endif %}").render(),
      "false"
    )
  }

  // ===== Truthiness =====

  test("truthiness: nil is falsy") {
    assertEquals(
      Template.parse("{% if nil %}yes{% else %}no{% endif %}").render(),
      "no"
    )
  }

  test("truthiness: empty string is truthy in Liquid") {
    // In Liquid, only nil and false are falsy — empty string is truthy
    assertEquals(
      Template.parse("{% if '' %}yes{% else %}no{% endif %}").render(),
      "yes"
    )
  }

  test("truthiness: false is falsy") {
    assertEquals(
      Template.parse("{% if false %}yes{% else %}no{% endif %}").render(),
      "no"
    )
  }

  test("truthiness: true is truthy") {
    assertEquals(
      Template.parse("{% if true %}yes{% endif %}").render(),
      "yes"
    )
  }

  test("truthiness: string is truthy") {
    assertEquals(
      Template.parse("{% if 'hello' %}yes{% endif %}").render(),
      "yes"
    )
  }

  test("truthiness: 0 is truthy in Liquid") {
    // In Liquid, 0 is truthy (unlike many other languages)
    assertEquals(
      Template.parse("{% if 0 %}yes{% endif %}").render(),
      "yes"
    )
  }

  // ===== Operator precedence =====

  test("operator precedence: true or false and false evaluates to true") {
    // 'and' binds tighter than 'or', so: true or (false and false) => true
    assertEquals(
      Template.parse("{% if true or false and false %}true{% endif %}").render(),
      "true"
    )
  }

  test("multiple comparisons: 1 == 1 and 2 == 2") {
    assertEquals(
      Template.parse("{% if 1 == 1 and 2 == 2 %}true{% endif %}").render(),
      "true"
    )
  }

  // ===== Alternate syntax =====

  test("not equal with <>: 1 <> 2") {
    assertEquals(
      Template.parse("{% if 1 <> 2 %}true{% endif %}").render(),
      "true"
    )
  }
}
