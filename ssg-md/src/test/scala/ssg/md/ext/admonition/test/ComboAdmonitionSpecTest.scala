/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-admonition/.../ComboAdmonitionSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package admonition
package test

import ssg.md.Nullable
import ssg.md.ext.admonition.AdmonitionExtension
import ssg.md.ext.tables.TablesExtension
import ssg.md.parser.Parser
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{DataHolder, MutableDataSet}

import java.util.{Arrays, HashMap}
import scala.language.implicitConversions

final class ComboAdmonitionSpecTest extends RendererSpecTestSuite {
  override def specResource: ResourceLocation = ComboAdmonitionSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder] = Nullable(ComboAdmonitionSpecTest.OPTIONS)
  override def optionsMap: java.util.Map[String, ? <: DataHolder] = ComboAdmonitionSpecTest.OPTIONS_MAP
}

object ComboAdmonitionSpecTest {
  val SPEC_RESOURCE: String = "/ssg/md/ext/admonition/test/ext_admonition_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboAdmonitionSpecTest], SPEC_RESOURCE)
  val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Arrays.asList(AdmonitionExtension.create(), TablesExtension.create()))
    .toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put("no-lazy-continuation", new MutableDataSet().set(AdmonitionExtension.ALLOW_LAZY_CONTINUATION, false).toImmutable)
    map.put("no-lead-space", new MutableDataSet().set(AdmonitionExtension.ALLOW_LEADING_SPACE, false).toImmutable)
    map.put("intellij", new MutableDataSet().set(Parser.INTELLIJ_DUMMY_IDENTIFIER, true).toImmutable)
    map
  }
}
