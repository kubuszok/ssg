/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-aside/.../ComboAsideFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package aside
package test

import ssg.md.Nullable
import ssg.md.ext.aside.AsideExtension
import ssg.md.parser.Parser
import ssg.md.test.util.FormatterSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Collections
import scala.language.implicitConversions

final class ComboAsideFormatterSpecTest extends FormatterSpecTestSuite {
  override def specResource:   ResourceLocation     = ComboAsideFormatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder] = Nullable(ComboAsideFormatterSpecTest.OPTIONS)
  override def knownFailurePrefixes: Set[String] = Set("Aside -", "Combined -")
}

object ComboAsideFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/aside/test/ext_aside_formatter_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboAsideFormatterSpecTest], SPEC_RESOURCE)
  val OPTIONS: DataHolder = new MutableDataSet()
    .set(Parser.EXTENSIONS, Collections.singleton(AsideExtension.create()))
    .toImmutable
}
