/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-strikethrough/.../ComboStrikethroughSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gfm
package strikethrough
package test

import ssg.md.Nullable
import ssg.md.ext.gfm.strikethrough.StrikethroughExtension
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.{ Collections, HashMap }
import scala.language.implicitConversions

final class ComboStrikethroughSpecTest extends RendererSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboStrikethroughSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboStrikethroughSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboStrikethroughSpecTest.OPTIONS_MAP
}

object ComboStrikethroughSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/gfm/strikethrough/test/ext_gfm_strikethrough_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboStrikethroughSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(HtmlRenderer.INDENT_SIZE, 0).set(Parser.EXTENSIONS, Collections.singleton(StrikethroughExtension.create())).toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put(
      "style-strikethrough",
      new MutableDataSet().set(StrikethroughExtension.STRIKETHROUGH_STYLE_HTML_OPEN, "<span class=\"text-strike\">").set(StrikethroughExtension.STRIKETHROUGH_STYLE_HTML_CLOSE, "</span>").toImmutable
    )
    map
  }
}
