/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-typographic/.../ComboTypographicSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package typographic
package test

import ssg.md.Nullable
import ssg.md.ext.typographic.TypographicExtension
import ssg.md.parser.Parser
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.{ Collections, HashMap }
import scala.language.implicitConversions

final class ComboTypographicSpecTest extends RendererSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboTypographicSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboTypographicSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboTypographicSpecTest.OPTIONS_MAP
}

object ComboTypographicSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/typographic/test/ext_typographic_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboTypographicSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(TypographicExtension.create())).toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put("no-quotes", new MutableDataSet().set(TypographicExtension.ENABLE_QUOTES, false).toImmutable)
    map.put("no-smarts", new MutableDataSet().set(TypographicExtension.ENABLE_SMARTS, false).toImmutable)
    map
  }
}
