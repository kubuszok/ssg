/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-gfm-strikethrough/src/main/java/com/vladsch/flexmark/ext/gfm/strikethrough/StrikethroughVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package gfm
package strikethrough

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object StrikethroughVisitorExt {
  def VISIT_HANDLERS[V <: StrikethroughVisitor](visitor: V): Array[VisitHandler[?]] = {
    Array(new VisitHandler[Strikethrough](classOf[Strikethrough], visitor.visit(_)))
  }
}
