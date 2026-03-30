/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-abbreviation/src/main/java/com/vladsch/flexmark/ext/abbreviation/AbbreviationVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package abbreviation

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object AbbreviationVisitorExt {
  def VISIT_HANDLERS[V <: AbbreviationVisitor](visitor: V): Array[VisitHandler[?]] = {
    Array(
      new VisitHandler[AbbreviationBlock](classOf[AbbreviationBlock], visitor.visit(_)),
      new VisitHandler[Abbreviation](classOf[Abbreviation], visitor.visit(_))
    )
  }
}
