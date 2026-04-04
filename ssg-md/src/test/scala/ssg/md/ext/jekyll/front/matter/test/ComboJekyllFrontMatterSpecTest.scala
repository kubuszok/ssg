/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-front-matter/.../ComboJekyllFrontMatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package jekyll
package front
package matter
package test

import ssg.md.Nullable
import ssg.md.ext.jekyll.front.matter.JekyllFrontMatterExtension
import ssg.md.parser.Parser
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Collections
import scala.language.implicitConversions

final class ComboJekyllFrontMatterSpecTest extends RendererSpecTestSuite {
  override def specResource:   ResourceLocation     = ComboJekyllFrontMatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder] = Nullable(ComboJekyllFrontMatterSpecTest.OPTIONS)
}

object ComboJekyllFrontMatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/jekyll/front/matter/test/ext_jekyll_front_matter_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboJekyllFrontMatterSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(JekyllFrontMatterExtension.create())).toImmutable
}
