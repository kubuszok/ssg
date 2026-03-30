/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/AttributesExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package attributes

import ssg.md.ext.attributes.internal.*
import ssg.md.formatter.Formatter
import ssg.md.html.{HtmlRenderer, RendererBuilder, RendererExtension}
import ssg.md.parser.Parser
import ssg.md.util.ast.KeepType
import ssg.md.util.data.{DataKey, MutableDataHolder}
import ssg.md.util.format.options.DiscretionaryText

import scala.language.implicitConversions

/**
 * Extension for attributes
 *
 * Create it with [[AttributesExtension.create]] and then configure it on the builders
 *
 * The parsed attributes text is turned into [[AttributesNode]] nodes.
 */
class AttributesExtension private ()
    extends Parser.ParserExtension
    with RendererExtension
    with HtmlRenderer.HtmlRendererExtension
    with Formatter.FormatterExtension {

  override def parserOptions(options: MutableDataHolder): Unit = {
    if (options.contains(AttributesExtension.FENCED_CODE_INFO_ATTRIBUTES) && AttributesExtension.FENCED_CODE_INFO_ATTRIBUTES.get(options) && !options.contains(AttributesExtension.FENCED_CODE_ADD_ATTRIBUTES)) {
      // change default to pre only, to add to code use attributes after info
      options.set(AttributesExtension.FENCED_CODE_ADD_ATTRIBUTES, FencedCodeAddType.ADD_TO_PRE)
    }
  }

  override def extend(parserBuilder: Parser.Builder): Unit = {
    parserBuilder.postProcessorFactory(new AttributesNodePostProcessor.Factory())
    parserBuilder.customInlineParserExtensionFactory(new AttributesInlineParserExtension.Factory())
  }

  override def extend(formatterBuilder: Formatter.Builder, rendererType: String): Unit = {
    formatterBuilder.nodeFormatterFactory(new AttributesNodeFormatter.Factory())
  }

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit = {
    if (AttributesExtension.ASSIGN_TEXT_ATTRIBUTES.get(htmlRendererBuilder)) {
      htmlRendererBuilder.nodeRendererFactory(new AttributesNodeRenderer.Factory())
    }
    htmlRendererBuilder.attributeProviderFactory(new AttributesAttributeProvider.Factory())
  }

  override def extend(rendererBuilder: RendererBuilder, rendererType: String): Unit = {
    rendererBuilder.attributeProviderFactory(new AttributesAttributeProvider.Factory())
  }
}

object AttributesExtension {

  val NODE_ATTRIBUTES: DataKey[NodeAttributeRepository] = new DataKey[NodeAttributeRepository]("NODE_ATTRIBUTES", new NodeAttributeRepository(null), (options: ssg.md.util.data.DataHolder) => new NodeAttributeRepository(options)) // @nowarn - Java interop
  val ATTRIBUTES_KEEP: DataKey[KeepType] = new DataKey[KeepType]("ATTRIBUTES_KEEP", KeepType.FIRST)
  val ASSIGN_TEXT_ATTRIBUTES: DataKey[Boolean] = new DataKey[Boolean]("ASSIGN_TEXT_ATTRIBUTES", true)
  val FENCED_CODE_INFO_ATTRIBUTES: DataKey[Boolean] = new DataKey[Boolean]("FENCED_CODE_INFO_ATTRIBUTES", false)
  val FENCED_CODE_ADD_ATTRIBUTES: DataKey[FencedCodeAddType] = new DataKey[FencedCodeAddType]("FENCED_CODE_ADD_ATTRIBUTES", FencedCodeAddType.ADD_TO_PRE_CODE)
  val WRAP_NON_ATTRIBUTE_TEXT: DataKey[Boolean] = new DataKey[Boolean]("WRAP_NON_ATTRIBUTE_TEXT", true)
  val USE_EMPTY_IMPLICIT_AS_SPAN_DELIMITER: DataKey[Boolean] = new DataKey[Boolean]("USE_EMPTY_IMPLICIT_AS_SPAN_DELIMITER", false)

  val FORMAT_ATTRIBUTES_COMBINE_CONSECUTIVE: DataKey[Boolean] = new DataKey[Boolean]("FORMAT_ATTRIBUTES_COMBINE_CONSECUTIVE", false)
  val FORMAT_ATTRIBUTES_SORT: DataKey[Boolean] = new DataKey[Boolean]("FORMAT_ATTRIBUTES_SORT", false)
  val FORMAT_ATTRIBUTES_SPACES: DataKey[DiscretionaryText] = new DataKey[DiscretionaryText]("FORMAT_ATTRIBUTES_SPACES", DiscretionaryText.AS_IS)
  val FORMAT_ATTRIBUTE_EQUAL_SPACE: DataKey[DiscretionaryText] = new DataKey[DiscretionaryText]("FORMAT_ATTRIBUTE_EQUAL_SPACE", DiscretionaryText.AS_IS)
  val FORMAT_ATTRIBUTE_VALUE_QUOTES: DataKey[AttributeValueQuotes] = new DataKey[AttributeValueQuotes]("FORMAT_ATTRIBUTE_VALUE_QUOTES", AttributeValueQuotes.AS_IS)
  val FORMAT_ATTRIBUTE_ID: DataKey[AttributeImplicitName] = new DataKey[AttributeImplicitName]("FORMAT_ATTRIBUTE_ID", AttributeImplicitName.AS_IS)
  val FORMAT_ATTRIBUTE_CLASS: DataKey[AttributeImplicitName] = new DataKey[AttributeImplicitName]("FORMAT_ATTRIBUTE_CLASS", AttributeImplicitName.AS_IS)

  def create(): AttributesExtension = new AttributesExtension()
}
