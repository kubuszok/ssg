/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package md
package ext
package resizable
package image

import munit.FunSuite

import scala.language.implicitConversions

/** Test suite for the ResizableImage extension.
  *
  * Spec resource: ext/resizable/image/ext_resizable_image_spec.md
  *
  * TODO: Implement spec-based rendering tests once the test harness (FlexmarkSpecExampleRenderer) is ported from flexmark-test-util.
  */
class ResizableImageExtensionSuite extends FunSuite {

  test("ResizableImageExtension can be created") {
    val ext = ResizableImageExtension.create()
    assert(ext != null)
  }

  test("ResizableImage node holds text, source, width, height") {
    import ssg.md.util.sequence.BasedSequence
    // Use a single base sequence so spanningChars works
    val base   = BasedSequence.of("alt text|image.png|100|200")
    val text   = base.subSequence(0, 8) // "alt text"
    val source = base.subSequence(9, 18) // "image.png"
    val width  = base.subSequence(19, 22) // "100"
    val height = base.subSequence(23, 26) // "200"
    val node   = new ResizableImage(text, source, width, height)
    assertEquals(node.text.toString, "alt text")
    assertEquals(node.source.toString, "image.png")
    assertEquals(node.width.toString, "100")
    assertEquals(node.height.toString, "200")
  }
}
