/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
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

  // --- End-to-end rendering tests ---

  import ssg.md.html.HtmlRenderer
  import ssg.md.parser.Parser

  private def render(markdown: String): String = {
    val extensions = java.util.Collections.singletonList(ResizableImageExtension.create())
    val parser     = Parser.builder().extensions(extensions).build()
    val renderer   = HtmlRenderer.builder().extensions(extensions).build()
    val doc        = parser.parse(markdown)
    renderer.render(doc)
  }

  test("e2e: image with width and height renders img with dimensions") {
    val html = render("![alt](image.png =100x200)\n")
    assert(html.contains("<img"), s"Should contain <img> tag, got: $html")
    assert(html.contains("src=\"image.png\""), s"Should have src attribute, got: $html")
    assert(html.contains("alt=\"alt\""), s"Should have alt attribute, got: $html")
    assert(html.contains("width=\"100px\""), s"Should have width attribute, got: $html")
    assert(html.contains("height=\"200px\""), s"Should have height attribute, got: $html")
  }

  test("e2e: image with width only renders img with width") {
    val html = render("![photo](photo.jpg =300x)\n")
    assert(html.contains("<img"), s"Should contain <img> tag, got: $html")
    assert(html.contains("src=\"photo.jpg\""), s"Should have src attribute, got: $html")
    assert(html.contains("width=\"300px\""), s"Should have width attribute, got: $html")
    assert(!html.contains("height="), s"Should NOT have height attribute, got: $html")
  }

  test("e2e: image with height only renders img with height") {
    val html = render("![icon](icon.svg =x50)\n")
    assert(html.contains("<img"), s"Should contain <img> tag, got: $html")
    assert(html.contains("src=\"icon.svg\""), s"Should have src attribute, got: $html")
    assert(!html.contains("width="), s"Should NOT have width attribute, got: $html")
    assert(html.contains("height=\"50px\""), s"Should have height attribute, got: $html")
  }

  test("e2e: standard image without dimensions still renders normally") {
    // The extension only matches the =WxH syntax; standard images should pass through
    val html = render("![normal](normal.png)\n")
    assert(html.contains("<img"), s"Should contain <img> tag, got: $html")
    assert(html.contains("src=\"normal.png\""), s"Should have src attribute, got: $html")
    assert(html.contains("alt=\"normal\""), s"Should have alt attribute, got: $html")
  }

  test("e2e: image with both dimensions set to specific values") {
    val html = render("![banner](banner.jpg =800x120)\n")
    assert(html.contains("width=\"800px\""), s"Should have width 800px, got: $html")
    assert(html.contains("height=\"120px\""), s"Should have height 120px, got: $html")
    assert(html.contains("alt=\"banner\""), s"Should have alt text, got: $html")
  }
}
