/*
 * Copyright (c) 2026 SSG contributors
 * SPDX-License-Identifier: Apache-2.0
 *
 * Ported from: flexmark-ext-jekyll-front-matter/src/main/java/com/vladsch/flexmark/ext/jekyll/front/matter/JekyllFrontMatterVisitorExt.java
 * Original: Copyright (c) 2016-2023 Vladimir Schneider
 * Original license: BSD-2-Clause
 *
 * Covenant: full-port
 * Covenant-java-reference: flexmark-ext-jekyll-front-matter/src/main/java/com/vladsch/flexmark/ext/jekyll/front/matter/JekyllFrontMatterVisitorExt.java
 * Covenant-verified: 2026-04-26
 */
package ssg
package md
package ext
package jekyll
package front
package matter

import ssg.md.util.ast.VisitHandler
import scala.language.implicitConversions

object JekyllFrontMatterVisitorExt {

  def VISIT_HANDLERS[V <: JekyllFrontMatterVisitor](visitor: V): Array[VisitHandler[?]] =
    Array(
      new VisitHandler[JekyllFrontMatterBlock](classOf[JekyllFrontMatterBlock], visitor.visit(_))
    )
}
