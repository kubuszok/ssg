/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package sequence
package test

import ssg.md.util.misc.CharPredicate
import ssg.md.util.misc.CharPredicate.*

final class CharPredicateSuite extends munit.FunSuite {

  test("and") {
    val test1: CharPredicate = (value: Int) => value == 'a'
    val test2: CharPredicate = (value: Int) => value == 'b'

    assert((NONE.and(ALL)) eq NONE)
    assert((ALL.and(NONE)) eq NONE)
    assert((test1.and(NONE)) eq NONE)
    assert((NONE.and(test1)) eq NONE)

    assert((ALL.and(ALL)) eq ALL)
    assert((test1.and(ALL)) eq test1)
    assert((ALL.and(test1)) eq test1)

    assert((ALL.and(test2)) eq test2)
    assert((test2.and(ALL)) eq test2)

    assert(!test1.and(test2).test('a'))
    assert(!test1.and(test2).test('b'))
    assert(!test2.and(test1).test('a'))
    assert(!test2.and(test1).test('b'))
    assert(!test2.and(test2).test('a'))
    assert(!test1.and(test1).test('b'))

    assert(test1.and(test1).test('a'))
    assert(test2.and(test2).test('b'))
  }

  test("negate") {
    val test1: CharPredicate = (value: Int) => value == 'a'
    val test2: CharPredicate = (value: Int) => value == 'b'

    assert(NONE.negate() eq ALL)
    assert(ALL.negate() eq NONE)

    assert(!test1.negate().test('a'))
    assert(!test2.negate().test('b'))

    assert(test1.negate().test('b'))
    assert(test2.negate().test('a'))
  }

  test("or") {
    val test1: CharPredicate = (value: Int) => value == 'a'
    val test2: CharPredicate = (value: Int) => value == '0'

    assert((NONE.or(ALL)) eq ALL)
    assert((ALL.or(NONE)) eq ALL)
    assert((test1.or(ALL)) eq ALL)
    assert((ALL.or(test1)) eq ALL)

    assert((NONE.or(NONE)) eq NONE)
    assert((test1.or(NONE)) eq test1)
    assert((NONE.or(test1)) eq test1)

    assert((NONE.or(test2)) eq test2)
    assert((test2.or(NONE)) eq test2)

    assert(!test1.or(test2).test('c'))
    assert(!test1.or(test2).test('c'))
    assert(!test1.or(test2).test('c'))
    assert(!test2.or(test1).test('c'))
    assert(!test2.or(test1).test('c'))
    assert(!test2.or(test2).test('c'))
    assert(!test1.or(test1).test('c'))

    assert(test1.or(test2).test('a'))
    assert(test1.or(test2).test('0'))
    assert(test2.or(test1).test('a'))
    assert(test2.or(test1).test('0'))

    assert(!test2.or(test2).test('a'))
    assert(!test1.or(test1).test('0'))
  }

  test("testStandardOrAnyOf1") {
    assert(standardOrAnyOf(' ') eq SPACE)
    assert(standardOrAnyOf('\t') eq TAB)
    assert(standardOrAnyOf('\n') eq EOL)

    val test1 = standardOrAnyOf('a')
    val test2 = standardOrAnyOf('0')

    assert(test1.test('a'))
    assert(!test1.test('0'))

    assert(test2.test('0'))
    assert(!test2.test('a'))
  }

  test("testStandardOrAnyOf2") {
    assert(standardOrAnyOf(' ', ' ') eq SPACE)
    assert(standardOrAnyOf('\t', '\t') eq TAB)
    assert(standardOrAnyOf(' ', '\t') eq SPACE_TAB)
    assert(standardOrAnyOf('\t', ' ') eq SPACE_TAB)
    assert(standardOrAnyOf('\n', '\n') eq EOL)
    assert(standardOrAnyOf('\n', '\r') eq ANY_EOL)
    assert(standardOrAnyOf('\r', '\n') eq ANY_EOL)

    val test1 = standardOrAnyOf('a', 'b')
    val test2 = standardOrAnyOf('0', '1')

    assert(test1.test('a'))
    assert(test1.test('b'))
    assert(!test1.test('0'))
    assert(!test1.test('1'))

    assert(test2.test('0'))
    assert(test2.test('1'))
    assert(!test2.test('a'))
    assert(!test2.test('b'))
  }

