/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-macros/src/main/java/com/vladsch/flexmark/ext/macros/MacrosVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-macros/src/main/java/com/vladsch/flexmark/ext/macros/MacrosVisitorExt.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package macros

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object MacrosVisitorExt {
  def VISIT_HANDLERS[V <: MacrosVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(
      new VisitHandler[MacroReference](classOf[MacroReference], visitor.visit(_)),
      new VisitHandler[MacroDefinitionBlock](classOf[MacroDefinitionBlock], visitor.visit(_))
    )
}
