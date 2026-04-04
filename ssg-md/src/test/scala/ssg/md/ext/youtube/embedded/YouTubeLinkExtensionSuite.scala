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
}
