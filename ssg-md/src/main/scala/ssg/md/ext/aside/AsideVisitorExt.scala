/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-aside/src/main/java/com/vladsch/flexmark/ext/aside/AsideVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package aside

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object AsideVisitorExt {
  def VISIT_HANDLERS[V <: AsideVisitor](visitor: V): Array[VisitHandler[?]] = {
    Array(new VisitHandler[AsideBlock](classOf[AsideBlock], visitor.visit(_)))
  }
}
