/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass
package parse

final class ParserSuite extends munit.FunSuite {

  test("parseIdentifier accepts basic identifier") {
    assertEquals(Parser.parseIdentifier("foo"), "foo")
    assertEquals(Parser.parseIdentifier("foo-bar"), "foo-bar")
    assertEquals(Parser.parseIdentifier("_private"), "_private")
    assertEquals(Parser.parseIdentifier("--custom"), "--custom")
  }

  test("parseIdentifier handles single dash") {
    assertEquals(Parser.parseIdentifier("-foo"), "-foo")
    assertEquals(Parser.parseIdentifier("-webkit-transform"), "-webkit-transform")
  }

  test("parseIdentifier rejects invalid identifiers") {
    assert(!Parser.isIdentifier(""))
    assert(!Parser.isIdentifier("123abc"))
    assert(!Parser.isIdentifier(" foo"))
    assert(!Parser.isIdentifier("foo bar"))
  }

  test("parseIdentifier accepts unicode") {
    assert(Parser.isIdentifier("café"))
    assert(Parser.isIdentifier("über"))
  }

  test("isIdentifier validates") {
    assert(Parser.isIdentifier("foo"))
    assert(Parser.isIdentifier("foo-bar"))
    assert(!Parser.isIdentifier("123"))
    assert(!Parser.isIdentifier(""))
  }

  test("isVariableDeclarationLike detects variables") {
    assert(Parser.isVariableDeclarationLike("$foo: 1"))
    assert(Parser.isVariableDeclarationLike("$bar:1px"))
    assert(Parser.isVariableDeclarationLike("$baz : red"))
    assert(!Parser.isVariableDeclarationLike("foo: 1"))
    assert(!Parser.isVariableDeclarationLike("$foo"))
    assert(!Parser.isVariableDeclarationLike("$"))
  }
}
