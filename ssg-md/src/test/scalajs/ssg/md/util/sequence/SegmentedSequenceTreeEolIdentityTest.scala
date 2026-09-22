/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Scala.js: this row keeps the boxed-zero identity JLS 5.1.7 promises, so `SegmentedSequenceTreeTest`'s six eol-length
 * tests run here over that suite's own sequence construction (see EolLengthIdentityTests). */
package ssg
package md
package util
package sequence

final class SegmentedSequenceTreeEolIdentityTest extends munit.FunSuite with EolLengthIdentityTests {

  def basedSequenceOf(chars: CharSequence): BasedSequence = SegmentedSequenceTreeTest.basedSequenceOf(chars)
}
