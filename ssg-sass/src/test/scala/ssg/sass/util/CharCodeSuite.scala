/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package sass
package util

final class CharCodeSuite extends munit.FunSuite {

  test("ASCII letter constants are correct") {
    assertEquals(CharCode.$a, 0x61)
    assertEquals(CharCode.$z, 0x7a)
    assertEquals(CharCode.$A, 0x41)
    assertEquals(CharCode.$Z, 0x5a)
  }

  test("digit constants are correct") {
    assertEquals(CharCode.$0, 0x30)
    assertEquals(CharCode.$9, 0x39)
  }

  test("symbol constants are correct") {
    assertEquals(CharCode.$hash, '#'.toInt)
    assertEquals(CharCode.$dollar, '$'.toInt)
    assertEquals(CharCode.$at, '@'.toInt)
    assertEquals(CharCode.$colon, ':'.toInt)
    assertEquals(CharCode.$semicolon, ';'.toInt)
    assertEquals(CharCode.$lbrace, '{'.toInt)
    assertEquals(CharCode.$rbrace, '}'.toInt)
  }

  test("isAlphabetic identifies ASCII letters") {
    assert(CharCode.isAlphabetic('a'.toInt))
    assert(CharCode.isAlphabetic('Z'.toInt))
    assert(!CharCode.isAlphabetic('0'.toInt))
    assert(!CharCode.isAlphabetic(' '.toInt))
  }

  test("isDigit identifies ASCII digits") {
    assert(CharCode.isDigit('0'.toInt))
    assert(CharCode.isDigit('9'.toInt))
    assert(!CharCode.isDigit('a'.toInt))
  }

  test("isHex identifies hex digits") {
    assert(CharCode.isHex('0'.toInt))
    assert(CharCode.isHex('9'.toInt))
    assert(CharCode.isHex('a'.toInt))
    assert(CharCode.isHex('f'.toInt))
    assert(CharCode.isHex('A'.toInt))
    assert(CharCode.isHex('F'.toInt))
    assert(!CharCode.isHex('g'.toInt))
    assert(!CharCode.isHex('G'.toInt))
  }

  test("isNameStart identifies valid identifier start characters") {
    assert(CharCode.isNameStart('a'.toInt))
    assert(CharCode.isNameStart('_'.toInt))
    assert(CharCode.isNameStart(0x0080)) // non-ASCII
    assert(!CharCode.isNameStart('-'.toInt))
    assert(!CharCode.isNameStart('0'.toInt))
  }

  test("isName identifies valid identifier body characters") {
    assert(CharCode.isName('a'.toInt))
    assert(CharCode.isName('_'.toInt))
    assert(CharCode.isName('-'.toInt))
    assert(CharCode.isName('0'.toInt))
    assert(!CharCode.isName(' '.toInt))
  }

  test("isWhitespace identifies whitespace characters") {
    assert(CharCode.isWhitespace(' '.toInt))
    assert(CharCode.isWhitespace('\t'.toInt))
    assert(CharCode.isWhitespace('\n'.toInt))
    assert(CharCode.isWhitespace('\r'.toInt))
    assert(!CharCode.isWhitespace('a'.toInt))
  }

  test("isNewline identifies newline characters") {
    assert(CharCode.isNewline('\n'.toInt))
    assert(CharCode.isNewline('\r'.toInt))
    assert(CharCode.isNewline(CharCode.$ff))
    assert(!CharCode.isNewline(' '.toInt))
  }

  test("asHex converts hex digits correctly") {
    assertEquals(CharCode.asHex('0'.toInt), 0)
    assertEquals(CharCode.asHex('9'.toInt), 9)
    assertEquals(CharCode.asHex('a'.toInt), 10)
    assertEquals(CharCode.asHex('f'.toInt), 15)
    assertEquals(CharCode.asHex('A'.toInt), 10)
    assertEquals(CharCode.asHex('F'.toInt), 15)
  }

  test("hexCharFor converts values to hex digits") {
    assertEquals(CharCode.hexCharFor(0), '0'.toInt)
    assertEquals(CharCode.hexCharFor(9), '9'.toInt)
    assertEquals(CharCode.hexCharFor(10), 'a'.toInt)
    assertEquals(CharCode.hexCharFor(15), 'f'.toInt)
  }

  test("opposite returns matching brackets") {
    assertEquals(CharCode.opposite('('.toInt), ')'.toInt)
    assertEquals(CharCode.opposite(')'.toInt), '('.toInt)
    assertEquals(CharCode.opposite('['.toInt), ']'.toInt)
    assertEquals(CharCode.opposite('{'.toInt), '}'.toInt)
  }

  test("case conversion works") {
    assertEquals(CharCode.toUpperCase('a'.toInt), 'A'.toInt)
    assertEquals(CharCode.toLowerCase('A'.toInt), 'a'.toInt)
    assertEquals(CharCode.toUpperCase('0'.toInt), '0'.toInt) // non-letter unchanged
  }

  test("characterEqualsIgnoreCase compares correctly") {
    assert(CharCode.characterEqualsIgnoreCase('a'.toInt, 'A'.toInt))
    assert(CharCode.characterEqualsIgnoreCase('Z'.toInt, 'z'.toInt))
    assert(!CharCode.characterEqualsIgnoreCase('a'.toInt, 'b'.toInt))
  }

  test("combineSurrogates produces correct code point") {
    // U+10000 = high 0xD800 + low 0xDC00
    assertEquals(CharCode.combineSurrogates(0xd800, 0xdc00), 0x10000)
  }

  test("isPrivate identifies private Sass identifiers") {
    assert(CharCode.isPrivate("-foo"))
    assert(CharCode.isPrivate("_bar"))
    assert(!CharCode.isPrivate("baz"))
    assert(!CharCode.isPrivate(""))
  }
}
