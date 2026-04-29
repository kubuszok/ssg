/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-abbreviation/.../ComboAbbreviationFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package abbreviation
package test

import ssg.md.Nullable
import ssg.md.ext.abbreviation.AbbreviationExtension
import ssg.md.parser.Parser
import ssg.md.test.util.FormatterSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Collections
import scala.language.implicitConversions

final class ComboAbbreviationFormatterSpecTest extends FormatterSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboAbbreviationFormatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboAbbreviationFormatterSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboAbbreviationFormatterSpecTest.OPTIONS_MAP
  override def knownFailurePrefixes: Set[String] = Set("Abbreviation -")
}

object ComboAbbreviationFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/abbreviation/test/ext_abbreviation_formatter_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboAbbreviationFormatterSpecTest], SPEC_RESOURCE)
  val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Collections.singleton(AbbreviationExtension.create()))
    .set(Parser.LISTS_AUTO_LOOSE, false)
    .toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = FormatterSpecTestSuite.placementAndSortOptions(
    Nullable(AbbreviationExtension.ABBREVIATIONS_KEEP),
    Nullable(AbbreviationExtension.ABBREVIATIONS_PLACEMENT),
    Nullable(AbbreviationExtension.ABBREVIATIONS_SORT)
  )
}
