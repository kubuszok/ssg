/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-tag/.../ComboJekyllTagFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package jekyll
package tag
package test

import ssg.md.Nullable
import ssg.md.ext.jekyll.tag.JekyllTagExtension
import ssg.md.parser.Parser
import ssg.md.test.util.FormatterSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.{ Collections, HashMap }
import scala.language.implicitConversions

final class ComboJekyllTagFormatterSpecTest extends FormatterSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboJekyllTagFormatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboJekyllTagFormatterSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboJekyllTagFormatterSpecTest.OPTIONS_MAP
  override def knownFailurePrefixes: Set[String] = Set("Jekyll Tag -")
}

object ComboJekyllTagFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/jekyll/tag/test/ext_jekyll_tag_formatter_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboJekyllTagFormatterSpecTest], SPEC_RESOURCE)
  val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Collections.singleton(JekyllTagExtension.create()))
    .set(JekyllTagExtension.EMBED_INCLUDED_CONTENT, false)
    .toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put("dummy-identifier", new MutableDataSet().set(Parser.INTELLIJ_DUMMY_IDENTIFIER, true).toImmutable)
    map.put("no-inlines", new MutableDataSet().set(JekyllTagExtension.ENABLE_INLINE_TAGS, false).toImmutable)
    map.put("no-blocks", new MutableDataSet().set(JekyllTagExtension.ENABLE_BLOCK_TAGS, false).toImmutable)
    map.put("embed-includes", new MutableDataSet().set(JekyllTagExtension.EMBED_INCLUDED_CONTENT, true).toImmutable)
    val content = new HashMap[String, String]()
    content.put("test.html", "<h1>Heading 1</h1>\n<p>test text</p>\n")
    content.put("test2.md", "Included Text\n")
    content.put("links.html", "")
    map.put("includes", new MutableDataSet().set(JekyllTagExtension.INCLUDED_HTML, content).toImmutable)
    map
  }
}
