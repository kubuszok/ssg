/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-wikilink/src/main/java/com/vladsch/flexmark/ext/wikilink/WikiLinkVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 */
package ssg
package md
package ext
package wikilink

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object WikiLinkVisitorExt {
  def VISIT_HANDLERS[V <: WikiLinkVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(new VisitHandler[WikiLink](classOf[WikiLink], visitor.visit(_)))
}