  test("testStandardOrAnyOf3") {
    assert(standardOrAnyOf(' ', ' ', ' ') eq SPACE)
    assert(standardOrAnyOf('\t', '\t', '\t') eq TAB)
    assert(standardOrAnyOf('\n', '\n', '\n') eq EOL)
    assert(standardOrAnyOf('\n', '\n', '\r') eq ANY_EOL)
    assert(standardOrAnyOf('\n', '\r', '\n') eq ANY_EOL)
    assert(standardOrAnyOf('\r', '\n', '\n') eq ANY_EOL)
    assert(standardOrAnyOf('\n', '\r', '\r') eq ANY_EOL)
    assert(standardOrAnyOf('\r', '\n', '\r') eq ANY_EOL)
    assert(standardOrAnyOf('\r', '\r', '\n') eq ANY_EOL)

    val test1 = standardOrAnyOf('a', 'b', 'c')
    val test2 = standardOrAnyOf('0', '1', '2')

    assert(test1.test('a'))
    assert(test1.test('b'))
    assert(test1.test('c'))
    assert(!test1.test('0'))
    assert(!test1.test('1'))
    assert(!test1.test('2'))

    assert(test2.test('0'))
    assert(test2.test('1'))
    assert(test2.test('2'))
    assert(!test2.test('a'))
    assert(!test2.test('b'))
    assert(!test2.test('c'))
  }

  test("testStandardOrAnyOf4") {
    assert(standardOrAnyOf(' ', ' ', ' ', ' ') eq SPACE)
    assert(standardOrAnyOf('\t', '\t', '\t', '\t') eq TAB)
    assert(standardOrAnyOf('\n', '\n', '\n', '\n') eq EOL)

    var i = 0
    while (i < 16) {
      val c0 = if ((i & 1) != 0) ' ' else '\t'
      val c1 = if ((i & 2) != 0) ' ' else '\t'
      val c2 = if ((i & 4) != 0) ' ' else '\t'
      val c3 = if ((i & 8) != 0) ' ' else '\t'
      if (i == 0) assert(standardOrAnyOf(c0, c1, c2, c3) eq TAB)
      else if (i == 15) assert(standardOrAnyOf(c0, c1, c2, c3) eq SPACE)
      else assert(standardOrAnyOf(c0, c1, c2, c3) eq SPACE_TAB)
      i += 1
    }

    i = 0
    while (i < 16) {
      if (i != 0) {
        val c0 = if ((i & 1) != 0) '\n' else '\r'
        val c1 = if ((i & 2) != 0) '\n' else '\r'
        val c2 = if ((i & 4) != 0) '\n' else '\r'
        val c3 = if ((i & 8) != 0) '\n' else '\r'

        if (i == 15) assert(standardOrAnyOf(c0, c1, c2, c3) eq EOL)
        else assert(standardOrAnyOf(c0, c1, c2, c3) eq ANY_EOL)
      }
      i += 1
    }

    val test1 = standardOrAnyOf('a', 'b', 'c', 'd')
    val test2 = standardOrAnyOf('0', '1', '2', '3')

    @scala.annotation.nowarn("msg=unused")
    val test3 = standardOrAnyOf('a', 'b', 'd', 'd')

    assert(test1.test('a'))
    assert(test1.test('b'))
    assert(test1.test('c'))
    assert(test1.test('d'))
    assert(!test1.test('0'))
    assert(!test1.test('1'))
    assert(!test1.test('2'))
    assert(!test1.test('3'))

    assert(test2.test('0'))
    assert(test2.test('1'))
    assert(test2.test('2'))
    assert(test2.test('3'))
    assert(!test2.test('a'))
    assert(!test2.test('b'))
    assert(!test2.test('c'))
    assert(!test2.test('d'))

    val charCount = 4
    val chars     = new Array[Char](charCount)
    var j         = 0
    while (j < charCount) {
      chars(j) = randomAscii()
      j += 1
    }

    val text     = String.valueOf(chars)
    val charTest = anyOf(chars*)
    val seqTest  = anyOf(text)

    var k = 0
    while (k < 1000) {
      val c = randomChar()
      if (text.indexOf(c) != -1) {
        assert(charTest.test(c))
        assert(seqTest.test(c))
      } else {
        assert(!charTest.test(c))
        assert(!seqTest.test(c))
      }
      k += 1
    }
  }

