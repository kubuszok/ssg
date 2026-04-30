/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-definition/.../ComboDefinitionFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package definition
package test

import ssg.md.Nullable
import ssg.md.ext.definition.DefinitionExtension
import ssg.md.parser.Parser
import ssg.md.test.util.FormatterSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }
import ssg.md.util.format.options.DefinitionMarker

import java.util.{ Collections, HashMap }
import scala.language.implicitConversions

final class ComboDefinitionFormatterSpecTest extends FormatterSpecTestSuite {
  override def specResource:         ResourceLocation                       = ComboDefinitionFormatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions:       Nullable[DataHolder]                   = Nullable(ComboDefinitionFormatterSpecTest.OPTIONS)
  override def optionsMap:           java.util.Map[String, ? <: DataHolder] = ComboDefinitionFormatterSpecTest.OPTIONS_MAP
  override def knownFailurePrefixes: Set[String]                            = Set("Definition List Extension -", "Issue")
}

object ComboDefinitionFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/definition/test/ext_definition_formatter_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboDefinitionFormatterSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(DefinitionExtension.create())).set(Parser.LISTS_AUTO_LOOSE, false).toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put("marker-spaces-1", new MutableDataSet().set(DefinitionExtension.FORMAT_MARKER_SPACES, 1).toImmutable)
    map.put("marker-spaces-2", new MutableDataSet().set(DefinitionExtension.FORMAT_MARKER_SPACES, 2).toImmutable)
    map.put("marker-spaces-3", new MutableDataSet().set(DefinitionExtension.FORMAT_MARKER_SPACES, 3).toImmutable)
    map.put("marker-type-any", new MutableDataSet().set(DefinitionExtension.FORMAT_MARKER_TYPE, DefinitionMarker.ANY).toImmutable)
    map.put(
      "marker-type-colon",
      new MutableDataSet().set(DefinitionExtension.FORMAT_MARKER_TYPE, DefinitionMarker.COLON).toImmutable
    )
    map.put(
      "marker-type-tilde",
      new MutableDataSet().set(DefinitionExtension.FORMAT_MARKER_TYPE, DefinitionMarker.TILDE).toImmutable
    )
    map.put("no-blank-lines", new MutableDataSet().set(Parser.BLANK_LINES_IN_AST, false).toImmutable)
    map
  }
}
