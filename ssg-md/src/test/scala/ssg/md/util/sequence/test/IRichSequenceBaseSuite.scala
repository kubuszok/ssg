/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package sequence
package test

// TEST: complete tests for implementation
final class IRichSequenceBaseSuite extends munit.FunSuite {

  test("padStart") {
    assertEquals(RichSequence.of("").padStart(5).toString, "     ")
    assertEquals(RichSequence.of("a").padStart(5).toString, "    a")
    assertEquals(RichSequence.of("ab").padStart(5).toString, "   ab")
    assertEquals(RichSequence.of("abc").padStart(5).toString, "  abc")
    assertEquals(RichSequence.of("abcd").padStart(5).toString, " abcd")
    assertEquals(RichSequence.of("abcde").padStart(5).toString, "abcde")
    assertEquals(RichSequence.of("abcdef").padStart(5).toString, "abcdef")
  }

  test("padEnd") {
    assertEquals(RichSequence.of("").padEnd(5).toString, "     ")
    assertEquals(RichSequence.of("a").padEnd(5).toString, "a    ")
    assertEquals(RichSequence.of("ab").padEnd(5).toString, "ab   ")
    assertEquals(RichSequence.of("abc").padEnd(5).toString, "abc  ")
    assertEquals(RichSequence.of("abcd").padEnd(5).toString, "abcd ")
    assertEquals(RichSequence.of("abcde").padEnd(5).toString, "abcde")
    assertEquals(RichSequence.of("abcdef").padEnd(5).toString, "abcdef")
  }

  test("padStartDash") {
    assertEquals(RichSequence.of("").padStart(5, '-').toString, "-----")
    assertEquals(RichSequence.of("a").padStart(5, '-').toString, "----a")
    assertEquals(RichSequence.of("ab").padStart(5, '-').toString, "---ab")
    assertEquals(RichSequence.of("abc").padStart(5, '-').toString, "--abc")
    assertEquals(RichSequence.of("abcd").padStart(5, '-').toString, "-abcd")
    assertEquals(RichSequence.of("abcde").padStart(5, '-').toString, "abcde")
    assertEquals(RichSequence.of("abcdef").padStart(5, '-').toString, "abcdef")
  }

  test("padEndDash") {
    assertEquals(RichSequence.of("").padEnd(5, '-').toString, "-----")
    assertEquals(RichSequence.of("a").padEnd(5, '-').toString, "a----")
    assertEquals(RichSequence.of("ab").padEnd(5, '-').toString, "ab---")
    assertEquals(RichSequence.of("abc").padEnd(5, '-').toString, "abc--")
    assertEquals(RichSequence.of("abcd").padEnd(5, '-').toString, "abcd-")
    assertEquals(RichSequence.of("abcde").padEnd(5, '-').toString, "abcde")
    assertEquals(RichSequence.of("abcdef").padEnd(5, '-').toString, "abcdef")
  }
}
