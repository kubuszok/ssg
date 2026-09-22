/*
 * Ported from flexmark-java - https://github.com/vsch/flexmark-java
 * Original source: flexmark-util/src/test/java/com/vladsch/flexmark/util/sequence/BasedSequenceFullImplTest.java
 *   and SegmentedSequenceTreeTest.java (test_eolEndLength1..3, test_eolStartLength1..3)
 * Original license: BSD-2-Clause, Copyright (c) 2015-2016 Atlassian Pty Ltd, 2016-2018 Vladimir Schneider
 *
 * Migration notes:
 *   Origin: java writes `assertSame(0, sequence.eolEndLength(i))` — reference identity of two boxed
 *     zeros, which holds by JLS 5.1.7 (a boxed value in -128..127 is one object) and which Scala.js
 *     keeps (a boxed Int is the number itself). Scala Native boxes afresh at every conversion, so the
 *     assertion cannot hold there; the twelve tests are dropped from the generated suites
 *     (ssg-md/port/test.conf) and run from here on the JVM and Scala.js rows, over each suite's own
 *     sequence construction. Every assertion is java's, including the identity ones.
 */
package ssg
package md
package util
package sequence

trait EolLengthIdentityTests { this: munit.FunSuite =>

  def basedSequenceOf(chars: CharSequence): BasedSequence

  private def assertSame(expected: Int, actual: Int): Unit =
    assert(actual.asInstanceOf[java.lang.Integer] eq expected.asInstanceOf[java.lang.Integer])

  test("test_eolEndLength1") {
    val sequence = basedSequenceOf("\n1234\n6789\n")
    assertSame(0, sequence.eolEndLength(0))
    assertSame(0, sequence.eolEndLength(2))
    assertSame(0, sequence.eolEndLength(3))
    assertSame(0, sequence.eolEndLength(4))
    assertSame(0, sequence.eolEndLength(5))
    assertSame(0, sequence.eolEndLength(7))
    assertSame(0, sequence.eolEndLength(8))
    assertSame(0, sequence.eolEndLength(9))
    assertSame(0, sequence.eolEndLength(10))
    assertEquals(sequence.eolEndLength(1), 1)
    assertEquals(sequence.eolEndLength(6), 1)
    assertEquals(sequence.eolEndLength(11), 1)
  }

  test("test_eolEndLength2") {
    val sequence = basedSequenceOf("\r1234\r6789\r")
    assertSame(0, sequence.eolEndLength(0))
    assertEquals(sequence.eolEndLength(1), 1)
    assertSame(0, sequence.eolEndLength(2))
    assertSame(0, sequence.eolEndLength(3))
    assertSame(0, sequence.eolEndLength(4))
    assertSame(0, sequence.eolEndLength(5))
    assertEquals(sequence.eolEndLength(6), 1)
    assertSame(0, sequence.eolEndLength(7))
    assertSame(0, sequence.eolEndLength(8))
    assertSame(0, sequence.eolEndLength(9))
    assertSame(0, sequence.eolEndLength(10))
    assertEquals(sequence.eolEndLength(11), 1)
  }

  test("test_eolEndLength3") {
    val sequence = basedSequenceOf("\r\n234\r\n789\r\n")
    assertSame(0, sequence.eolEndLength(0))
    assertSame(0, sequence.eolEndLength(1))
    assertEquals(sequence.eolEndLength(2), 2)
    assertSame(0, sequence.eolEndLength(3))
    assertSame(0, sequence.eolEndLength(4))
    assertSame(0, sequence.eolEndLength(5))
    assertSame(0, sequence.eolEndLength(6))
    assertEquals(sequence.eolEndLength(7), 2)
    assertSame(0, sequence.eolEndLength(8))
    assertSame(0, sequence.eolEndLength(9))
    assertSame(0, sequence.eolEndLength(10))
    assertSame(0, sequence.eolEndLength(11))
    assertEquals(sequence.eolEndLength(12), 2)
  }

  test("test_eolStartLength1") {
    val sequence = basedSequenceOf("\n1234\n6789\n")
    assertEquals(sequence.eolStartLength(0), 1)
    assertSame(0, sequence.eolStartLength(1))
    assertSame(0, sequence.eolStartLength(2))
    assertSame(0, sequence.eolStartLength(3))
    assertSame(0, sequence.eolStartLength(4))
    assertEquals(sequence.eolStartLength(5), 1)
    assertSame(0, sequence.eolStartLength(6))
    assertSame(0, sequence.eolStartLength(7))
    assertSame(0, sequence.eolStartLength(8))
    assertSame(0, sequence.eolStartLength(9))
    assertEquals(sequence.eolStartLength(10), 1)
    assertSame(0, sequence.eolStartLength(11))
  }

  test("test_eolStartLength2") {
    val sequence = basedSequenceOf("\r1234\r6789\r")
    assertEquals(sequence.eolStartLength(0), 1)
    assertSame(0, sequence.eolStartLength(1))
    assertSame(0, sequence.eolStartLength(2))
    assertSame(0, sequence.eolStartLength(3))
    assertSame(0, sequence.eolStartLength(4))
    assertEquals(sequence.eolStartLength(5), 1)
    assertSame(0, sequence.eolStartLength(6))
    assertSame(0, sequence.eolStartLength(7))
    assertSame(0, sequence.eolStartLength(8))
    assertSame(0, sequence.eolStartLength(9))
    assertEquals(sequence.eolStartLength(10), 1)
    assertSame(0, sequence.eolStartLength(11))
  }

  test("test_eolStartLength3") {
    val sequence = basedSequenceOf("\r\n234\r\n789\r\n")
    assertEquals(sequence.eolStartLength(0), 2)
    assertSame(0, sequence.eolStartLength(1))
    assertSame(0, sequence.eolStartLength(2))
    assertSame(0, sequence.eolStartLength(3))
    assertSame(0, sequence.eolStartLength(4))
    assertEquals(sequence.eolStartLength(5), 2)
    assertSame(0, sequence.eolStartLength(6))
    assertSame(0, sequence.eolStartLength(7))
    assertSame(0, sequence.eolStartLength(8))
    assertSame(0, sequence.eolStartLength(9))
    assertEquals(sequence.eolStartLength(10), 2)
    assertSame(0, sequence.eolStartLength(11))
    assertSame(0, sequence.eolStartLength(12))
  }
}
