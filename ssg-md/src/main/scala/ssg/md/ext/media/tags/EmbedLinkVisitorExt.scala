/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-media-tags/src/main/java/com/vladsch/flexmark/ext/media/tags/EmbedLinkVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package media
package tags

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object EmbedLinkVisitorExt {
  def VISIT_HANDLERS[V <: EmbedLinkVisitor](visitor: V): Array[VisitHandler[?]] = {
    Array(new VisitHandler[EmbedLink](classOf[EmbedLink], visitor.visit(_)))
  }
}
