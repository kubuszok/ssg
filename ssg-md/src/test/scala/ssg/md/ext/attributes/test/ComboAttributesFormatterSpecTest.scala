/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/.../ComboAttributesFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package attributes
package test

import ssg.md.Nullable
import ssg.md.ext.anchorlink.AnchorLinkExtension
import ssg.md.ext.attributes.{ AttributeImplicitName, AttributeValueQuotes, AttributesExtension }
import ssg.md.ext.emoji.EmojiExtension
import ssg.md.ext.toc.TocExtension
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.test.util.FormatterSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }
import ssg.md.util.format.options.DiscretionaryText

import java.util.{ Arrays, Collections, HashMap }
import scala.language.implicitConversions

final class ComboAttributesFormatterSpecTest extends FormatterSpecTestSuite {
  override def specResource:         ResourceLocation                       = ComboAttributesFormatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions:       Nullable[DataHolder]                   = Nullable(ComboAttributesFormatterSpecTest.OPTIONS)
  override def optionsMap:           java.util.Map[String, ? <: DataHolder] = ComboAttributesFormatterSpecTest.OPTIONS_MAP
  override def knownFailurePrefixes: Set[String]                            = Set(
    "Anchor Targets -",
    "Attributes on Reference -",
    "Format -",
    "Headings Tests -",
    "No Previous Sibling -",
    "Non Text Node Previous Sibling -",
    "Paragraphs -",
    "Random Tests -",
    "Spaces -",
    "TOC -",
    "Text Node Previous Sibling -"
  )
}

object ComboAttributesFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/attributes/test/ext_attributes_format_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboAttributesFormatterSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(AttributesExtension.create())).set(Parser.LISTS_AUTO_LOOSE, false).toImmutable

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
    map.put(
      "attributes-spaces-as-is",
      new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTES_SPACES, DiscretionaryText.AS_IS).toImmutable
    )
    map.put(
      "attributes-spaces-add",
      new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTES_SPACES, DiscretionaryText.ADD).toImmutable
    )
    map.put(
      "attributes-spaces-remove",
      new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTES_SPACES, DiscretionaryText.REMOVE).toImmutable
    )
    map.put(
      "sep-spaces-as-is",
      new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTE_EQUAL_SPACE, DiscretionaryText.AS_IS).toImmutable
    )
    map.put(
      "sep-spaces-add",
      new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTE_EQUAL_SPACE, DiscretionaryText.ADD).toImmutable
    )
    map.put(
      "sep-spaces-remove",
      new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTE_EQUAL_SPACE, DiscretionaryText.REMOVE).toImmutable
    )
    map.put(
      "value-quotes-as-is",
      new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTE_VALUE_QUOTES, AttributeValueQuotes.AS_IS).toImmutable
    )
    map.put(
      "value-quotes-no-quotes-single-preferred",
      new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTE_VALUE_QUOTES, AttributeValueQuotes.NO_QUOTES_SINGLE_PREFERRED).toImmutable
    )
    map.put(
      "value-quotes-no-quotes-double-preferred",
      new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTE_VALUE_QUOTES, AttributeValueQuotes.NO_QUOTES_DOUBLE_PREFERRED).toImmutable
    )
    map.put(
      "value-quotes-single-preferred",
      new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTE_VALUE_QUOTES, AttributeValueQuotes.SINGLE_PREFERRED).toImmutable
    )
    map.put(
      "value-quotes-double-preferred",
      new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTE_VALUE_QUOTES, AttributeValueQuotes.DOUBLE_PREFERRED).toImmutable
    )
    map.put(
      "value-quotes-single-quotes",
      new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTE_VALUE_QUOTES, AttributeValueQuotes.SINGLE_QUOTES).toImmutable
    )
    map.put(
      "value-quotes-double-quotes",
      new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTE_VALUE_QUOTES, AttributeValueQuotes.DOUBLE_QUOTES).toImmutable
    )
    map.put(
      "combine-consecutive",
      new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTES_COMBINE_CONSECUTIVE, true).toImmutable
    )
    map.put("sort-attributes", new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTES_SORT, true).toImmutable)
    map.put("id-as-is", new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTE_ID, AttributeImplicitName.AS_IS).toImmutable)
    map.put(
      "id-implicit",
      new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTE_ID, AttributeImplicitName.IMPLICIT_PREFERRED).toImmutable
    )
    map.put(
      "id-explicit",
      new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTE_ID, AttributeImplicitName.EXPLICIT_PREFERRED).toImmutable
    )
    map.put(
      "class-as-is",
      new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTE_CLASS, AttributeImplicitName.AS_IS).toImmutable
    )
    map.put(
      "class-implicit",
      new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTE_CLASS, AttributeImplicitName.IMPLICIT_PREFERRED).toImmutable
    )
    map.put(
      "class-explicit",
      new MutableDataSet().set(AttributesExtension.FORMAT_ATTRIBUTE_CLASS, AttributeImplicitName.EXPLICIT_PREFERRED).toImmutable
    )
    map
  }
}
