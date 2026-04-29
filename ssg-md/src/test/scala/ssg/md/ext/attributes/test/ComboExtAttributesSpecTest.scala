/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/.../ComboExtAttributesSpecTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package ext
package attributes
package test

import ssg.md.Nullable
import ssg.md.ext.anchorlink.AnchorLinkExtension
import ssg.md.ext.attributes.{ AttributesExtension, FencedCodeAddType }
import ssg.md.ext.definition.DefinitionExtension
import ssg.md.ext.emoji.EmojiExtension
import ssg.md.ext.escaped.character.EscapedCharacterExtension
import ssg.md.ext.tables.TablesExtension
import ssg.md.ext.toc.TocExtension
import ssg.md.ext.typographic.TypographicExtension
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.{ Arrays, HashMap }
import scala.language.implicitConversions

final class ComboExtAttributesSpecTest extends RendererSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboExtAttributesSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboExtAttributesSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboExtAttributesSpecTest.OPTIONS_MAP
}

object ComboExtAttributesSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/attributes/test/ext_attributes_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboExtAttributesSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet()
    .set(HtmlRenderer.RENDER_HEADER_ID, true)
    .set(AttributesExtension.ASSIGN_TEXT_ATTRIBUTES, true)
    .set(
      Parser.EXTENSIONS,
      Arrays.asList(
        AttributesExtension.create(),
        TocExtension.create(),
        EmojiExtension.create(),
        DefinitionExtension.create(),
        EscapedCharacterExtension.create(),
        TypographicExtension.create(),
        TablesExtension.create()
      )
    )
    .toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put(
      "anchors",
      new MutableDataSet()
        .set(
          Parser.EXTENSIONS,
          Arrays.asList(AnchorLinkExtension.create(), AttributesExtension.create(), TocExtension.create(), EmojiExtension.create())
        )
        .set(AnchorLinkExtension.ANCHORLINKS_WRAP_TEXT, false)
        .set(HtmlRenderer.RENDER_HEADER_ID, false)
        .toImmutable
    )
    map.put("text-attributes", new MutableDataSet().set(AttributesExtension.ASSIGN_TEXT_ATTRIBUTES, true).toImmutable)
    map.put("no-text-attributes", new MutableDataSet().set(AttributesExtension.ASSIGN_TEXT_ATTRIBUTES, false).toImmutable)
    map.put("dont-wrap-non-attributes", new MutableDataSet().set(AttributesExtension.WRAP_NON_ATTRIBUTE_TEXT, false).toImmutable)
    map.put(
      "empty-implicit-delimiters",
      new MutableDataSet().set(AttributesExtension.USE_EMPTY_IMPLICIT_AS_SPAN_DELIMITER, true).toImmutable
    )
    map.put("no-info-attributes", new MutableDataSet().set(AttributesExtension.FENCED_CODE_INFO_ATTRIBUTES, false).toImmutable)
    map.put("info-attributes", new MutableDataSet().set(AttributesExtension.FENCED_CODE_INFO_ATTRIBUTES, true).toImmutable)
    map.put(
      "fenced-code-to-both",
      new MutableDataSet().set(AttributesExtension.FENCED_CODE_ADD_ATTRIBUTES, FencedCodeAddType.ADD_TO_PRE_CODE).toImmutable
    )
    map.put(
      "fenced-code-to-pre",
      new MutableDataSet().set(AttributesExtension.FENCED_CODE_ADD_ATTRIBUTES, FencedCodeAddType.ADD_TO_PRE).toImmutable
    )
    map.put(
      "fenced-code-to-code",
      new MutableDataSet().set(AttributesExtension.FENCED_CODE_ADD_ATTRIBUTES, FencedCodeAddType.ADD_TO_CODE).toImmutable
    )
    map
  }
}
