/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-macros/.../ComboMacrosSpecTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
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
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.ast.KeepType
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.{ Arrays, HashMap }
import scala.language.implicitConversions

final class ComboMacrosSpecTest extends RendererSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboMacrosSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboMacrosSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboMacrosSpecTest.OPTIONS_MAP
}

object ComboMacrosSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/macros/test/ext_macros_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboMacrosSpecTest], SPEC_RESOURCE)
  // Note: original OPTIONS is not .toImmutable — but we call .toImmutable for consistency
  val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Arrays.asList(MacrosExtension.create(), GitLabExtension.create(), TablesExtension.create()))
    .set(GitLabExtension.RENDER_BLOCK_MATH, false)
    .set(GitLabExtension.RENDER_BLOCK_MERMAID, false)
    .set(GitLabExtension.DEL_PARSER, false)
    .set(GitLabExtension.INS_PARSER, false)
    .set(GitLabExtension.RENDER_VIDEO_IMAGES, false)
    .toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put("references-keep-first", new MutableDataSet().set(MacrosExtension.MACRO_DEFINITIONS_KEEP, KeepType.FIRST).toImmutable)
    map.put("references-keep-last", new MutableDataSet().set(MacrosExtension.MACRO_DEFINITIONS_KEEP, KeepType.LAST).toImmutable)
    map
  }
}
