/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package util
package sequence
package test

import ssg.md.util.misc.CharPredicate.SPACE

final class SequenceUtilsSuite extends munit.FunSuite {

  test("lastIndexOfAnyNot") {
    assertEquals(SequenceUtils.lastIndexOfAnyNot("0123456789  2345", SPACE, 0, 17), 15)
    assertEquals(SequenceUtils.lastIndexOfAnyNot("0123456789  2345", SPACE, 0, 16), 15)
    assertEquals(SequenceUtils.lastIndexOfAnyNot("0123456789  2344", SPACE, 0, 15), 15)
    assertEquals(SequenceUtils.lastIndexOfAnyNot("0123456789  2343", SPACE, 0, 14), 14)
    assertEquals(SequenceUtils.lastIndexOfAnyNot("0123456789  2342", SPACE, 0, 13), 13)
    assertEquals(SequenceUtils.lastIndexOfAnyNot("0123456789  2342", SPACE, 0, 12), 12)
    assertEquals(SequenceUtils.lastIndexOfAnyNot("0123456789  2342", SPACE, 0, 11), 9)
  }

  test("countTrailing") {
    assertEquals(SequenceUtils.countTrailing("0123456789  2345", SPACE, 0, 17), 0)
    assertEquals(SequenceUtils.countTrailing("0123456789  2345", SPACE, 0, 16), 0)
    assertEquals(SequenceUtils.countTrailing("0123456789  2344", SPACE, 0, 15), 0)
    assertEquals(SequenceUtils.countTrailing("0123456789  2343", SPACE, 0, 14), 0)
    assertEquals(SequenceUtils.countTrailing("0123456789  2342", SPACE, 0, 13), 0)
    assertEquals(SequenceUtils.countTrailing("0123456789  2342", SPACE, 0, 12), 2)
    assertEquals(SequenceUtils.countTrailing("0123456789  2342", SPACE, 0, 11), 1)
    assertEquals(SequenceUtils.countTrailing("0123456789  2342", SPACE, 0, 10), 0)
  }
}
