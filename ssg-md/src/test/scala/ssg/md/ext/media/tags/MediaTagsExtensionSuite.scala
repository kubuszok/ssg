/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package ssg
package md
package ext
package media
package tags

import munit.FunSuite

/** Test suite for the MediaTags extension.
  *
  * Spec resources: ext/media/tags/ext_media_tags_audio_link_spec.md ext/media/tags/ext_media_tags_embed_link_spec.md ext/media/tags/ext_media_tags_picture_link_spec.md
  * ext/media/tags/ext_media_tags_video_link_spec.md
  *
  * TODO: Implement spec-based rendering tests once the test harness (FlexmarkSpecExampleRenderer) is ported from flexmark-test-util.
  */
class MediaTagsExtensionSuite extends FunSuite {

  test("MediaTagsExtension can be created") {
    val ext = MediaTagsExtension.create()
    assert(ext != null)
  }

  test("AudioLink has correct prefix") {
    assertEquals(AudioLink.PREFIX, "!A")
  }

  test("EmbedLink has correct prefix") {
    assertEquals(EmbedLink.PREFIX, "!E")
  }

  test("PictureLink has correct prefix") {
    assertEquals(PictureLink.PREFIX, "!P")
  }

  test("VideoLink has correct prefix") {
    assertEquals(VideoLink.PREFIX, "!V")
  }

  test("Utilities resolves audio types") {
    import ssg.md.ext.media.tags.internal.Utilities
    assertEquals(Utilities.resolveAudioType("file.mp3").get, "audio/mpeg")
    assertEquals(Utilities.resolveAudioType("file.ogg").get, "audio/ogg")
    assert(Utilities.resolveAudioType("file.unknown").isEmpty)
    assert(Utilities.resolveAudioType("noextension").isEmpty)
  }

  test("Utilities resolves video types") {
    import ssg.md.ext.media.tags.internal.Utilities
    assertEquals(Utilities.resolveVideoType("file.mp4").get, "video/mp4")
    assertEquals(Utilities.resolveVideoType("file.webm").get, "video/webm")
    assert(Utilities.resolveVideoType("file.unknown").isEmpty)
  }

  // --- End-to-end rendering tests ---

  import ssg.md.html.HtmlRenderer
  import ssg.md.parser.Parser

  private def render(markdown: String): String = {
    val extensions = java.util.Collections.singletonList(MediaTagsExtension.create())
    val parser     = Parser.builder().extensions(extensions).build()
    val renderer   = HtmlRenderer.builder().extensions(extensions).build()
    val doc        = parser.parse(markdown)
    renderer.render(doc)
  }

  test("e2e: audio link renders <audio> tag with <source>") {
    val html = render("!A[My Audio](audio.mp3)\n")
    assert(html.contains("<audio"), s"Should contain <audio> tag, got: $html")
    assert(html.contains("title=\"My Audio\""), s"Should have title attribute, got: $html")
    assert(html.contains("controls"), s"Should have controls attribute, got: $html")
    assert(html.contains("<source"), s"Should contain <source> tag, got: $html")
    assert(html.contains("src=\"audio.mp3\""), s"Should have src for source, got: $html")
    assert(html.contains("type=\"audio/mpeg\""), s"Should resolve audio/mpeg type for .mp3, got: $html")
    assert(html.contains("</audio>"), s"Should close audio tag, got: $html")
  }

  test("e2e: video link renders <video> tag with <source>") {
    val html = render("!V[My Video](video.mp4)\n")
    assert(html.contains("<video"), s"Should contain <video> tag, got: $html")
    assert(html.contains("title=\"My Video\""), s"Should have title attribute, got: $html")
    assert(html.contains("controls"), s"Should have controls attribute, got: $html")
    assert(html.contains("<source"), s"Should contain <source> tag, got: $html")
    assert(html.contains("src=\"video.mp4\""), s"Should have src for source, got: $html")
    assert(html.contains("type=\"video/mp4\""), s"Should resolve video/mp4 type for .mp4, got: $html")
    assert(html.contains("</video>"), s"Should close video tag, got: $html")
  }

  test("e2e: picture link renders <picture> tag with <img> fallback") {
    val html = render("!P[My Picture](image.png)\n")
    assert(html.contains("<picture>"), s"Should contain <picture> tag, got: $html")
    assert(html.contains("<img"), s"Should contain <img> fallback, got: $html")
    assert(html.contains("src=\"image.png\""), s"Should have src on img, got: $html")
    assert(html.contains("alt=\"My Picture\""), s"Should have alt on img, got: $html")
    assert(html.contains("</picture>"), s"Should close picture tag, got: $html")
  }

  test("e2e: embed link renders self-closing <embed> tag") {
    val html = render("!E[My Embed](content.swf)\n")
    assert(html.contains("<embed"), s"Should contain <embed> tag, got: $html")
    assert(html.contains("title=\"My Embed\""), s"Should have title attribute, got: $html")
    assert(html.contains("src=\"content.swf\""), s"Should have src attribute, got: $html")
  }

  test("e2e: audio link with multiple sources via pipe separator") {
    val html = render("!A[Song](song.ogg|song.mp3)\n")
    assert(html.contains("<audio"), s"Should contain <audio> tag, got: $html")
    // Should have two <source> elements
    val sourceCount = "<source".r.findAllMatchIn(html).length
    assert(sourceCount == 2, s"Should have 2 <source> elements, got $sourceCount in: $html")
    assert(html.contains("song.ogg"), s"Should contain first source, got: $html")
    assert(html.contains("song.mp3"), s"Should contain second source, got: $html")
  }

  test("e2e: video link with multiple sources via pipe separator") {
    val html = render("!V[Clip](clip.webm|clip.mp4)\n")
    assert(html.contains("<video"), s"Should contain <video> tag, got: $html")
    val sourceCount = "<source".r.findAllMatchIn(html).length
    assert(sourceCount == 2, s"Should have 2 <source> elements, got $sourceCount in: $html")
    assert(html.contains("clip.webm"), s"Should contain webm source, got: $html")
    assert(html.contains("clip.mp4"), s"Should contain mp4 source, got: $html")
  }

  test("e2e: picture link with multiple sources uses last as img") {
    val html = render("!P[Photo](photo.webp|photo.png)\n")
    assert(html.contains("<picture>"), s"Should contain <picture> tag, got: $html")
    // First source goes in <source srcset=...>, last goes in <img src=...>
    assert(html.contains("srcset=\"photo.webp\""), s"Should have srcset for first source, got: $html")
    assert(html.contains("src=\"photo.png\""), s"Should have src on img for last source, got: $html")
    assert(html.contains("alt=\"Photo\""), s"Should have alt on img, got: $html")
  }

  test("e2e: audio link with fallback text for unsupported browsers") {
    val html = render("!A[Track](track.wav)\n")
    assert(html.contains("Your browser does not support the audio element."), s"Should have fallback text, got: $html")
  }
}
