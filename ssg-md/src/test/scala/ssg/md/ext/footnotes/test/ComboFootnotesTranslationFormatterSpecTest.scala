/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-footnotes/.../ComboFootnotesTranslationFormatterSpecTest.java
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
import ssg.md.test.util.{ FormatterSpecTestSuite, TranslationFormatterSpecTestSuite }
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Collections
import scala.language.implicitConversions

final class ComboFootnotesTranslationFormatterSpecTest extends TranslationFormatterSpecTestSuite {
  override def specResource:         ResourceLocation                       = ComboFootnotesTranslationFormatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions:       Nullable[DataHolder]                   = Nullable(ComboFootnotesTranslationFormatterSpecTest.OPTIONS)
  override def optionsMap:           java.util.Map[String, ? <: DataHolder] = ComboFootnotesTranslationFormatterSpecTest.OPTIONS_MAP
  override def knownFailurePrefixes: Set[String]                            = Set("Footnotes -", "Placement Options -")
}

object ComboFootnotesTranslationFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/footnotes/test/ext_footnotes_translation_formatter_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboFootnotesTranslationFormatterSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(FootnoteExtension.create())).toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = FormatterSpecTestSuite.placementAndSortOptions(
    Nullable(FootnoteExtension.FOOTNOTES_KEEP),
    Nullable(FootnoteExtension.FOOTNOTE_PLACEMENT),
    Nullable(FootnoteExtension.FOOTNOTE_SORT)
  )
}
