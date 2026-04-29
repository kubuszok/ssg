/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-core-test/.../renderer/ComboKramdownCompatibilitySpecTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package core
package test

import ssg.md.Nullable
import ssg.md.html.HtmlRenderer
import ssg.md.parser.ParserEmulationProfile
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, DataSet, MutableDataSet }

import java.{ util => ju }
import scala.language.implicitConversions

/** Kramdown compatibility spec test — parses core_kramdown_compatibility_spec.md with KRAMDOWN emulation profile.
  */
final class ComboKramdownCompatibilitySpecTest extends RendererSpecTestSuite {

  override def specResource: ResourceLocation = ComboKramdownCompatibilitySpecTest.RESOURCE_LOCATION

  override def defaultOptions: Nullable[DataHolder] = Nullable(ComboKramdownCompatibilitySpecTest.MERGED_OPTIONS)

  override def optionsMap: ju.Map[String, ? <: DataHolder] = CoreRendererOptions.OPTIONS_MAP

  override def knownFailures: Set[String] = Set(
    "List Item Indent Handling - 8",
    "List Item Indent Handling - 9",
    "List Item Indent Handling - 12",
    "Block quote parsing - 1",
    "Block quote parsing - 7",
    "Block quote parsing - 9",
    "Block quote parsing - 11"
  )
}

object ComboKramdownCompatibilitySpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/core/test/core_kramdown_compatibility_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboKramdownCompatibilitySpecTest], SPEC_RESOURCE)

  val OPTIONS: DataHolder = new MutableDataSet().setFrom(ParserEmulationProfile.KRAMDOWN).set(HtmlRenderer.INDENT_SIZE, 4).toImmutable

  /** Merged: CoreRendererOptions.BASE_OPTIONS + this test's OPTIONS */
  val MERGED_OPTIONS: DataHolder =
    DataSet.aggregate(Nullable(CoreRendererOptions.BASE_OPTIONS), Nullable(OPTIONS)).toImmutable
}
