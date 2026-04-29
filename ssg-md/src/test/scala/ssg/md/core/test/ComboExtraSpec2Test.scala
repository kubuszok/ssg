/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../renderer/ComboExtraSpec2Test.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
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

/** Extra spec test 2 — parses core_extra_ast_spec2.md and validates each example's markdown->HTML rendering.
  */
final class ComboExtraSpec2Test extends RendererSpecTestSuite {

  override def specResource: ResourceLocation = ComboExtraSpec2Test.RESOURCE_LOCATION

  override def defaultOptions: Nullable[DataHolder] = Nullable(ComboExtraSpec2Test.MERGED_OPTIONS)

  override def optionsMap: ju.Map[String, ? <: DataHolder] = CoreRendererOptions.OPTIONS_MAP
}

object ComboExtraSpec2Test {
  val SPEC_RESOURCE:     String           = "/ssg/md/core/test/core_extra_ast_spec2.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboExtraSpec2Test], SPEC_RESOURCE)

  val OPTIONS: DataHolder = new MutableDataSet().set(HtmlRenderer.PERCENT_ENCODE_URLS, true).toImmutable

  /** Merged: CoreRendererOptions.BASE_OPTIONS + this test's OPTIONS */
  val MERGED_OPTIONS: DataHolder =
    DataSet.aggregate(Nullable(CoreRendererOptions.BASE_OPTIONS), Nullable(OPTIONS)).toImmutable
}
