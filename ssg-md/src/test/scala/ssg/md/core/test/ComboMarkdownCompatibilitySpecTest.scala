/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../renderer/ComboMarkdownCompatibilitySpecTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
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

/** Markdown compatibility spec test — parses core_markdown_compatibility_spec.md with MARKDOWN emulation profile.
  */
final class ComboMarkdownCompatibilitySpecTest extends RendererSpecTestSuite {

  override def specResource: ResourceLocation = ComboMarkdownCompatibilitySpecTest.RESOURCE_LOCATION

  override def defaultOptions: Nullable[DataHolder] = Nullable(ComboMarkdownCompatibilitySpecTest.MERGED_OPTIONS)

  override def optionsMap: ju.Map[String, ? <: DataHolder] = CoreRendererOptions.OPTIONS_MAP

  override def knownFailures: Set[String] = Set(
    "List Item Indent Handling - 15"
  )
}

object ComboMarkdownCompatibilitySpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/core/test/core_markdown_compatibility_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboMarkdownCompatibilitySpecTest], SPEC_RESOURCE)

  val OPTIONS: DataHolder = new MutableDataSet().setFrom(ParserEmulationProfile.MARKDOWN).set(HtmlRenderer.INDENT_SIZE, 4).toImmutable

  /** Merged: CoreRendererOptions.BASE_OPTIONS + this test's OPTIONS */
  val MERGED_OPTIONS: DataHolder =
    DataSet.aggregate(Nullable(CoreRendererOptions.BASE_OPTIONS), Nullable(OPTIONS)).toImmutable
}
