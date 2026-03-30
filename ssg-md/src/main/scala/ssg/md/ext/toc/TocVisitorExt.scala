/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-toc/src/main/java/com/vladsch/flexmark/ext/toc/TocVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package toc

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object TocVisitorExt {
  def VISIT_HANDLERS[V <: TocVisitor](visitor: V): Array[VisitHandler[?]] = {
    Array(new VisitHandler[TocBlock](classOf[TocBlock], visitor.visit(_)))
  }
}
