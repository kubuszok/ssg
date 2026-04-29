/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../renderer/ComboExtraSpecTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package core
package test

import ssg.md.Nullable
import ssg.md.html.HtmlRenderer
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, DataSet, MutableDataSet }

import java.{ util => ju }
import scala.language.implicitConversions

/** Extra spec test — parses core_extra_ast_spec.md and validates each example's markdown->HTML rendering.
  */
final class ComboExtraSpecTest extends RendererSpecTestSuite {

  override def specResource: ResourceLocation = ComboExtraSpecTest.RESOURCE_LOCATION

  override def defaultOptions: Nullable[DataHolder] = Nullable(ComboExtraSpecTest.MERGED_OPTIONS)

  override def optionsMap: ju.Map[String, ? <: DataHolder] = CoreRendererOptions.OPTIONS_MAP

  override def knownFailures: Set[String] = Set(
    "Heading options - 1",
    "Heading options - 3",
    "Heading options - 4",
    "Heading options - 6",
    "Heading options - 7",
    "Heading options - 8",
    "Heading options - 9"
  )
}

object ComboExtraSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/core/test/core_extra_ast_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboExtraSpecTest], SPEC_RESOURCE)

  val OPTIONS: DataHolder = new MutableDataSet().set(HtmlRenderer.PERCENT_ENCODE_URLS, true).toImmutable

  /** Merged: CoreRendererOptions.BASE_OPTIONS + this test's OPTIONS */
  val MERGED_OPTIONS: DataHolder =
    DataSet.aggregate(Nullable(CoreRendererOptions.BASE_OPTIONS), Nullable(OPTIONS)).toImmutable
}
