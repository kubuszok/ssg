/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-admonition/.../ComboAdmonitionFormatterSpecTest.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause */
package ssg
package md
package ext
package admonition
package test

import ssg.md.Nullable
import ssg.md.ext.admonition.AdmonitionExtension
import ssg.md.parser.Parser
import ssg.md.test.util.FormatterSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Collections
import scala.language.implicitConversions

final class ComboAdmonitionFormatterSpecTest extends FormatterSpecTestSuite {
  override def specResource:         ResourceLocation     = ComboAdmonitionFormatterSpecTest.RESOURCE_LOCATION
  override def defaultOptions:       Nullable[DataHolder] = Nullable(ComboAdmonitionFormatterSpecTest.OPTIONS)
  override def knownFailurePrefixes: Set[String]          = Set("Admonition Extension - Basic Tests -")
}

object ComboAdmonitionFormatterSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/admonition/test/ext_admonition_formatter_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboAdmonitionFormatterSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(AdmonitionExtension.create())).set(Parser.LISTS_AUTO_LOOSE, false).toImmutable
}
