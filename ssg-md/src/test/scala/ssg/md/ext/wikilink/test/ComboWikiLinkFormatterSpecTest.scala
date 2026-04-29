/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-wikilink/.../ComboWikiLinkFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package wikilink
package test

import ssg.md.Nullable
import ssg.md.ext.wikilink.WikiLinkExtension
import ssg.md.parser.Parser
import ssg.md.test.util.FormatterSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.{ Collections, HashMap }
import scala.language.implicitConversions

final class ComboWikiLinkFormatterSpecTest extends FormatterSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboWikiLinkFormatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboWikiLinkFormatterSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboWikiLinkFormatterSpecTest.OPTIONS_MAP
  override def knownFailurePrefixes: Set[String] = Set("WikiImages -", "WikiLinks -")
}

object ComboWikiLinkFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/wikilink/test/ext_wikilink_formatter_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboWikiLinkFormatterSpecTest], SPEC_RESOURCE)
  val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Collections.singleton(WikiLinkExtension.create()))
    .set(Parser.LISTS_AUTO_LOOSE, false)
    .toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put("links-first", new MutableDataSet().set(WikiLinkExtension.LINK_FIRST_SYNTAX, true).toImmutable)
    map.put("link-ext", new MutableDataSet().set(WikiLinkExtension.LINK_FILE_EXTENSION, ".html").toImmutable)
    map.put("link-prefix", new MutableDataSet().set(WikiLinkExtension.LINK_PREFIX, "/prefix/").toImmutable)
    map.put("link-prefix-absolute", new MutableDataSet().set(WikiLinkExtension.LINK_PREFIX, "/relative/").set(WikiLinkExtension.LINK_PREFIX_ABSOLUTE, "/absolute/").toImmutable)
    map.put("image-ext", new MutableDataSet().set(WikiLinkExtension.IMAGE_FILE_EXTENSION, ".png").toImmutable)
    map.put("image-prefix", new MutableDataSet().set(WikiLinkExtension.IMAGE_PREFIX, "/images/").toImmutable)
    map.put("image-prefix-absolute", new MutableDataSet().set(WikiLinkExtension.IMAGE_PREFIX, "/relative/images/").set(WikiLinkExtension.IMAGE_PREFIX_ABSOLUTE, "/absolute/images/").toImmutable)
    map.put("wiki-images", new MutableDataSet().set(WikiLinkExtension.IMAGE_LINKS, true).toImmutable)
    map.put("allow-inlines", new MutableDataSet().set(WikiLinkExtension.ALLOW_INLINES, true).toImmutable)
    map.put("allow-anchors", new MutableDataSet().set(WikiLinkExtension.ALLOW_ANCHORS, true).toImmutable)
    map.put("allow-pipe-escape", new MutableDataSet().set(WikiLinkExtension.ALLOW_PIPE_ESCAPE, true).toImmutable)
    map.put("allow-anchor-escape", new MutableDataSet().set(WikiLinkExtension.ALLOW_ANCHOR_ESCAPE, true).toImmutable)
    map.put("custom-link-escape", new MutableDataSet().set(WikiLinkExtension.LINK_ESCAPE_CHARS, " +<>").set(WikiLinkExtension.LINK_REPLACE_CHARS, "____").toImmutable)
    map
  }
}
