/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-tag/src/main/java/com/vladsch/flexmark/ext/jekyll/tag/JekyllTagExtension.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package jekyll
package tag

import ssg.md.ext.jekyll.tag.internal.*
import ssg.md.formatter.Formatter
import ssg.md.html.{ HtmlRenderer, LinkResolverFactory, UriContentResolverFactory }
import ssg.md.parser.Parser
import ssg.md.util.data.{ DataKey, MutableDataHolder, NullableDataKey }

import java.util.{ ArrayList, Collections, List as JList, Map as JMap }
import scala.language.implicitConversions

/** Extension for jekyll_tags.
  *
  * Create it with [[JekyllTagExtension.create]] and then configure it on the builders.
  *
  * The parsed jekyll_tag text is turned into [[JekyllTag]] nodes.
  */
class JekyllTagExtension private () extends Parser.ParserExtension, HtmlRenderer.HtmlRendererExtension, Formatter.FormatterExtension {

  override def extend(formatterBuilder: Formatter.Builder, rendererType: String): Unit =
    formatterBuilder.nodeFormatterFactory(new JekyllTagNodeFormatter.Factory())

  override def rendererOptions(options: MutableDataHolder): Unit = {}

  override def parserOptions(options: MutableDataHolder): Unit = {}

  override def extend(parserBuilder: Parser.Builder): Unit = {
    if (JekyllTagExtension.ENABLE_BLOCK_TAGS.get(parserBuilder)) {
      parserBuilder.customBlockParserFactory(new JekyllTagBlockParser.Factory())
    }
    if (JekyllTagExtension.ENABLE_INLINE_TAGS.get(parserBuilder)) {
      parserBuilder.customInlineParserExtensionFactory(new JekyllTagInlineParserExtension.Factory())
    }

    val includeMap = JekyllTagExtension.INCLUDED_HTML.get(parserBuilder)
    @SuppressWarnings(Array("org.wartremover.warts.Null"))
    val hasIncludeMap = includeMap != null && !includeMap.isEmpty // @nowarn - Java interop: NullableDataKey may return null
    if (hasIncludeMap || !JekyllTagExtension.LINK_RESOLVER_FACTORIES.get(parserBuilder).isEmpty) {
      parserBuilder.postProcessorFactory(new IncludeNodePostProcessor.Factory())
    }
  }

  override def extend(htmlRendererBuilder: HtmlRenderer.Builder, rendererType: String): Unit =
    if ("HTML" == rendererType) {
      htmlRendererBuilder.nodeRendererFactory(new JekyllTagNodeRenderer.Factory())
    }
}

object JekyllTagExtension {

  val ENABLE_INLINE_TAGS:         DataKey[Boolean]                          = new DataKey[Boolean]("ENABLE_INLINE_TAGS", true)
  val ENABLE_BLOCK_TAGS:          DataKey[Boolean]                          = new DataKey[Boolean]("ENABLE_BLOCK_TAGS", true)
  val LIST_INCLUDES_ONLY:         DataKey[Boolean]                          = new DataKey[Boolean]("LIST_INCLUDES_ONLY", true)
  val EMBED_INCLUDED_CONTENT:     DataKey[Boolean]                          = new DataKey[Boolean]("EMBED_INCLUDED_CONTENT", false)
  val LINK_RESOLVER_FACTORIES:    DataKey[JList[LinkResolverFactory]]       = new DataKey[JList[LinkResolverFactory]]("LINK_RESOLVER_FACTORIES", Collections.emptyList())
  val CONTENT_RESOLVER_FACTORIES: DataKey[JList[UriContentResolverFactory]] = new DataKey[JList[UriContentResolverFactory]]("LINK_RESOLVER_FACTORIES", Collections.emptyList())
  val INCLUDED_HTML:              NullableDataKey[JMap[String, String]]     = new NullableDataKey[JMap[String, String]]("INCLUDED_HTML")
  val TAG_LIST:                   DataKey[JList[JekyllTag]]                 = new DataKey[JList[JekyllTag]](
    "TAG_LIST",
    new ssg.md.util.data.NotNullValueSupplier[JList[JekyllTag]] { override def get: JList[JekyllTag] = new ArrayList[JekyllTag]() }
  )

  /** @deprecated not used nor needed */
  @deprecated("not used nor needed", "2020/04/17")
  val ENABLE_RENDERING: DataKey[Boolean] = new DataKey[Boolean]("ENABLE_RENDERING", false)

  def create(): JekyllTagExtension = new JekyllTagExtension()
}
