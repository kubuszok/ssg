/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-typographic/src/main/java/com/vladsch/flexmark/ext/typographic/TypographicVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package typographic

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object TypographicVisitorExt {

  def VISIT_HANDLERS[V <: TypographicVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(
      new VisitHandler[TypographicSmarts](classOf[TypographicSmarts], visitor.visit(_)),
      new VisitHandler[TypographicQuotes](classOf[TypographicQuotes], visitor.visit(_))
    )
}
