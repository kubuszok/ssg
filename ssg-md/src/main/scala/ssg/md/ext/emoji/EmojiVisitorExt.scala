/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-emoji/src/main/java/com/vladsch/flexmark/ext/emoji/EmojiVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-emoji/src/main/java/com/vladsch/flexmark/ext/emoji/EmojiVisitorExt.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package emoji

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object EmojiVisitorExt {
  def VISIT_HANDLERS[V <: EmojiVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(new VisitHandler[Emoji](classOf[Emoji], visitor.visit(_)))
}
