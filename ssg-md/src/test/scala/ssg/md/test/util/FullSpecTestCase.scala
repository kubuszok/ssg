/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-test-util/src/main/java/com/vladsch/flexmark/test/util/FullSpecTestCase.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package test
package util

import ssg.md.test.util.spec.{ ResourceLocation, SpecReader }

// JUnit 4: abstract test case with @Test annotation — will need adaptation to munit later
abstract class FullSpecTestCase extends RenderingTestCase with SpecExampleProcessor {

  def create(location: ResourceLocation): DumpSpecReader =
    SpecReader.create(location, (stream, fileUrl) => new DumpSpecReader(stream, this, fileUrl, compoundSections()))

  protected def compoundSections(): Boolean = false

  protected def specResourceLocation: ResourceLocation

  protected def fullTestSpecStarting(): Unit = {}

  protected def fullTestSpecComplete(): Unit = {}

  // JUnit 4: @Test — will need adaptation to munit later
  def testSpecExample(): Unit = {
    val location = specResourceLocation
    if (location.isNull) {
      // skip
    } else {
      fullTestSpecStarting()
      val reader = create(location)
      reader.readExamples()
      fullTestSpecComplete()

      val actual   = reader.fullSpec
      val expected = reader.expectedFullSpec

      if (reader.fileUrl.nonEmpty) {
        assert(expected == actual, reader.fileUrl)
      } else {
        assert(expected == actual)
      }
    }
  }
}
