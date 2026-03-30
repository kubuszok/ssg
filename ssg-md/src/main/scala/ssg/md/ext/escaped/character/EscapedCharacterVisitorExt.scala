/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-escaped-character/src/main/java/com/vladsch/flexmark/ext/escaped/character/EscapedCharacterVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package escaped
package character

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object EscapedCharacterVisitorExt {
  def VISIT_HANDLERS[V <: EscapedCharacterVisitor](visitor: V): Array[VisitHandler[?]] = {
    Array(new VisitHandler[EscapedCharacter](classOf[EscapedCharacter], visitor.visit(_)))
  }
}
