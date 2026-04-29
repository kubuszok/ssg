/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../renderer/ComboMultiMarkdownCompatibilitySpecTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package core
package test

import ssg.md.Nullable
import ssg.md.html.HtmlRenderer
import ssg.md.parser.ParserEmulationProfile
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, DataSet, MutableDataSet }

import java.{ util => ju }
import scala.language.implicitConversions

/** MultiMarkdown compatibility spec test — parses core_multi_markdown_compatibility_spec.md with MULTI_MARKDOWN emulation profile.
  */
final class ComboMultiMarkdownCompatibilitySpecTest extends RendererSpecTestSuite {

  override def specResource: ResourceLocation = ComboMultiMarkdownCompatibilitySpecTest.RESOURCE_LOCATION

  override def defaultOptions: Nullable[DataHolder] = Nullable(ComboMultiMarkdownCompatibilitySpecTest.MERGED_OPTIONS)

  override def optionsMap: ju.Map[String, ? <: DataHolder] = CoreRendererOptions.OPTIONS_MAP

  override def knownFailures: Set[String] = Set(
    "Block quote parsing - 9"
  )
}

object ComboMultiMarkdownCompatibilitySpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/core/test/core_multi_markdown_compatibility_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboMultiMarkdownCompatibilitySpecTest], SPEC_RESOURCE)

  val OPTIONS: DataHolder = new MutableDataSet().setFrom(ParserEmulationProfile.MULTI_MARKDOWN).set(HtmlRenderer.INDENT_SIZE, 4).toImmutable

  /** Merged: CoreRendererOptions.BASE_OPTIONS + this test's OPTIONS */
  val MERGED_OPTIONS: DataHolder =
    DataSet.aggregate(Nullable(CoreRendererOptions.BASE_OPTIONS), Nullable(OPTIONS)).toImmutable
}
