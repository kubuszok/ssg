/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../ComboCoreFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package test
package util
package formatter

import ssg.md.test.util.FormatterSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation

final class ComboCoreFormatterSpecTest extends FormatterSpecTestSuite {
  override def specResource: ResourceLocation = ComboCoreFormatterSpecTest.RESOURCE_LOCATION

  // Pre-existing bugs: ListSpacing Nullable cast, IndexedIterable cast, heading equalize issues
  override def knownFailurePrefixes: Set[String] = Set(
    "Block Quotes -",
    "Format Control -",
    "Format Conversion -",
    "Formatter -",
    "Headings -",
    "Headings - Prefer ATX -",
    "Headings - Prefer Setext -",
    "Issue - 271 -",
    "Lists -",
    "MdNav - 770 -",
    "Reference Placement -"
  )
}

object ComboCoreFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/test/util/formatter/core_formatter_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboCoreFormatterSpecTest], SPEC_RESOURCE)
}
