/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-superscript/src/main/java/com/vladsch/flexmark/ext/superscript/SuperscriptVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package superscript

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object SuperscriptVisitorExt {
  def VISIT_HANDLERS[V <: SuperscriptVisitor](visitor: V): Array[VisitHandler[?]] = {
    Array(new VisitHandler[Superscript](classOf[Superscript], visitor.visit(_)))
  }
}
