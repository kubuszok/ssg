/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/.../ComboAttributesTranslationFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package attributes
package test

import ssg.md.Nullable
import ssg.md.ext.anchorlink.AnchorLinkExtension
import ssg.md.ext.attributes.AttributesExtension
import ssg.md.ext.emoji.EmojiExtension
import ssg.md.ext.toc.TocExtension
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.test.util.TranslationFormatterSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.{ Arrays, Collections, HashMap }
import scala.language.implicitConversions

final class ComboAttributesTranslationFormatterSpecTest extends TranslationFormatterSpecTestSuite {
  override def specResource:         ResourceLocation                       = ComboAttributesTranslationFormatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions:       Nullable[DataHolder]                   = Nullable(ComboAttributesTranslationFormatterSpecTest.OPTIONS)
  override def optionsMap:           java.util.Map[String, ? <: DataHolder] = ComboAttributesTranslationFormatterSpecTest.OPTIONS_MAP
  override def knownFailurePrefixes: Set[String]                            = Set(
    "Anchor Targets -",
    "Attributes -",
    "Attributes on Reference -",
    "Headings Tests -",
    "No Previous Sibling -",
    "Non Text Node Previous Sibling -",
    "Paragraphs -",
    "Random Tests -",
    "TOC -",
    "Text Node Previous Sibling -"
  )
}

object ComboAttributesTranslationFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/attributes/test/ext_attributes_translation_format_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboAttributesTranslationFormatterSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(AttributesExtension.create())).toImmutable

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
    map
  }
}
