/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-aside/.../ComboAsideSpecTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package ext
package aside
package test

import ssg.md.Nullable
import ssg.md.ext.aside.AsideExtension
import ssg.md.parser.Parser
import ssg.md.test.util.{ RendererSpecTestSuite, TestUtils }
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.{ Collections, HashMap }
import scala.language.implicitConversions

final class ComboAsideSpecTest extends RendererSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboAsideSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboAsideSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboAsideSpecTest.OPTIONS_MAP
}

object ComboAsideSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/aside/test/ext_aside_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboAsideSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(AsideExtension.create())).toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put(
      "extend-to-blank-line",
      new MutableDataSet().set(AsideExtension.EXTEND_TO_BLANK_LINE, true).set(Parser.BLOCK_QUOTE_EXTEND_TO_BLANK_LINE, true).toImmutable
    )
    map.put(
      "ignore-blank-line",
      new MutableDataSet().set(AsideExtension.IGNORE_BLANK_LINE, true).set(Parser.BLOCK_QUOTE_IGNORE_BLANK_LINE, true).toImmutable
    )
    map.put("blank-lines", new MutableDataSet().set(Parser.BLANK_LINES_IN_AST, true).set(TestUtils.NO_FILE_EOL, false).toImmutable)
    map
  }
}
