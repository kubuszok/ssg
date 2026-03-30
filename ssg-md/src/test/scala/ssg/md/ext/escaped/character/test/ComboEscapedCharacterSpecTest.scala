/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-escaped-character/.../ComboEscapedCharacterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package escaped
package character
package test

import ssg.md.Nullable
import ssg.md.ext.escaped.character.EscapedCharacterExtension
import ssg.md.parser.Parser
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{DataHolder, MutableDataSet}

import java.util.Collections
import scala.language.implicitConversions

final class ComboEscapedCharacterSpecTest extends RendererSpecTestSuite {
  override def specResource: ResourceLocation = ComboEscapedCharacterSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder] = Nullable(ComboEscapedCharacterSpecTest.OPTIONS)
}

object ComboEscapedCharacterSpecTest {
  val SPEC_RESOURCE: String = "/ssg/md/ext/escaped/character/test/ext_escaped_character_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboEscapedCharacterSpecTest], SPEC_RESOURCE)
  val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Collections.singleton(EscapedCharacterExtension.create()))
    .toImmutable
}
