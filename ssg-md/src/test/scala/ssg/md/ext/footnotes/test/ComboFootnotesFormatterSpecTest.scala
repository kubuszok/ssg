/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-footnotes/.../ComboFootnotesFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package footnotes
package test

import ssg.md.Nullable
import ssg.md.ext.footnotes.FootnoteExtension
import ssg.md.parser.Parser
import ssg.md.test.util.FormatterSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Collections
import scala.language.implicitConversions

final class ComboFootnotesFormatterSpecTest extends FormatterSpecTestSuite {
  override def specResource:         ResourceLocation                       = ComboFootnotesFormatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions:       Nullable[DataHolder]                   = Nullable(ComboFootnotesFormatterSpecTest.OPTIONS)
  override def optionsMap:           java.util.Map[String, ? <: DataHolder] = ComboFootnotesFormatterSpecTest.OPTIONS_MAP
  override def knownFailurePrefixes: Set[String]                            = Set("Footnotes -", "Issue", "Placement Options -")
}

object ComboFootnotesFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/footnotes/test/ext_footnotes_formatter_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboFootnotesFormatterSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(FootnoteExtension.create())).set(Parser.LISTS_AUTO_LOOSE, false).toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = FormatterSpecTestSuite.placementAndSortOptions(
    Nullable(FootnoteExtension.FOOTNOTES_KEEP),
    Nullable(FootnoteExtension.FOOTNOTE_PLACEMENT),
    Nullable(FootnoteExtension.FOOTNOTE_SORT)
  )
}
