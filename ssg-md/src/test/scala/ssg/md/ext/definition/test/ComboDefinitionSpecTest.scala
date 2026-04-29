/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-definition/.../ComboDefinitionSpecTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package ext
package definition
package test

import ssg.md.Nullable
import ssg.md.ext.definition.DefinitionExtension
import ssg.md.html.HtmlRenderer
import ssg.md.parser.Parser
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.{ Collections, HashMap }
import scala.language.implicitConversions

final class ComboDefinitionSpecTest extends RendererSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboDefinitionSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboDefinitionSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboDefinitionSpecTest.OPTIONS_MAP
}

object ComboDefinitionSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/definition/test/ext_definition_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboDefinitionSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(DefinitionExtension.create())).toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put("blank-lines-in-ast", new MutableDataSet().set(Parser.BLANK_LINES_IN_AST, true).toImmutable)
    map.put("no-auto-loose", new MutableDataSet().set(Parser.LISTS_AUTO_LOOSE, false).toImmutable)
    map.put("break-list", new MutableDataSet().set(DefinitionExtension.DOUBLE_BLANK_LINE_BREAKS_LIST, true).toImmutable)
    map.put(
      "suppress-format-eol",
      new MutableDataSet().set(HtmlRenderer.HTML_BLOCK_OPEN_TAG_EOL, false).set(HtmlRenderer.HTML_BLOCK_CLOSE_TAG_EOL, false).set(HtmlRenderer.INDENT_SIZE, 0).toImmutable
    )
    map
  }
}
