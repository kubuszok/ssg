/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-media-tags/.../ComboMediaTagsEmbedLinkSpecTest.java Original: Copyright (c) 2018 Cornelia Ada Schultz Original license: BSD-2-Clause */
package ssg
package md
package ext
package media
package tags
package test

import ssg.md.Nullable
import ssg.md.ext.media.tags.MediaTagsExtension
import ssg.md.parser.Parser
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Collections
import scala.language.implicitConversions

final class ComboMediaTagsEmbedLinkSpecTest extends RendererSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboMediaTagsEmbedLinkSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboMediaTagsEmbedLinkSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = new java.util.HashMap[String, DataHolder]()
}

object ComboMediaTagsEmbedLinkSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/media/tags/test/ext_media_tags_embed_link_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboMediaTagsEmbedLinkSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(MediaTagsExtension.create())).toImmutable
}
