/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-enumerated-reference/.../ComboEnumeratedReferenceFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package enumerated
package reference
package test

import ssg.md.Nullable
import ssg.md.ext.enumerated.reference.EnumeratedReferenceExtension
import ssg.md.parser.Parser
import ssg.md.test.util.FormatterSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Collections
import scala.language.implicitConversions

final class ComboEnumeratedReferenceFormatterSpecTest extends FormatterSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboEnumeratedReferenceFormatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboEnumeratedReferenceFormatterSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboEnumeratedReferenceFormatterSpecTest.OPTIONS_MAP
  override def knownFailurePrefixes: Set[String] = Set("Enumerated Reference -")
}

object ComboEnumeratedReferenceFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/enumerated/reference/test/ext_enumerated_reference_formatter_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboEnumeratedReferenceFormatterSpecTest], SPEC_RESOURCE)
  val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Collections.singleton(EnumeratedReferenceExtension.create()))
    .set(Parser.LISTS_AUTO_LOOSE, false)
    .toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = FormatterSpecTestSuite.placementAndSortOptions(
    Nullable(EnumeratedReferenceExtension.ENUMERATED_REFERENCES_KEEP),
    Nullable(EnumeratedReferenceExtension.ENUMERATED_REFERENCE_PLACEMENT),
    Nullable(EnumeratedReferenceExtension.ENUMERATED_REFERENCE_SORT)
  )
}
