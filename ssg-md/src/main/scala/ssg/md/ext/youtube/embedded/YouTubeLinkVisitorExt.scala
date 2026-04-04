/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-youtube-embedded/src/main/java/com/vladsch/flexmark/ext/youtube/embedded/YouTubeLinkVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package youtube
package embedded

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object YouTubeLinkVisitorExt {
  def VISIT_HANDLERS[V <: YouTubeLinkVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(new VisitHandler[YouTubeLink](classOf[YouTubeLink], visitor.visit(_)))
}