  private def randomChar(): Char =
    (Character.MAX_CODE_POINT * Math.random()).toChar

  private def randomAscii(): Char =
    (128 * Math.random()).toChar

  test("anyOfChars") {
    assert(anyOf() eq NONE)
    assert(anyOf("") eq NONE)
    assert(anyOf(' ') eq SPACE)
    assert(anyOf(' ', ' ') eq SPACE)
    assert(anyOf(' ', ' ', ' ', ' ') eq SPACE)
    assert(!(anyOf(' ', ' ', ' ', ' ', ' ') eq SPACE))
    assert(anyOf('\t') eq TAB)
    assert(anyOf('\t', '\t') eq TAB)
    assert(anyOf('\t', '\t', '\t', '\t') eq TAB)
    assert(!(anyOf('\t', '\t', '\t', '\t', '\t') eq TAB))
    assert(anyOf('\n') eq EOL)
    assert(anyOf('\n', '\n') eq EOL)
    assert(anyOf('\n', '\n', '\n', '\n') eq EOL)
    assert(!(anyOf('\n', '\n', '\n', '\n', '\n') eq EOL))

    var i = 0
    while (i < 16) {
      val c0 = if ((i & 1) != 0) ' ' else '\t'
      val c1 = if ((i & 2) != 0) ' ' else '\t'
      val c2 = if ((i & 4) != 0) ' ' else '\t'
      val c3 = if ((i & 8) != 0) ' ' else '\t'
      if (i == 0) assert(anyOf(c0, c1, c2, c3) eq TAB)
      else if (i == 15) assert(anyOf(c0, c1, c2, c3) eq SPACE)
      else assert(anyOf(c0, c1, c2, c3) eq SPACE_TAB)
      i += 1
    }

    i = 0
    while (i < 16) {
      if (i != 0) {
        val c0 = if ((i & 1) != 0) '\n' else '\r'
        val c1 = if ((i & 2) != 0) '\n' else '\r'
        val c2 = if ((i & 4) != 0) '\n' else '\r'
        val c3 = if ((i & 8) != 0) '\n' else '\r'

        if (i == 15) assert(anyOf(c0, c1, c2, c3) eq EOL)
        else assert(anyOf(c0, c1, c2, c3) eq ANY_EOL)
      }
      i += 1
    }

    i = 0
    while (i < 500) {
      val chars = new Array[Char](i)
      var j     = 0
      while (j < i) {
        chars(j) = randomAscii()
        j += 1
      }

      val text     = String.valueOf(chars)
      val charTest = anyOf(chars*)
      val seqTest  = anyOf(text)

      var k = 0
      while (k < 1000) {
        val c = randomChar()
        if (text.indexOf(c) != -1) {
          assert(charTest.test(c))
          assert(seqTest.test(c))
        } else {
          assert(!charTest.test(c))
          assert(!seqTest.test(c))
        }
        k += 1
      }
      i += 1
    }

    i = 0
    while (i < 1000) {
      val chars = new Array[Char](i)
      var j     = 0
      while (j < i) {
        chars(j) = randomChar()
        j += 1
      }

      val text     = String.valueOf(chars)
      val charTest = anyOf(chars*)
      val seqTest  = anyOf(text)

      var k = 0
      while (k < 1000) {
        val c = randomChar()
        if (text.indexOf(c) != -1) {
          assert(charTest.test(c))
          assert(seqTest.test(c))
        } else {
          assert(!charTest.test(c))
          assert(!seqTest.test(c))
        }
        k += 1
      }
      i += 1
    }
  }

  private def allIn(text: String, predicate: CharPredicate): Boolean = {
    val iMax = text.length
    var i    = 0
    while (i < iMax) {
      if (!predicate.test(text.charAt(i))) return false
      i += 1
    }
    true
  }

  test("testMisc") {
    assert(allIn(" \t\r\n\u00A0", WHITESPACE_NBSP))
    assert(allIn(SequenceUtils.WHITESPACE_NBSP, WHITESPACE_NBSP))
    assert(allIn(SequenceUtils.WHITESPACE, WHITESPACE_NBSP))
  }
}
