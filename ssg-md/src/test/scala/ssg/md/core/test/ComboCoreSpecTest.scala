/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../renderer/ComboCoreSpecTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package core
package test

import ssg.md.Nullable
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.test.util.{ RendererSpecTestSuite, TestUtils }
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import scala.language.implicitConversions

/** Core CommonMark spec test — parses ast_spec.md and validates each example's markdown→HTML rendering.
  */
final class ComboCoreSpecTest extends RendererSpecTestSuite {

  override def specResource: ResourceLocation = ComboCoreSpecTest.RESOURCE_LOCATION

  override def defaultOptions: Nullable[DataHolder] = ComboCoreSpecTest.OPTIONS

  override def compoundSections: Boolean = false
}

object ComboCoreSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/core/test/ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboCoreSpecTest], SPEC_RESOURCE)

  val OPTIONS: DataHolder = new MutableDataSet()
    .set(HtmlRenderer.INDENT_SIZE, java.lang.Integer.valueOf(0))
    .set(Parser.INLINE_DELIMITER_DIRECTIONAL_PUNCTUATIONS, java.lang.Boolean.valueOf(false))
    .set(HtmlRenderer.PERCENT_ENCODE_URLS, java.lang.Boolean.valueOf(true))
    .set(TestUtils.NO_FILE_EOL, java.lang.Boolean.valueOf(false))
    .toImmutable()
}
