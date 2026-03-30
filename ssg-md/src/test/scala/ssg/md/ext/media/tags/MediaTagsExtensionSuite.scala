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

/**
 * Test suite for the MediaTags extension.
 *
 * Spec resources:
 *   ext/media/tags/ext_media_tags_audio_link_spec.md
 *   ext/media/tags/ext_media_tags_embed_link_spec.md
 *   ext/media/tags/ext_media_tags_picture_link_spec.md
 *   ext/media/tags/ext_media_tags_video_link_spec.md
 *
 * TODO: Implement spec-based rendering tests once the test harness
 * (FlexmarkSpecExampleRenderer) is ported from flexmark-test-util.
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
}
