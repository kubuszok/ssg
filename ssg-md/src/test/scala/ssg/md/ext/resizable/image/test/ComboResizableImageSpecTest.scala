/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-resizable-image/.../ComboResizableImageSpecTest.java Original: Copyright (c) 2016-2023 Vladimir Schneider Original license: BSD-2-Clause */
package ssg
package md
package ext
package resizable
package image
package test

import ssg.md.Nullable
import ssg.md.ext.resizable.image.ResizableImageExtension
import ssg.md.parser.Parser
import ssg.md.test.util.RendererSpecTestSuite
import ssg.md.test.util.spec.ResourceLocation
import ssg.md.util.data.{ DataHolder, MutableDataSet }

import java.util.Collections
import scala.language.implicitConversions

final class ComboResizableImageSpecTest extends RendererSpecTestSuite {
  override def specResource:   ResourceLocation                       = ComboResizableImageSpecTest.RESOURCE_LOCATION
  override def defaultOptions: Nullable[DataHolder]                   = Nullable(ComboResizableImageSpecTest.OPTIONS)
  override def optionsMap:     java.util.Map[String, ? <: DataHolder] = new java.util.HashMap[String, DataHolder]()
}

object ComboResizableImageSpecTest {
  val SPEC_RESOURCE:     String           = "/ssg/md/ext/resizable/image/test/ext_resizable_image_spec.md"
  val RESOURCE_LOCATION: ResourceLocation = ResourceLocation.of(classOf[ComboResizableImageSpecTest], SPEC_RESOURCE)
  val OPTIONS:           DataHolder       = new MutableDataSet().set(Parser.EXTENSIONS, Collections.singleton(ResizableImageExtension.create())).toImmutable
}
