/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-ins/.../ComboInsSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package ins
package test

import ssg.md.Nullable
import ssg.md.ext.ins.InsExtension
import ssg.md.parser.Parser
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.{ Collections, HashMap }
import scala.language.implicitConversions

final class ComboInsSpecTest extends RendererSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboInsSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboInsSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboInsSpecTest.OPTIONS_MAP
}

object ComboInsSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/ins/test/ins_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboInsSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(InsExtension.create())).toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put(
      "style-ins",
      new MutableDataSet().set(InsExtension.INS_STYLE_HTML_OPEN, "<span class=\"text-ins\">").set(InsExtension.INS_STYLE_HTML_CLOSE, "</span>").toImmutable
    )
    map
  }
}
