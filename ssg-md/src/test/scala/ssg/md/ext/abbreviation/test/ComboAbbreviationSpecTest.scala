/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-abbreviation/.../ComboAbbreviationSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package abbreviation
package test

import ssg.md.Nullable
import ssg.md.ext.abbreviation.AbbreviationExtension
import ssg.md.ext.escaped.character.EscapedCharacterExtension
import ssg.md.ext.gfm.strikethrough.StrikethroughSubscriptExtension
import ssg.md.ext.ins.InsExtension
import ssg.md.ext.superscript.SuperscriptExtension
import ssg.md.ext.typographic.TypographicExtension
import ssg.md.parser.Parser
import ssg.md.test.util.{ RendererSpecTestSuite, TestUtils }
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.{ Arrays, Collections, HashMap }
import scala.language.implicitConversions

final class ComboAbbreviationSpecTest extends RendererSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboAbbreviationSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboAbbreviationSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboAbbreviationSpecTest.OPTIONS_MAP
}

object ComboAbbreviationSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/abbreviation/test/ext_abbreviation_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboAbbreviationSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet()
    .set(
      Parser.EXTENSIONS,
      Arrays.asList(
        EscapedCharacterExtension.create(),
        AbbreviationExtension.create(),
        TypographicExtension.create(),
        InsExtension.create(),
        StrikethroughSubscriptExtension.create(),
        SuperscriptExtension.create()
      )
    )
    .toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put("links", new MutableDataSet().set(AbbreviationExtension.USE_LINKS, true).toImmutable)
    map.put(
      "no-abbr",
      new MutableDataSet().set(TestUtils.UNLOAD_EXTENSIONS, Collections.singletonList(classOf[AbbreviationExtension])).toImmutable
    )
    map
  }
}
