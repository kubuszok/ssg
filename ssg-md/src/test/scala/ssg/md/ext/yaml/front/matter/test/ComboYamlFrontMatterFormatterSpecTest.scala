/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-yaml-front-matter/.../ComboYamlFrontMatterFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package yaml
package front
package matter
package test

import ssg.md.Nullable
import ssg.md.ext.yaml.front.matter.YamlFrontMatterExtension
import ssg.md.formatter.Formatter
import ssg.md.parser.Parser
import ssg.md.test.util.FormatterSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Collections
import scala.language.implicitConversions

final class ComboYamlFrontMatterFormatterSpecTest extends FormatterSpecTestSuite {
  override def specResource:         ResourceLocation                       = ComboYamlFrontMatterFormatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions:       Nullable[DataHolder]                   = Nullable(ComboYamlFrontMatterFormatterSpecTest.OPTIONS)
  override def optionsMap:           java.util.Map[String, ? <: DataHolder] = ComboYamlFrontMatterFormatterSpecTest.OPTIONS_MAP
  override def knownFailurePrefixes: Set[String]                            = Set("Yaml Front Matter -")
}

object ComboYamlFrontMatterFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/yaml/front/matter/test/ext_yaml_front_matter_formatter_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboYamlFrontMatterFormatterSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(YamlFrontMatterExtension.create())).set(Parser.LISTS_AUTO_LOOSE, false).toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = FormatterSpecTestSuite.placementAndSortOptions(
    Nullable(Parser.REFERENCES_KEEP),
    Nullable(Formatter.REFERENCE_PLACEMENT),
    Nullable(Formatter.REFERENCE_SORT)
  )
}
