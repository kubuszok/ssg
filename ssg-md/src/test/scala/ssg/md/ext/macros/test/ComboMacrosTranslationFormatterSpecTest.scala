/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-macros/.../ComboMacrosTranslationFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package macros
package test

import ssg.md.Nullable
import ssg.md.ext.gitlab.GitLabExtension
import ssg.md.ext.macros.MacrosExtension
import ssg.md.ext.tables.TablesExtension
import ssg.md.parser.Parser
import ssg.md.test.util.{ FormatterSpecTestSuite, TranslationFormatterSpecTestSuite }
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Arrays
import scala.language.implicitConversions

final class ComboMacrosTranslationFormatterSpecTest extends TranslationFormatterSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboMacrosTranslationFormatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboMacrosTranslationFormatterSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboMacrosTranslationFormatterSpecTest.OPTIONS_MAP
  override def knownFailurePrefixes: Set[String] = Set("Macros -")
}

object ComboMacrosTranslationFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/macros/test/ext_macros_translation_formatter_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboMacrosTranslationFormatterSpecTest], SPEC_RESOURCE)
  val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Arrays.asList(MacrosExtension.create(), GitLabExtension.create(), TablesExtension.create()))
    .set(GitLabExtension.RENDER_BLOCK_MATH, false)
    .set(GitLabExtension.RENDER_BLOCK_MERMAID, false)
    .set(GitLabExtension.DEL_PARSER, false)
    .set(GitLabExtension.INS_PARSER, false)
    .set(GitLabExtension.RENDER_VIDEO_IMAGES, false)
    .toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = FormatterSpecTestSuite.placementAndSortOptions(
    Nullable(MacrosExtension.MACRO_DEFINITIONS_KEEP),
    Nullable(MacrosExtension.MACRO_DEFINITIONS_PLACEMENT),
    Nullable(MacrosExtension.MACRO_DEFINITIONS_SORT)
  )
}
