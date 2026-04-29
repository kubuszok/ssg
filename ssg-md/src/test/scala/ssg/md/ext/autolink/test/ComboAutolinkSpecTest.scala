/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-autolink/.../ComboAutolinkSpecTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package ext
package autolink
package test

import ssg.md.Nullable
import ssg.md.ext.autolink.AutolinkExtension
import ssg.md.ext.typographic.TypographicExtension
import ssg.md.parser.Parser
import ssg.md.test.util.{ RendererSpecTestSuite, TestUtils }
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.{ Arrays, Collections, HashMap }
import scala.language.implicitConversions

final class ComboAutolinkSpecTest extends RendererSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboAutolinkSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboAutolinkSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = ComboAutolinkSpecTest.OPTIONS_MAP
}

object ComboAutolinkSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/autolink/test/ext_autolink_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboAutolinkSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(AutolinkExtension.create())).toImmutable

  val OPTIONS_MAP: java.util.Map[String, DataHolder] = {
    val map = new HashMap[String, DataHolder]()
    map.put(
      "no-autolink",
      new MutableDataSet().set(TestUtils.UNLOAD_EXTENSIONS, Collections.singletonList(classOf[AutolinkExtension])).toImmutable
    )
    map.put("ignore-google", new MutableDataSet().set(AutolinkExtension.IGNORE_LINKS, "www.google.com").toImmutable)
    map.put("intellij-dummy", new MutableDataSet().set(Parser.INTELLIJ_DUMMY_IDENTIFIER, true).toImmutable)
    map.put(
      "typographic-ext",
      new MutableDataSet().set(Parser.EXTENSIONS, Arrays.asList(AutolinkExtension.create(), TypographicExtension.create())).toImmutable
    )
    map
  }
}
