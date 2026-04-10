/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package md
package ext
package youtube
package embedded

import munit.FunSuite

/** Test suite for the YouTubeLink extension.
  *
  * Spec resource: ext/youtube/embedded/ext_youtube_link_spec.md
  *
  * TODO: Implement spec-based rendering tests once the test harness (FlexmarkSpecExampleRenderer) is ported from flexmark-test-util.
  */
class YouTubeLinkExtensionSuite extends FunSuite {

  test("YouTubeLinkExtension can be created") {
    val ext = YouTubeLinkExtension.create()
    assert(ext != null)
  }

  test("YouTubeLink node can be instantiated") {
    val node = new YouTubeLink()
    assert(node != null)
  }

  // --- End-to-end rendering tests ---

  import ssg.md.html.HtmlRenderer
  import ssg.md.parser.Parser

  private def render(markdown: String): String = {
    val extensions = java.util.List.of(YouTubeLinkExtension.create())
    val parser     = Parser.builder().extensions(extensions).build()
    val renderer   = HtmlRenderer.builder().extensions(extensions).build()
    val doc        = parser.parse(markdown)
    renderer.render(doc)
  }

  test("e2e: youtu.be short URL renders as iframe with youtube-nocookie.com") {
    val html = render("@[Video](https://youtu.be/abc123)\n")
    assert(html.contains("<iframe"), s"Should render as iframe, got: $html")
    assert(html.contains("src=\"https://www.youtube-nocookie.com/embed/abc123\""), s"Should rewrite to youtube-nocookie embed URL, got: $html")
    assert(html.contains("width=\"560\""), s"Should have width 560, got: $html")
    assert(html.contains("height=\"315\""), s"Should have height 315, got: $html")
    assert(html.contains("class=\"youtube-embedded\""), s"Should have youtube-embedded class, got: $html")
    assert(html.contains("allowfullscreen=\"true\""), s"Should have allowfullscreen, got: $html")
    assert(html.contains("</iframe>"), s"Should close iframe, got: $html")
  }

  test("e2e: youtube.com/watch URL renders as iframe with embed URL") {
    val html = render("@[Video](https://www.youtube.com/watch?v=xyz789)\n")
    assert(html.contains("<iframe"), s"Should render as iframe, got: $html")
    assert(html.contains("src=\"https://www.youtube.com/embed/xyz789\""), s"Should rewrite watch URL to embed URL, got: $html")
    assert(html.contains("width=\"420\""), s"Should have width 420, got: $html")
    assert(html.contains("height=\"315\""), s"Should have height 315, got: $html")
    assert(html.contains("class=\"youtube-embedded\""), s"Should have youtube-embedded class, got: $html")
  }

  test("e2e: non-YouTube URL renders as regular link") {
    val html = render("@[Click here](https://example.com/video)\n")
    assert(html.contains("<a"), s"Should render as anchor tag, got: $html")
    assert(html.contains("href=\"https://example.com/video\""), s"Should have href to original URL, got: $html")
    assert(html.contains("Click here"), s"Should contain link text, got: $html")
    assert(!html.contains("<iframe"), s"Should NOT render as iframe, got: $html")
  }

  test("e2e: youtu.be URL with timestamp converts t= to start=") {
    val html = render("@[Video](https://youtu.be/abc123?t=42)\n")
    assert(html.contains("<iframe"), s"Should render as iframe, got: $html")
    assert(html.contains("start=42"), s"Should convert ?t= to ?start=, got: $html")
    assert(!html.contains("?t="), s"Should NOT contain original ?t= parameter, got: $html")
  }

  test("e2e: youtube link with frameborder and allow attributes") {
    val html = render("@[Demo](https://youtu.be/test456)\n")
    assert(html.contains("frameborder=\"0\""), s"Should have frameborder=0, got: $html")
    assert(html.contains("allow=\"accelerometer; autoplay; encrypted-media; gyroscope; picture-in-picture\""), s"Should have allow attribute, got: $html")
  }
}
