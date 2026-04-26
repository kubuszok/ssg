/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-ins/src/main/java/com/vladsch/flexmark/ext/ins/InsVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-ins/src/main/java/com/vladsch/flexmark/ext/ins/InsVisitorExt.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package ins

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object InsVisitorExt {
  def VISIT_HANDLERS[V <: InsVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(new VisitHandler[Ins](classOf[Ins], visitor.visit(_)))
}
