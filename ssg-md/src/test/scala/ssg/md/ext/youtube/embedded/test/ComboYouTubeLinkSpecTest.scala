/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-youtube-embedded/.../ComboYouTubeLinkSpecTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package ext
package youtube
package embedded
package test

import ssg.md.Nullable
import ssg.md.ext.youtube.embedded.YouTubeLinkExtension
import ssg.md.parser.Parser
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Collections
import scala.language.implicitConversions

final class ComboYouTubeLinkSpecTest extends RendererSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboYouTubeLinkSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboYouTubeLinkSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = new java.util.HashMap[String, DataHolder]()
}

object ComboYouTubeLinkSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/youtube/embedded/test/ext_youtube_link_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboYouTubeLinkSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(YouTubeLinkExtension.create())).toImmutable
}
