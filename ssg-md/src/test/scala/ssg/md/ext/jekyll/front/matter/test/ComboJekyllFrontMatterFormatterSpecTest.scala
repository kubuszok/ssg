/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-front-matter/.../ComboJekyllFrontMatterFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package jekyll
package front
package matter
package test

import ssg.md.Nullable
import ssg.md.ext.jekyll.front.matter.JekyllFrontMatterExtension
import ssg.md.formatter.Formatter
import ssg.md.parser.Parser
import ssg.md.test.util.FormatterSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Collections
import scala.language.implicitConversions

final class ComboJekyllFrontMatterFormatterSpecTest extends FormatterSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboJekyllFrontMatterFormatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboJekyllFrontMatterFormatterSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboJekyllFrontMatterFormatterSpecTest.OPTIONS_MAP
  override def knownFailurePrefixes: Set[String] = Set("FlexmarkFrontMatter -", "JekyllFrontMatter -")
}

object ComboJekyllFrontMatterFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/jekyll/front/matter/test/ext_jekyll_front_matter_formatter_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboJekyllFrontMatterFormatterSpecTest], SPEC_RESOURCE)
  val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Collections.singleton(JekyllFrontMatterExtension.create()))
    .set(Parser.LISTS_AUTO_LOOSE, false)
    .toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = FormatterSpecTestSuite.placementAndSortOptions(
    Nullable(Parser.REFERENCES_KEEP),
    Nullable(Formatter.REFERENCE_PLACEMENT),
    Nullable(Formatter.REFERENCE_SORT)
  )
}
