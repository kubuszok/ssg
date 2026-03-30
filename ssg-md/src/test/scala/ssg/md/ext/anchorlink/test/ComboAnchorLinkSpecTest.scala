/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-anchorlink/.../ComboAnchorLinkSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package anchorlink
package test

import ssg.md.Nullable
import ssg.md.ext.anchorlink.AnchorLinkExtension
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{DataHolder, MutableDataSet}

import java.util.{Collections, HashMap}
import scala.language.implicitConversions

final class ComboAnchorLinkSpecTest extends RendererSpecTestSuite {
  override def specResource: ResourceLocation = ComboAnchorLinkSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder] = Nullable(ComboAnchorLinkSpecTest.OPTIONS)
  override def optionsMap: java.util.Map[String, ? <: DataHolder] = ComboAnchorLinkSpecTest.OPTIONS_MAP
}

object ComboAnchorLinkSpecTest {
  val SPEC_RESOURCE: String = "/ssg/md/ext/anchorlink/test/ext_anchorlink_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboAnchorLinkSpecTest], SPEC_RESOURCE)
  val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Collections.singleton(AnchorLinkExtension.create()))
    .set(AnchorLinkExtension.ANCHORLINKS_ANCHOR_CLASS, "anchor")
    .set(AnchorLinkExtension.ANCHORLINKS_NO_BLOCK_QUOTE, true)
    .set(HtmlRenderer.RENDER_HEADER_ID, false)
    .set(HtmlRenderer.GENERATE_HEADER_ID, true)
    .toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put("no-wrap", new MutableDataSet().set(AnchorLinkExtension.ANCHORLINKS_WRAP_TEXT, false).toImmutable)
    map.put("set-name", new MutableDataSet().set(AnchorLinkExtension.ANCHORLINKS_SET_NAME, true).toImmutable)
    map.put("no-id", new MutableDataSet().set(AnchorLinkExtension.ANCHORLINKS_SET_ID, false).toImmutable)
    map.put("no-class", new MutableDataSet().set(AnchorLinkExtension.ANCHORLINKS_ANCHOR_CLASS, "").toImmutable)
    map.put("prefix-suffix", new MutableDataSet()
      .set(AnchorLinkExtension.ANCHORLINKS_TEXT_PREFIX, "<span class=\"anchor\">")
      .set(AnchorLinkExtension.ANCHORLINKS_TEXT_SUFFIX, "</span>")
      .toImmutable)
    map
  }
}
