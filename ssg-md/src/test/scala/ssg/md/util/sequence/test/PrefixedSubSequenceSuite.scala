/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package sequence
package test

import scala.language.implicitConversions

final class PrefixedSubSequenceSuite extends munit.FunSuite {

  private val sequence  = BasedSequence.of("abcdefghi").subSequence(3, 6)
  private val substring = PrefixedSubSequence.prefixOf("0123", sequence)

  test("testLength") {
    assertEquals(substring.length(), 7)
  }

  test("testCharAt") {
    assertEquals(substring.charAt(0), '0')
    assertEquals(substring.charAt(1), '1')
    assertEquals(substring.charAt(2), '2')
    assertEquals(substring.charAt(3), '3')
    assertEquals(substring.charAt(4), 'd')
    assertEquals(substring.charAt(5), 'e')
    assertEquals(substring.charAt(6), 'f')
  }

  test("testSubSequence") {
    assertEquals(substring.subSequence(0, 1).toString, "0")
    assertEquals(substring.subSequence(1, 2).toString, "1")
    assertEquals(substring.subSequence(2, 3).toString, "2")
    assertEquals(substring.subSequence(3, 4).toString, "3")
    assertEquals(substring.subSequence(4, 5).toString, "d")
    assertEquals(substring.subSequence(5, 6).toString, "e")
    assertEquals(substring.subSequence(6, 7).toString, "f")
    assertEquals(substring.subSequence(0, 4).toString, "0123")
    assertEquals(substring.subSequence(0, 7).toString, "0123def")
    assertEquals(substring.subSequence(3, 6).toString, "3de")
    assertEquals(substring.subSequence(4, 7).toString, "def")
  }
}
