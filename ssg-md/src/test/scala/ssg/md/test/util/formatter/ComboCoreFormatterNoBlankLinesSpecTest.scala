/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../ComboCoreFormatterNoBlankLinesSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package test
package util
package formatter

import ssg.md.Nullable
import ssg.md.parser.Parser
import ssg.md.test.util.FormatterSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import scala.language.implicitConversions

final class ComboCoreFormatterNoBlankLinesSpecTest extends FormatterSpecTestSuite {
  override def specResource:   ResourceLocation     = ComboCoreFormatterNoBlankLinesSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder] = Nullable(ComboCoreFormatterNoBlankLinesSpecTest.OPTIONS)
  // Pre-existing formatter bugs
  override def knownFailurePrefixes: Set[String] = Set(
    "Block Quotes -", "Empty List Items -", "Format Control -", "Format Conversion -",
    "Formatter -", "HTML Blocks -", "Headings -", "Lists -", "Reference Placement -"
  )
}

object ComboCoreFormatterNoBlankLinesSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/test/util/formatter/core_formatter_no_blanklines_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboCoreFormatterNoBlankLinesSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.BLANK_LINES_IN_AST, false).toImmutable
}
