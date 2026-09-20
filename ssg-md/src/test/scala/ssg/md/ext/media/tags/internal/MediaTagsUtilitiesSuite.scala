/* Copyright (c) 2026 SSG contributors SPDX-License-Identifier: Apache-2.0 */
package ssg
package md
package ext
package media
package tags
package internal

import munit.FunSuite

/** The type-resolution tests of MediaTagsExtensionSuite, placed in this package because java declares Utilities and its methods package-private. */
class MediaTagsUtilitiesSuite extends FunSuite {

  test("Utilities resolves audio types") {
    assertEquals(Nullable(Utilities.resolveAudioType("file.mp3")).get, "audio/mpeg")
    assertEquals(Nullable(Utilities.resolveAudioType("file.ogg")).get, "audio/ogg")
    assert(Nullable(Utilities.resolveAudioType("file.unknown")).isEmpty)
    assert(Nullable(Utilities.resolveAudioType("noextension")).isEmpty)
  }

  test("Utilities resolves video types") {
    assertEquals(Nullable(Utilities.resolveVideoType("file.mp4")).get, "video/mp4")
    assertEquals(Nullable(Utilities.resolveVideoType("file.webm")).get, "video/webm")
    assert(Nullable(Utilities.resolveVideoType("file.unknown")).isEmpty)
  }
}
