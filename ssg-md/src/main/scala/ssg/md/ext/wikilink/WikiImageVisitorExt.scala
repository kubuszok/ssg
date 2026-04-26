/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-wikilink/src/main/java/com/vladsch/flexmark/ext/wikilink/WikiImageVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-wikilink/src/main/java/com/vladsch/flexmark/ext/wikilink/WikiImageVisitorExt.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package wikilink

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object WikiImageVisitorExt {
  def VISIT_HANDLERS[V <: WikiImageVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(new VisitHandler[WikiImage](classOf[WikiImage], visitor.visit(_)))
}
