/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/DefinitionVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-definition/src/main/java/com/vladsch/flexmark/ext/definition/DefinitionVisitorExt.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package definition

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object DefinitionVisitorExt {
  def VISIT_HANDLERS[V <: DefinitionVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(
      new VisitHandler[DefinitionList](classOf[DefinitionList], visitor.visit(_)),
      new VisitHandler[DefinitionTerm](classOf[DefinitionTerm], visitor.visit(_)),
      new VisitHandler[DefinitionItem](classOf[DefinitionItem], visitor.visit(_))
    )
}
