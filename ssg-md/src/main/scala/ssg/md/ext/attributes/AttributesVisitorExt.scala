/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-attributes/src/main/java/com/vladsch/flexmark/ext/attributes/AttributesVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package attributes

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object AttributesVisitorExt {
  def VISIT_HANDLERS[V <: AttributesVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(
      new VisitHandler[AttributesNode](classOf[AttributesNode], visitor.visit(_)),
      new VisitHandler[AttributeNode](classOf[AttributeNode], visitor.visit(_))
    )
}
