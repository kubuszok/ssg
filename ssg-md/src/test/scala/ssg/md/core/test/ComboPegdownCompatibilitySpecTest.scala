/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../renderer/ComboPegdownCompatibilitySpecTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
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

/** Pegdown compatibility spec test — parses core_pegdown_compatibility_spec.md with PEGDOWN_STRICT emulation profile.
  */
final class ComboPegdownCompatibilitySpecTest extends RendererSpecTestSuite {

  override def specResource: ResourceLocation = ComboPegdownCompatibilitySpecTest.RESOURCE_LOCATION

  override def defaultOptions: Nullable[DataHolder] = Nullable(ComboPegdownCompatibilitySpecTest.MERGED_OPTIONS)

  override def optionsMap: ju.Map[String, ? <: DataHolder] = ComboPegdownCompatibilitySpecTest.MERGED_OPTIONS_MAP

  override def knownFailures: Set[String] = Set(
    "List Item Indent Handling - 8",
    "Block quote parsing - 5",
    "Block quote parsing - 10",
    "Block quote parsing - 11"
  )
}

object ComboPegdownCompatibilitySpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/core/test/core_pegdown_compatibility_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboPegdownCompatibilitySpecTest], SPEC_RESOURCE)

  val OPTIONS: DataHolder = new MutableDataSet().setFrom(ParserEmulationProfile.PEGDOWN_STRICT).set(HtmlRenderer.INDENT_SIZE, 2).set(HtmlRenderer.PERCENT_ENCODE_URLS, true).toImmutable

  /** Merged: CoreRendererOptions.BASE_OPTIONS + this test's OPTIONS */
  val MERGED_OPTIONS: DataHolder =
    DataSet.aggregate(Nullable(CoreRendererOptions.BASE_OPTIONS), Nullable(OPTIONS)).toImmutable

  /** This test's own options. */
  private val LOCAL_OPTIONS_MAP: ju.Map[String, DataHolder] = {
    val map = new ju.HashMap[String, DataHolder]()
    map.put("lists-item-indent", new MutableDataSet().set(Parser.LISTS_ITEM_INDENT, 2))
    map.put("blank-line-interrupts-html", new MutableDataSet().set(Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS, true))
    map.put(
      "lists-no-loose",
      new MutableDataSet()
        .set(Parser.HTML_BLOCK_DEEP_PARSE_BLANK_LINE_INTERRUPTS, false)
        .set(Parser.LISTS_AUTO_LOOSE, false)
        .set(Parser.LISTS_AUTO_LOOSE_ONE_LEVEL_LISTS, false)
        .set(Parser.LISTS_LOOSE_WHEN_PREV_HAS_TRAILING_BLANK_LINE, false)
        .set(Parser.LISTS_LOOSE_WHEN_LAST_ITEM_PREV_HAS_TRAILING_BLANK_LINE, false)
        .set(Parser.LISTS_LOOSE_WHEN_HAS_NON_LIST_CHILDREN, false)
        .set(Parser.LISTS_LOOSE_WHEN_BLANK_LINE_FOLLOWS_ITEM_PARAGRAPH, false)
        .set(Parser.LISTS_LOOSE_WHEN_HAS_LOOSE_SUB_ITEM, false)
        .set(Parser.LISTS_LOOSE_WHEN_HAS_TRAILING_BLANK_LINE, false)
        .set(Parser.LISTS_LOOSE_WHEN_CONTAINS_BLANK_LINE, false)
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
