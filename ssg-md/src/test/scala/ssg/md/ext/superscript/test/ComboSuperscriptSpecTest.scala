/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-superscript/.../ComboSuperscriptSpecTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package ext
package superscript
package test

import ssg.md.Nullable
import ssg.md.ext.superscript.SuperscriptExtension
import ssg.md.parser.Parser
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.{ Collections, HashMap }
import scala.language.implicitConversions

final class ComboSuperscriptSpecTest extends RendererSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboSuperscriptSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboSuperscriptSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboSuperscriptSpecTest.OPTIONS_MAP
}

object ComboSuperscriptSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/superscript/test/superscript_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboSuperscriptSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(SuperscriptExtension.create())).toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put(
      "style-superscript",
      new MutableDataSet().set(SuperscriptExtension.SUPERSCRIPT_STYLE_HTML_OPEN, "<span class=\"text-sup\">").set(SuperscriptExtension.SUPERSCRIPT_STYLE_HTML_CLOSE, "</span>").toImmutable
    )
    map
  }
}
