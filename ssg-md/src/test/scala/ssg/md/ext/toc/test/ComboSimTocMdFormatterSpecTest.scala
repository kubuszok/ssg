/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/.../ComboSimTocMdFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package toc
package test

import ssg.md.Nullable
import ssg.md.ext.toc.{ SimTocExtension, SimTocGenerateOnFormat, TocExtension }
import ssg.md.ext.toc.internal.TocOptions
import ssg.md.ext.typographic.TypographicExtension
import ssg.md.html.HtmlRenderer
import ssg.md.parser.{ Parser, ParserEmulationProfile }
import ssg.md.test.util.FormatterSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.{ Arrays, Collections, HashMap }
import scala.language.implicitConversions

final class ComboSimTocMdFormatterSpecTest extends FormatterSpecTestSuite {
  override def specResource:         ResourceLocation                       = ComboSimTocMdFormatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions:       Nullable[DataHolder]                   = Nullable(ComboSimTocMdFormatterSpecTest.OPTIONS)
  override def optionsMap:           java.util.Map[String, ? <: DataHolder] = ComboSimTocMdFormatterSpecTest.OPTIONS_MAP
  override def knownFailurePrefixes: Set[String]                            = Set("As Is -", "Issue -", "No Spacer -", "Remove -", "Spacer -", "Update -")
}

object ComboSimTocMdFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/toc/test/ext_simtoc_formatter_markdown_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboSimTocMdFormatterSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       =
    new MutableDataSet().set(HtmlRenderer.RENDER_HEADER_ID, true).set(Parser.EXTENSIONS, Collections.singletonList(SimTocExtension.create())).set(TocExtension.IS_HTML, false).toImmutable

  // Build TOC_OPTIONS equivalent: levels=2,3,4, title="Table of Contents", isTextOnly=false, isHtml=false
  private val TOC_OPTIONS: DataHolder = {
    val levels = TocOptions.getLevels(2, 3, 4)
    val opts   = TocOptions.DEFAULT.withLevels(levels).withTitle("Table of Contents").withIsTextOnly(false).withIsHtml(false)
    val ds     = new MutableDataSet()
    opts.setIn(ds)
    ds.toImmutable
  }

  // Empty title variant
  private val EMPTY_TOC_OPTIONS: DataHolder = {
    val levels = TocOptions.getLevels(2, 3, 4)
    val opts   = TocOptions.DEFAULT.withLevels(levels).withTitle("").withIsTextOnly(false).withIsHtml(false)
    val ds     = new MutableDataSet()
    opts.setIn(ds)
    ds.toImmutable
  }

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put("text-only", new MutableDataSet().set(TocExtension.IS_TEXT_ONLY, true).toImmutable)
    map.put("formatted", new MutableDataSet().set(TocExtension.IS_TEXT_ONLY, false).toImmutable)
    map.put("hierarchy", new MutableDataSet().set(TocExtension.LIST_TYPE, TocOptions.ListType.HIERARCHY).toImmutable)
    map.put("flat", new MutableDataSet().set(TocExtension.LIST_TYPE, TocOptions.ListType.FLAT).toImmutable)
    map.put("flat-reversed", new MutableDataSet().set(TocExtension.LIST_TYPE, TocOptions.ListType.FLAT_REVERSED).toImmutable)
    map.put("sorted", new MutableDataSet().set(TocExtension.LIST_TYPE, TocOptions.ListType.SORTED).toImmutable)
    map.put("sorted-reversed", new MutableDataSet().set(TocExtension.LIST_TYPE, TocOptions.ListType.SORTED_REVERSED).toImmutable)
    map.put("with-option-list", new MutableDataSet().set(TocExtension.AST_INCLUDE_OPTIONS, true).toImmutable)
    map.put(
      "typographic",
      new MutableDataSet().set(Parser.EXTENSIONS, Arrays.asList(SimTocExtension.create(), TypographicExtension.create())).toImmutable
    )
    map.put("numbered", new MutableDataSet().set(TocExtension.IS_NUMBERED, true).toImmutable)
    map.put("spacer", new MutableDataSet().set(TocExtension.BLANK_LINE_SPACER, true).toImmutable)
    map.put("github", new MutableDataSet().setFrom(ParserEmulationProfile.GITHUB_DOC).toImmutable)
    map.put("div-class", new MutableDataSet().set(TocExtension.DIV_CLASS, "content-class").toImmutable)
    map.put("list-class", new MutableDataSet().set(TocExtension.LIST_CLASS, "list-class").toImmutable)
    map.put(
      "on-format-as-is",
      new MutableDataSet().set(TocExtension.FORMAT_UPDATE_ON_FORMAT, SimTocGenerateOnFormat.AS_IS).toImmutable
    )
    map.put(
      "on-format-remove",
      new MutableDataSet().set(TocExtension.FORMAT_UPDATE_ON_FORMAT, SimTocGenerateOnFormat.REMOVE).toImmutable
    )
    map.put(
      "on-format-update",
      new MutableDataSet().set(TocExtension.FORMAT_UPDATE_ON_FORMAT, SimTocGenerateOnFormat.UPDATE).toImmutable
    )
    map.put("default-toc", TOC_OPTIONS)
    map.put("default-empty-toc", EMPTY_TOC_OPTIONS)
    map
  }
}
