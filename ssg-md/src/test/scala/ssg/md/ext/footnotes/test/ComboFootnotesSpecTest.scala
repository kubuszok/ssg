/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-footnotes/.../ComboFootnotesSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package footnotes
package test

import ssg.md.Nullable
import ssg.md.ext.footnotes.FootnoteExtension
import ssg.md.ext.tables.TablesExtension
import ssg.md.parser.Parser
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{DataHolder, MutableDataSet}

import java.util.{Arrays, HashMap}
import scala.language.implicitConversions

final class ComboFootnotesSpecTest extends RendererSpecTestSuite {
  override def specResource: ResourceLocation = ComboFootnotesSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder] = Nullable(ComboFootnotesSpecTest.OPTIONS)
  override def optionsMap: java.util.Map[String, ? <: DataHolder] = ComboFootnotesSpecTest.OPTIONS_MAP
}

object ComboFootnotesSpecTest {
  val SPEC_RESOURCE: String = "/ssg/md/ext/footnotes/test/ext_footnotes_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboFootnotesSpecTest], SPEC_RESOURCE)
  val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Arrays.asList(FootnoteExtension.create(), TablesExtension.create()))
    .toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put("custom", new MutableDataSet()
      .set(FootnoteExtension.FOOTNOTE_REF_PREFIX, "[")
      .set(FootnoteExtension.FOOTNOTE_REF_SUFFIX, "]")
      .set(FootnoteExtension.FOOTNOTE_BACK_REF_STRING, "&lt;back&gt;")
      .toImmutable)
    map.put("link-class-none", new MutableDataSet().set(FootnoteExtension.FOOTNOTE_LINK_REF_CLASS, "").toImmutable)
    map.put("link-class-text", new MutableDataSet().set(FootnoteExtension.FOOTNOTE_LINK_REF_CLASS, "text").toImmutable)
    map.put("back-link-class-none", new MutableDataSet().set(FootnoteExtension.FOOTNOTE_BACK_LINK_REF_CLASS, "").toImmutable)
    map.put("back-link-class-text", new MutableDataSet().set(FootnoteExtension.FOOTNOTE_BACK_LINK_REF_CLASS, "text").toImmutable)
    map.put("item-indent-8", new MutableDataSet().set(Parser.LISTS_ITEM_INDENT, 8).toImmutable)
    map.put("link-text-priority", new MutableDataSet().set(Parser.LINK_TEXT_PRIORITY_OVER_LINK_REF, true).toImmutable)
    map
  }
}
