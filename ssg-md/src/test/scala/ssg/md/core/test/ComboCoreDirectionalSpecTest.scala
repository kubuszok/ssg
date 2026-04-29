/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../renderer/ComboCoreDirectionalSpecTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package core
package test

import ssg.md.Nullable
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.test.util.{ RendererSpecTestSuite, TestUtils }
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, DataSet, MutableDataSet }

import java.{ util => ju }
import scala.language.implicitConversions

/** Core directional spec test — parses ast_spec.md with INLINE_DELIMITER_DIRECTIONAL_PUNCTUATIONS=true and validates each example's markdown->HTML rendering.
  */
final class ComboCoreDirectionalSpecTest extends RendererSpecTestSuite {

  override def specResource: ResourceLocation = ComboCoreDirectionalSpecTest.RESOURCE_LOCATION

  override def defaultOptions: Nullable[DataHolder] = Nullable(ComboCoreDirectionalSpecTest.MERGED_OPTIONS)

  override def optionsMap: ju.Map[String, ? <: DataHolder] = CoreRendererOptions.OPTIONS_MAP

  override def compoundSections: Boolean = false
}

object ComboCoreDirectionalSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/core/test/ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboCoreDirectionalSpecTest], SPEC_RESOURCE)

  val OPTIONS: DataHolder = new MutableDataSet()
    .set(HtmlRenderer.INDENT_SIZE, 0)
    .set(Parser.INLINE_DELIMITER_DIRECTIONAL_PUNCTUATIONS, true)
    .set(HtmlRenderer.PERCENT_ENCODE_URLS, true)
    .set(TestUtils.NO_FILE_EOL, false)
    .toImmutable

  /** Merged: CoreRendererOptions.BASE_OPTIONS + this test's OPTIONS */
  val MERGED_OPTIONS: DataHolder =
    DataSet.aggregate(Nullable(CoreRendererOptions.BASE_OPTIONS), Nullable(OPTIONS)).toImmutable
}
