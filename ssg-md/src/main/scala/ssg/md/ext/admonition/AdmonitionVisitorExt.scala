/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-admonition/src/main/java/com/vladsch/flexmark/ext/admonition/AdmonitionVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package admonition

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object AdmonitionVisitorExt {
  def VISIT_HANDLERS[V <: AdmonitionVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(new VisitHandler[AdmonitionBlock](classOf[AdmonitionBlock], visitor.visit(_)))
}
