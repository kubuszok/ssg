/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../renderer/ComboGitHubCompatibilitySpecTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package core
package test

import ssg.md.Nullable
import ssg.md.html.HtmlRenderer
import ssg.md.parser.{ Parser, ParserEmulationProfile }
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, DataSet, MutableDataSet }

import java.{ util => ju }
import scala.language.implicitConversions

/** GitHub document compatibility spec test — parses core_gfm_doc_compatibility_spec.md and validates each example's markdown->HTML rendering with GITHUB_DOC emulation profile.
  */
final class ComboGitHubCompatibilitySpecTest extends RendererSpecTestSuite {

  override def specResource: ResourceLocation = ComboGitHubCompatibilitySpecTest.RESOURCE_LOCATION

  override def defaultOptions: Nullable[DataHolder] = Nullable(ComboGitHubCompatibilitySpecTest.MERGED_OPTIONS)

  override def optionsMap: ju.Map[String, ? <: DataHolder] = ComboGitHubCompatibilitySpecTest.MERGED_OPTIONS_MAP

  override def knownFailures: Set[String] = Set(
    "List Item Indent Handling - 11",
    "List Item Indent Handling - 12",
    "List Item Indent Handling - 20",
    "List Item Indent Handling - 21",
    "List Item Indent Handling - 24",
    "List Item Indent Handling - 26",
    "Block quote parsing - 7",
    "Block quote parsing - 9",
    "Block quote parsing - 11",
    "73, Can't nest code blocks in ordered list - 1",
    "73, Can't nest code blocks in ordered list - 2"
  )
}

object ComboGitHubCompatibilitySpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/core/test/core_gfm_doc_compatibility_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboGitHubCompatibilitySpecTest], SPEC_RESOURCE)

  val OPTIONS: DataHolder = new MutableDataSet()
    .setFrom(ParserEmulationProfile.GITHUB_DOC)
    // .set(Parser.THEMATIC_BREAK_RELAXED_START, true)
    .set(HtmlRenderer.INDENT_SIZE, 4)
    .set(HtmlRenderer.RENDER_HEADER_ID, true)
    .set(HtmlRenderer.SOFT_BREAK, " ")
    .toImmutable

  /** Merged: CoreRendererOptions.BASE_OPTIONS + this test's OPTIONS */
  val MERGED_OPTIONS: DataHolder =
    DataSet.aggregate(Nullable(CoreRendererOptions.BASE_OPTIONS), Nullable(OPTIONS)).toImmutable

  /** This test's own options. */
  private val LOCAL_OPTIONS_MAP: ju.Map[String, DataHolder] = {
    val map = new ju.HashMap[String, DataHolder]()
    map.put(
      "no-loose-non-list-children",
      new MutableDataSet().set(Parser.LISTS_LOOSE_WHEN_HAS_NON_LIST_CHILDREN, false).set(Parser.LISTS_LOOSE_WHEN_BLANK_LINE_FOLLOWS_ITEM_PARAGRAPH, false)
    )
    map
  }

  /** Merged: CoreRendererOptions.OPTIONS_MAP + this test's LOCAL_OPTIONS_MAP */
  val MERGED_OPTIONS_MAP: ju.Map[String, DataHolder] = {
    val map = new ju.HashMap[String, DataHolder](CoreRendererOptions.OPTIONS_MAP)
    map.putAll(LOCAL_OPTIONS_MAP)
    map
  }
}
