/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../ComboCoreWrappingSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package test
package util
package formatter

import ssg.md.Nullable
import ssg.md.test.util.FormatterSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet, SharedDataKeys }

import scala.language.implicitConversions

final class ComboCoreWrappingSpecTest extends FormatterSpecTestSuite {
  override def specResource:   ResourceLocation     = ComboCoreWrappingSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder] = Nullable(ComboCoreWrappingSpecTest.OPTIONS)
  override def knownFailurePrefixes: Set[String] = Set("Wrap -", "Wrap - Delete Indent -", "Wrap - Images -", "Wrap - Links -", "Wrap - Restore Spaces -")
}

object ComboCoreWrappingSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/test/util/formatter/core_wrapping_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboCoreWrappingSpecTest], SPEC_RESOURCE)
  val OPTIONS: DataHolder = new MutableDataSet()
    .set(SharedDataKeys.RUNNING_TESTS, false)  // Set to true to get stdout printout of intermediate wrapping information
    .toImmutable
}
