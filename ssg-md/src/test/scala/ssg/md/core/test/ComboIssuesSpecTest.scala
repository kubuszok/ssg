/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../renderer/ComboIssuesSpecTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package core
package test

import ssg.md.Nullable
import ssg.md.html.HtmlRenderer
import ssg.md.parser.{ Parser, ParserEmulationProfile }
import ssg.md.test.util.{ RendererSpecTestSuite, TestUtils }
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, DataSet, MutableDataSet }
import ssg.md.util.sequence.LineAppendable

import java.{ util => ju }
import scala.language.implicitConversions

/** Issues spec test — parses core_issues_ast_spec.md and validates each example's markdown->HTML rendering.
  */
final class ComboIssuesSpecTest extends RendererSpecTestSuite {

  override def specResource: ResourceLocation = ComboIssuesSpecTest.RESOURCE_LOCATION

  override def defaultOptions: Nullable[DataHolder] = Nullable(ComboIssuesSpecTest.MERGED_OPTIONS)

  override def optionsMap: ju.Map[String, ? <: DataHolder] = ComboIssuesSpecTest.MERGED_OPTIONS_MAP

  override def knownFailures: Set[String] = Set(
    "Core Issues Tests - 101 - 2"
  )
}

object ComboIssuesSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/core/test/core_issues_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboIssuesSpecTest], SPEC_RESOURCE)

  val OPTIONS: DataHolder = new MutableDataSet().set(HtmlRenderer.INDENT_SIZE, 2).set(HtmlRenderer.PERCENT_ENCODE_URLS, true).toImmutable

  /** Merged: CoreRendererOptions.BASE_OPTIONS + this test's OPTIONS */
  val MERGED_OPTIONS: DataHolder =
    DataSet.aggregate(Nullable(CoreRendererOptions.BASE_OPTIONS), Nullable(OPTIONS)).toImmutable

  /** This test's own options. */
  private val LOCAL_OPTIONS_MAP: ju.Map[String, DataHolder] = {
    val map = new ju.HashMap[String, DataHolder]()
    map.put("block-no-interrupt-paragraph", new MutableDataSet().set(Parser.BLOCK_QUOTE_INTERRUPTS_PARAGRAPH, false))
    map.put("fixed-indent", new MutableDataSet().setFrom(ParserEmulationProfile.FIXED_INDENT))
    map.put("html-comment-full-lines", new MutableDataSet().set(Parser.HTML_BLOCK_COMMENT_ONLY_FULL_LINE, true))
    map.put("allow-javascript", new MutableDataSet().set(HtmlRenderer.SUPPRESSED_LINKS, ""))
    map.put("pass-through", new MutableDataSet().set(HtmlRenderer.FORMAT_FLAGS, LineAppendable.F_PASS_THROUGH))
    map.put("strip-indent", new MutableDataSet().set(TestUtils.SOURCE_INDENT, "> > "))
    map.put("link-over-linkref", new MutableDataSet().set(Parser.LINK_TEXT_PRIORITY_OVER_LINK_REF, true))
    map.put("no-html-blocks", new MutableDataSet().set(Parser.HTML_BLOCK_PARSER, false))
    map.put(
      "sub-parse",
      new MutableDataSet().set(TestUtils.SOURCE_PREFIX, "Source Prefix\n").set(TestUtils.SOURCE_SUFFIX, "Source Suffix\n")
    )

    val customHtmlBlockTags = Parser.HTML_BLOCK_TAGS.get(Nullable.empty) :+ "warp10-warpscript-widget"
    map.put(
      "custom-html-block",
      new MutableDataSet()
        .set(Parser.HTML_BLOCK_TAGS, customHtmlBlockTags)
        .set(Parser.HTML_BLOCK_DEEP_PARSER, true)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS, false)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_FIRST_OPEN_TAG_ON_ONE_LINE, true)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS_PARTIAL_TAG, false)
    )
    map.put(
      "deep-html-parser",
      new MutableDataSet()
        .set(Parser.HTML_BLOCK_DEEP_PARSER, true)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS, false)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_FIRST_OPEN_TAG_ON_ONE_LINE, true)
        .set(Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS_PARTIAL_TAG, false)
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
