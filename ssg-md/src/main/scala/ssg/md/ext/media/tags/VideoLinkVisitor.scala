/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-media-tags/src/main/java/com/vladsch/flexmark/ext/media/tags/VideoLinkVisitor.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-media-tags/src/main/java/com/vladsch/flexmark/ext/media/tags/VideoLinkVisitor.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package media
package tags

trait VideoLinkVisitor {
  def visit(node: VideoLink): Unit
}
