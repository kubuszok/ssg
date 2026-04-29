/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/.../ComboEnumeratedReferenceTranslationFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package enumerated
package reference
package test

import ssg.md.Nullable
import ssg.md.ext.attributes.AttributesExtension
import ssg.md.ext.enumerated.reference.EnumeratedReferenceExtension
import ssg.md.parser.Parser
import ssg.md.test.util.{ FormatterSpecTestSuite, TranslationFormatterSpecTestSuite }
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Arrays
import scala.language.implicitConversions

final class ComboEnumeratedReferenceTranslationFormatterSpecTest extends TranslationFormatterSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboEnumeratedReferenceTranslationFormatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboEnumeratedReferenceTranslationFormatterSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboEnumeratedReferenceTranslationFormatterSpecTest.OPTIONS_MAP
  override def knownFailurePrefixes: Set[String] = Set("Enumerated Reference -")
}

object ComboEnumeratedReferenceTranslationFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/enumerated/reference/test/ext_enumerated_reference_translation_formatter_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboEnumeratedReferenceTranslationFormatterSpecTest], SPEC_RESOURCE)
  val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Arrays.asList(EnumeratedReferenceExtension.create(), AttributesExtension.create()))
    .toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = FormatterSpecTestSuite.placementAndSortOptions(
    Nullable(EnumeratedReferenceExtension.ENUMERATED_REFERENCES_KEEP),
    Nullable(EnumeratedReferenceExtension.ENUMERATED_REFERENCE_PLACEMENT),
    Nullable(EnumeratedReferenceExtension.ENUMERATED_REFERENCE_SORT)
  )
}
